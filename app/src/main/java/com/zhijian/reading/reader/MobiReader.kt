package com.zhijian.reading.reader

import com.zhijian.reading.data.FileMetadata
import com.zhijian.reading.data.EpubMeta
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset

/**
 * Un-DRM'd MOBI / AZW (KF7) and AZW3 (KF8) files: the PalmDB record list,
 * the PalmDOC header, the MOBI metadata header with its EXTH records,
 * per-record trailing-entry trimming (extra data flags, multibyte overlap)
 * and PalmDOC LZ77 decompression. Anything HUFF/CDIC compressed or
 * encrypted is refused with null, not half-read. AZW3/KF8 text records are
 * the same PalmDOC stream as KF7 — the KF8 skeleton only shapes pagination,
 * which our own engine does anyway — so one decoder serves both.
 *
 * Decompression notes, learned from real books:
 * - The LZ77 back reference packs distance and length as
 *   c = (b shl 8) or next, dist = (c shr 3) and 0x7FF, len = (c and 7) + 3
 *   — the same layout calibre's compressor emits.
 * - Files with MOBI header length >= 0xE4 and version >= 5 carry extra
 *   data flags (rec0[0xF2]): N varint trailing entries per record plus an
 *   optional multibyte-overlap trailer. Both must be trimmed BEFORE
 *   decompressing, or every record ends in garbage.
 */
object MobiReader {

    fun prepare(file: File, cacheRoot: File, theme: String = "light"): BookContent? {
        val mobi = parse(file) ?: run {
            android.util.Log.e("MobiReader", "parse returned null for ${file.name}")
            return null
        }
        return runCatching {
            val dir = File(cacheRoot, "book-" + file.absolutePath.hashCode() + "-" + file.length())
            val body = StringBuilder()

            // Images live in trailing records; extract the ones the text
            // cites (recindex is 1-based over the image block).
            val imageBase = mobi.firstImageIndex
            val imgDir = dir.apply { mkdirs() }
            val imageUris = mutableMapOf<Int, String>()
            if (imageBase > 0 && imageBase < mobi.recordCount) {
                for (n in 1..(mobi.recordCount - imageBase)) {
                    val bytes = record(file, mobi, imageBase + n - 1) ?: continue
                    val ext = imageExt(bytes) ?: continue
                    val f = File(imgDir, "img$n.$ext")
                    f.writeBytes(bytes)
                    imageUris[n] = f.toURI().toString()
                }
            }

            // The body keeps chapters only: a page that is just the book's
            // own table of contents (heading 目录 + a wall of links) lives
            // in the TOC panel, not in the reading flow. AZW3 books often
            // carry the TOC INSIDE the single content document, so a chunk
            // is stripped in place rather than dropped wholesale.
            val rawChunks = splitChunks(mobi.htmlBytes, mobi.encoding)
                .filter { it.text.isNotBlank() }
            var chunks = rawChunks
                .map { RawChunk(it.start, stripTocSection(it.text) ?: "") }
                .filter { it.text.isNotBlank() }
            if (chunks.isEmpty()) {
                // Everything looked like TOC — keep the book visible rather
                // than refusing to open.
                chunks = rawChunks
            }
            // Books that ship as one document (many AZW3) carry all their
            // chapters in a single section; splitting at the top headings
            // makes 每章独立成页 and the TOC panel work like elsewhere.
            if (chunks.size == 1) {
                val one = chunks[0]
                val marks = Regex("""<h[12]\b""", RegexOption.IGNORE_CASE)
                    .findAll(one.text).toList()
                if (marks.size > 1) {
                    val pieces = mutableListOf<RawChunk>()
                    pieces += RawChunk(one.start, one.text.substring(0, marks[0].range.first))
                    for (i in 1 until marks.size) {
                        val s = marks[i].range.first
                        val e = if (i + 1 < marks.size) marks[i + 1].range.first else one.text.length
                        pieces += RawChunk(one.start + s, one.text.substring(s, e))
                    }
                    val real = pieces.filter {
                        PageTemplate.stripTags(it.text).replace(Regex("\\s+"), "").isNotBlank()
                    }
                    if (real.size > 1) chunks = real
                }
            }

            val sections = chunks.mapIndexed { i, chunk ->
                PageTemplate.chapter(i, rewritten(chunk.text, imageUris))
            }
            if (sections.isEmpty()) return null
            sections.forEach { body.append(it) }

            val toc = buildToc(mobi.htmlBytes, chunks, mobi.encoding)
                ?: buildToc(chunks.map { it.text })
            BookContent(
                html = PageTemplate.wrap(mobi.title, body.toString(), theme),
                baseUrl = imgDir.toURI().toString(),
                title = mobi.title,
                dir = imgDir,
                toc = toc,
            )
        }.onFailure { android.util.Log.e("MobiReader", "prepare failed for ${file.name}", it) }.getOrNull()
    }

    fun metadata(file: File): FileMetadata? {
        val mobi = parse(file) ?: return null
        return runCatching {
            var cover: ByteArray? = null
            val coverOffset = mobi.exth[201L]?.let { if (it.size >= 4) u32(it, 0).toInt() else 0 }
            if (coverOffset != null && coverOffset > 0 && mobi.firstImageIndex > 0) {
                val idx = mobi.firstImageIndex + coverOffset.toInt()
                if (idx < mobi.recordCount) cover = record(file, mobi, idx)
            }
            FileMetadata(
                title = mobi.title,
                authors = mobi.authors,
                publisher = mobi.exth[101L]?.let { decodeText(it, mobi.encoding) } ?: "",
                comments = mobi.exth[503L]?.let { decodeText(it, mobi.encoding) } ?: "",
                cover = cover,
            )
        }.getOrNull()
    }

    // ---- structure -----------------------------------------------------------

    private class Mobi(
        val recordOffsets: List<Int>,
        val compression: Int,
        val textLength: Int,
        val textRecordCount: Int,
        val encoding: CharsetHandle,
        val title: String,
        val authors: List<String>,
        val exth: Map<Long, ByteArray>,
        val firstImageIndex: Int,
        val htmlBytes: ByteArray,
    ) {
        val recordCount: Int get() = recordOffsets.size - 1
    }

    private class CharsetHandle(val utf8: Boolean) {
        fun decode(b: ByteArray): String =
            if (utf8) String(b, Charsets.UTF_8) else String(b, windows1252())
    }

    private fun windows1252(): Charset =
        runCatching { Charset.forName("windows-1252") }.getOrDefault(Charsets.ISO_8859_1)

    private fun parse(file: File): Mobi? = runCatching {
        val raf = java.io.RandomAccessFile(file, "r")
        raf.use { r ->
            val head = ByteArray(78)
            if (r.read(head) != head.size) return null
            if (String(head, 60, 8) != "BOOKMOBI") return null
            val numRecords = u16(head, 76)
            if (numRecords < 2) return null
            val offsets = IntArray(numRecords + 1)
            val info = ByteArray(numRecords * 8)
            if (r.read(info) != info.size) return null
            var prev = -1
            for (i in 0 until numRecords) {
                val off = u32(info, i * 8).toInt()
                if (off <= prev) return null
                prev = off
                offsets[i] = off
            }
            offsets[numRecords] = file.length().toInt()
            val get: (Int) -> ByteArray? = { idx ->
                if (idx < 0 || idx >= numRecords) null
                else {
                    val len = offsets[idx + 1] - offsets[idx]
                    if (len <= 0) null else {
                        r.seek(offsets[idx].toLong())
                        val buf = ByteArray(len)
                        if (r.read(buf) != len) null else buf
                    }
                }
            }
            val rec0 = get(0) ?: return null

            val compression = u16(rec0, 0)
            val textLength = u32(rec0, 4).toInt()
            val textRecordCount = u16(rec0, 8)
            val encryption = u16(rec0, 12)
            if (encryption != 0) return null
            if (rec0.size < 16 || String(rec0, 16, 4) != "MOBI") return null
            val mobiLen = u32(rec0, 20).toInt()
            if (mobiLen <= 0 || 16 + mobiLen > rec0.size) return null

            // HUFF/CDIC (compression 17480) needs the huffman table records.
            var huff: HuffCdic? = null
            if (compression == 17480) {
                val huffOff = u32(rec0, 16 + 96).toInt()
                val huffCnt = u32(rec0, 16 + 100).toInt()
                if (huffOff <= 0 || huffCnt <= 0 || huffOff + huffCnt > numRecords) return null
                val recs = (huffOff until huffOff + huffCnt).mapNotNull { get(it) }
                huff = HuffCdic.parse(recs) ?: return null
            }
            val hasBoundary = indexOf(rec0, "BOUNDARY".toByteArray())

            var fullNameOffset = 0
            var fullNameLength = 0
            var textEncoding = 1252L
            var firstImageIndex = -1
            if (mobiLen >= 76) {
                fullNameOffset = u32(rec0, 16 + 68).toInt()
                fullNameLength = u32(rec0, 16 + 72).toInt()
                textEncoding = u32(rec0, 16 + 12)
            }
            if (mobiLen >= 96) {
                val fx = u32(rec0, 16 + 92).toInt()
                if (fx >= 0) firstImageIndex = fx
            }
            if (mobiLen >= 100) {
                val huffOff = u32(rec0, 16 + 96).toInt()
                if (huffOff > 0 && compression != 17480) return null
            }

            // Extra data flags (rec0[0xF2]): how many varint trailing
            // entries follow each text record, and whether a multibyte
            // character may straddle the record boundary.
            var trailers = 0
            var multibyte = false
            val mobiVersion = u32(rec0, 16 + 88).toInt()
            if (mobiLen >= 0xE4 && mobiVersion >= 5) {
                var flags = u16(rec0, 16 + 226)
                multibyte = flags and 1 == 1
                while (flags > 1) {
                    if (flags and 2 == 2) trailers++
                    flags = flags shr 1
                }
            }

            // A dual-mode file carries the MOBI7 half before the KF8
            // boundary; keep only those records.
            var limit = textRecordCount
            if (hasBoundary >= 0 && hasBoundary + 12 <= rec0.size) {
                val kf8Start = u32(rec0, hasBoundary + 8).toInt()
                if (kf8Start in 2 until limit) limit = kf8Start - 1
            }

            val exth = linkedMapOf<Long, ByteArray>()
            val exthFlags = if (mobiLen >= 116) u32(rec0, 16 + 112) else 0
            if (exthFlags and 0x40L != 0L) {
                var p = 16 + mobiLen
                if (p + 12 <= rec0.size && String(rec0, p, 4) == "EXTH") {
                    val count = u32(rec0, p + 8).toInt()
                    var q = p + 12
                    repeat(count) {
                        if (q + 8 > rec0.size) return@repeat
                        val type = u32(rec0, q)
                        val len = u32(rec0, q + 4).toInt()
                        if (len < 8 || q + len > rec0.size) return@repeat
                        exth[type] = rec0.copyOfRange(q + 8, q + len)
                        q += len
                    }
                }
            }

            val cs = CharsetHandle(textEncoding == 65001L)
            var title = ""
            if (fullNameOffset > 0 && fullNameOffset + fullNameLength <= rec0.size) {
                title = cs.decode(rec0.copyOfRange(fullNameOffset, fullNameOffset + fullNameLength)).trim()
            }
            val authors = mutableListOf<String>()
            exth[100L]?.let { authors += decodeText(it, cs) }
            // PalmDOC decompress and concatenate records 1..recordCount.
            val out = ByteArrayOutputStream(maxOf(4096, textLength))
            for (i in 1..limit) {
                val chunk = get(i) ?: continue
                val trimmed = trimTrailing(chunk, trailers, multibyte)
                val plain = when (compression) {
                    1 -> trimmed
                    2 -> palmDoc(trimmed, out.size() + trimmed.size * 8)
                    17480 -> huff?.unpack(trimmed) ?: return null
                    else -> return null
                }
                out.write(plain, 0, plain.size)
            }
            // Keep the decompressed text as raw bytes: the MOBI TOC's
            // filepos anchors are byte offsets into exactly this stream.
            var bytes = out.toByteArray()
            if (textLength > 0 && textLength <= bytes.size) bytes = bytes.copyOf(textLength)
            if (title.isBlank()) title = EpubMeta.fileTitleFallback(file.nameWithoutExtension)

            Mobi(
                recordOffsets = offsets.toList(),
                compression = compression,
                textLength = textLength,
                textRecordCount = textRecordCount,
                encoding = cs,
                title = title,
                authors = authors,
                exth = exth,
                firstImageIndex = firstImageIndex,
                htmlBytes = bytes,
            )
        }
    }.onFailure { android.util.Log.e("MobiReader", "parse threw for ${file.name}", it) }.getOrNull()

    private fun record(file: File, mobi: Mobi, index: Int): ByteArray? = runCatching {
        val offsets = mobi.recordOffsets
        if (index < 0 || index >= offsets.size - 1) return@runCatching null
        java.io.RandomAccessFile(file, "r").use { r ->
            val len = offsets[index + 1] - offsets[index]
            if (len <= 0) return@runCatching null
            r.seek(offsets[index].toLong())
            val buf = ByteArray(len)
            if (r.read(buf) != len) null else buf
        }
    }.getOrNull()

    // ---- PalmDOC LZ77 ---------------------------------------------------------

    private fun palmDoc(data: ByteArray, maxOut: Int): ByteArray {
        val out = ByteArrayOutputStream(maxOf(4096, maxOut))
        var i = 0
        while (i < data.size) {
            val b = data[i].toInt() and 0xFF
            when {
                b == 0 -> { out.write(0); i++ }
                b < 9 -> {
                    // Escape: the next b bytes pass through verbatim — the
                    // only channel for bytes ≥ 0x80 (UTF-8 Chinese).
                    val end = minOf(data.size, i + b + 1)
                    for (k in i + 1 until end) out.write(data[k].toInt())
                    i = end
                }
                b < 0x80 -> { out.write(b); i++ }
                b < 0xC0 -> {
                    if (i + 1 >= data.size) break
                    val next = data[i + 1].toInt() and 0xFF
                    val c = (b shl 8) or next
                    val dist = (c shr 3) and 0x7FF
                    val len = (c and 0x7) + 3
                    val size = out.size()
                    if (dist in 1..size) {
                        val src = out.toByteArray()
                        for (k in 0 until len) out.write(src[size - dist + (k % dist)].toInt())
                    }
                    i += 2
                }
                else -> { out.write(0x20); out.write(b and 0x7F); i++ }
            }
        }
        return out.toByteArray()
    }

    // ---- HUFF/CDIC (MOBI compression 17480, common in AZW3/KF8) --------------
    // Port of the classic KindleUnpack algorithm: the HUFF record holds two
    // tables (dict1: per-first-byte code length/termination/maxcode; dict2:
    // per-length min/max codes), the CDIC records hold phrase slices the
    // bitstream indexes into. Slices flagged "not decompressed" recurse.

    private class HuffCdic private constructor(
        private val codeLen: IntArray,
        private val term: BooleanArray,
        private val maxCode1: LongArray,
        private val minCode: LongArray,
        private val maxCode: LongArray,
    ) {
        // Mutable cache: (bytes, already-decompressed). Null entries are
        // reserved phrases the file never references.
        private val dictionary = ArrayList<Pair<ByteArray, Boolean>?>()

        fun unpack(data: ByteArray): ByteArray {
            var bitsLeft = data.size * 8
            val buf = data + ByteArray(8)
            var pos = 0
            var x = u64(buf, 0)
            var n = 32
            val out = ByteArrayOutputStream(4096)
            while (true) {
                if (n <= 0) {
                    pos += 4
                    x = u64(buf, pos)
                    n += 32
                }
                val code = (x ushr n) and 0xFFFFFFFFL
                val slot = (code ushr 24).toInt()
                var codelen = codeLen[slot]
                var limit = maxCode1[slot]
                if (!term[slot]) {
                    while (codelen < 33 && code < minCode[codelen]) codelen++
                    if (codelen > 32) throw IllegalStateException("huff code overrun")
                    limit = maxCode[codelen]
                }
                n -= codelen
                bitsLeft -= codelen
                if (bitsLeft < 0) break
                val r = ((limit - code) ushr (32 - codelen)).toInt()
                if (r < 0 || r >= dictionary.size) throw IllegalStateException("huff phrase $r out of range")
                val (raw, done) = dictionary[r] ?: throw IllegalStateException("huff phrase $r null")
                val slice = if (done) raw else {
                    val plain = unpack(raw)
                    dictionary[r] = plain to true
                    plain
                }
                out.write(slice)
            }
            return out.toByteArray()
        }

        companion object {
            fun parse(records: List<ByteArray>): HuffCdic? = runCatching {
                val huff = records.firstOrNull() ?: return null
                if (huff.size < 24 || String(huff, 0, 4) != "HUFF") return null
                if (u32(huff, 4) != 0x18L) return null
                val off1 = u32(huff, 8).toInt()
                val off2 = u32(huff, 12).toInt()
                if (off1 <= 0 || off1 + 256 * 4 > huff.size) return null
                if (off2 <= 0 || off2 + 64 * 4 > huff.size) return null

                val codeLen = IntArray(256)
                val term = BooleanArray(256)
                val maxCode1 = LongArray(256)
                for (i in 0 until 256) {
                    val v = u32(huff, off1 + 4 * i)
                    val cl = (v and 0x1F).toInt()
                    if (cl == 0) return null
                    codeLen[i] = cl
                    term[i] = v and 0x80L != 0L
                    // KindleUnpack keeps these UNMASKED (they can exceed 32
                    // bits); masking breaks the min/max comparisons.
                    maxCode1[i] = (((v ushr 8) + 1) shl (32 - cl)) - 1
                }

                val minCode = LongArray(33)
                val maxCode = LongArray(33)
                for (c in 1..32) {
                    val lo = u32(huff, off2 + 4 * ((c - 1) * 2))
                    val hi = u32(huff, off2 + 4 * ((c - 1) * 2 + 1))
                    minCode[c] = lo shl (32 - c)
                    maxCode[c] = ((hi + 1) shl (32 - c)) - 1
                }

                val cd = HuffCdic(codeLen, term, maxCode1, minCode, maxCode)
                for (k in 1 until records.size) {
                    val cdic = records[k]
                    if (cdic.size < 20 || String(cdic, 0, 4) != "CDIC") return null
                    if (u32(cdic, 4) != 0x10L) return null
                    val phrases = u32(cdic, 8).toInt()
                    val bits = u32(cdic, 12).toInt()
                    val n = minOf(1 shl bits, phrases - cd.dictionary.size)
                    if (n <= 0) break
                    for (i in 0 until n) {
                        val off = u16(cdic, 16 + 2 * i)
                        val blen = u16(cdic, 16 + off)
                        val slice = cdic.copyOfRange(18 + off, 18 + off + (blen and 0x7FFF))
                        cd.dictionary.add(slice to (blen and 0x8000 != 0))
                    }
                }
                cd
            }.getOrNull()
        }
    }

    private fun u64(b: ByteArray, at: Int): Long {
        var v = 0L
        for (k in 0 until 8) v = (v shl 8) or (b[at + k].toLong() and 0xFF)
        return v
    }

    private fun u16(b: ByteArray, at: Int): Int = (b[at].toInt() and 0xFF) shl 8 or (b[at + 1].toInt() and 0xFF)

    // ---- trailing entries -------------------------------------------------------
    // Text records of MOBI v5+ files carry a small footer (extra data flags,
    // rec0[0xF2]) that must be stripped before decompression: N varint-sized
    // entries, then — when the multibyte flag is set — one byte whose low two
    // bits plus one count the overlap of a UTF-8 character split across the
    // record boundary (the next record re-emits those bytes).

    private fun trimTrailing(data: ByteArray, trailers: Int, multibyte: Boolean): ByteArray {
        var buf = data
        repeat(trailers) {
            val num = sizeOfTrailingEntry(buf)
            if (num <= 0 || num > buf.size) return buf
            buf = buf.copyOfRange(0, buf.size - num)
        }
        if (multibyte && buf.isNotEmpty()) {
            val num = (buf[buf.size - 1].toInt() and 3) + 1
            if (num <= buf.size) buf = buf.copyOfRange(0, buf.size - num)
        }
        return buf
    }

    private fun sizeOfTrailingEntry(buf: ByteArray): Int {
        var num = 0
        for (k in maxOf(0, buf.size - 4) until buf.size) {
            val v = buf[k].toInt() and 0xFF
            if (v and 0x80 != 0) num = 0
            num = (num shl 7) or (v and 0x7F)
        }
        return num
    }

    // ---- text shaping ----------------------------------------------------------

    private fun decodeText(b: ByteArray, cs: CharsetHandle): String =
        cs.decode(b).trim('\u0000', ' ')

    private fun imageExt(b: ByteArray): String? = when {
        b.size > 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() -> "jpg"
        b.size > 4 && b[0] == 0x89.toByte() && b[1] == 0x50.toByte() -> "png"
        b.size > 3 && b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() -> "gif"
        b.size > 2 && b[0] == 'B'.code.toByte() && b[1] == 'M'.code.toByte() -> "bmp"
        else -> null
    }

    /**
     * recindex attributes point at image records; rewrite the whole tag to a
     * clean `<img ... src="file-uri"/>`. Real books emit forms like
     * `<img align="baseline" recindex="00010" width="525"></img>`, so the
     * match must span the full open tag and the rebuilt tag carries its own
     * closing bracket — appending `src` after the original `>` used to leak
     * it as body text and leave a broken image behind.
     */
    private fun rewritten(html: String, images: Map<Int, String>): String =
        Regex("""<img[^>]*?\s(recindex|lowrecindex|coverrecindex)="(\d+)"[^>]*?/?>""", RegexOption.IGNORE_CASE)
            .replace(html) { m ->
                val n = m.groupValues[2].toIntOrNull() ?: 0
                val uri = images[n] ?: return@replace ""
                m.groupValues[0]
                    .replace(Regex("""\s(recindex|lowrecindex|coverrecindex|recglerowsperpixel|recglyph)="[^"]*""", RegexOption.IGNORE_CASE), "")
                    .removeSuffix("/>").removeSuffix(">") + " src=\"$uri\"/>"
            }
            .replace(Regex("""\s(recindex|lowrecindex|multimediarecindex|audiorecindex|coverage recindex)="[^"]*"""", RegexOption.IGNORE_CASE), "")

    /**
     * Removes the book's own 目录 section from a chunk of HTML: finds the
     * heading block (目录/Contents), runs over the entry blocks that follow
     * (short, no sentence punctuation, no heading of their own), and joins
     * the HTML before the heading with everything from the first real
     * content block. Returns null when the chunk is TOC-only or carries no
     * recognizable TOC layout — the caller decides what that means.
     */
    private val htmlBlock = Regex(
        """<(h[1-6]|p|div|li|td|blockquote)\b[^>]*>[\s\S]*?</\1\s*>""",
        RegexOption.IGNORE_CASE,
    )

    private val tocHeadingLine = Regex(
        """^\s*(目\s*录|目\s*次|contents|table\s+of\s+contents)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    private fun blockText(b: String): String =
        PageTemplate.unescape(PageTemplate.stripTags(b)).replace(Regex("\\s+"), " ").trim()

    private fun isTocEntryBlock(b: String, text: String): Boolean {
        if (text.isEmpty() || text.length > 60) return false
        if (text.endsWith("。") || text.endsWith("！") || text.endsWith("？")) return false
        if (Regex("""<(h[1-6]|img)\b""", RegexOption.IGNORE_CASE).containsMatchIn(b)) return false
        return true
    }

    private fun stripTocSection(html: String): String? {
        val blocks = htmlBlock.findAll(html).toList()
        val hIdx = blocks.indices.take(6).firstOrNull { i ->
            tocHeadingLine.containsMatchIn(blockText(blocks[i].value))
        } ?: return html
        var k = hIdx + 1
        var entries = 0
        while (k < blocks.size) {
            val t = blockText(blocks[k].value)
            if (t.isEmpty()) { k++; continue }
            if (isTocEntryBlock(blocks[k].value, t)) { entries++; k++; continue }
            break
        }
        if (entries < 2) return html
        if (k >= blocks.size) return null
        val out = html.substring(0, blocks[hIdx].range.first) + html.substring(blocks[k].range.first)
        return out.ifBlank { null }
    }

    /** One pagebreak-delimited chunk: its byte range plus the decoded text. */
    private class RawChunk(val start: Int, val text: String)

    /**
     * Split on `<mbp:pagebreak>` at the byte level so each chunk keeps the
     * offset it is cited by in the TOC's filepos anchors. mbp header/footer
     * blocks (page furniture Kindle injects per page) are stripped here.
     */
    private fun splitChunks(raw: ByteArray, cs: CharsetHandle): List<RawChunk> {
        val needle = "<mbp:pagebreak".toByteArray()
        val chunks = mutableListOf<RawChunk>()
        var start = 0
        var i = 0
        while (i < raw.size) {
            if (matchesAt(raw, i, needle)) {
                // Swallow the rest of the tag (attributes, slash, bracket).
                var j = i + needle.size
                while (j < raw.size && raw[j] != '>'.code.toByte()) j++
                val tagEnd = if (j < raw.size) j + 1 else j
                chunks += decodeChunk(raw, start, i, cs)
                start = tagEnd
                i = tagEnd
            } else {
                i++
            }
        }
        chunks += decodeChunk(raw, start, raw.size, cs)
        return chunks
    }

    private fun decodeChunk(raw: ByteArray, from: Int, to: Int, cs: CharsetHandle): RawChunk {
        val text = cs.decode(raw.copyOfRange(from, to))
            .replace(
                Regex("<mbp:(header|footer)[\\s\\S]*?</mbp:(header|footer)>", RegexOption.IGNORE_CASE),
                "",
            )
            .trim()
        return RawChunk(from, text)
    }

    private fun matchesAt(raw: ByteArray, at: Int, needle: ByteArray): Boolean {
        if (at + needle.size > raw.size) return false
        for (k in needle.indices) {
            if ((raw[at + k].toInt() and 0xDF) != (needle[k].toInt() and 0xDF)) return false
        }
        return true
    }

    /**
     * The TOC the way readers actually show it: every `<a filepos=N>title</a>`
     * anchor maps to the chunk whose byte range contains N — real chapter
     * titles, in book order, deduped per chunk. Falls back to the first
     * heading heuristic when the book carries no usable anchors.
     */
    private fun buildToc(raw: ByteArray, chunks: List<RawChunk>, cs: CharsetHandle): List<TocItem>? {
        if (chunks.isEmpty()) return null
        val starts = chunks.map { it.start }
        val toc = mutableListOf<TocItem>()
        val seen = mutableSetOf<Int>()
        val aTag = "<a".toByteArray()
        val closeTag = "</a>".toByteArray()
        var i = 0
        while (i < raw.size) {
            if (!matchesAt(raw, i, aTag)) { i++; continue }
            var tagEnd = i
            while (tagEnd < raw.size && raw[tagEnd] != '>'.code.toByte() && tagEnd - i < 512) tagEnd++
            if (tagEnd >= raw.size) break
            val tag = String(raw, i, tagEnd - i, Charsets.ISO_8859_1)
            // filepos=<digits> (quotes optional in real files); parsed by hand
            // — a compiled Regex here misfired on device for no visible reason.
            val fp = tag.indexOf("filepos", ignoreCase = true)
            val pos: Int? = if (fp < 0) null else {
                var q = fp + 8
                while (q < tag.length && (tag[q] == '=' || tag[q] == '"' || tag[q] == ' ')) q++
                var e = q
                while (e < tag.length && tag[e].isDigit()) e++
                if (e > q) tag.substring(q, e).toIntOrNull() else null
            }
            if (pos == null) { i = tagEnd + 1; continue }
            var close = indexOfFrom(raw, closeTag, tagEnd)
            if (close < 0) close = minOf(tagEnd + 1, raw.size)
            val title = PageTemplate.unescape(PageTemplate.stripTags(cs.decode(raw.copyOfRange(tagEnd + 1, close))))
                .replace(Regex("\\s+"), " ").trim()
            // The chunk holding the target: last one that starts at or before it.
            var idx = -1
            for (k in starts.indices) if (starts[k] <= pos) idx = k else break
            if (idx >= 0 && title.isNotBlank() && title.length <= 60 && seen.add(idx)) {
                toc += TocItem(idx, title)
            }
            i = close + closeTag.size
        }
        return toc.takeIf { it.size >= 3 }
    }

    /** One TOC entry per pagebreak chapter, titled by its first heading/text. */
    private fun buildToc(chunks: List<String>): List<TocItem> {
        val toc = mutableListOf<TocItem>()
        var index = 0
        chunks.forEach { chunk ->
            val cleaned = chunk.trim()
            if (cleaned.isBlank()) return@forEach
            val heading = Regex("<h[1-4][^>]*>(.*?)</h[1-4]>", RegexOption.DOT_MATCHES_ALL)
                .find(cleaned)?.groupValues?.get(1)
                ?: Regex("<b>(.*?)</b>", RegexOption.DOT_MATCHES_ALL).find(cleaned)?.groupValues?.get(1)
            val text = PageTemplate.unescape(PageTemplate.stripTags(heading ?: cleaned))
                .replace(Regex("\\s+"), " ").trim()
            toc += TocItem(index, if (text.isBlank()) "§ ${index + 1}" else text.take(40))
            index++
        }
        return toc
    }

    private fun indexOf(data: ByteArray, needle: ByteArray): Int = indexOfFrom(data, needle, 0)

    private fun indexOfFrom(data: ByteArray, needle: ByteArray, from: Int): Int {
        if (from < 0) return -1
        outer@ for (i in from..data.size - needle.size) {
            for (j in needle.indices) if (data[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }


    private fun u32(b: ByteArray, at: Int): Long =
        ((b[at].toLong() and 0xFF) shl 24) or ((b[at + 1].toLong() and 0xFF) shl 16) or
            ((b[at + 2].toLong() and 0xFF) shl 8) or (b[at + 3].toLong() and 0xFF)
}

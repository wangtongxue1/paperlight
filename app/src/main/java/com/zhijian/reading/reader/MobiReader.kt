package com.zhijian.reading.reader

import com.zhijian.reading.data.FileMetadata
import com.zhijian.reading.data.EpubMeta
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset

/**
 * Un-DRM'd MOBI / AZW (KF7) files: the PalmDB record list, the PalmDOC
 * header, the MOBI metadata header with its EXTH records, per-record
 * trailing-entry trimming (extra data flags, multibyte overlap) and
 * PalmDOC LZ77 decompression. KF8-only (AZW3) files and anything
 * HUFF/CDIC compressed or encrypted are refused with null, not half-read.
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
        val mobi = parse(file) ?: return null
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
            // in the TOC panel, not in the reading flow.
            val chunks = splitChunks(mobi.htmlBytes, mobi.encoding)
                .filter { it.text.isNotBlank() && !isTocPage(it.text) }

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
        }.getOrNull()
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

            // HUFF/CDIC (compression 17480) and KF8-only files are out of scope.
            if (compression == 17480) return null
            val hasBoundary = indexOf(rec0, "BOUNDARY".toByteArray())
            var mobiType = u32(rec0, 16 + 8).toInt()

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
                if (huffOff > 0) return null
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
                mobiType = 2
            }
            if (mobiType == 8 && hasBoundary < 0) return null

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
    }.getOrNull()

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
     * A page that is nothing but a link listing under a 目录 heading. Real
     * conversions (calibre, KINDLEGEN) emit one as the first pagebreak
     * chunk; the reading flow doesn't need it twice.
     */
    private fun isTocPage(chunk: String): Boolean {
        val linkCount = Regex("<a[\\s>]", RegexOption.IGNORE_CASE).findAll(chunk).count()
        if (linkCount < 3) return false
        val text = PageTemplate.unescape(PageTemplate.stripTags(chunk)).replace(Regex("\\s+"), "")
        return text.startsWith("目录")
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

    private fun u16(b: ByteArray, at: Int): Int = (b[at].toInt() and 0xFF) shl 8 or (b[at + 1].toInt() and 0xFF)

    private fun u32(b: ByteArray, at: Int): Long =
        ((b[at].toLong() and 0xFF) shl 24) or ((b[at + 1].toLong() and 0xFF) shl 16) or
            ((b[at + 2].toLong() and 0xFF) shl 8) or (b[at + 3].toLong() and 0xFF)
}

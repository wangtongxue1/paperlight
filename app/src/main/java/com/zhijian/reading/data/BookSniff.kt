package com.zhijian.reading.data

import java.io.File
import java.util.zip.ZipFile

/**
 * What a book file actually is, judged by its bytes.
 *
 * File names lie: a collection downloaded as `东野圭吾作品集` has no
 * extension at all, some sites serve `.epub` that is really a plain zip of
 * books, and `.txt` files turn out to be HTML. The extension the user sees
 * is only trusted when it is one this app already knows; everything else is
 * identified here so a book is never turned away because of its name.
 */
object BookSniff {

    /** Book formats this app can file, by extension. */
    val KNOWN = setOf("EPUB", "PDF", "TXT", "HTML", "HTM", "MD", "FB2", "MOBI", "AZW3")

    /** A zip that is not an EPUB: a collection of book files. */
    const val ARCHIVE = "ZIP"

    /**
     * The extension to store [name] under, or null when the bytes are not
     * anything readable. Supported names win as they are — a `.mobi` stays
     * `.mobi` — and only unknown ones are sniffed.
     */
    fun extensionOf(file: File, name: String): String? {
        val declared = name.substringAfterLast('.', "").uppercase()
        if (declared in KNOWN) return declared
        if (declared == ARCHIVE) return if (isEpubZip(file)) "EPUB" else ARCHIVE
        return sniff(file)
    }

    /** Sniffed extension of a file whose name says nothing useful. */
    fun sniff(file: File): String? {
        val head = head(file, 1024) ?: return null
        if (head.size < 8) return null

        if (head.startsWithAscii("%PDF")) return "PDF"

        if (head[0] == 0x50.toByte() && head[1] == 0x4B.toByte()) {
            // PK\x03\x04 — a zip. EPUB when the OCF container is inside.
            return if (isEpubZip(file)) "EPUB" else ARCHIVE
        }

        // Palm database: the type/creator sits at offset 60.
        if (head.size >= 68) {
            val kind = String(head, 60, 8, Charsets.US_ASCII)
            if (kind == "BOOKMOBI" || kind == "TEXtREAd") return "MOBI"
        }

        val text = String(head, Charsets.UTF_8).trimStart('\uFEFF', ' ', '\r', '\n', '\t')
        val lower = text.take(400).lowercase()
        if (lower.contains("<fictionbook")) return "FB2"
        if (lower.startsWith("<?xml")) {
            // An XML file that is not FB2 is most likely an unzipped EPUB
            // page; HTML handles it, plain text is the safer fallback.
            return if (lower.contains("<html")) "HTML" else "TXT"
        }
        if (lower.startsWith("<!doctype html") || lower.startsWith("<html")) return "HTML"
        // Anything else made of text is read as a book of plain text.
        return if (looksLikeText(head)) "TXT" else null
    }

    private fun isEpubZip(file: File): Boolean = runCatching {
        ZipFile(file).use { zip ->
            zip.getEntry("META-INF/container.xml") != null
        }
    }.getOrDefault(false)

    private fun head(file: File, count: Int): ByteArray? = runCatching {
        file.inputStream().use { input ->
            val buf = ByteArray(count)
            var read = 0
            while (read < count) {
                val n = input.read(buf, read, count - read)
                if (n <= 0) break
                read += n
            }
            buf.copyOf(read)
        }
    }.getOrNull()

    /** No NUL bytes and few control characters: bytes a reader can print. */
    private fun looksLikeText(head: ByteArray): Boolean {
        var control = 0
        for (b in head) {
            if (b == 0.toByte()) return false
            val v = b.toInt() and 0xFF
            if (v < 0x09) control++
            if (v in 0x0E..0x1F) control++
        }
        return control * 20 <= head.size
    }

    private fun ByteArray.startsWithAscii(s: String): Boolean {
        if (size < s.length) return false
        for (i in s.indices) if (this[i] != s[i].code.toByte()) return false
        return true
    }
}

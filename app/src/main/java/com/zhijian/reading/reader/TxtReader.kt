package com.zhijian.reading.reader

import com.zhijian.reading.data.EpubMeta
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * Plain-text books: encoding sniffing (BOM, strict UTF-8, then GB18030 —
 * the superset most Chinese TXT files use), chapter splitting on the
 * 第X章/Chapter N conventions, and one escaped paragraph per line.
 */
object TxtReader {

    private val chapterHan = Regex("""^\s*第\s*[0-9０-９零一二三四五六七八九十百千万两]+\s*[章节卷部回集篇幕]""")
    private val chapterLatin = Regex("""^\s*Chapter\s*\d+""", RegexOption.IGNORE_CASE)
    private val chapterNamed = Regex("""^\s*(序章|序言|楔子|引子|前言|后记|尾声|终章|番外)""")

    /** A heading only counts on a short line — body text never matches. */
    private fun isChapter(line: String): Boolean {
        if (line.length > 60) return false
        return chapterHan.containsMatchIn(line) ||
            chapterLatin.containsMatchIn(line) ||
            chapterNamed.containsMatchIn(line)
    }

    fun prepare(file: File, cacheRoot: File, theme: String = "light"): BookContent? = runCatching {
        val text = readText(file) ?: return null
        val title = EpubMeta.fileTitleFallback(file.nameWithoutExtension)

        val body = StringBuilder()
        val toc = mutableListOf<TocItem>()
        val lines = text.split('\n')
        var buf = StringBuilder()
        var currentTitle = ""
        var index = 0

        fun flush() {
            // A heading followed by nothing before the next heading is a
            // TOC entry, not a chapter: TXT novels list every 章 title up
            // front, and emitting those ghosts doubles the book with empty
            // sections. Real chapters always carry body text.
            if (buf.isBlank()) return
            // The preamble before the first heading can be the book's own
            // 目录 listing; that belongs in the panel, not the flow. The
            // same goes for a 目录 section that survived as a chapter.
            if (index <= 1 && TocStrip.isTocText(buf.toString())) return
            val name = if (currentTitle.isBlank()) title else currentTitle.trim()
            body.append(PageTemplate.chapter(index, "<h2>${PageTemplate.escape(name)}</h2>${PageTemplate.paragraphs(buf.toString())}"))
            toc += TocItem(index, name)
            index++
            buf = StringBuilder()
        }

        lines.forEach { line ->
            if (isChapter(line)) {
                flush()
                currentTitle = line.trim()
            } else {
                buf.append(line).append('\n')
            }
        }
        flush()
        if (index == 0) {
            // No headings at all: one chapter holding the whole text.
            body.append(PageTemplate.chapter(0, PageTemplate.paragraphs(text)))
            toc += TocItem(0, title)
        }

        BookContent(
            html = PageTemplate.wrap(title, body.toString(), theme),
            baseUrl = "about:blank",
            title = title,
            toc = toc,
        )
    }.getOrNull()

    /** Decodes a text file: BOM first, then strict UTF-8, then GB18030. */
    fun readText(file: File): String? = runCatching {
        val bytes = file.readBytes()
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return@runCatching String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return@runCatching String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return@runCatching String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        val strictUtf8 = decodeStrict(bytes, Charsets.UTF_8)
        strictUtf8 ?: decodeStrict(bytes, charset("GB18030")) ?: String(bytes, Charsets.ISO_8859_1)
    }.getOrNull()

    private fun decodeStrict(bytes: ByteArray, cs: java.nio.charset.Charset): String? {
        val dec = cs.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return runCatching { dec.decode(ByteBuffer.wrap(bytes)).toString() }.getOrNull()
    }
}

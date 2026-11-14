package com.zhijian.reading.reader

/**
 * Detects the book's own table-of-contents page — the "目录" listing that
 * many EPUBs and TXT files ship as their first pages. It belongs in the
 * 目录 panel, never in the reading flow, so every format decoder asks this
 * helper before emitting a section.
 */
object TocStrip {

    /** A 目录 / Contents heading standing alone on its line. */
    private val tocHeading = Regex(
        "^\\s*(目\\s*录|目\\s*次|contents|table\\s+of\\s+contents)\\s*$",
        RegexOption.IGNORE_CASE,
    )

    /** Unambiguous chapter titles: 第X章…, Chapter N, named openers. */
    private val strongTitle = Regex(
        "^\\s*(?:" +
            "第\\s*[0-9０-９零一二三四五六七八九十百千万两]+\\s*[章节卷部回集篇幕][^。！？]{0,30}|" +
            "chapter\\s*\\d+[^。！？]{0,30}|" +
            "(?:序章|序言|楔子|引子|前言|后记|尾声|终章|番外|附录)[^。！？]{0,20}" +
            ")\\s*$",
        RegexOption.IGNORE_CASE,
    )

    /** Weaker signals: dotted page leaders, bare numbered entries. */
    private val weakTitle = Regex(
        "^\\s*(?:" +
            ".*[·•.]{2,}\\s*\\d{0,4}|" +
            "[（(]?[0-9０-９一二三四五六七八九十]{1,4}[）)]?\\s*[^。！？\\s]{1,25}" +
            ")\\s*$",
    )

    /** An EPUB spine item's raw XHTML that is nothing but the TOC. */
    fun isTocPageHtml(raw: String): Boolean {
        // EPUB 3 nav documents mark themselves.
        if (Regex("""<nav[^>]*epub:type\s*=\s*"[^"]*\btoc\b""", RegexOption.IGNORE_CASE)
                .containsMatchIn(raw)
        ) return true
        val text = PageTemplate.stripTags(
            raw.substringAfterLast("<body", raw)
                .substringAfter('>', raw)
                .substringBefore("</body"),
        )
        return isTocText(text)
    }

    /** Plain text (already tag-stripped) that is a TOC listing. */
    fun isTocText(raw: String): Boolean {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 4) return false
        val strong = lines.count { it.length <= 40 && strongTitle.containsMatchIn(it) }
        val weak = lines.count { it.length <= 40 && weakTitle.containsMatchIn(it) }
        val hasHeading = lines.take(3).any { tocHeading.containsMatchIn(it) }
        // An explicit 目录 heading plus a run of entries is decisive.
        if (hasHeading && strong + weak >= 4) return true
        // No heading survived, but the page is a wall of short chapter-ish
        // lines with several real chapter titles in it.
        return lines.size >= 8 && strong >= 3 && strong + weak >= lines.size * 3 / 4
    }
}

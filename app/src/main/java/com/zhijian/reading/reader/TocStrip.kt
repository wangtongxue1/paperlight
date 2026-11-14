package com.zhijian.reading.reader

/**
 * Detects and removes the book's own table-of-contents page — the "目录 /
 * Contents" listing that most EPUB, MOBI, AZW3 and TXT files ship as their
 * first pages. It belongs in the 目录 panel, never in the reading flow, so
 * every format decoder runs its sections through [stripSection].
 *
 * Real books break the naive version of this in three ways, all seen on
 * device:
 * - AZW3 single-document books begin with a whole XHTML document
 *   (`<html><head><title>Contents</title>…` followed by the body). Cutting
 *   the TOC leaves that shell behind, which renders as a blank page titled
 *   "Contents" — so the shell is removed first.
 * - KF7 MOBI books put a "目录" heading alone on its own page (the entries
 *   are anchors that only exist in the guide) — a page with no entries at
 *   all still has to go.
 * - Some books list the TOC at the END of the text, under a "Table of
 *   Contents" heading, so the heading is searched beyond the first block
 *   and the scan is not limited to the front matter.
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
        return isTocText(plainText(bodyOf(raw)))
    }

    /** Plain text (already tag-stripped) that is a TOC listing. */
    fun isTocText(raw: String): Boolean {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 4) return false
        val strong = lines.count { it.length <= 40 && strongTitle.containsMatchIn(it) }
        val weak = lines.count { it.length <= 40 && weakTitle.containsMatchIn(it) }
        val hasHeading = lines.take(3).any { tocHeading.containsMatchIn(it) }
        // An explicit 目录 heading plus a run of entries is decisive. The
        // entries may be plain prose titles (no 第X章 pattern, no page
        // numbers), so short lines under the heading count on their own.
        if (hasHeading && lines.size >= 4) {
            val short = lines.count { it.length <= 40 }
            if (strong + weak >= 4 || short >= lines.size * 3 / 4) return true
        }
        // No heading survived, but the page is a wall of short chapter-ish
        // lines with several real chapter titles in it.
        return lines.size >= 8 && strong >= 3 && strong + weak >= lines.size * 3 / 4
    }

    /**
     * The chapter body with the book's own TOC page taken out, or null when
     * nothing readable is left — the caller then drops the section instead
     * of showing an empty page.
     */
    fun stripSection(raw: String): String? {
        val html = stripDocumentShell(raw)
        val blocks = block.findAll(html).toList()
        if (blocks.isEmpty()) return html.ifBlank { null }

        val hIdx = blocks.indices.take(8)
            .firstOrNull { tocHeading.containsMatchIn(blockText(blocks[it].value)) }
            ?: return html.ifBlank { null }

        // Walk the entry blocks that follow the heading (short, no sentence
        // punctuation, no heading of their own) until real prose starts.
        var k = hIdx + 1
        var entries = 0
        while (k < blocks.size) {
            val t = blockText(blocks[k].value)
            if (t.isEmpty()) { k++; continue }
            if (isTocEntryBlock(blocks[k].value, t)) { entries++; k++; continue }
            break
        }
        val head = html.substring(0, blocks[hIdx].range.first)
        val rest = if (k < blocks.size) html.substring(blocks[k].range.first) else ""
        val kept = (head + rest).trim()

        // Two or more entries under the heading: the TOC layout is certain,
        // cut it out. With fewer, only a page that turns empty is a TOC —
        // anything substantial is left untouched so a chapter that merely
        // quotes a 目录 line keeps its text.
        if (entries >= 2) return kept.ifBlank { null }
        return if (plainText(kept).length >= 40) html else null
    }

    /**
     * Removes the XHTML document furniture a single-document AZW3/MOBI chunk
     * starts with: the XML declaration, doctype, `<head>` (whose `<title>`
     * is what showed up as a phantom "Contents" page), and the `<html>` /
     * `<body>` tags themselves.
     */
    fun stripDocumentShell(raw: String): String {
        var s = raw
        s = Regex("""<\?xml[\s\S]*?\?>""", RegexOption.IGNORE_CASE).replace(s, "")
        s = Regex("""<!DOCTYPE[^>]*>""", RegexOption.IGNORE_CASE).replace(s, "")
        s = Regex("""<head\b[\s\S]*?</head\s*>""", RegexOption.IGNORE_CASE).replace(s, "")
        s = Regex("""<(meta|link)\b[^>]*/?>""", RegexOption.IGNORE_CASE).replace(s, "")
        s = Regex("""<style\b[\s\S]*?</style\s*>""", RegexOption.IGNORE_CASE).replace(s, "")
        s = Regex("""<script\b[\s\S]*?</script\s*>""", RegexOption.IGNORE_CASE).replace(s, "")
        s = Regex("""<title\b[\s\S]*?</title\s*>""", RegexOption.IGNORE_CASE).replace(s, "")
        s = Regex("""</?html\b[^>]*>""", RegexOption.IGNORE_CASE).replace(s, "")
        s = Regex("""</?body\b[^>]*>""", RegexOption.IGNORE_CASE).replace(s, "")
        // A malformed `<head>` with no closing tag would swallow the body;
        // drop the bare tag and let the rest render.
        s = Regex("""<head\b[^>]*>""", RegexOption.IGNORE_CASE).replace(s, "")
        return s.trim()
    }

    /** Visible text of a fragment, tags gone and whitespace collapsed. */
    fun plainText(html: String): String =
        PageTemplate.unescape(PageTemplate.stripTags(html)).replace(Regex("\\s+"), " ").trim()

    /** True when a section has nothing to read: no text and no image. */
    fun isBlankSection(html: String): Boolean =
        plainText(html).isEmpty() && !Regex("<img\\b", RegexOption.IGNORE_CASE).containsMatchIn(html)

    // ---- internals -----------------------------------------------------------

    private val block = Regex(
        """<(h[1-6]|p|div|li|td|blockquote)\b[^>]*>[\s\S]*?</\1\s*>""",
        RegexOption.IGNORE_CASE,
    )

    private fun bodyOf(raw: String): String {
        val start = raw.indexOf("<body", ignoreCase = true)
        if (start < 0) return raw
        val open = raw.indexOf('>', start)
        val end = raw.lastIndexOf("</body", ignoreCase = true)
        if (open < 0 || end < 0 || end <= open) return raw
        return raw.substring(open + 1, end)
    }

    private fun blockText(b: String): String = plainText(b)

    private fun isTocEntryBlock(b: String, text: String): Boolean {
        if (text.isEmpty() || text.length > 60) return false
        // A listing row is a link to somewhere else in the book. Chapter
        // names often end in a question mark ("…吗？"), so a block that
        // holds an anchor counts as an entry whatever its punctuation.
        if (Regex("<a\\b", RegexOption.IGNORE_CASE).containsMatchIn(b)) return true
        if (text.endsWith("。") || text.endsWith("！") || text.endsWith("？")) return false
        if (Regex("""<(h[1-6]|img)\b""", RegexOption.IGNORE_CASE).containsMatchIn(b)) return false
        return true
    }
}

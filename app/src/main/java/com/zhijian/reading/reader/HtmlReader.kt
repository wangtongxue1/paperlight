package com.zhijian.reading.reader

import android.util.Xml
import com.zhijian.reading.data.EpubMeta
import org.xmlpull.v1.XmlPullParser
import java.io.File

/**
 * Single-file formats that need no unpacking: HTML pages, Markdown notes
 * and FB2 fiction books. The base URL is the file's own folder, so a
 * plain HTML's relative images just work in the WebView.
 */
object HtmlReader {

    fun prepare(file: File, cacheRoot: File, theme: String = "light"): BookContent? = when {
        file.extension.equals("fb2", true) -> Fb2.prepare(file, theme)
        file.extension.equals("md", true) -> Markdown.prepare(file, theme)
        else -> plainHtml(file, theme)
    }

    /** Title / author for the import path. */
    fun fb2Metadata(file: File): com.zhijian.reading.data.FileMetadata? = Fb2.metadata(file)

    // ---- plain HTML ---------------------------------------------------------

    private fun plainHtml(file: File, theme: String): BookContent? = runCatching {
        val raw = TxtReader.readText(file) ?: return null
        val title = guessTitle(raw) ?: EpubMeta.fileTitleFallback(file.nameWithoutExtension)
        val inner = extractBody(raw)
        // The book's own 目录 listing (a 目录/Contents heading followed by a
        // run of entries) belongs in the TOC panel, not the reading flow.
        val body = TocStrip.stripSection(inner) ?: inner
        BookContent(
            html = PageTemplate.wrap(title, PageTemplate.chapter(0, body), theme),
            baseUrl = file.parentFile?.toURI().toString(),
            title = title,
            toc = listOf(TocItem(0, title)),
        )
    }.getOrNull()

    private fun guessTitle(raw: String): String? {
        val m = Regex("<title[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
            .find(raw) ?: return null
        val t = PageTemplate.unescape(PageTemplate.stripTags(m.groupValues[1])).trim()
        return t.takeIf { it.isNotBlank() }
    }

    private fun extractBody(html: String): String {
        val start = html.indexOf("<body", ignoreCase = true)
        val withoutBody = if (start < 0) html else {
            val open = html.indexOf('>', start)
            val end = html.lastIndexOf("</body", ignoreCase = true)
            if (open < 0 || end < 0 || end <= open) html else html.substring(open + 1, end)
        }
        // Scripts and styles never belong in a reading page.
        return withoutBody
            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
    }

    // ---- markdown -----------------------------------------------------------

    private object Markdown {
        fun prepare(file: File, theme: String): BookContent? = runCatching {
            val raw = TxtReader.readText(file) ?: return null
            val title = Regex("^#\\s+(.+)$", RegexOption.MULTILINE).find(raw)
                ?.groupValues?.get(1)?.trim()
                ?: EpubMeta.fileTitleFallback(file.nameWithoutExtension)
            BookContent(
                html = PageTemplate.wrap(title, PageTemplate.chapter(0, toHtml(raw)), theme),
                baseUrl = file.parentFile?.toURI().toString(),
                title = title,
                toc = listOf(TocItem(0, title)),
            )
        }.getOrNull()

        /** A small, strict subset: headings, hr, quote, list, code, emphasis. */
        fun toHtml(md: String): String {
            val out = StringBuilder()
            var inCode = false
            var codeLang = ""
            val list = StringBuilder()
            var listTag = ""

            fun closeList() {
                if (list.isNotEmpty()) {
                    out.append("<").append(listTag).append(">").append(list).append("</").append(listTag).append(">")
                    list.clear(); listTag = ""
                }
            }

            fun inline(s: String): String = PageTemplate.escape(s)
                .replace(Regex("\\*\\*(.+?)\\*\\*")) { "<strong>${it.groupValues[1]}</strong>" }
                .replace(Regex("__(.+?)__")) { "<strong>${it.groupValues[1]}</strong>" }
                .replace(Regex("\\*(.+?)\\*")) { "<em>${it.groupValues[1]}</em>" }
                .replace(Regex("`(.+?)`")) { "<code>${it.groupValues[1]}</code>" }
                .replace(Regex("!\\[(.*?)\\]\\((.*?)\\)")) { m ->
                    "<img alt=\"${m.groupValues[1]}\" src=\"${m.groupValues[2]}\"/>"
                }
                .replace(Regex("\\[(.*?)\\]\\((.*?)\\)")) { m ->
                    "<a href=\"${m.groupValues[2]}\">${m.groupValues[1]}</a>"
                }

            md.split('\n').forEach { line0 ->
                val line = line0.trimEnd('\r')
                when {
                    line.trimStart().startsWith("```") -> {
                        closeList()
                        if (inCode) {
                            out.append("<pre>").append(PageTemplate.escape(codeLang)).append("</pre>")
                            inCode = false; codeLang = ""
                        } else {
                            inCode = true
                            codeLang = line.trim().removePrefix("```")
                        }
                    }
                    inCode -> codeLang += line + '\n'
                    Regex("^\\s*$").matches(line) -> closeList()
                    Regex("^#{1,6}\\s+").containsMatchIn(line) -> {
                        closeList()
                        val level = line.indexOfFirst { it != '#' } - 1
                        val h = (level + 1).coerceIn(1, 6)
                        val heading = line.replaceFirst(Regex("^#{1,6}\\s+"), "")
                        out.append("<h").append(h).append(">")
                            .append(inline(heading))
                            .append("</h").append(h).append(">")
                    }
                    line.trim() == "---" || line.trim() == "***" -> {
                        closeList(); out.append("<hr/>")
                    }
                    Regex("^>\\s?").containsMatchIn(line) -> {
                        closeList()
                        out.append("<blockquote>").append(inline(line.removePrefix(">").trim())).append("</blockquote>")
                    }
                    Regex("^[-*+]\\s+").containsMatchIn(line) -> {
                        if (listTag != "ul") { closeList(); listTag = "ul" }
                        list.append("<li>").append(inline(line.replaceFirst(Regex("^[-*+]\\s+"), ""))).append("</li>")
                    }
                    Regex("^\\d+\\.\\s+").containsMatchIn(line) -> {
                        if (listTag != "ol") { closeList(); listTag = "ol" }
                        list.append("<li>").append(inline(line.replaceFirst(Regex("^\\d+\\.\\s+"), ""))).append("</li>")
                    }
                    else -> {
                        closeList()
                        out.append("<p>").append(inline(line)).append("</p>")
                    }
                }
            }
            if (inCode) out.append("<pre>").append(PageTemplate.escape(codeLang)).append("</pre>")
            closeList()
            return out.toString()
        }
    }

    // ---- FB2 -----------------------------------------------------------------

    private object Fb2 {

        private class Section {
            var title = ""
            val paras = mutableListOf<StringBuilder>()
        }

        fun prepare(file: File, theme: String): BookContent? {
            val parsed = parse(file) ?: return null
            val body = StringBuilder()
            val toc = mutableListOf<TocItem>()
            parsed.sections.forEachIndexed { i, sec ->
                val title = sec.title.ifBlank { "§ ${i + 1}" }
                val paras = sec.paras.joinToString("") { "<p>${PageTemplate.escape(it.toString().trim())}</p>" }
                body.append(PageTemplate.chapter(i, "<h2>${PageTemplate.escape(title)}</h2>$paras"))
                toc += TocItem(i, title.take(60))
            }
            if (parsed.sections.isEmpty()) return null
            val html = linkImages(body.toString(), parsed.binaries)
            return BookContent(
                html = PageTemplate.wrap(parsed.title, html, theme),
                baseUrl = file.parentFile?.toURI().toString(),
                title = parsed.title,
                toc = toc,
            )
        }

        /** Title / author for the import path. */
        fun metadata(file: File): com.zhijian.reading.data.FileMetadata? {
            val parsed = parse(file) ?: return null
            if (parsed.title.isBlank() && parsed.sections.isEmpty()) return null
            return com.zhijian.reading.data.FileMetadata(
                title = parsed.title,
                authors = parsed.authors,
            )
        }

        private class Parsed {
            var title = ""
            val authors = mutableListOf<String>()
            val sections = mutableListOf<Section>()
            val binaries = linkedMapOf<String, String>()
        }

        private fun parse(file: File): Parsed? = runCatching {
            val out = Parsed()
            val p = Xml.newPullParser()
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            // Feed the whole file in memory: pulling lazily from a stream
            // that the `use` block already closed is a classic trap.
            p.setInput(java.io.ByteArrayInputStream(file.readBytes()), null)

            var event = p.eventType
            var inBody = false
            var inTitleInfo = false
            var inBookTitle = false
            var inAuthor = false
            var authorBits = StringBuilder()
            var secTitle = false
            var textSink: StringBuilder? = null
            val stack = mutableListOf<Section>()

            fun current(): Section = stack.lastOrNull() ?: Section().also { out.sections += it; stack += it }

            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (p.name) {
                        "body" -> inBody = true
                        "title-info" -> inTitleInfo = true
                        "book-title" -> if (inTitleInfo) { inBookTitle = true; textSink = StringBuilder() }
                        "author" -> if (inTitleInfo) { inAuthor = true; authorBits = StringBuilder() }
                        "first-name", "last-name", "nickname" -> if (inAuthor) textSink = StringBuilder()
                        "binary" -> {
                            val id = p.getAttributeValue(null, "id")
                            val ct = (p.getAttributeValue(null, "content-type") ?: "image/jpeg").substringBefore(';')
                            if (id != null) {
                                var e = p.next()
                                while (e != XmlPullParser.TEXT && e != XmlPullParser.END_DOCUMENT) e = p.next()
                                if (e == XmlPullParser.TEXT) {
                                    // FB2 binaries are already base64; only
                                    // squeeze the line breaks out of them.
                                    runCatching {
                                        out.binaries[id] = "data:$ct;base64," +
                                            p.text.replace(Regex("\\s+"), "")
                                    }
                                }
                            }
                        }
                        // Body content: sections and paragraphs.
                        "section" -> if (inBody) stack += Section()
                        "title" -> if (inBody) { secTitle = true; textSink = StringBuilder() }
                        "p", "v", "poem" -> if (inBody && !secTitle) { textSink = StringBuilder(); current().paras += textSink!! }
                        "empty-line" -> if (inBody && !secTitle) current().paras += StringBuilder()
                        "image" -> if (inBody && !secTitle) {
                            val href = p.getAttributeValue("http://www.w3.org/1999/xlink", "href")
                                ?: p.getAttributeValue(null, "href")
                            val id = href?.removePrefix("#").orEmpty()
                            val para = current().paras.lastOrNull()
                            if (para != null && id.isNotEmpty()) para.append("\uFFFCIMG:$id\uFFFC")
                        }
                    }
                    XmlPullParser.TEXT -> textSink?.append(p.text)
                    XmlPullParser.END_TAG -> when (p.name) {
                        "body" -> inBody = false
                        "title-info" -> inTitleInfo = false
                        "book-title" -> {
                            out.title = (textSink?.toString() ?: "").trim()
                            inBookTitle = false; textSink = null
                        }
                        "first-name", "last-name", "nickname" -> if (inAuthor) {
                            val part = (textSink?.toString() ?: "").trim()
                            if (part.isNotBlank()) {
                                if (authorBits.isNotEmpty()) authorBits.append(' ')
                                authorBits.append(part)
                            }
                            textSink = null
                        }
                        "author" -> if (inAuthor) {
                            val name = authorBits.toString().trim()
                            if (name.isNotBlank()) out.authors += name
                            inAuthor = false; authorBits = StringBuilder()
                        }
                        "title" -> if (inBody) {
                            val t = (textSink?.toString() ?: "").trim()
                            if (stack.isEmpty()) {
                                // A body-level <title> is the book's own name.
                                if (out.title.isBlank()) out.title = t
                            } else {
                                stack.last().title = t
                            }
                            secTitle = false; textSink = null
                        }
                        "p", "v", "poem" -> if (!secTitle) textSink = null
                        "section" -> if (inBody) {
                            stack.lastOrNull()?.let { sec ->
                                stack.removeAt(stack.lastIndex)
                                // Drop sections that carry nothing at all.
                                if (sec.paras.isNotEmpty() || sec.title.isNotBlank()) out.sections += sec
                            }
                        }
                    }
                }
                event = p.next()
            }
            if (out.title.isBlank()) out.title = file.nameWithoutExtension
            out
        }.getOrNull()

        /** FB2 keeps images in trailing <binary> blobs; splice the data URIs in. */
        private fun linkImages(html: String, binaries: Map<String, String>): String =
            Regex("\uFFFCIMG:([^\uFFFC]+)\uFFFC").replace(html) { m ->
                binaries[m.groupValues[1]]?.let { uri -> "<img src=\"$uri\"/>" } ?: ""
            }
    }
}

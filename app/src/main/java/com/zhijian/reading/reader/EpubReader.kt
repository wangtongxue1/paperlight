package com.zhijian.reading.reader

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile

/**
 * Turns an EPUB into one HTML document a WebView can render: the spine in
 * reading order, its images rewritten to the paths they were unpacked to,
 * and a stylesheet of our own so the page reads well in either theme.
 * The table of contents comes from the book's own nav document or NCX,
 * falling back to each chapter's first heading.
 */
object EpubReader {

    /** One entry of the table of contents: its spine chapter and a title. */
    data class TocItem(val index: Int, val title: String)

    data class Content(
        val html: String,
        val baseUrl: String,
        val title: String,
        val dir: File,
        val toc: List<TocItem>,
    )

    private data class ManifestItem(val href: String, val type: String, val properties: String = "")

    fun prepare(
        epub: File,
        cacheRoot: File,
        dark: Boolean,
    ): Content? = runCatching {
        val key = epub.absolutePath.hashCode().toString() + "-" + epub.length()
        val dir = File(cacheRoot, "book-$key")
        if (!dir.exists()) {
            dir.mkdirs()
            ZipFile(epub).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    if (entry.isDirectory) return@forEach
                    val target = File(dir, entry.name)
                    // Never write outside the book's own folder.
                    if (!target.canonicalPath.startsWith(dir.canonicalPath)) return@forEach
                    target.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        target.outputStream().use { input.copyTo(it) }
                    }
                }
            }
        }

        val opfPath = containerOpf(dir) ?: return null
        val opfFile = File(dir, opfPath)
        if (!opfFile.exists()) return null
        val opfDir = opfFile.parentFile ?: dir

        val (title, manifest, spine) = parseOpf(opfFile)
        val body = StringBuilder()
        spine.forEachIndexed { index, idref ->
            val item = manifest[idref] ?: return@forEachIndexed
            val file = File(opfDir, item.href)
            if (!file.exists()) return@forEachIndexed
            val raw = file.readText()
            val inner = extractBody(raw)
            body.append("<section class=\"chapter\" id=\"ch$index\">")
            body.append(rewriteImages(inner, file.parentFile ?: opfDir))
            body.append("</section>")
        }

        Content(
            html = document(title, body.toString(), dark),
            baseUrl = opfDir.toURI().toString(),
            title = title,
            dir = dir,
            toc = buildToc(opfDir, title, manifest, spine),
        )
    }.getOrNull()

    // ---- table of contents -------------------------------------------------

    private fun buildToc(
        opfDir: File,
        bookTitle: String,
        manifest: Map<String, ManifestItem>,
        spine: List<String>,
    ): List<TocItem> {
        val byPath = spineIndexByPath(opfDir, manifest, spine)

        // EPUB 3: the nav document named by properties="nav".
        manifest.values.firstOrNull { it.properties.split(Regex("\\s+")).contains("nav") }
            ?.let { nav ->
                val f = File(opfDir, nav.href)
                if (f.exists()) {
                    val items = parseNav(f, byPath)
                    if (items.isNotEmpty()) return items
                }
            }

        // EPUB 2: the NCX table of contents.
        manifest.values.firstOrNull { it.type.equals("application/x-dtbncx+xml", true) }
            ?.let { ncx ->
                val f = File(opfDir, ncx.href)
                if (f.exists()) {
                    val items = parseNcx(f, byPath)
                    if (items.isNotEmpty()) return items
                }
            }

        // Fallback: each chapter's first heading, or a plain marker.
        val items = mutableListOf<TocItem>()
        spine.forEachIndexed { i, idref ->
            val item = manifest[idref] ?: return@forEachIndexed
            val f = File(opfDir, item.href)
            val t = if (f.exists()) firstHeading(f.readText()) else null
            items += TocItem(i, t ?: "§ ${i + 1}")
        }
        if (items.size == 1) items[0] = TocItem(items[0].index, bookTitle.ifBlank { items[0].title })
        return items
    }

    /** href without fragment → spine order index, matched on real files. */
    private fun spineIndexByPath(
        opfDir: File,
        manifest: Map<String, ManifestItem>,
        spine: List<String>,
    ): Map<String, Int> {
        val map = linkedMapOf<String, Int>()
        spine.forEachIndexed { i, idref ->
            val href = manifest[idref]?.href ?: return@forEachIndexed
            runCatching { File(opfDir, href).canonicalFile.absolutePath }.getOrNull()
                ?.let { map[it] = i }
        }
        return map
    }

    private fun parseNav(navFile: File, byPath: Map<String, Int>): List<TocItem> {
        val raw = runCatching { navFile.readText() }.getOrNull() ?: return emptyList()
        val start = raw.indexOf("<nav", ignoreCase = true)
        val end = raw.indexOf("</nav>", ignoreCase = true)
        if (start < 0 || end <= start) return emptyList()
        val block = raw.substring(start, end + 6)
        val navDir = navFile.parentFile
        val items = mutableListOf<TocItem>()
        Regex("<a\\s[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
            .findAll(block)
            .forEach { m ->
                val href = m.groupValues[1].substringBefore('#').trim()
                if (href.isEmpty()) return@forEach
                val f = runCatching { File(navDir, href).canonicalFile.absolutePath }.getOrNull()
                    ?: return@forEach
                val idx = byPath[f] ?: return@forEach
                val t = unescape(stripTags(m.groupValues[2])).trim()
                if (t.isNotBlank() && items.none { it.index == idx }) items += TocItem(idx, t)
            }
        return items.sortedBy { it.index }
    }

    private fun parseNcx(ncxFile: File, byPath: Map<String, Int>): List<TocItem> {
        val raw = runCatching { ncxFile.readText() }.getOrNull() ?: return emptyList()
        val ncxDir = ncxFile.parentFile
        val items = mutableListOf<TocItem>()
        Regex(
            "<text>(.*?)</text>[\\s\\S]*?<content\\s+src=\"([^\"]+)\"",
            RegexOption.DOT_MATCHES_ALL,
        ).findAll(raw).forEach { m ->
            val src = m.groupValues[2].substringBefore('#').trim()
            if (src.isEmpty()) return@forEach
            val f = runCatching { File(ncxDir, src).canonicalFile.absolutePath }.getOrNull()
                ?: return@forEach
            val idx = byPath[f] ?: return@forEach
            val t = unescape(stripTags(m.groupValues[1])).trim()
            if (t.isNotBlank() && items.none { it.index == idx }) items += TocItem(idx, t)
        }
        return items.sortedBy { it.index }
    }

    private fun firstHeading(raw: String): String? {
        val m = Regex("<h[1-4][^>]*>(.*?)</h[1-4]>", RegexOption.DOT_MATCHES_ALL).find(raw)
            ?: return null
        val t = unescape(stripTags(m.groupValues[1])).trim()
        return t.takeIf { it.isNotBlank() }?.take(40)
    }

    private fun stripTags(s: String) = s.replace(Regex("<[^>]*>"), "")

    private fun unescape(s: String) = s
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")

    // ---- packaging -----------------------------------------------------------

    private fun containerOpf(dir: File): String? {
        val f = File(dir, "META-INF/container.xml")
        if (!f.exists()) return null
        f.inputStream().use { input ->
            val p = Xml.newPullParser()
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            p.setInput(input, null)
            var e = p.eventType
            while (e != XmlPullParser.END_DOCUMENT) {
                if (e == XmlPullParser.START_TAG && p.name == "rootfile") {
                    p.getAttributeValue(null, "full-path")?.let { if (it.isNotBlank()) return it }
                }
                e = p.next()
            }
        }
        return null
    }

    private fun parseOpf(opf: File): Triple<String, Map<String, ManifestItem>, List<String>> {
        var title = ""
        val manifest = linkedMapOf<String, ManifestItem>()
        val spine = mutableListOf<String>()
        opf.inputStream().use { input ->
            val p = Xml.newPullParser()
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            p.setInput(input, null)
            var e = p.eventType
            var text = StringBuilder()
            while (e != XmlPullParser.END_DOCUMENT) {
                when (e) {
                    XmlPullParser.START_TAG -> when (p.name) {
                        "item" -> {
                            val id = p.getAttributeValue(null, "id")
                            val href = p.getAttributeValue(null, "href")
                            val type = p.getAttributeValue(null, "media-type").orEmpty()
                            val props = p.getAttributeValue(null, "properties").orEmpty()
                            if (id != null && href != null) manifest[id] = ManifestItem(href, type, props)
                        }
                        "itemref" -> p.getAttributeValue(null, "idref")?.let { spine += it }
                    }
                    XmlPullParser.TEXT -> text.append(p.text)
                    XmlPullParser.END_TAG -> {
                        if (p.name == "title" && title.isBlank()) title = text.toString().trim()
                        text = StringBuilder()
                    }
                }
                e = p.next()
            }
        }
        return Triple(title, manifest, spine)
    }

    private fun extractBody(xhtml: String): String {
        val start = xhtml.indexOf("<body", ignoreCase = true)
        if (start < 0) return xhtml
        val open = xhtml.indexOf('>', start)
        val end = xhtml.lastIndexOf("</body", ignoreCase = true)
        if (open < 0 || end < 0 || end <= open) return xhtml
        return xhtml.substring(open + 1, end)
    }

    private fun rewriteImages(html: String, base: File): String =
        Regex("""(src|href)\s*=\s*"([^"]+)"""").replace(html) { m ->
            val attr = m.groupValues[1]
            val path = m.groupValues[2]
            if (path.startsWith("http") || path.startsWith("data:") || path.startsWith("#")) {
                m.value
            } else {
                val clean = path.substringBefore('#')
                val f = File(base, clean)
                if (f.exists() && (attr == "src")) {
                    """src="${f.toURI()}""""
                } else if (attr == "href") {
                    m.value
                } else {
                    m.value
                }
            }
        }

    /**
     * The page is styled through CSS variables so the reader can switch
     * themes at runtime; the JS engine in assets/reader.js drives the rest.
     */
    private fun document(title: String, body: String, dark: Boolean): String {
        return """
            <!DOCTYPE html>
            <html${if (dark) " class=\"pl-dark\"" else ""}><head>
            <meta charset="utf-8"/>
            <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"/>
            <title>${escape(title)}</title>
            <style>
              :root {
                --ink: #21201c; --dim: #4a463f; --bg: #ffffff; --rule: #e2e1de;
                --link: #3a5bc7; --selbg: #e1e9ff; --hlbg: rgba(255,196,0,.42); --codebg: #f4f3f1;
              }
              html.pl-dark {
                --ink: #eeeeec; --dim: #b5b3ad; --bg: #111110; --rule: #31312e;
                --link: #9eb1ff; --selbg: #1d2e62; --hlbg: rgba(255,196,0,.30); --codebg: #191918;
              }
              :root { color-scheme: light; }
              html.pl-dark { color-scheme: dark; }
              body {
                margin: 0; padding: 20px 22px 64px;
                background: var(--bg); color: var(--ink);
                font-family: Georgia, 'Noto Serif', 'Songti SC', serif;
                font-size: 17px; line-height: 1.78;
                -webkit-text-size-adjust: 100%;
                word-wrap: break-word;
              }
              section.chapter + section.chapter { margin-top: 2.2em; padding-top: 1.6em; border-top: 1px solid var(--rule); }
              h1, h2, h3, h4 { font-family: -apple-system, 'Noto Sans', sans-serif; line-height: 1.32; margin: 1.6em 0 .8em; }
              h1 { font-size: 1.42em; } h2 { font-size: 1.22em; } h3 { font-size: 1.08em; }
              p { margin: 0 0 1em; text-indent: 1.5em; text-align: justify; }
              p:first-of-type, h1 + p, h2 + p, h3 + p { text-indent: 0; }
              img { max-width: 100%; height: auto; display: block; margin: 1.2em auto; }
              blockquote { margin: 1em 0; padding-left: 1em; border-left: 2px solid var(--rule); color: var(--dim); }
              a { color: var(--link); text-decoration: none; }
              pre { white-space: pre-wrap; font-size: .86em; background: var(--codebg); padding: 10px 12px; border-radius: 6px; overflow-x: auto; }
              hr { border: 0; border-top: 1px solid var(--rule); margin: 1.8em 0; }
              ::selection { background: var(--selbg); }
              span.pl-hl {
                background: var(--hlbg); color: inherit;
                -webkit-box-decoration-break: clone; box-decoration-break: clone;
                border-radius: 2px; padding: 1px 0;
              }
              #pl-pager::-webkit-scrollbar { display: none; }
            </style>
            </head><body>$body</body></html>
        """.trimIndent()
    }

    private fun escape(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

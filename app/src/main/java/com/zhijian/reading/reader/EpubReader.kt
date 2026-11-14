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

    private data class ManifestItem(val href: String, val type: String, val properties: String = "")

    fun prepare(
        epub: File,
        cacheRoot: File,
        theme: String = "light",
    ): BookContent? = runCatching {
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
        // The book's own 目录 page (EPUB 3 nav or a spine item that is only
        // a chapter listing) belongs in the 目录 panel, not the reading flow:
        // drop it before numbering, so chapter indices stay contiguous.
        val pages = spine.mapNotNull { idref ->
            val item = manifest[idref] ?: return@mapNotNull null
            if (item.properties.split(Regex("\\s+")).contains("nav")) return@mapNotNull null
            val file = File(opfDir, item.href)
            if (!file.exists()) return@mapNotNull null
            val raw = file.readText()
            if (TocStrip.isTocPageHtml(raw)) return@mapNotNull null
            Triple(idref, item, raw)
        }
        // Each spine item also loses a 目录 block embedded inside it (some
        // EPUBs keep the listing in the first chapter document instead of
        // giving it a page of its own); a page left with nothing to read
        // is dropped so it can't show up as a blank one.
        var kept = pages.mapNotNull { (idref, item, raw) ->
            val inner = TocStrip.stripSection(extractBody(raw)) ?: return@mapNotNull null
            Triple(idref, item, inner)
        }
        if (kept.isEmpty()) {
            kept = pages.map { (idref, item, raw) -> Triple(idref, item, extractBody(raw)) }
        }
        val body = StringBuilder()
        kept.forEachIndexed { index, (_, item, inner) ->
            val file = File(opfDir, item.href)
            body.append(PageTemplate.chapter(index, rewriteImages(inner, file.parentFile ?: opfDir)))
        }

        BookContent(
            html = PageTemplate.wrap(title, body.toString(), theme),
            baseUrl = opfDir.toURI().toString(),
            title = title,
            dir = dir,
            toc = buildToc(opfDir, title, manifest, kept.map { it.first }),
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
                val t = PageTemplate.unescape(PageTemplate.stripTags(m.groupValues[2])).trim()
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
            val t = PageTemplate.unescape(PageTemplate.stripTags(m.groupValues[1])).trim()
            if (t.isNotBlank() && items.none { it.index == idx }) items += TocItem(idx, t)
        }
        return items.sortedBy { it.index }
    }

    private fun firstHeading(raw: String): String? {
        val m = Regex("<h[1-4][^>]*>(.*?)</h[1-4]>", RegexOption.DOT_MATCHES_ALL).find(raw)
            ?: return null
        val t = PageTemplate.unescape(PageTemplate.stripTags(m.groupValues[1])).trim()
        return t.takeIf { it.isNotBlank() }?.take(40)
    }

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

}

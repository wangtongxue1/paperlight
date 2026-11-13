package com.zhijian.reading.reader

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile

/**
 * Turns an EPUB into one HTML document a WebView can render: the spine in
 * reading order, its images rewritten to the paths they were unpacked to,
 * and a stylesheet of our own so the page reads well in either theme.
 */
object EpubReader {

    data class Content(
        val html: String,
        val baseUrl: String,
        val title: String,
        val dir: File,
    )

    private data class ManifestItem(val href: String, val type: String)

    fun prepare(
        epub: File,
        cacheRoot: File,
        dark: Boolean,
        fontScale: Float,
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
            html = document(title, body.toString(), dark, fontScale),
            baseUrl = opfDir.toURI().toString(),
            title = title,
            dir = dir,
        )
    }.getOrNull()

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
                            if (id != null && href != null) manifest[id] = ManifestItem(href, type)
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

    private fun document(title: String, body: String, dark: Boolean, fontScale: Float): String {
        val ink = if (dark) "#eeeeec" else "#21201c"
        val dim = if (dark) "#b5b3ad" else "#4a463f"
        val bg = if (dark) "#111110" else "#ffffff"
        val rule = if (dark) "#31312e" else "#e2e1de"
        val size = (17f * fontScale)
        return """
            <!DOCTYPE html>
            <html><head>
            <meta charset="utf-8"/>
            <meta name="viewport" content="width=device-width, initial-scale=1"/>
            <title>${escape(title)}</title>
            <style>
              :root { color-scheme: ${if (dark) "dark" else "light"}; }
              body {
                margin: 0; padding: 20px 22px 64px;
                background: $bg; color: $ink;
                font-family: Georgia, 'Noto Serif', 'Songti SC', serif;
                font-size: ${size}px; line-height: 1.78;
                -webkit-text-size-adjust: 100%;
                word-wrap: break-word;
              }
              section.chapter + section.chapter { margin-top: 2.2em; padding-top: 1.6em; border-top: 1px solid $rule; }
              h1, h2, h3, h4 { font-family: -apple-system, 'Noto Sans', sans-serif; line-height: 1.32; margin: 1.6em 0 .8em; }
              h1 { font-size: 1.42em; } h2 { font-size: 1.22em; } h3 { font-size: 1.08em; }
              p { margin: 0 0 1em; text-indent: 1.5em; text-align: justify; }
              p:first-of-type, h1 + p, h2 + p, h3 + p { text-indent: 0; }
              img { max-width: 100%; height: auto; display: block; margin: 1.2em auto; }
              blockquote { margin: 1em 0; padding-left: 1em; border-left: 2px solid $rule; color: $dim; }
              a { color: ${if (dark) "#9eb1ff" else "#3a5bc7"}; text-decoration: none; }
              pre { white-space: pre-wrap; font-size: .86em; background: ${if (dark) "#191918" else "#f4f3f1"}; padding: 10px 12px; border-radius: 6px; overflow-x: auto; }
              hr { border: 0; border-top: 1px solid $rule; margin: 1.8em 0; }
              ::selection { background: ${if (dark) "#1d2e62" else "#e1e9ff"}; }
            </style>
            </head><body>$body</body></html>
        """.trimIndent()
    }

    private fun escape(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

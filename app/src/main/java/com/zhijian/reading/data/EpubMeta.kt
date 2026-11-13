package com.zhijian.reading.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/** What a book's own file says about itself. */
data class FileMetadata(
    val title: String = "",
    val authors: List<String> = emptyList(),
    val publisher: String = "",
    val pubdate: String = "",
    val language: String = "",
    val series: String = "",
    val seriesIndex: Double = 1.0,
    val tags: List<String> = emptyList(),
    val comments: String = "",
    val isbn: String = "",
    val cover: ByteArray? = null,
)

/**
 * Reads title, authors, series and cover from an EPUB, as the desktop
 * app's `internal/ebookmeta` does: the OPF package document inside the zip,
 * found through `META-INF/container.xml`.
 */
object EpubMeta {

    fun parse(file: File): FileMetadata? = runCatching {
        ZipFile(file).use { zip ->
            val opfPath = containerOpf(zip) ?: return@use null
            val opf = zip.getInputStream(zip.getEntry(opfPath) ?: return@use null).use { readMeta(it, zip, opfPath) }
            opf
        }
    }.getOrNull()

    private fun containerOpf(zip: ZipFile): String? {
        val entry = zip.getEntry("META-INF/container.xml") ?: return null
        zip.getInputStream(entry).use { input ->
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(input, null)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "rootfile") {
                    val path = parser.getAttributeValue(null, "full-path")
                    if (!path.isNullOrBlank()) return path
                }
                event = parser.next()
            }
        }
        return null
    }

    private fun readMeta(input: InputStream, zip: ZipFile, opfPath: String): FileMetadata {
        val parser = Xml.newPullParser()
        // Namespaces stay off, so tags arrive qualified ("dc:title"); the
        // prefix is stripped below rather than resolved, which reads the same
        // for EPUB 2 and EPUB 3 package documents.
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var title = ""
        var publisher = ""
        var pubdate = ""
        var language = ""
        var description = ""
        val authors = mutableListOf<String>()
        val tags = mutableListOf<String>()
        var series = ""
        var seriesIndex = 1.0
        var isbn = ""
        var coverId: String? = null
        var coverHref: String? = null
        val manifest = mutableMapOf<String, String>()
        var metaProperty = ""

        var event = parser.eventType
        var inMetadata = false
        var text = StringBuilder()

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    text = StringBuilder()
                    metaProperty = ""
                    when (parser.name.substringAfterLast(':')) {
                        "metadata" -> inMetadata = true
                        "item" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val href = parser.getAttributeValue(null, "href")
                            val props = parser.getAttributeValue(null, "properties").orEmpty()
                            if (id != null && href != null) manifest[id] = href
                            if (props.contains("cover-image")) coverHref = href
                        }
                        "meta" -> {
                            // EPUB 2 carries these in name/content, EPUB 3 in
                            // property/refines with the value as text.
                            val name = parser.getAttributeValue(null, "name")
                            val content = parser.getAttributeValue(null, "content")
                            if (name == "cover" && content != null) coverId = content
                            if (name == "calibre:series" && content != null) series = content
                            if (name == "calibre:series_index" && content != null) {
                                seriesIndex = content.toDoubleOrNull() ?: 1.0
                            }
                            metaProperty = parser.getAttributeValue(null, "property").orEmpty()
                        }
                    }
                }
                XmlPullParser.TEXT -> text.append(parser.text)
                XmlPullParser.END_TAG -> {
                    val value = text.toString().trim()
                    when (parser.name.substringAfterLast(':')) {
                        "metadata" -> inMetadata = false
                        "title" -> if (inMetadata && title.isBlank()) title = value
                        "creator" -> if (inMetadata && value.isNotBlank() && !authors.contains(value)) {
                            authors += value
                        }
                        "publisher" -> if (inMetadata) publisher = value
                        "date" -> if (inMetadata && pubdate.isBlank()) pubdate = value.take(10)
                        "language" -> if (inMetadata) language = value
                        "description" -> if (inMetadata) description = value
                        "subject" -> if (inMetadata && value.isNotBlank()) tags += value
                        "identifier" -> if (inMetadata && value.startsWith("urn:isbn:", true)) {
                            isbn = value.substringAfterLast(':')
                        }
                        "meta" -> when (metaProperty) {
                            "calibre:series" -> if (value.isNotBlank()) series = value
                            "calibre:series_index" -> seriesIndex = value.toDoubleOrNull() ?: seriesIndex
                        }
                    }
                }
            }
            event = parser.next()
        }

        val href = coverHref ?: coverId?.let { manifest[it] }
        val cover = href?.let { readCover(zip, opfPath, it) }

        return FileMetadata(
            title = title,
            authors = authors,
            publisher = publisher,
            pubdate = normalizeDate(pubdate),
            language = language.take(2).lowercase(),
            series = series,
            seriesIndex = seriesIndex,
            tags = tags.distinct(),
            comments = description,
            isbn = isbn,
            cover = cover,
        )
    }

    private fun readCover(zip: ZipFile, opfPath: String, href: String): ByteArray? = runCatching {
        val dir = opfPath.substringBeforeLast('/', "")
        val full = normalizePath(if (dir.isEmpty()) href else "$dir/$href")
        zip.getInputStream(zip.getEntry(full) ?: return null).use { it.readBytes() }
    }.getOrNull()

    private fun normalizePath(p: String): String {
        val parts = mutableListOf<String>()
        p.split('/').forEach {
            when (it) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += it
            }
        }
        return parts.joinToString("/")
    }

    /** A title from the file name, when the file has none. */
    fun fileTitleFallback(name: String): String =
        name.substringBeforeLast('.').replace('_', ' ').trim().ifBlank { "Untitled" }

    private fun normalizeDate(s: String): String {
        if (s.isBlank()) return ""
        return if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(s)) s
        else if (Regex("^\\d{4}$").matches(s)) "$s-01-01"
        else ""
    }
}

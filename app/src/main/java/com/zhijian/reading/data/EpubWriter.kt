package com.zhijian.reading.data

import java.io.File
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a small, valid EPUB 2 package — what the sample library's books
 * are made of, so that Open has something real to read on a fresh install.
 */
object EpubWriter {

    fun write(
        file: File,
        title: String,
        author: String,
        language: String,
        publisher: String,
        date: String,
        chapters: List<Chapter>,
        cover: ByteArray? = null,
        series: String? = null,
        seriesIndex: Double = 1.0,
    ) {
        file.parentFile?.mkdirs()
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            // The first entry must be stored, not deflated, for readers to sniff it.
            val mime = "application/epub+zip".toByteArray()
            val mimeEntry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mime.size.toLong()
                compressedSize = mime.size.toLong()
                crc = CRC32().apply { update(mime) }.value
            }
            zip.putNextEntry(mimeEntry)
            zip.write(mime)
            zip.closeEntry()

            zip.text(
                "META-INF/container.xml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """.trimIndent(),
            )

            val uid = "urn:uuid:${UUID.randomUUID()}"
            val manifest = StringBuilder()
            val spine = StringBuilder()
            chapters.forEachIndexed { i, _ ->
                val id = "ch${i + 1}"
                manifest.append("""    <item id="$id" href="$id.xhtml" media-type="application/xhtml+xml"/>""").append('\n')
                spine.append("""    <itemref idref="$id"/>""").append('\n')
                zip.text("OEBPS/$id.xhtml", chapterXhtml(title, chapters[i]))
            }

            val coverManifest = if (cover != null) {
                zip.putNextEntry(ZipEntry("OEBPS/cover.jpg"))
                zip.write(cover)
                zip.closeEntry()
                """    <item id="cover-img" href="cover.jpg" media-type="image/jpeg"/>"""
            } else ""
            val coverMeta = if (cover != null) {
                """    <meta name="cover" content="cover-img"/>"""
            } else ""
            zip.text(
                "OEBPS/style.css",
                """
                body { margin: 0 24px; font-family: serif; line-height: 1.6; }
                h1 { font-size: 1.4em; margin: 2em 0 1em; font-weight: 600; }
                p { margin: 0 0 1em; text-indent: 1.4em; text-align: justify; }
                """.trimIndent(),
            )

            val seriesMeta = if (series.isNullOrBlank()) "" else
                """
                    <meta name="calibre:series" content="${esc(series)}"/>
                    <meta name="calibre:series_index" content="$seriesIndex"/>
                """.trimIndent()

            zip.text(
                "OEBPS/content.opf",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="bookid">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                    <dc:title>${esc(title)}</dc:title>
                    <dc:creator opf:role="aut">${esc(author)}</dc:creator>
                    <dc:language>${esc(language)}</dc:language>
                    <dc:publisher>${esc(publisher)}</dc:publisher>
                    <dc:date>${esc(date)}</dc:date>
                    <dc:identifier id="bookid">$uid</dc:identifier>
                    <dc:description>${esc(chapters.firstOrNull()?.body?.take(400).orEmpty())}</dc:description>
                $seriesMeta
                $coverMeta
                  </metadata>
                  <manifest>
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    <item id="css" href="style.css" media-type="text/css"/>
                $manifest$coverManifest
                  </manifest>
                  <spine toc="ncx">
                $spine  </spine>
                </package>
                """.trimIndent(),
            )

            zip.text(
                "OEBPS/toc.ncx",
                buildString {
                    append(
                        """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                          <head><meta name="dtb:uid" content="$uid"/></head>
                          <docTitle><text>${esc(title)}</text></docTitle>
                          <navMap>
                        """.trimIndent(),
                    )
                    chapters.forEachIndexed { i, ch ->
                        append(
                            """
                              <navPoint id="np${i + 1}" playOrder="${i + 1}">
                                <navLabel><text>${esc(ch.title)}</text></navLabel>
                                <content src="ch${i + 1}.xhtml"/>
                              </navPoint>
                            """.trimIndent(),
                        )
                        append('\n')
                    }
                    append("  </navMap>\n</ncx>")
                },
            )
        }
    }

    private fun chapterXhtml(bookTitle: String, ch: Chapter): String {
        // Blank lines separate paragraphs; single newlines are line breaks
        // inside one paragraph, which is what verse (千字文, 三字经, 诗词)
        // needs if the lines are not to run together on screen.
        val body = ch.body
            .split(Regex("\\n\\s*\\n"))
            .filter { it.isNotBlank() }
            .joinToString("\n") { block ->
                val lines = block.trim().split("\n").map { esc(it.trim()) }
                "<p>" + lines.joinToString("<br/>") + "</p>"
            }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html>
            <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
            <head>
              <meta charset="utf-8"/>
              <title>${esc(ch.title)}</title>
              <link rel="stylesheet" href="style.css" type="text/css"/>
            </head>
            <body>
              <h1>${esc(ch.title)}</h1>
              $body
            </body>
            </html>
        """.trimIndent()
    }

    private fun ZipOutputStream.text(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        // An XML declaration must be the very first thing in the file, so no
        // leading whitespace may survive: interpolated fragments defeat
        // trimIndent's idea of the common margin, so trim the whole thing.
        write(content.trim().toByteArray())
        closeEntry()
    }

    fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    data class Chapter(val title: String, val body: String)
}

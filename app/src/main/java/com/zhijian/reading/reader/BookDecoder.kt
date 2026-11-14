package com.zhijian.reading.reader

import java.io.File

/**
 * Chooses the decoder for a book file. Every supported format becomes the
 * same BookContent the reader screen can render; a format we can't read
 * (DRM'd, corrupt) comes back null and the UI says so cleanly.
 */
object BookDecoder {

    /** Formats the reader can open in-app, for the import UI and detail page. */
    val SUPPORTED = setOf("EPUB", "PDF", "TXT", "HTML", "HTM", "MD", "FB2", "MOBI", "AZW3")

    fun prepare(file: File, cacheRoot: File, theme: String = "light"): BookContent? = when (
        file.extension.uppercase()
    ) {
        "EPUB" -> EpubReader.prepare(file, cacheRoot, theme)
        "TXT" -> TxtReader.prepare(file, cacheRoot, theme)
        "HTML", "HTM", "MD" -> HtmlReader.prepare(file, cacheRoot, theme)
        "FB2" -> HtmlReader.prepare(file, cacheRoot, theme)
        "MOBI", "AZW3" -> MobiReader.prepare(file, cacheRoot, theme)
        else -> null
    }
}

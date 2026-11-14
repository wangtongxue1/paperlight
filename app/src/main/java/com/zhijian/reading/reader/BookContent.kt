package com.zhijian.reading.reader

import java.io.File

/**
 * One entry of the table of contents: the index of the `section.chapter`
 * it points at, and its display title.
 */
data class TocItem(val index: Int, val title: String)

/**
 * What every format decoder hands the reader: one HTML document the
 * WebView can render, the base URL relative assets resolve against, and
 * the chapters a table of contents can jump to.
 */
data class BookContent(
    val html: String,
    val baseUrl: String,
    val title: String,
    /** Cache folder the book was unpacked to, when the format has files. */
    val dir: File? = null,
    val toc: List<TocItem> = emptyList(),
)

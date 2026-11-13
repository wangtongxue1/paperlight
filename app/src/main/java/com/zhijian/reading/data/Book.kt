package com.zhijian.reading.data

import java.io.File

/** A file of a book on disk: format, its name without extension, its size. */
data class BookFile(
    val format: String,
    val name: String,
    val size: Long,
    val path: String,
) {
    val fileName: String get() = "$name.${format.lowercase()}"
}

/** A book, as the library holds it. */
data class Book(
    val id: Long,
    val title: String,
    val sort: String,
    val authors: List<String>,
    val timestamp: Long,
    val pubdate: String,
    val series: String?,
    val seriesIndex: Double,
    val publisher: String?,
    val tags: List<String>,
    val rating: Int,
    val languages: List<String>,
    val formats: List<BookFile>,
    val path: String,
    val hasCover: Boolean,
    val comments: String,
    val identifiers: Map<String, String>,
) {
    val authorLine: String get() = if (authors.isEmpty()) "" else authors.joinToString(", ")
    val size: Long get() = formats.sumOf { it.size }
    val seriesLine: String
        get() = series?.let { "$it · ${trimIndex(seriesIndex)}" } ?: ""

    /** The folder the book's files live in, relative to the library root. */
    fun folder(root: File): File = File(root, path)

    fun coverFile(root: File): File = File(folder(root), "cover.jpg")
}

fun trimIndex(f: Double): String =
    if (f == f.toLong().toDouble()) f.toLong().toString() else String.format("%.1f", f)

fun humanSize(n: Long): String = when {
    n <= 0 -> "—"
    n < 1 shl 20 -> "${(n + 1023) / 1024} KB"
    n < 1 shl 30 -> String.format("%.1f MB", n.toDouble() / (1 shl 20))
    else -> String.format("%.1f GB", n.toDouble() / (1 shl 30))
}

/** How the library is narrowed: a shelf, a format, or one named value. */
sealed interface Filter {
    data object All : Filter
    data object RecentlyAdded : Filter
    data object Reading : Filter
    data object Favorites : Filter
    data class ByFormat(val format: String) : Filter
    data class ByAuthor(val name: String) : Filter
    data class BySeries(val name: String) : Filter
    data class ByTag(val name: String) : Filter
    data class ByPublisher(val name: String) : Filter
}

enum class SortKey(val label: String) {
    DateAdded("Date added"),
    Title("Title"),
    Author("Author"),
    Series("Series"),
    Rating("Rating"),
    Size("Size"),
}

/** What the shelf currently shows: filter, search text and order. */
data class Query(
    val filter: Filter = Filter.All,
    val text: String = "",
    val sort: SortKey = SortKey.DateAdded,
    val descending: Boolean = true,
    val tagChip: String? = null,
)

/** The sidebar's counts, as the desktop app shows them beside each row. */
data class Counts(
    val all: Int,
    val recentlyAdded: Int,
    val reading: Int,
    val favorites: Int,
    val formats: Map<String, Int>,
    val authors: Int,
    val series: Int,
    val tags: Int,
    val publishers: Int,
)

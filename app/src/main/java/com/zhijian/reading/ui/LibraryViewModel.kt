package com.zhijian.reading.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zhijian.reading.data.Book
import com.zhijian.reading.data.BookFile
import com.zhijian.reading.data.Counts
import com.zhijian.reading.data.EpubMeta
import com.zhijian.reading.data.Filter
import com.zhijian.reading.data.Library
import com.zhijian.reading.data.Query
import com.zhijian.reading.data.SampleLibrary
import com.zhijian.reading.data.Settings
import com.zhijian.reading.data.SortKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What a run of imports did. */
data class ImportResult(val added: Int, val failed: List<String>)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = Settings(app)
    val library = Library(app, File(app.filesDir, "Library"))

    var books by mutableStateOf<List<Book>>(emptyList())
        private set

    var query by mutableStateOf(
        Query(
            sort = runCatching { SortKey.valueOf(settings.sortKey) }.getOrDefault(SortKey.DateAdded),
            descending = settings.sortDescending,
        ),
    )
        private set

    var ready by mutableStateOf(false)
        private set

    var busy by mutableStateOf(false)
        private set

    var themeMode by mutableIntStateOf(settings.themeMode)
        private set

    /** Interface language: "zh" (default), "en" or "system". */
    var langPref by mutableStateOf(settings.langPref)
        private set

    var viewMode by mutableIntStateOf(settings.viewMode)
        private set

    var showRatings by mutableStateOf(settings.showRatings)
        private set

    var readingIds by mutableStateOf(settings.readingIds())
        private set

    /** How far into each book the reader got, 0..1. */
    var progress by mutableStateOf(settings.allProgress())
        private set

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { SampleLibrary.seedIfEmpty(library) }
            reload()
            ready = true
        }
    }

    suspend fun reload() {
        val list = withContext(Dispatchers.IO) { library.loadBooks() }
        withContext(Dispatchers.Main) {
            books = list
            readingIds = settings.readingIds()
            progress = settings.allProgress()
        }
    }

    // ---- Query ------------------------------------------------------------

    fun setFilter(f: Filter) {
        query = query.copy(filter = f, tagChip = null)
    }

    fun setText(t: String) {
        query = query.copy(text = t)
    }

    fun setTagChip(tag: String?) {
        query = query.copy(tagChip = tag)
    }

    fun setSort(key: SortKey, descending: Boolean = query.descending) {
        query = query.copy(sort = key, descending = descending)
        settings.sortKey = key.name
        settings.sortDescending = descending
    }

    fun toggleSortDirection() = setSort(query.sort, !query.descending)

    fun setTheme(mode: Int) {
        themeMode = mode
        settings.themeMode = mode
    }

    fun setLang(pref: String) {
        langPref = pref
        settings.langPref = pref
    }

    fun chooseViewMode(mode: Int) {
        viewMode = mode
        settings.viewMode = mode
    }

    fun chooseShowRatings(on: Boolean) {
        showRatings = on
        settings.showRatings = on
    }

    /** The books the shelf shows, filtered, searched and ordered. */
    fun shown(): List<Book> = applyQuery(books, query, readingIds)

    /** The tags offered as chips: the ones most books carry, in shelf order. */
    fun chips(): List<String> {
        val counts = books.flatMap { it.tags }.groupingBy { it }.eachCount()
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(6)
            .map { it.key }
    }

    fun counts(): Counts {
        val now = System.currentTimeMillis()
        val recent = 30L * 86_400_000L
        return Counts(
            all = books.size,
            recentlyAdded = books.count { now - it.timestamp <= recent },
            reading = books.count { readingIds.contains(it.id) },
            favorites = books.count { it.rating >= 5 },
            formats = books.flatMap { b -> b.formats.map { it.format } }
                .groupingBy { it }.eachCount(),
            authors = books.flatMap { it.authors }.toSet().size,
            series = books.mapNotNull { it.series }.toSet().size,
            tags = books.flatMap { it.tags }.toSet().size,
            publishers = books.mapNotNull { it.publisher }.toSet().size,
        )
    }

    fun byId(id: Long): Book? = books.firstOrNull { it.id == id }

    // ---- Writing ----------------------------------------------------------

    suspend fun save(book: Book) {
        withContext(Dispatchers.IO) { library.update(book) }
        reload()
    }

    suspend fun rate(book: Book, rating: Int) {
        withContext(Dispatchers.IO) { library.setRating(book.id, rating) }
        reload()
    }

    suspend fun delete(book: Book) {
        withContext(Dispatchers.IO) {
            library.delete(book)
            settings.clearProgress(book.id)
        }
        reload()
    }

    fun markRead(bookId: Long, progress: Float) {
        settings.markRead(bookId, progress)
        readingIds = settings.readingIds()
        this.progress = settings.allProgress()
    }

    /** Replaces a book's cover with the JPEG bytes given. */
    suspend fun setCover(book: Book, bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            val folder = book.folder(library.root).apply { mkdirs() }
            File(folder, "cover.jpg").writeBytes(bytes)
            library.setCoverFlag(book.id, true)
        }
        reload()
    }

    // ---- Import -----------------------------------------------------------

    /** Persisted SAF folder used by the in-app device browser, if any. */
    var importTreeUri by mutableStateOf(settings.importTreeUri)
        private set

    fun setImportTree(uri: Uri?) {
        settings.importTreeUri = uri?.toString()
        importTreeUri = uri?.toString()
    }

    suspend fun import(resolver: ContentResolver, uris: List<Uri>): ImportResult {
        busy = true
        val failures = mutableListOf<String>()
        var added = 0
        withContext(Dispatchers.IO) {
            uris.forEach { uri ->
                val name = displayName(resolver, uri) ?: "Untitled"
                runCatching {
                    val temp = File(getApplication<Application>().cacheDir, "import-$name")
                    resolver.openInputStream(uri)?.use { input ->
                        temp.outputStream().use { input.copyTo(it) }
                    } ?: throw IllegalStateException("cannot open")
                    if (importFile(temp, name)) added++ else failures += name
                }.onFailure { failures += name }
            }
        }
        reload()
        busy = false
        return ImportResult(added, failures)
    }

    /**
     * Files one file into the library: reads what it says about itself,
     * makes the book, copies the file into its folder. Returns false when
     * the format is not one this app knows.
     */
    suspend fun importFile(file: File, name: String): Boolean = withContext(Dispatchers.IO) {
        val ext = name.substringAfterLast('.', "").uppercase()
        if (ext !in SUPPORTED_FORMATS) return@withContext false
        runCatching {
            val meta = if (ext == "EPUB") EpubMeta.parse(file) else null
            val title = meta?.title?.takeIf { it.isNotBlank() }
                ?: EpubMeta.fileTitleFallback(name)
            val authors = meta?.authors?.takeIf { it.isNotEmpty() } ?: listOf("Unknown")
            // A book already on the shelf under the same title and author is
            // reported as landed rather than filed twice.
            val known = library.loadBooks().any {
                it.title.equals(title, ignoreCase = true) &&
                    it.authors.firstOrNull()?.equals(authors.first(), ignoreCase = true) == true
            }
            if (known) {
                if (file.parentFile == getApplication<Application>().cacheDir) file.delete()
                return@withContext true
            }
            val id = library.insert(
                title = title,
                authors = authors,
                tags = meta?.tags.orEmpty(),
                series = meta?.series?.takeIf { it.isNotBlank() },
                seriesIndex = meta?.seriesIndex ?: 1.0,
                publisher = meta?.publisher?.takeIf { it.isNotBlank() },
                pubdate = meta?.pubdate.orEmpty(),
                languages = listOfNotNull(meta?.language?.takeIf { it.isNotBlank() }),
                comments = meta?.comments.orEmpty(),
                identifiers = if (meta?.isbn.isNullOrBlank()) emptyMap() else mapOf("isbn" to meta.isbn),
            )
            val path = Library.bookPathFor(authors, title, id)
            val folder = File(library.root, path).apply { mkdirs() }
            val stem = Library.sanitize(title)
            val target = File(folder, "$stem.${ext.lowercase()}")
            if (target.exists()) target.delete()
            file.copyTo(target, overwrite = true)
            library.registerFile(id, path, ext, stem, target.length())
            meta?.cover?.let { bytes ->
                File(folder, "cover.jpg").writeBytes(bytes)
                library.setCoverFlag(id, true)
            }
            if (file.parentFile == getApplication<Application>().cacheDir) file.delete()
            true
        }.getOrDefault(false)
    }

    /**
     * Downloads a file from a link and files it, the way a local import is
     * filed. The name comes from the URL's last segment or the response's
     * own file name.
     */
    suspend fun importFromUrl(url: String): Result<String> {
        val trimmed = url.trim()
        val ok = trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)
        if (!ok) return Result.failure(IllegalArgumentException("not a link"))
        return withContext(Dispatchers.IO) {
            runCatching {
                busy = true
                try {
                    val conn = java.net.URL(trimmed).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 30_000
                    conn.readTimeout = 60_000
                    conn.instanceFollowRedirects = true
                    conn.setRequestProperty("User-Agent", "Zhijian-Reading/0.2")
                    try {
                        if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                        val name = fileNameFrom(conn, trimmed)
                        val temp = File(getApplication<Application>().cacheDir, "import-$name")
                        conn.inputStream.use { input -> temp.outputStream().use { input.copyTo(it) } }
                        if (!importFile(temp, name)) error("unsupported file: $name")
                        reload()
                        name
                    } finally {
                        conn.disconnect()
                    }
                } finally {
                    busy = false
                }
            }
        }
    }

    /** File name from Content-Disposition, else the URL, else a default. */
    private fun fileNameFrom(conn: java.net.HttpURLConnection, url: String): String {
        val disposition = conn.getHeaderField("Content-Disposition").orEmpty()
        val quoted = Regex("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?", RegexOption.IGNORE_CASE)
            .find(disposition)?.groupValues?.get(1)
        val fromUrl = url.substringAfterLast('/').substringBefore('?').substringBefore('#')
        val name = (quoted ?: fromUrl).trim()
        if (name.isNotBlank() && name.contains('.')) return Library.sanitize(name)
        val type = conn.contentType.orEmpty().substringBefore(';').lowercase()
        val ext = when (type) {
            "application/epub+zip" -> "epub"
            "application/pdf" -> "pdf"
            "text/plain" -> "txt"
            "application/x-mobipocket-ebook" -> "mobi"
            "application/vnd.amazon.ebook" -> "azw3"
            else -> ""
        }
        return if (ext.isNotEmpty()) "book.$ext" else "book.epub"
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String? {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return uri.lastPathSegment
    }

    // ---- The book's own files --------------------------------------------

    fun fileOf(book: Book, format: String): File? {
        val f = book.formats.firstOrNull { it.format.equals(format, true) } ?: return null
        val file = File(library.root, f.path)
        return if (file.exists()) file else null
    }

    private fun loadBooks(): List<Book> = library.loadBooks()

    companion object {

        val SUPPORTED_FORMATS = setOf("EPUB", "PDF", "MOBI", "AZW3", "AZW", "TXT")

        /** Filters, searches and orders a list of books. */
        fun applyQuery(books: List<Book>, q: Query, reading: Set<Long> = emptySet()): List<Book> {
            val now = System.currentTimeMillis()
            var list = when (val f = q.filter) {
                is Filter.All -> books
                is Filter.RecentlyAdded -> books.filter { now - it.timestamp <= 30L * 86_400_000L }
                is Filter.Reading -> books.filter { reading.contains(it.id) }
                is Filter.Favorites -> books.filter { it.rating >= 5 }
                is Filter.ByFormat -> books.filter { b -> b.formats.any { it.format.equals(f.format, true) } }
                is Filter.ByAuthor -> books.filter { b -> b.authors.any { it.equals(f.name, true) } }
                is Filter.BySeries -> books.filter { it.series.equals(f.name, true) }
                is Filter.ByTag -> books.filter { b -> b.tags.any { it.equals(f.name, true) } }
                is Filter.ByPublisher -> books.filter { it.publisher.equals(f.name, true) }
            }

            q.tagChip?.let { chip ->
                list = list.filter { b -> b.tags.any { it.equals(chip, true) } }
            }

            val text = q.text.trim()
            if (text.isNotEmpty()) {
                list = list.filter { b ->
                    b.title.contains(text, true) ||
                        b.authors.any { it.contains(text, true) } ||
                        b.tags.any { it.contains(text, true) } ||
                        (b.series?.contains(text, true) == true) ||
                        (b.publisher?.contains(text, true) == true)
                }
            }

            val sorted = when (q.sort) {
                SortKey.DateAdded -> list.sortedBy { it.timestamp }
                SortKey.Title -> list.sortedBy { it.sort.lowercase() }
                SortKey.Author -> list.sortedBy { (it.authors.firstOrNull() ?: "").lowercase() }
                SortKey.Series -> list.sortedWith(
                    compareBy({ it.series ?: "" }, { it.seriesIndex }),
                )
                SortKey.Rating -> list.sortedBy { it.rating }
                SortKey.Size -> list.sortedBy { it.size }
            }
            return if (q.descending) sorted.reversed() else sorted
        }

        fun formatsOf(book: Book): List<BookFile> = book.formats
    }
}

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
import com.zhijian.reading.data.BookStorage
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

    /**
     * The library folder in use. It is a `var` because the reader can move
     * it — out to `MyBooks` on shared storage, or back to the app's own
     * folder — and the shelf has to follow without a restart.
     */
    var library by mutableStateOf(Library(app, BookStorage.current(app, settings)))
        private set

    /** Absolute path of [library]'s root, as the 我的 page shows it. */
    var libraryPath by mutableStateOf(library.root.absolutePath)
        private set

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

    var readingIds by mutableStateOf(settings.readingIds())
        private set

    /** How far into each book the reader got, 0..1. */
    var progress by mutableStateOf(settings.allProgress())
        private set

    /** When the reader last opened each book, so 继续阅读 leads with the newest. */
    var lastOpen by mutableStateOf(settings.allLastOpen())
        private set

    /** Seconds spent reading, across the whole library. */
    var readSeconds by mutableStateOf(settings.totalReadingSeconds())
        private set

    fun markOpened(bookId: Long) {
        settings.markOpen(bookId)
        lastOpen = settings.allLastOpen()
    }

    fun addReadingSeconds(bookId: Long, secs: Int) {
        settings.addReadingSeconds(bookId, secs)
        readSeconds = settings.totalReadingSeconds()
    }

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

    /** Removes several books in one pass, then reloads the shelf once. */
    suspend fun deleteAll(books: List<Book>) {
        withContext(Dispatchers.IO) {
            books.forEach { book ->
                library.delete(book)
                settings.clearProgress(book.id)
            }
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

    // ---- Where the books live ---------------------------------------------

    /** True while the library is being copied to its new folder. */
    var storageBusy by mutableStateOf(false)
        private set

    /** True once the user asked for MyBooks and the system prompt is due. */
    var awaitingStoragePermission by mutableStateOf(false)
        private set

    /** A short failure note for 我的 to show when a move did not work. */
    var storageError by mutableStateOf<String?>(null)
        private set

    val usingPublicStorage: Boolean
        get() = libraryPath == BookStorage.publicRoot().absolutePath

    fun canUsePublicStorage(): Boolean = BookStorage.hasPublicAccess(getApplication())

    /**
     * Switches the library to `MyBooks` in shared storage. Returns false
     * when the system permission is still missing — the caller then opens
     * the 所有文件访问 page and [onStoragePermissionResult] finishes the job.
     */
    fun requestPublicStorage(): Boolean {
        storageError = null
        if (!canUsePublicStorage()) {
            awaitingStoragePermission = true
            return false
        }
        awaitingStoragePermission = false
        moveLibraryTo(BookStorage.publicRoot())
        return true
    }

    /** Back from the system permission page: finish the switch if granted. */
    fun onStoragePermissionResult() {
        if (!awaitingStoragePermission) return
        if (!canUsePublicStorage()) return
        awaitingStoragePermission = false
        moveLibraryTo(BookStorage.publicRoot())
    }

    fun usePrivateStorage() {
        val app = getApplication<Application>()
        if (library.root.absolutePath == BookStorage.privateRoot(app).absolutePath) return
        moveLibraryTo(BookStorage.privateRoot(app))
    }

    private fun moveLibraryTo(target: File) {
        if (storageBusy) return
        val app = getApplication<Application>()
        viewModelScope.launch {
            storageBusy = true
            val moved = withContext(Dispatchers.IO) { BookStorage.moveTo(app, settings, target) }
            if (moved == null) {
                storageError = target.absolutePath
            } else {
                // The private folder is the default, so it is stored as
                // "no choice" rather than as a path.
                if (moved.absolutePath == BookStorage.privateRoot(app).absolutePath) {
                    BookStorage.reset(settings)
                }
                withContext(Dispatchers.IO) { library.close() }
                library = Library(app, moved)
                libraryPath = moved.absolutePath
                reload()
            }
            storageBusy = false
        }
    }

    /**
     * Files that were dropped into the library folder by hand (with the
     * phone's own file manager) are picked up here: each unknown book file
     * is read, given its folder, and moved inside it.
     */
    suspend fun scanLibraryFolder(): Int = withContext(Dispatchers.IO) {
        val root = library.root
        val known = HashSet<String>()
        library.loadBooks().forEach { b ->
            b.formats.forEach { f -> known += File(File(root, f.path), f.fileName).absolutePath }
        }
        val found = root.walkTopDown()
            .filter { it.isFile && it.extension.uppercase() in SUPPORTED_FORMATS }
            .toList()
        var added = 0
        found.forEach { f ->
            if (f.absolutePath in known) return@forEach
            if (fileIntoLibrary(f, f.name, move = true) == 1) added++
        }
        if (added > 0) reload()
        added
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

    // The shelf shows how a running import is doing — 导入 progress, the
    // 微信读书 way. The work lives in the ViewModel's own scope, so the
    // import screen can hand off and pop back while files keep copying.
    var importTotal by mutableStateOf(0)
        private set
    var importDone by mutableStateOf(0)
        private set

    /**
     * Imports the picked files in the background and reports progress
     * through [importTotal]/[importDone]. The toast lands when the last
     * file is filed, wherever the user happens to be looking then.
     */
    fun startImport(resolver: ContentResolver, uris: List<Uri>) {
        if (uris.isEmpty() || importTotal > 0) return
        importTotal = uris.size
        importDone = 0
        busy = true
        viewModelScope.launch {
            var added = 0
            val failures = mutableListOf<String>()
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
                    importDone++
                }
            }
            reload()
            busy = false
            importTotal = 0
            importDone = 0
            notifyImport(added)
        }
    }

    /** Same hand-off for the online link import: progress + toast off-screen. */
    fun startUrlImport(url: String, onDone: (Boolean) -> Unit) {
        if (importTotal > 0) return
        importTotal = 1
        importDone = 0
        busy = true
        viewModelScope.launch {
            val ok = importFromUrl(url).isSuccess
            importTotal = 0
            importDone = 0
            notifyImport(if (ok) 1 else 0)
            withContext(Dispatchers.Main) { onDone(ok) }
        }
    }

    private suspend fun notifyImport(added: Int) {
        val zh = langPref != "en"
        val text = when {
            added > 0 -> if (zh) "已导入 $added 本" else
                if (added == 1) "1 book added" else "$added books added"
            else -> if (zh) "没有导入任何文件" else "Nothing was added"
        }
        withContext(Dispatchers.Main) {
            android.widget.Toast.makeText(getApplication(), text, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Files one file into the library: reads what it says about itself,
     * makes the book, copies the file into its folder. Returns false when
     * the format is not one this app knows.
     */
    suspend fun importFile(file: File, name: String): Boolean = withContext(Dispatchers.IO) {
        val code = fileIntoLibrary(file, name, move = false)
        // The picker's copy in the cache is ours to clean up either way.
        if (code >= 0 && file.parentFile == getApplication<Application>().cacheDir) file.delete()
        code >= 0
    }

    /**
     * The one place a book file becomes a shelf entry: metadata, a row, the
     * book's own folder and the file inside it. Returns 1 when it landed,
     * 0 when the shelf already had that title, -1 when the format is
     * unknown or the copy failed. [move] takes the file instead of copying
     * it — what the library-folder scan needs.
     */
    private fun fileIntoLibrary(file: File, name: String, move: Boolean): Int {
        val ext = name.substringAfterLast('.', "").uppercase()
        if (ext !in SUPPORTED_FORMATS) return -1
        return runCatching {
            // Metadata where the format carries it; the file name carries
            // the rest, so TXT/MD/HTML books still land with a real title.
            val meta = when (ext) {
                "EPUB" -> EpubMeta.parse(file)
                "MOBI", "AZW3" -> com.zhijian.reading.reader.MobiReader.metadata(file)
                "FB2" -> com.zhijian.reading.reader.HtmlReader.fb2Metadata(file)
                else -> null
            }
            val title = meta?.title?.takeIf { it.isNotBlank() }
                ?: EpubMeta.fileTitleFallback(name)
            val authors = meta?.authors?.takeIf { it.isNotEmpty() } ?: listOf("Unknown")
            // A book already on the shelf under the same title and author is
            // reported as landed rather than filed twice.
            val known = library.loadBooks().any {
                it.title.equals(title, ignoreCase = true) &&
                    it.authors.firstOrNull()?.equals(authors.first(), ignoreCase = true) == true
            }
            if (known) return@runCatching 0
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
            if (move && file.absolutePath != target.absolutePath) file.delete()
            1
        }.getOrDefault(-1)
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

        /** Only formats the reader can actually open in-app. */
        val SUPPORTED_FORMATS = setOf("EPUB", "PDF", "TXT", "HTML", "HTM", "MD", "FB2", "MOBI", "AZW3")

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

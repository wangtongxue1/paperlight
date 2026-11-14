package com.zhijian.reading.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * A library folder on disk: its `metadata.db`, the books' folders and the
 * covers. Reads and writes the database in calibre's own shape, so the
 * folder is a calibre library in every way that matters.
 */
class Library(context: Context, val root: File) {

    private val db = LibraryDb(context.applicationContext, root)

    init {
        root.mkdirs()
    }

    // ---- Reading ----------------------------------------------------------

    fun loadBooks(): List<Book> {
        val d = db.readableDatabase
        val books = LinkedHashMap<Long, MutableBook>()

        d.rawQuery(
            """
            SELECT id, title, COALESCE(sort, title) AS sort,
                   CAST((julianday(COALESCE(timestamp, '2000-01-01 00:00:00+00:00')) - 2440587.5) * 86400 AS INTEGER) AS ts,
                   COALESCE(strftime('%Y-%m-%d', pubdate), '') AS pub,
                   series_index, path, has_cover
            FROM books ORDER BY id
            """.trimIndent(), null,
        ).use { c ->
            while (c.moveToNext()) {
                val b = MutableBook(
                    id = c.getLong(0),
                    title = c.getString(1).orEmpty(),
                    sort = c.getString(2).orEmpty(),
                    timestamp = c.getLong(3) * 1000L,
                    pubdate = c.getString(4).orEmpty(),
                    seriesIndex = c.getDouble(5),
                    path = c.getString(6).orEmpty(),
                    hasCover = c.getInt(7) != 0,
                )
                books[b.id] = b
            }
        }
        if (books.isEmpty()) return emptyList()

        fun many(sql: String, put: (MutableBook, String) -> Unit) {
            d.rawQuery(sql, null).use { c ->
                while (c.moveToNext()) books[c.getLong(0)]?.let { put(it, c.getString(1).orEmpty()) }
            }
        }

        many(
            """SELECT l.book, a.name FROM books_authors_link l
               JOIN authors a ON a.id = l.author ORDER BY l.book, a.sort""",
        ) { b, v -> b.authors += v }

        many(
            """SELECT l.book, t.name FROM books_tags_link l
               JOIN tags t ON t.id = l.tag ORDER BY l.book, t.name""",
        ) { b, v -> b.tags += v }

        many(
            """SELECT l.book, s.name FROM books_series_link l
               JOIN series s ON s.id = l.series""",
        ) { b, v -> b.series = v }

        many(
            """SELECT l.book, p.name FROM books_publishers_link l
               JOIN publishers p ON p.id = l.publisher""",
        ) { b, v -> b.publisher = v }

        // books_ratings_link.rating is a foreign key into ratings, whose
        // rating column is calibre's 2-steps-per-star value.
        many(
            """SELECT l.book, CAST(r.rating / 2 AS INTEGER) FROM books_ratings_link l
               JOIN ratings r ON r.id = l.rating""",
        ) { b, v -> b.rating = v.toIntOrNull() ?: 0 }

        many(
            """SELECT l.book, g.lang_code FROM books_languages_link l
               JOIN languages g ON g.id = l.lang_code ORDER BY l.item_order""",
        ) { b, v -> b.languages += v }

        d.rawQuery(
            """SELECT book, format, name, uncompressed_size FROM data ORDER BY book, format""",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                val b = books[c.getLong(0)] ?: continue
                val format = c.getString(1).orEmpty().uppercase()
                val name = c.getString(2).orEmpty()
                val size = c.getLong(3)
                val folder = b.path
                b.formats += BookFile(
                    format = format,
                    name = name,
                    size = size,
                    path = "$folder/$name.${format.lowercase()}",
                )
            }
        }

        d.rawQuery("SELECT book, text FROM comments", null).use { c ->
            while (c.moveToNext()) books[c.getLong(0)]?.comments = c.getString(1).orEmpty()
        }

        d.rawQuery("SELECT book, type, val FROM identifiers", null).use { c ->
            while (c.moveToNext()) {
                books[c.getLong(0)]?.identifiers?.put(c.getString(1).orEmpty(), c.getString(2).orEmpty())
            }
        }

        return books.values.map { it.build() }
    }

    private class MutableBook(
        val id: Long,
        val title: String,
        val sort: String,
        val timestamp: Long,
        val pubdate: String,
        val seriesIndex: Double,
        val path: String,
        val hasCover: Boolean,
    ) {
        val authors = mutableListOf<String>()
        val tags = mutableListOf<String>()
        val languages = mutableListOf<String>()
        val formats = mutableListOf<BookFile>()
        val identifiers = linkedMapOf<String, String>()
        var series: String? = null
        var publisher: String? = null
        var rating: Int = 0
        var comments: String = ""

        fun build() = Book(
            id = id, title = title, sort = sort.ifBlank { title },
            authors = authors, timestamp = timestamp, pubdate = pubdate,
            series = series, seriesIndex = if (seriesIndex <= 0) 1.0 else seriesIndex,
            publisher = publisher, tags = tags, rating = rating,
            languages = languages, formats = formats, path = path,
            hasCover = hasCover, comments = comments,
            identifiers = identifiers.toMap(),
        )
    }

    // ---- Writing ----------------------------------------------------------

    /** Writes a book's metadata back, replacing its links, its tags and its files. */
    fun update(b: Book) {
        val d = db.writableDatabase
        d.beginTransaction()
        try {
            val now = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss'+00:00'")
                .format(Instant.now().atOffset(ZoneOffset.UTC))
            val cv = ContentValues().apply {
                put("title", b.title)
                put("sort", b.sort.ifBlank { b.title })
                put("author_sort", b.authors.firstOrNull()?.let { "$it" } ?: "Unknown")
                put("pubdate", b.pubdate)
                put("series_index", b.seriesIndex)
                put("has_cover", if (b.hasCover) 1 else 0)
                put("last_modified", now)
            }
            d.update("books", cv, "id = ?", arrayOf(b.id.toString()))
            d.delete("books_authors_link", "book = ?", arrayOf(b.id.toString()))
            d.delete("books_tags_link", "book = ?", arrayOf(b.id.toString()))
            d.delete("books_series_link", "book = ?", arrayOf(b.id.toString()))
            d.delete("books_publishers_link", "book = ?", arrayOf(b.id.toString()))
            d.delete("books_ratings_link", "book = ?", arrayOf(b.id.toString()))
            d.delete("books_languages_link", "book = ?", arrayOf(b.id.toString()))
            d.delete("identifiers", "book = ?", arrayOf(b.id.toString()))
            d.delete("comments", "book = ?", arrayOf(b.id.toString()))

            b.authors.forEachIndexed { i, name ->
                val id = lookupId(d, "authors", name)
                link(d, "books_authors_link", "author", b.id, id)
            }
            b.tags.forEach { name ->
                val id = lookupId(d, "tags", name)
                link(d, "books_tags_link", "tag", b.id, id)
            }
            b.series?.takeIf { it.isNotBlank() }?.let {
                val id = lookupId(d, "series", it)
                link(d, "books_series_link", "series", b.id, id)
            }
            b.publisher?.takeIf { it.isNotBlank() }?.let {
                val id = lookupId(d, "publishers", it)
                link(d, "books_publishers_link", "publisher", b.id, id)
            }
            if (b.rating > 0) {
                val rid = lookupRating(d, b.rating * 2)
                link(d, "books_ratings_link", "rating", b.id, rid)
            }
            b.languages.forEachIndexed { i, code ->
                val id = lookupLang(d, code)
                val cv2 = ContentValues().apply {
                    put("book", b.id); put("lang_code", id); put("item_order", i)
                }
                d.insertWithOnConflict("books_languages_link", null, cv2, SQLiteDatabase.CONFLICT_REPLACE)
            }
            if (b.comments.isNotBlank()) {
                d.insertWithOnConflict("comments", null, ContentValues().apply {
                    put("book", b.id); put("text", b.comments)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            b.identifiers.forEach { (k, v) ->
                if (v.isNotBlank()) {
                    d.insertWithOnConflict("identifiers", null, ContentValues().apply {
                        put("book", b.id); put("type", k); put("val", v)
                    }, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }
            // calibre refreshes a book's .opf next time it sees this mark.
            d.insertWithOnConflict("metadata_dirtied", null, ContentValues().apply {
                put("book", b.id)
            }, SQLiteDatabase.CONFLICT_IGNORE)
            d.setTransactionSuccessful()
        } finally {
            d.endTransaction()
        }
    }

    /** Adds a book and its files; returns the new id. */
    fun insert(
        title: String,
        authors: List<String>,
        tags: List<String> = emptyList(),
        series: String? = null,
        seriesIndex: Double = 1.0,
        publisher: String? = null,
        pubdate: String = "",
        languages: List<String> = emptyList(),
        rating: Int = 0,
        comments: String = "",
        identifiers: Map<String, String> = emptyMap(),
    ): Long {
        val d = db.writableDatabase
        val now = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss'+00:00'")
            .format(Instant.now().atOffset(ZoneOffset.UTC))
        val cv = ContentValues().apply {
            put("title", title)
            put("sort", title)
            put("author_sort", authors.firstOrNull() ?: "Unknown")
            put("timestamp", now)
            put("pubdate", pubdate)
            put("series_index", seriesIndex)
            put("path", "")
            put("uuid", UUID.randomUUID().toString())
            put("has_cover", 0)
            put("last_modified", now)
        }
        val id = d.insertOrThrow("books", null, cv)
        val path = bookPathFor(authors, title, id)
        d.update("books", ContentValues().apply { put("path", path) }, "id = ?", arrayOf(id.toString()))
        File(root, path).mkdirs()

        val book = Book(
            id = id, title = title, sort = title, authors = authors,
            timestamp = System.currentTimeMillis(), pubdate = pubdate,
            series = series, seriesIndex = seriesIndex, publisher = publisher,
            tags = tags, rating = rating, languages = languages, formats = emptyList(),
            path = path, hasCover = false, comments = comments, identifiers = identifiers,
        )
        update(book)
        return id
    }

    fun registerFile(bookId: Long, bookPath: String, format: String, name: String, size: Long) {
        db.writableDatabase.insertWithOnConflict("data", null, ContentValues().apply {
            put("book", bookId)
            put("format", format.uppercase())
            put("uncompressed_size", size)
            put("name", name)
        }, SQLiteDatabase.CONFLICT_REPLACE)
        db.writableDatabase.insertWithOnConflict("metadata_dirtied", null, ContentValues().apply {
            put("book", bookId)
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun removeFile(bookId: Long, format: String) {
        db.writableDatabase.delete("data", "book = ? AND format = ?", arrayOf(bookId.toString(), format.uppercase()))
    }

    fun delete(b: Book) {
        db.writableDatabase.delete("books", "id = ?", arrayOf(b.id.toString()))
        (b.folder(root)).deleteRecursively()
    }

    fun setRating(id: Long, rating: Int) {
        val d = db.writableDatabase
        d.delete("books_ratings_link", "book = ?", arrayOf(id.toString()))
        if (rating > 0) {
            val rid = lookupRating(d, rating * 2)
            link(d, "books_ratings_link", "rating", id, rid)
        }
    }

    fun setCoverFlag(id: Long, hasCover: Boolean) {
        db.writableDatabase.update(
            "books",
            ContentValues().apply { put("has_cover", if (hasCover) 1 else 0) },
            "id = ?", arrayOf(id.toString()),
        )
    }

    fun setTimestamp(id: Long, epochMillis: Long) {
        val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss'+00:00'")
            .format(Instant.ofEpochMilli(epochMillis).atOffset(ZoneOffset.UTC))
        db.writableDatabase.update(
            "books",
            ContentValues().apply { put("timestamp", stamp) },
            "id = ?", arrayOf(id.toString()),
        )
    }

    private fun lookupId(d: SQLiteDatabase, table: String, name: String): Long {
        d.rawQuery("SELECT id FROM $table WHERE name = ? COLLATE NOCASE", arrayOf(name)).use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }
        return d.insertOrThrow(table, null, ContentValues().apply { put("name", name) })
    }

    private fun lookupRating(d: SQLiteDatabase, value: Int): Long {
        d.rawQuery("SELECT id FROM ratings WHERE rating = ?", arrayOf(value.toString())).use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }
        return d.insertOrThrow("ratings", null, ContentValues().apply { put("rating", value) })
    }

    private fun lookupLang(d: SQLiteDatabase, code: String): Long {
        d.rawQuery("SELECT id FROM languages WHERE lang_code = ? COLLATE NOCASE", arrayOf(code)).use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }
        return d.insertOrThrow("languages", null, ContentValues().apply { put("lang_code", code) })
    }

    private fun link(d: SQLiteDatabase, table: String, column: String, book: Long, value: Long) {
        val cv = ContentValues().apply {
            put("book", book); put(column, value)
        }
        d.insertWithOnConflict(table, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun close() = db.close()

    companion object {
        private val illegal = Regex("[\\\\/:*?\"<>|]")

        /** calibre's folder for a book: `Author/Title (id)`. */
        fun bookPathFor(authors: List<String>, title: String, id: Long): String {
            val author = sanitize(authors.firstOrNull() ?: "Unknown").ifBlank { "Unknown" }
            val name = sanitize(title).ifBlank { "Unknown" }
            return "$author/$name ($id)"
        }

        fun sanitize(s: String): String =
            s.replace(illegal, "_").replace(Regex("\\s+"), " ").trim().trimEnd('.').take(120)
    }
}

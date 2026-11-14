package com.zhijian.reading.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** A place the reader can jump back to, dropped anywhere in a book. */
data class Bookmark(
    val id: String,
    val bookId: Long,
    /** 0..1 position in the book, as the reader measures it. */
    val progress: Float,
    val chapter: Int,
    val label: String,
    val createdAt: Long,
)

/** A passage the reader kept: the quoted text plus an optional note. */
data class Annotation(
    val id: String,
    val bookId: Long,
    val chapter: Int,
    val quote: String,
    val note: String,
    val progress: Float,
    val createdAt: Long,
    /** Highlight look: yellow / green / blue / pink / underline. */
    val style: String = "yellow",
)

/**
 * Bookmarks and annotations, kept in a small JSON file of our own rather
 * than calibre's metadata.db — these are reading notes, not library
 * metadata, and the folder should stay a plain calibre library.
 */
class ReadingNotes(context: Context) {

    private val file = File(context.filesDir, "reading-notes.json")
    private val lock = Any()

    // ---- reads -------------------------------------------------------------

    fun bookmarks(bookId: Long): List<Bookmark> = synchronized(lock) {
        read().first.filter { it.bookId == bookId }.sortedBy { it.progress }
    }

    fun annotations(bookId: Long): List<Annotation> = synchronized(lock) {
        read().second.filter { it.bookId == bookId }.sortedBy { it.createdAt }
    }

    // ---- writes ------------------------------------------------------------

    fun addBookmark(bookId: Long, progress: Float, chapter: Int, label: String): Bookmark =
        synchronized(lock) {
            val (marks, notes) = read()
            val b = Bookmark(
                id = UUID.randomUUID().toString().take(12),
                bookId = bookId,
                progress = progress.coerceIn(0f, 1f),
                chapter = chapter,
                label = label,
                createdAt = System.currentTimeMillis(),
            )
            write(marks + b, notes)
            b
        }

    fun removeBookmark(id: String) = synchronized(lock) {
        val (marks, notes) = read()
        write(marks.filterNot { it.id == id }, notes)
    }

    fun addAnnotation(
        bookId: Long,
        chapter: Int,
        quote: String,
        note: String,
        progress: Float,
        style: String = "yellow",
    ): Annotation = synchronized(lock) {
        val (marks, notes) = read()
        val a = Annotation(
            id = UUID.randomUUID().toString().take(12),
            bookId = bookId,
            chapter = chapter,
            quote = quote,
            note = note.trim(),
            progress = progress.coerceIn(0f, 1f),
            createdAt = System.currentTimeMillis(),
            style = style,
        )
        write(marks, notes + a)
        a
    }

    fun removeAnnotation(id: String) = synchronized(lock) {
        val (marks, notes) = read()
        write(marks, notes.filterNot { it.id == id })
    }

    // ---- storage -----------------------------------------------------------

    private fun read(): Pair<List<Bookmark>, List<Annotation>> {
        val marks = mutableListOf<Bookmark>()
        val notes = mutableListOf<Annotation>()
        runCatching {
            if (!file.exists()) return Pair(marks, notes)
            val root = JSONObject(file.readText())
            root.optJSONArray("bookmarks")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    marks += Bookmark(
                        id = o.getString("id"),
                        bookId = o.getLong("bookId"),
                        progress = o.optDouble("progress", 0.0).toFloat(),
                        chapter = o.optInt("chapter", 0),
                        label = o.optString("label", ""),
                        createdAt = o.optLong("createdAt", 0L),
                    )
                }
            }
            root.optJSONArray("annotations")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    notes += Annotation(
                        id = o.getString("id"),
                        bookId = o.getLong("bookId"),
                        chapter = o.optInt("chapter", 0),
                        quote = o.optString("quote", ""),
                        note = o.optString("note", ""),
                        progress = o.optDouble("progress", 0.0).toFloat(),
                        createdAt = o.optLong("createdAt", 0L),
                        style = o.optString("style", "yellow"),
                    )
                }
            }
        }
        return Pair(marks, notes)
    }

    private fun write(marks: List<Bookmark>, notes: List<Annotation>) {
        runCatching {
            val root = JSONObject()
            val m = JSONArray()
            marks.forEach {
                m.put(
                    JSONObject()
                        .put("id", it.id)
                        .put("bookId", it.bookId)
                        .put("progress", it.progress.toDouble())
                        .put("chapter", it.chapter)
                        .put("label", it.label)
                        .put("createdAt", it.createdAt),
                )
            }
            val a = JSONArray()
            notes.forEach {
                a.put(
                    JSONObject()
                        .put("id", it.id)
                        .put("bookId", it.bookId)
                        .put("chapter", it.chapter)
                        .put("quote", it.quote)
                        .put("note", it.note)
                        .put("progress", it.progress.toDouble())
                        .put("style", it.style)
                        .put("createdAt", it.createdAt),
                )
            }
            root.put("bookmarks", m)
            root.put("annotations", a)
            file.parentFile?.mkdirs()
            file.writeText(root.toString())
        }
    }
}

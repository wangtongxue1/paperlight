package com.zhijian.reading.data

import android.content.Context

/** What the app remembers between launches. */
class Settings(context: Context) {

    private val p = context.getSharedPreferences("zhijian.settings", Context.MODE_PRIVATE)
    private val reading = context.getSharedPreferences("zhijian.reading", Context.MODE_PRIVATE)

    /** 0 follows the system, 1 is light, 2 is dark. */
    var themeMode: Int
        get() = p.getInt("theme", 0)
        set(v) = p.edit().putInt("theme", v).apply()

    /** Interface language: "zh" (default), "en" or "system". */
    var langPref: String
        get() = p.getString("lang", "zh") ?: "zh"
        set(v) = p.edit().putString("lang", v).apply()

    /** 0 is the shelf, 1 is the list. */
    var viewMode: Int
        get() = p.getInt("view", 0)
        set(v) = p.edit().putInt("view", v).apply()

    var sortKey: String
        get() = p.getString("sort", SortKey.DateAdded.name) ?: SortKey.DateAdded.name
        set(v) = p.edit().putString("sort", v).apply()

    var sortDescending: Boolean
        get() = p.getBoolean("desc", true)
        set(v) = p.edit().putBoolean("desc", v).apply()

    var detailSidePanel: Boolean
        get() = p.getBoolean("detailPanel", true)
        set(v) = p.edit().putBoolean("detailPanel", v).apply()

    /** Persisted SAF tree for the in-app device browser, or null. */
    var importTreeUri: String?
        get() = p.getString("importTree", null)
        set(v) = p.edit().putString("importTree", v).apply()

    /** Reader page mode: "scroll" (default) or "paged". */
    var readerMode: String
        get() = p.getString("readerMode", "scroll") ?: "scroll"
        set(v) = p.edit().putString("readerMode", v).apply()

    /** Reader text scale, 1 = the book's own size. */
    var readerFontScale: Float
        get() = p.getFloat("readerFontScale", 1f)
        set(v) = p.edit().putFloat("readerFontScale", v).apply()

    /** Reader page theme: "light" (default), "sepia", "gray" or "dark". */
    var readerTheme: String
        get() = p.getString("readerTheme", "light") ?: "light"
        set(v) = p.edit().putString("readerTheme", v).apply()

    /** Reader body line height, 1 = the book's own. */
    var readerLineHeight: Float
        get() = p.getFloat("readerLineHeight", 1.9f)
        set(v) = p.edit().putFloat("readerLineHeight", v).apply()

    /** Reader text face: "serif" (default) or "sans". */
    var readerFontFamily: String
        get() = p.getString("readerFontFamily", "serif") ?: "serif"
        set(v) = p.edit().putString("readerFontFamily", v).apply()

    /** Reader screen brightness, 0..1; 0 follows the system. */
    var readerBrightness: Float
        get() = p.getFloat("readerBrightness", 0f)
        set(v) = p.edit().putFloat("readerBrightness", v).apply()

    // ---- Reading progress -------------------------------------------------

    fun progress(bookId: Long): Float = reading.getFloat("p$bookId", 0f)

    fun markRead(bookId: Long, value: Float) {
        reading.edit().putFloat("p$bookId", value.coerceIn(0f, 1f)).apply()
    }

    fun readingIds(): Set<Long> =
        reading.all.keys
            .filter { it.startsWith("p") }
            .mapNotNull { it.drop(1).toLongOrNull() }
            .filter { (reading.getFloat("p$it", 0f)) > 0f }
            .toSet()

    fun allProgress(): Map<Long, Float> =
        reading.all.entries
            .mapNotNull { (k, v) ->
                val id = k.drop(1).takeIf { k.startsWith("p") }?.toLongOrNull() ?: return@mapNotNull null
                val f = (v as? Float) ?: return@mapNotNull null
                if (f > 0f) id to f else null
            }
            .toMap()

    fun clearProgress(bookId: Long) {
        reading.edit()
            .remove("p$bookId")
            .remove("t$bookId")
            .remove("o$bookId")
            .remove("c$bookId")
            .remove("f$bookId")
            .apply()
    }

    // ---- How many times each book was read through -------------------------
    // Keys "c$bookId" count the finishes; "f$bookId" is the tick that keeps
    // one finish from being counted twice when the reader scrolls back over
    // the last page and forward again.

    fun readCount(bookId: Long): Int = reading.getInt("c$bookId", 0)

    fun bumpReadCount(bookId: Long): Int {
        val next = readCount(bookId) + 1
        reading.edit().putInt("c$bookId", next).apply()
        return next
    }

    fun allReadCounts(): Map<Long, Int> =
        reading.all.entries
            .mapNotNull { (k, v) ->
                val id = k.drop(1).takeIf { k.startsWith("c") }?.toLongOrNull() ?: return@mapNotNull null
                val n = (v as? Int) ?: return@mapNotNull null
                if (n > 0) id to n else null
            }
            .toMap()

    fun isCountedFinished(bookId: Long): Boolean = reading.getBoolean("f$bookId", false)

    fun setCountedFinished(bookId: Long, done: Boolean) {
        reading.edit().putBoolean("f$bookId", done).apply()
    }

    // ---- Reading time -------------------------------------------------------
    // Keys "t$bookId" hold the seconds spent in that book's reader; "ttotal"
    // is the library-wide sum so 我的 never has to walk every book.

    fun addReadingSeconds(bookId: Long, secs: Int) {
        if (secs <= 0) return
        reading.edit()
            .putLong("t$bookId", reading.getLong("t$bookId", 0L) + secs)
            .putLong("ttotal", reading.getLong("ttotal", 0L) + secs)
            .apply()
    }

    fun totalReadingSeconds(): Long = reading.getLong("ttotal", 0L)

    // ---- Last opened --------------------------------------------------------
    // Keys "o$bookId" remember when the reader last opened each book, so
    // 继续阅读 can put the newest-read book on stage.

    fun markOpen(bookId: Long) {
        reading.edit().putLong("o$bookId", System.currentTimeMillis()).apply()
    }

    fun allLastOpen(): Map<Long, Long> =
        reading.all.keys
            .filter { it.startsWith("o") }
            .mapNotNull { it.drop(1).toLongOrNull() }
            .associateWith { reading.getLong("o$it", 0L) }
}

package com.zhijian.reading.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import java.io.File

/**
 * Draws the sample library's covers: plain typographic jackets in the six
 * inks the desktop app's demo uses, so the shelf has real covers to stand
 * on rather than gray boxes.
 */
object Covers {

    const val NAVY = 0xFF1E2A4A.toInt()
    const val TEAL = 0xFF2A5E6D.toInt()
    const val PLUM = 0xFF55294B.toInt()
    const val RUST = 0xFFB4543A.toInt()
    const val CREAM = 0xFFE6D9BD.toInt()
    const val OLIVE = 0xFF45543A.toInt()

    private const val W = 600
    private const val H = 900

    fun generate(
        file: File,
        title: String,
        author: String,
        ink: Int,
        publisher: String,
        year: String,
    ): Boolean = runCatching {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(ink)

        val onLight = ink == CREAM
        val fg = if (onLight) 0xFF21201C.toInt() else 0xFFF5F1E8.toInt()
        val dim = if (onLight) 0x9921201C.toInt() else 0xB3F5F1E8.toInt()
        val pad = 64f

        // A hairline frame, as printed jackets wear.
        val rule = Paint().apply {
            color = dim
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        c.drawRect(pad / 2, pad / 2, W - pad / 2, H - pad / 2, rule)

        val authorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dim
            textSize = 26f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.18f
        }
        c.drawText(author.uppercase().take(34), pad, pad + 44f, authorPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textSize = 78f
        }
        val maxWidth = W - pad * 2
        var size = 78f
        var lines: List<String> = emptyList()
        while (true) {
            titlePaint.textSize = size
            lines = wrap(title, titlePaint, maxWidth)
            val widest = lines.maxOfOrNull { titlePaint.measureText(it) } ?: 0f
            if (widest <= maxWidth && lines.size <= 5) break
            if (size <= 30f) break
            size -= 3f
        }
        var y = H * 0.46f
        if (lines.size > 3) y -= (lines.size - 3) * size * 0.6f
        lines.forEach { line ->
            c.drawText(line, pad, y, titlePaint)
            y += size * 1.22f
        }

        val footPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dim
            textSize = 24f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        c.drawText(listOf(publisher, year).filter { it.isNotBlank() }.joinToString(" · "), pad, H - pad - 16f, footPaint)

        val out = file.outputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
        out.close()
        bmp.recycle()
        true
    }.getOrDefault(false)

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return listOf("Untitled")
        // Latin breaks on spaces; Han runs break per character.
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch.code in 0x2E80..0x9FFF) {
                tokens += ch.toString(); i++
            } else {
                val start = i
                while (i < text.length && text[i].code !in 0x2E80..0x9FFF) i++
                text.substring(start, i).split(' ').forEach { if (it.isNotEmpty()) tokens += "$it " }
            }
        }
        for (t in tokens) {
            val trial = sb.toString() + t
            if (paint.measureText(trial.trim()) > maxWidth && sb.isNotEmpty()) {
                out += sb.toString().trim()
                sb.clear()
            }
            sb.append(t)
        }
        if (sb.isNotBlank()) out += sb.toString().trim()
        return if (out.isEmpty()) listOf(text) else out
    }

    /** A jacket's background, for a book that has no cover file. */
    fun inkOf(index: Int): Int = when (index % 6) {
        0 -> NAVY; 1 -> TEAL; 2 -> PLUM; 3 -> RUST; 4 -> CREAM; else -> OLIVE
    }

    fun isLight(ink: Int) = ink == CREAM

    /** The colors a coverless book is drawn in, mirroring the desktop's placeholder. */
    fun placeholderColors(book: Book): Pair<Int, Int> {
        val ink = inkOf(book.id.toInt())
        return ink to if (isLight(ink)) 0xFF21201C.toInt() else Color.WHITE
    }

    fun measureTextWidth(paint: Paint, s: String): Float {
        val r = Rect()
        paint.getTextBounds(s, 0, s.length, r)
        return r.width().toFloat()
    }
}

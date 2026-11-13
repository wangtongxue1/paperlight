package com.zhijian.reading.data

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File

/** Writes a one-page PDF for the sample books that carry that format. */
object PdfMake {

    fun write(file: File, title: String, author: String, about: String): Boolean = runCatching {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val c = page.canvas

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1A1712.toInt()
            textSize = 22f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val authorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF6B6558.toInt()
            textSize = 12f
            typeface = Typeface.SANS_SERIF
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF2A2A28.toInt()
            textSize = 11f
            typeface = Typeface.SERIF
        }
        val footPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF8D8D86.toInt()
            textSize = 9f
            typeface = Typeface.MONOSPACE
        }

        val margin = 64f
        val width = 595f - margin * 2

        c.drawText(title, margin, 120f, titlePaint)
        c.drawText(author, margin, 142f, authorPaint)
        c.drawLine(margin, 156f, 595f - margin, 156f, Paint().apply {
            color = 0xFFDAD9D6.toInt(); strokeWidth = 0.7f
        })

        var y = 186f
        wrap(about, bodyPaint, width).forEach { line ->
            c.drawText(line, margin, y, bodyPaint)
            y += 16f
        }

        y += 18f
        wrap(
            "这份 PDF 由纸间阅读随样书生成，用来演示该格式的打开与阅读。正文节选请看同名 EPUB。",
            bodyPaint, width,
        ).forEach { line ->
            c.drawText(line, margin, y, bodyPaint)
            y += 16f
        }

        c.drawText("纸间阅读 · 样书", margin, 800f, footPaint)

        doc.finishPage(page)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        true
    }.getOrDefault(false)

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch.code in 0x2E80..0x9FFF || ch.code in 0x3000..0x303F) {
                tokens += ch.toString(); i++
            } else {
                val start = i
                while (i < text.length && text[i].code !in 0x2E80..0x9FFF && text[i].code !in 0x3000..0x303F) i++
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
}

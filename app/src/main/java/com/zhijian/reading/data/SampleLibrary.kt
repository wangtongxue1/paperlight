package com.zhijian.reading.data

import java.io.File

/**
 * The library a fresh install opens: a handful of Chinese public-domain
 * classics shipped **in full**, not as excerpts. The texts live in
 * `assets/samples/` as plain text — a `## 章名` line marks each chapter,
 * blank lines separate paragraphs — so the shelf has real books to read
 * before anything is imported.
 *
 * Only works that can be shipped complete are included: the long novels
 * (红楼梦, 三国演义 …) are left to the reader to import, since a built-in
 * excerpt of them would be a fragment pretending to be a book.
 */
object SampleLibrary {

    private data class Spec(
        val title: String,
        val author: String,
        val ink: Int,
        val series: String? = null,
        val seriesIndex: Double = 1.0,
        val tags: List<String>,
        val rating: Int,
        val extraPdf: Boolean = false,
        val about: String,
        /** File under assets/samples, complete text. */
        val asset: String,
    )

    private val specs = listOf(
        Spec(
            "道德经", "老子", Covers.NAVY,
            "诸子经典", 1.0,
            tags = listOf("哲学", "道家", "经典"), rating = 5, extraPdf = true,
            about = "道家根本经典，五千余言，分八十一章。通行王弼本文字，全文收录。",
            asset = "daodejing.txt",
        ),
        Spec(
            "论语", "孔子弟子及再传弟子", Covers.RUST,
            "诸子经典", 2.0,
            tags = listOf("哲学", "儒家", "经典"), rating = 5,
            about = "孔子与弟子的言行记录，二十篇。语录体，一句一句，像有人在你对面说话。全文收录。",
            asset = "lunyu.txt",
        ),
        Spec(
            "大学", "曾子", Covers.CREAM,
            "诸子经典", 3.0,
            tags = listOf("哲学", "儒家", "经典"), rating = 5,
            about = "《礼记》中的一篇，朱熹列为四书之首、初学入德之门。经一章、传十章，全文收录。",
            asset = "daxue.txt",
        ),
        Spec(
            "中庸", "子思", Covers.CREAM,
            "诸子经典", 4.0,
            tags = listOf("哲学", "儒家", "经典"), rating = 5,
            about = "四书之一，讲天命之谓性、率性之谓道，把修养说到极细处。全文收录。",
            asset = "zhongyong.txt",
        ),
        Spec(
            "孙子兵法", "孙武", Covers.OLIVE,
            "诸子经典", 5.0,
            tags = listOf("军事", "兵家", "经典"), rating = 5,
            about = "兵家之祖，十三篇。讲的不只是打仗，是怎么把一件事的胜负在动手之前就算清楚。全文收录。",
            asset = "sunzi.txt",
        ),
        Spec(
            "庄子", "庄周", Covers.TEAL,
            "诸子经典", 6.0,
            tags = listOf("哲学", "道家", "经典"), rating = 5,
            about = "内篇七篇，先秦文章里想象最奇、文字最放的一部，一般认为是庄子本人所作。全文收录。",
            asset = "zhuangzi.txt",
        ),
        Spec(
            "三字经", "王应麟", Covers.CREAM,
            "蒙学", 1.0,
            tags = listOf("蒙学", "经典"), rating = 4,
            about = "旧时启蒙的三字一句，讲做人的规矩、读书的次序和一点历史的轮廓。通行本全文收录。",
            asset = "sanzijing.txt",
        ),
        Spec(
            "千字文", "周兴嗣", Covers.CREAM,
            "蒙学", 2.0,
            tags = listOf("蒙学", "经典"), rating = 4,
            about = "一千个字，四字一句，句句不重字。天地日月、历史典故、做人的道理，都装在这千字里。全文收录。",
            asset = "qianziwen.txt",
        ),
        Spec(
            "弟子规", "李毓秀", Covers.CREAM,
            "蒙学", 3.0,
            tags = listOf("蒙学", "经典"), rating = 4,
            about = "蒙学韵语，总叙之外分入则孝、出则弟、谨、信、泛爱众、亲仁、余力学文七门。全文收录。",
            asset = "dizigui.txt",
        ),
        Spec(
            "唐诗选", "李白、王维、王之涣等", Covers.PLUM,
            "诗选", 1.0,
            tags = listOf("诗词", "经典"), rating = 5, extraPdf = true,
            about = "十首唐人绝句，从静夜思到出塞，都是几乎人人能背的。每首均完整收录。",
            asset = "tangshi.txt",
        ),
        Spec(
            "宋词选", "苏轼、李清照、辛弃疾", Covers.PLUM,
            "诗选", 2.0,
            tags = listOf("诗词", "经典"), rating = 5,
            about = "词是唱出来的句子，长短不齐，反而更近人心。水调歌头、如梦令、青玉案三阕，均为完篇。",
            asset = "songci.txt",
        ),
        Spec(
            "古文观止选", "吴楚材、吴调侯", Covers.CREAM,
            "诗选", 3.0,
            tags = listOf("散文", "经典"), rating = 5,
            about = "旧时学古文的课本。陋室铭、爱莲说、岳阳楼记三篇，均全文收录。",
            asset = "guwen.txt",
        ),
        Spec(
            "山海经选", "佚名", Covers.PLUM,
            tags = listOf("神话", "经典"), rating = 4,
            about = "古人的地理志与神话簿，山川物产之外，记着精卫、夸父这些不认命的故事。两则均全文。",
            asset = "shanhaijing.txt",
        ),
        Spec(
            "世说新语选", "刘义庆", Covers.RUST,
            tags = listOf("笔记", "经典"), rating = 4,
            about = "魏晋名士的言行片段，一小段一小段，像随笔。咏雪、管宁割席两则，均为完篇。",
            asset = "shishuo.txt",
        ),
    )

    /** Fills an empty library with the sample books; returns true if it did. */
    fun seedIfEmpty(lib: Library): Boolean {
        if (lib.loadBooks().isNotEmpty()) return false
        seed(lib)
        return true
    }

    fun seed(lib: Library) {
        val root = lib.root
        // Newest at the top of the shelf, oldest at the bottom, as Date added.
        val base = System.currentTimeMillis() - 86_400_000L

        specs.forEachIndexed { index, s ->
            val id = lib.insert(
                title = s.title,
                authors = listOf(s.author),
                tags = s.tags,
                series = s.series,
                seriesIndex = s.seriesIndex,
                publisher = PUBLISHER,
                pubdate = PUBDATE,
                languages = listOf("zh"),
                rating = s.rating,
                comments = "<p>${EpubWriter.esc(s.about)}</p>",
                identifiers = mapOf("isbn" to pseudoIsbn(index)),
            )
            lib.setTimestamp(id, base - index * 86_400_000L)

            val path = Library.bookPathFor(listOf(s.author), s.title, id)
            val folder = File(root, path).apply { mkdirs() }

            var coverBytes: ByteArray? = null
            val jpg = File(folder, "cover.jpg")
            if (Covers.generate(jpg, s.title, s.author, s.ink, PUBLISHER, PUBDATE.take(4))) {
                coverBytes = jpg.readBytes()
            }

            val epub = File(folder, "${Library.sanitize(s.title)}.epub")
            EpubWriter.write(
                file = epub,
                title = s.title,
                author = s.author,
                language = "zh",
                publisher = PUBLISHER,
                date = PUBDATE,
                chapters = chaptersFor(lib, s),
                cover = coverBytes,
                series = s.series,
                seriesIndex = s.seriesIndex,
            )
            lib.registerFile(id, path, "EPUB", Library.sanitize(s.title), epub.length())

            if (s.extraPdf) {
                val pdf = File(folder, "${Library.sanitize(s.title)}.pdf")
                if (PdfMake.write(pdf, s.title, s.author, s.about)) {
                    lib.registerFile(id, path, "PDF", Library.sanitize(s.title), pdf.length())
                }
            }

            lib.setCoverFlag(id, coverBytes != null)
        }
    }

    private fun chaptersFor(lib: Library, s: Spec): List<EpubWriter.Chapter> {
        val text = lib.appContext.assets
            .open("samples/${s.asset}")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

        val chapters = mutableListOf<EpubWriter.Chapter>()
        var title: String? = null
        val body = StringBuilder()

        fun flush() {
            val t = title ?: return
            val b = body.toString().trim()
            if (b.isNotEmpty()) chapters += EpubWriter.Chapter(t, b)
            body.setLength(0)
        }

        text.lineSequence().forEach { line ->
            if (line.startsWith("## ")) {
                flush()
                title = line.removePrefix("## ").trim()
            } else {
                body.appendLine(line)
            }
        }
        flush()

        val note = EpubWriter.Chapter(
            "关于本书",
            s.about + "\n\n" +
                "${s.author}。公版典籍，全文收录。" +
                (s.series?.let { "「$it」第 ${trimIndex(s.seriesIndex)} 种。" } ?: "") +
                "归类：${s.tags.joinToString("、")}。\n\n" +
                "文本据中文维基文库校订，统一转为简体；原书异文与注解不列入正文。" +
                "本书随纸间阅读内置，用于首次打开时就有书可读。",
        )
        return listOf(note) + chapters
    }

    private fun pseudoIsbn(i: Int): String =
        "978" + (1000000000L + i * 7919L).toString().takeLast(10)

    private fun trimIndex(f: Double): String =
        if (f == f.toLong().toDouble()) f.toLong().toString()
        else String.format(java.util.Locale.US, "%.1f", f)

    private const val PUBLISHER = "纸间阅读·公版库"
    private const val PUBDATE = "2026-11-01"
}

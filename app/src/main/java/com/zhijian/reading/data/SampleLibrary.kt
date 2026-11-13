package com.zhijian.reading.data

import java.io.File

/**
 * The library a fresh install opens: Chinese public-domain classics with
 * real excerpts, so the shelf is useful before anything is imported and
 * the reader has genuine pages to set. Every text here is out of copyright
 * (pre-modern Chinese literature); where a work is long, the file carries
 * famous chapters and poems as 节选 rather than a whole edition.
 */
object SampleLibrary {

    private data class Spec(
        val title: String,
        val author: String,
        val ink: Int,
        val series: String? = null,
        val seriesIndex: Double = 1.0,
        val publisher: String,
        val date: String,
        val language: String = "zh",
        val tags: List<String>,
        val rating: Int,
        val cover: Boolean = true,
        val extraPdf: Boolean = false,
        val about: String,
        val chapters: List<Pair<String, String>> = emptyList(),
    )

    private val specs = listOf(
        Spec(
            "道德经", "老子", Covers.NAVY,
            "诸子经典", 1.0, "中华书局", "2016-01-01",
            tags = listOf("哲学", "经典", "诸子"), rating = 5, extraPdf = true,
            about = "道家的根本经典，五千言，分八十一章。言简意深，讲的是天地万物的来处与人该怎样活在其中。",
            chapters = listOf(
                "第一章" to "道可道，非常道；名可名，非常名。\n\n无名，天地之始；有名，万物之母。\n\n故常无欲，以观其妙；常有欲，以观其徼。\n\n此两者，同出而异名，同谓之玄。玄之又玄，众妙之门。",
                "第八章" to "上善若水。水善利万物而不争，处众人之所恶，故几于道。\n\n居善地，心善渊，与善仁，言善信，政善治，事善能，动善时。\n\n夫唯不争，故无尤。",
                "第三十三章" to "知人者智，自知者明。胜人者有力，自胜者强。\n\n知足者富。强行者有志。不失其所者久。死而不亡者寿。",
            ),
        ),
        Spec(
            "论语", "孔子及其弟子", Covers.RUST,
            "诸子经典", 2.0, "中华书局", "2016-01-01",
            tags = listOf("哲学", "经典", "诸子"), rating = 5,
            about = "孔子与弟子的言行录，二十篇。语录体，一句一句，像有人在你对面说话。",
            chapters = listOf(
                "学而（节选）" to "子曰：学而时习之，不亦说乎？有朋自远方来，不亦乐乎？人不知而不愠，不亦君子乎？\n\n曾子曰：吾日三省吾身——为人谋而不忠乎？与朋友交而不信乎？传不习乎？\n\n子曰：君子食无求饱，居无求安，敏于事而慎于言，就有道而正焉，可谓好学也已。",
                "为政（节选）" to "子曰：温故而知新，可以为师矣。\n\n子曰：学而不思则罔，思而不学则殆。\n\n子曰：由，诲女知之乎！知之为知之，不知为不知，是知也。",
            ),
        ),
        Spec(
            "庄子", "庄周", Covers.TEAL,
            "诸子经典", 3.0, "中华书局", "2016-01-01",
            tags = listOf("哲学", "经典", "诸子"), rating = 5,
            about = "先秦道家的文章，想象最奇，文字最放。内篇七篇一般认为是庄子本人所作。",
            chapters = listOf(
                "逍遥游（节选）" to "北冥有鱼，其名为鲲。鲲之大，不知其几千里也；化而为鸟，其名为鹏。鹏之背，不知其几千里也；怒而飞，其翼若垂天之云。\n\n是鸟也，海运则将徙于南冥。南冥者，天池也。\n\n且夫水之积也不厚，则其负大舟也无力。覆杯水于坳堂之上，则芥为之舟；置杯焉则胶，水浅而舟大也。",
            ),
        ),
        Spec(
            "孙子兵法", "孙武", Covers.OLIVE,
            "诸子经典", 4.0, "中华书局", "2016-01-01",
            tags = listOf("军事", "经典", "诸子"), rating = 4,
            about = "兵家之祖，十三篇。讲的不只是打仗，是怎么把一件事的胜负在动手之前就算清楚。",
            chapters = listOf(
                "计篇（节选）" to "孙子曰：兵者，国之大事，死生之地，存亡之道，不可不察也。\n\n故经之以五事，校之以计，而索其情：一曰道，二曰天，三曰地，四曰将，五曰法。\n\n道者，令民与上同意也，故可以与之死，可以与之生，而不畏危。天者，阴阳、寒暑、时制也。地者，远近、险易、广狭、死生也。",
            ),
        ),
        Spec(
            "三字经", "王应麟", Covers.CREAM,
            "蒙学", 1.0, "商务印书馆", "2018-01-01",
            tags = listOf("蒙学", "经典"), rating = 4,
            about = "旧时启蒙的三字一句，讲做人的规矩、读书的次序和一点历史的轮廓。读起来像儿歌，记下来是一生的底子。",
            chapters = listOf(
                "全文（节选）" to "人之初，性本善。性相近，习相远。苟不教，性乃迁。教之道，贵以专。\n\n昔孟母，择邻处。子不学，断机杼。窦燕山，有义方。教五子，名俱扬。\n\n养不教，父之过。教不严，师之惰。子不学，非所宜。幼不学，老何为。\n\n玉不琢，不成器。人不学，不知义。为人子，方少时。亲师友，习礼仪。",
            ),
        ),
        Spec(
            "千字文", "周兴嗣", Covers.CREAM,
            "蒙学", 2.0, "商务印书馆", "2018-01-01",
            tags = listOf("蒙学", "经典"), rating = 4,
            about = "一千个字，四字一句，句句不重字。天地日月、历史典故、做人的道理，都装在这千字里。",
            chapters = listOf(
                "开篇（节选）" to "天地玄黄，宇宙洪荒。日月盈昃，辰宿列张。\n\n寒来暑往，秋收冬藏。闰余成岁，律吕调阳。\n\n云腾致雨，露结为霜。金生丽水，玉出昆冈。\n\n剑号巨阙，珠称夜光。果珍李柰，菜重芥姜。",
            ),
        ),
        Spec(
            "唐诗选", "李白、杜甫、王维等", Covers.PLUM,
            "诗选", 1.0, "上海古籍出版社", "2019-01-01",
            tags = listOf("诗词", "经典"), rating = 5, extraPdf = true,
            about = "唐人写诗，把汉语的音韵推到了极致。这里选几首最熟的，都是几乎人人能背的。",
            chapters = listOf(
                "静夜思 · 李白" to "床前明月光，疑是地上霜。\n\n举头望明月，低头思故乡。",
                "春晓 · 孟浩然" to "春眠不觉晓，处处闻啼鸟。\n\n夜来风雨声，花落知多少。",
                "登鹳雀楼 · 王之涣" to "白日依山尽，黄河入海流。\n\n欲穷千里目，更上一层楼。",
                "相思 · 王维" to "红豆生南国，春来发几枝。\n\n愿君多采撷，此物最相思。",
                "江雪 · 柳宗元" to "千山鸟飞绝，万径人踪灭。\n\n孤舟蓑笠翁，独钓寒江雪。",
                "望庐山瀑布 · 李白" to "日照香炉生紫烟，遥看瀑布挂前川。\n\n飞流直下三千尺，疑是银河落九天。",
            ),
        ),
        Spec(
            "宋词选", "苏轼、李清照、辛弃疾等", Covers.PLUM,
            "诗选", 2.0, "上海古籍出版社", "2019-01-01",
            tags = listOf("诗词", "经典"), rating = 5,
            about = "词是唱出来的句子，长短不齐，反而更近人心。选几阕常被念起的。",
            chapters = listOf(
                "水调歌头 · 苏轼（节选）" to "明月几时有？把酒问青天。不知天上宫阙，今夕是何年。\n\n我欲乘风归去，又恐琼楼玉宇，高处不胜寒。起舞弄清影，何似在人间。\n\n人有悲欢离合，月有阴晴圆缺，此事古难全。但愿人长久，千里共婵娟。",
                "如梦令 · 李清照" to "常记溪亭日暮，沉醉不知归路。\n\n兴尽晚回舟，误入藕花深处。\n\n争渡，争渡，惊起一滩鸥鹭。",
                "青玉案 · 元夕 · 辛弃疾（节选）" to "东风夜放花千树。更吹落、星如雨。宝马雕车香满路。凤箫声动，玉壶光转，一夜鱼龙舞。\n\n蛾儿雪柳黄金缕。笑语盈盈暗香去。众里寻他千百度。蓦然回首，那人却在，灯火阑珊处。",
            ),
        ),
        Spec(
            "红楼梦", "曹雪芹", Covers.RUST,
            "古典小说", 1.0, "人民文学出版社", "2008-01-01",
            tags = listOf("小说", "经典", "中国文学"), rating = 5, extraPdf = true,
            about = "贾府的盛衰与宝黛的爱情交织在一起，写尽一个家族的日常、排场、诗词与败落。前八十回曹雪芹，后四十回一般认为是续作。",
            chapters = listOf(
                "第一回 甄士隐梦幻识通灵 贾雨村风尘怀闺秀（节选）" to "满纸荒唐言，一把辛酸泪！\n\n都云作者痴，谁解其中味？\n\n此回中凡用梦、用幻等情，是提醒阅者眼目，亦是此书立意本旨。",
                "好了歌（节选）" to "世人都晓神仙好，惟有功名忘不了！古今将相在何方？荒冢一堆草没了。\n\n世人都晓神仙好，只有金银忘不了！终朝只恨聚无多，及到多时眼闭了。\n\n世人都晓神仙好，只有姣妻忘不了！君生日日说恩情，君死又随人去了。",
            ),
        ),
        Spec(
            "三国演义", "罗贯中", Covers.NAVY,
            "古典小说", 2.0, "人民文学出版社", "2008-01-01",
            tags = listOf("小说", "经典", "中国文学"), rating = 5,
            about = "从黄巾起义写到三分归晋，把一段史写成了天下人都在看的英雄故事。开篇那阕词，几乎人人都会背两句。",
            chapters = listOf(
                "开篇词" to "滚滚长江东逝水，浪花淘尽英雄。\n\n是非成败转头空。青山依旧在，几度夕阳红。\n\n白发渔樵江渚上，惯看秋月春风。一壶浊酒喜相逢。古今多少事，都付笑谈中。",
                "第一回 宴桃园豪杰三结义 斩黄巾英雄首立功（回目）" to "话说天下大势，分久必合，合久必分。周末七国分争，并入于秦；及秦灭之后，楚、汉分争，又并入于汉。",
            ),
        ),
        Spec(
            "水浒传", "施耐庵", Covers.OLIVE,
            "古典小说", 3.0, "人民文学出版社", "2008-01-01",
            tags = listOf("小说", "经典", "中国文学"), rating = 4,
            about = "一百零八人聚在梁山泊，各有各的来路，各有各的脾气。写的是官逼民反，看的是人情冷暖。",
            chapters = listOf(
                "第一回 张天师祈禳瘟疫 洪太尉误走妖魔（回目）" to "话说大宋仁宗天子在位，嘉祐三年三月三日五更三时，京师瘟疫大作，伤损军民甚多。\n\n那一座三十六天罡、七十二地煞，共一百零八个魔君，从此散在四面八方，后成梁山之数。",
            ),
        ),
        Spec(
            "西游记", "吴承恩", Covers.TEAL,
            "古典小说", 4.0, "人民文学出版社", "2008-01-01",
            tags = listOf("小说", "经典", "中国文学"), rating = 5,
            about = "石猴出世，大闹天宫，西天取经。一部神魔小说，写的全是人间的心性。",
            chapters = listOf(
                "第一回 灵根育孕源流出 心性修持大道生（回目）" to "盖闻天地之数，有十二万九千六百岁为一元。将一元分为十二会，乃子、丑、寅、卯、辰、巳、午、未、申、酉、戌、亥之十二支也。\n\n那座山正当顶上，有一块仙石。其石有三丈六尺五寸高，有二丈四尺围圆。内育仙胞，一日迸裂，产一石卵，似圆球样大。因见风，化作一个石猴，五官俱备，四肢皆全。",
            ),
        ),
        Spec(
            "古文观止选", "吴楚材、吴调侯", Covers.CREAM,
            "诗选", 3.0, "中华书局", "2016-01-01",
            tags = listOf("散文", "经典"), rating = 5,
            about = "旧时学古文的课本，选先秦到明末的文章二百二十二篇。这里取三篇最短也最常读的。",
            chapters = listOf(
                "陋室铭 · 刘禹锡" to "山不在高，有仙则名。水不在深，有龙则灵。\n\n斯是陋室，惟吾德馨。苔痕上阶绿，草色入帘青。\n\n谈笑有鸿儒，往来无白丁。可以调素琴，阅金经。\n\n无丝竹之乱耳，无案牍之劳形。南阳诸葛庐，西蜀子云亭。\n\n孔子云：何陋之有？",
                "爱莲说 · 周敦颐" to "水陆草木之花，可爱者甚蕃。晋陶渊明独爱菊。自李唐来，世人甚爱牡丹。\n\n予独爱莲之出淤泥而不染，濯清涟而不妖，中通外直，不蔓不枝，香远益清，亭亭净植，可远观而不可亵玩焉。\n\n予谓菊，花之隐逸者也；牡丹，花之富贵者也；莲，花之君子者也。噫！菊之爱，陶后鲜有闻。莲之爱，同予者何人？牡丹之爱，宜乎众矣。",
                "岳阳楼记 · 范仲淹（节选）" to "嗟夫！予尝求古仁人之心，或异二者之为，何哉？\n\n不以物喜，不以己悲。居庙堂之高则忧其民，处江湖之远则忧其君。\n\n是进亦忧，退亦忧。然则何时而乐耶？其必曰：先天下之忧而忧，后天下之乐而乐乎。",
            ),
        ),
        Spec(
            "山海经选", "佚名", Covers.PLUM,
            "诸子经典", 5.0, "中华书局", "2016-01-01",
            tags = listOf("神话", "经典"), rating = 4,
            about = "古人的地理志与神话簿，山川物产之外，记着精卫、夸父这些不认命的故事。",
            chapters = listOf(
                "精卫填海" to "又北二百里，曰发鸠之山，其上多柘木。有鸟焉，其状如乌，文首、白喙、赤足，名曰精卫，其鸣自詨。\n\n是炎帝之少女，名曰女娃。女娃游于东海，溺而不返，故为精卫。\n\n常衔西山之木石，以堙于东海。",
                "夸父逐日" to "夸父与日逐走，入日；渴，欲得饮，饮于河、渭；河、渭不足，北饮大泽。\n\n未至，道渴而死。弃其杖，化为邓林。",
            ),
        ),
        Spec(
            "世说新语选", "刘义庆", Covers.RUST,
            "诸子经典", 6.0, "中华书局", "2016-01-01",
            tags = listOf("散文", "经典", "笔记"), rating = 4,
            about = "魏晋名士的言行片段，一小段一小段，像随笔。人物的神情都在对话里。",
            chapters = listOf(
                "咏雪" to "谢太傅寒雪日内集，与儿女讲论文义。俄而雪骤，公欣然曰：白雪纷纷何所似？\n\n兄子胡儿曰：撒盐空中差可拟。兄女曰：未若柳絮因风起。\n\n公大笑乐。即公大兄无奕女，左将军王凝之妻也。",
                "管宁割席" to "管宁、华歆共园中锄菜，见地有片金，管挥锄与瓦石不异，华捉而掷去之。\n\n又尝同席读书，有乘轩冕过门者，宁读如故，歆废书出看。\n\n宁割席分坐，曰：子非吾友也。",
            ),
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
                publisher = s.publisher,
                pubdate = s.date,
                languages = listOf(s.language),
                rating = s.rating,
                comments = "<p>${EpubWriter.esc(s.about)}</p>",
                identifiers = mapOf("isbn" to pseudoIsbn(index)),
            )
            lib.setTimestamp(id, base - index * 86_400_000L)

            val path = Library.bookPathFor(listOf(s.author), s.title, id)
            val folder = File(root, path).apply { mkdirs() }

            var coverBytes: ByteArray? = null
            if (s.cover) {
                val jpg = File(folder, "cover.jpg")
                if (Covers.generate(jpg, s.title, s.author, s.ink, s.publisher, s.date.take(4))) {
                    coverBytes = jpg.readBytes()
                }
            }

            val chapters = chaptersFor(s)
            val epub = File(folder, "${Library.sanitize(s.title)}.epub")
            EpubWriter.write(
                file = epub,
                title = s.title,
                author = s.author,
                language = s.language,
                publisher = s.publisher,
                date = s.date,
                chapters = chapters,
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

    private fun chaptersFor(s: Spec): List<EpubWriter.Chapter> {
        val real = s.chapters.map { EpubWriter.Chapter(it.first, it.second) }
        val note = EpubWriter.Chapter(
            "关于本书",
            s.about + "\n\n" +
                "${s.author}。${s.publisher}，${s.date.take(4)}。" +
                (s.series?.let { "「$it」第 ${trimIndex(s.seriesIndex)} 种。" } ?: "") +
                "归类：${s.tags.joinToString("、")}。\n\n" +
                "本书为公版典籍的节选本，由纸间阅读随应用内置，用来演示书架、封面与阅读器。" +
                "完整版本请自行导入 EPUB / PDF 文件。",
        )
        return listOf(note) + real
    }

    private fun pseudoIsbn(i: Int): String =
        "978" + (1000000000L + i * 7919L).toString().takeLast(10)

    private fun trimIndex(f: Double): String =
        if (f == f.toLong().toDouble()) f.toLong().toString() else String.format("%.1f", f)
}

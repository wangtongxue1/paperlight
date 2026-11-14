package com.zhijian.reading.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Interface language. Chinese is the default; English is the only other
 * language this build carries, chosen in Settings or followed from the system.
 */
enum class LangPref { ZH, EN, SYSTEM }

/** Every line the interface shows, in one place so the two languages agree. */
interface AppStrings {

    // ---- app & navigation ----
    val appName: String
    val navLibrary: String
    val navReading: String
    val navMine: String

    // ---- library ----
    val titleAll: String
    val titleRecent: String
    val titleReading: String
    val titleFavorites: String
    fun titleByFormat(fmt: String): String
    fun titleByAuthor(name: String): String
    fun titleBySeries(name: String): String
    fun titleByTag(name: String): String
    fun titleByPublisher(name: String): String
    fun bookCount(n: Int): String
    val searchHint: String
    val closeSearch: String
    val sortBy: String
    val sortAsc: String
    val sortDesc: String
    val sortDate: String
    val sortTitle: String
    val sortAuthor: String
    val sortRating: String
    val sortSize: String
    val sortSeries: String
    val filterAll: String
    val emptyNoBooks: String
    val emptyNoBooksBody: String
    val emptyNoMatch: String
    fun emptyNoMatchBody(query: String): String
    val addBooks: String
    val shelfTitle: String
    val shelfImport: String
    val shelfSelect: String
    val shelfSortDefault: String
    val shelfSortUpdated: String
    val shelfSortProgress: String
    val shelfSortRating: String
    val shelfSortTitle: String
    val shelfSortCategory: String
    val shelfSortWords: String
    val shelfUncategorized: String
    val importChooseTitle: String
    val importOnline: String
    val importOnlineNote: String
    val importLocal: String
    val importLocalNote: String
    val importScanNote: String
    val onlineImportTitle: String

    // ---- reading now ----
    val continueReading: String
    val recentlyAdded: String
    val finished: String
    val nothingInProgress: String
    val nothingInProgressBody: String
    val progressOf: String
    /** The 正在阅读 / 已读完 pages pushed from 阅读 and 我的. */
    val readingPageNote: String
    val finishedPageNote: String
    val emptyFinished: String
    val emptyFinishedBody: String
    fun readTimes(n: Int): String

    // ---- detail ----
    val details: String
    val edit: String
    val editMetadata: String
    val changeCover: String
    val remove: String
    fun removeTitle(title: String): String
    /** Shown while the tick is off: the file stays on disk. */
    val deleteKeepNote: String
    /** Shown once the tick is on: both are erased for good. */
    val deleteFilesWarn: String
    val deleteFilesOption: String
    fun deleteFilesHint(size: String): String
    val deleteFilesHintBare: String
    val cancel: String
    // ---- shelf selection ----
    val viewMoreHistory: String
    fun selectedCount(n: Int): String
    val selectAll: String
    val deselectAll: String
    fun deleteBooksTitle(n: Int): String
    val deleteAction: String
    val accept: String
    val read: String
    val formats: String
    val description: String
    val rating: String
    val language: String
    val publisher: String
    val published: String
    val isbn: String
    val added: String
    val series: String
    val tags: String
    val addTag: String
    val authors: String
    val folder: String
    val onDisk: String
    val storageToPublic: String
    val storageNote: String
    val storageScan: String
    val storageMoving: String
    val storageMoveFailed: String
    fun storageScanned(n: Int): String
    val unknown: String
    val untitled: String

    // ---- edit ----
    val save: String
    val saved: String
    val titleField: String
    val authorsHint: String
    val seriesIndex: String
    val descriptionField: String

    // ---- reader ----
    val smallerText: String
    val largerText: String
    val readerFailed: String
    val readerToc: String
    val readerAnnotations: String
    val readerLook: String
    fun annotationCount(n: Int): String
    val noToc: String
    val noAnnotations: String
    val pageMode: String
    val modeScroll: String
    val modePaged: String
    val modeScrollDesc: String
    val modePagedDesc: String
    val fontSize: String
    val readingTheme: String
    val readerThemeLight: String
    val readerThemeSepia: String
    val readerThemeGray: String
    val readerThemeNight: String
    val brightness: String
    val systemDefault: String
    val lineHeight: String
    val fontFamily: String
    val fontSerif: String
    val fontSans: String
    val shareText: String
    val makeNote: String
    val copyText: String
    val deselect: String
    val copied: String
    val noteHint: String
    val noteSaved: String
    val noteFailed: String
    val exportNotes: String
    val exportDone: String
    val exportFail: String
    val exportEmpty: String
    val delete: String
    val quoteLabel: String
    val noteLabel: String
    fun chapterPos(cur: Int, total: Int): String
    fun pagePos(cur: Int, total: Int): String
    val readerSearch: String
    val readerSearchHint: String
    val searchGo: String
    val searchPrev: String
    val searchNext: String
    fun searchCount(n: Int): String
    val searchNone: String
    val prevChapter: String
    val nextChapter: String
    val readerNight: String
    val readerLight: String
    val readerCollapse: String
    val jumpBookStart: String
    val readerColors: String
    fun readTo(pct: Int): String
    val autoTurn: String
    val autoOff: String
    val autoSlow: String
    val autoMid: String
    val autoFast: String
    val autoFinished: String
    val readerProgress: String
    val readerChapter: String
    val readerPage: String
    val jumpBookEnd: String
    fun pdfPageOf(cur: Int, total: Int): String

    // ---- get books / import ----
    val getBooksTitle: String
    val urlImport: String
    val urlHint: String
    val urlHint2: String
    val urlImportAction: String
    val urlFailed: String
    val importFromDevice: String
    val importFromDeviceNote: String
    val localImportTitle: String
    val localImportNoPerm: String
    val localImportChange: String
    val localImportPick: String
    val localImportPickNote: String
    val localImportSelectFiles: String
    val localImportFormats: String
    val localImportEmpty: String
    fun localImportSelected(n: Int): String
    val localImportAction: String
    fun imported(n: Int): String
    val importNone: String
    fun importProgress(done: Int, total: Int): String
    fun importFailed(name: String): String
    val builtinNote: String
    val nothingFound: String
    fun nothingFoundBody(query: String): String
    val filedToast: String
    val tappingDownloads: String

    // ---- settings ----
    val settingsTitle: String
    val appearance: String
    val theme: String
    val themeSystem: String
    val themeLight: String
    val themeDark: String
    val languageSetting: String
    val langSystem: String
    val defaultView: String
    val viewShelf: String
    val viewList: String
    val librarySection: String
    val bookCountLabel: String
    val statReading: String
    val statReadingTime: String

    /** "35 分钟" / "2.4 小时" style short reading-time labels. */
    fun minutesShort(n: Long): String
    fun hoursShort(x: String): String
    val about: String
    val aboutBody: String
    val version: String
    val coverUpdated: String
    val coverFail: String

    // ---- browse ----
    val browseAuthors: String
    val browseSeries: String
    val browseTags: String
    val browsePublishers: String
}

object StringsZh : AppStrings {
    override val appName = "纸间阅读"
    override val navLibrary = "书架"
    override val navReading = "阅读"
    override val navMine = "我的"

    override val titleAll = "全部书籍"
    override val titleRecent = "最近添加"
    override val titleReading = "正在阅读"
    override val titleFavorites = "我的收藏"
    override fun titleByFormat(fmt: String) = "$fmt 格式"
    override fun titleByAuthor(name: String) = "作者 · $name"
    override fun titleBySeries(name: String) = "丛书 · $name"
    override fun titleByTag(name: String) = "标签 · $name"
    override fun titleByPublisher(name: String) = "出版社 · $name"
    override fun bookCount(n: Int) = "$n 本"
    override val searchHint = "搜索书名、作者、标签"
    override val closeSearch = "关闭搜索"
    override val sortBy = "排序"
    override val sortAsc = "升序"
    override val sortDesc = "降序"
    override val sortDate = "添加时间"
    override val sortTitle = "书名"
    override val sortAuthor = "作者"
    override val sortRating = "评分"
    override val sortSize = "文件大小"
    override val sortSeries = "丛书序号"
    override val filterAll = "全部"
    override val emptyNoBooks = "书架还是空的"
    override val emptyNoBooksBody = "到「我的」页用文件或链接把书放进来。"
    override val emptyNoMatch = "没有匹配的书"
    override fun emptyNoMatchBody(query: String) = "没有书名、作者或标签匹配“$query”。"
    override val addBooks = "导入书籍"
    override val shelfTitle = "书架"
    override val shelfImport = "导入"
    override val shelfSelect = "选择"
    override val shelfSortDefault = "默认"
    override val shelfSortUpdated = "更新"
    override val shelfSortProgress = "进度"
    override val shelfSortRating = "推荐值"
    override val shelfSortTitle = "书名"
    override val shelfSortCategory = "分类"
    override val shelfSortWords = "字数"
    override val shelfUncategorized = "未分类"
    override val importChooseTitle = "导入书籍"
    override val importOnline = "在线导入"
    override val importOnlineNote = "填写文件直链，下载后自动进书架"
    override val importLocal = "本机导入"
    override val importLocalNote = "浏览本机目录，勾选导入"
    override val importScanNote = "扫描 MyBooks 文件夹，收进手动放入的书"
    override val onlineImportTitle = "在线导入"

    override val continueReading = "继续阅读"
    override val recentlyAdded = "最近添加"
    override val finished = "已读完"
    override val nothingInProgress = "还没有在读的书"
    override val nothingInProgressBody = "打开任意一本书开始阅读，进度会记在这里。"
    override val progressOf = "读至 %1\$d%%"
    override val readingPageNote = "按最近阅读排序，点开接着上次的进度读。"
    override val finishedPageNote = "点开任意一本即可重读：进度从头开始，读过的次数会保留。"
    override val emptyFinished = "还没有读完的书"
    override val emptyFinishedBody = "一本书读到最后一页，就会出现在这里。"
    override fun readTimes(n: Int) = "读过 $n 次"

    override val details = "详细信息"
    override val edit = "编辑"
    override val editMetadata = "编辑元数据"
    override val changeCover = "更换封面"
    override val remove = "移出书库"
    override fun removeTitle(title: String) = "移除《$title》？"
    override val deleteKeepNote = "只从书架移除，本地文件仍保留在 MyBooks，之后还能重新扫描导入。"
    override val deleteFilesWarn = "书架记录与本地文件会一并删除，此操作无法撤销。"
    override val deleteFilesOption = "同时删除本地文件"
    override fun deleteFilesHint(size: String) = "MyBooks 中 $size，勾选后从手机彻底删除"
    override val deleteFilesHintBare = "勾选后从手机存储中彻底删除，无法恢复"
    override val cancel = "取消"
    override val viewMoreHistory = "查看更多"
    override fun selectedCount(n: Int) = "已选 $n 项"
    override val selectAll = "全选"
    override val deselectAll = "取消全选"
    override fun deleteBooksTitle(n: Int) = "删除 $n 本书？"
    override val deleteAction = "删除"
    override val accept = "确定"
    override val read = "开始阅读"
    override val formats = "文件"
    override val description = "简介"
    override val rating = "评分"
    override val language = "语言"
    override val publisher = "出版社"
    override val published = "出版日期"
    override val isbn = "ISBN"
    override val added = "添加时间"
    override val series = "丛书"
    override val tags = "标签"
    override val addTag = "添加标签"
    override val authors = "作者"
    override val folder = "存放位置"
    override val onDisk = "占用空间"
    override val storageToPublic = "在手机根目录创建 MyBooks"
    override val storageNote = "书库默认存放在手机根目录的 MyBooks 文件夹。首次使用需要授权「所有文件访问」一次，之后用文件管理器就能直接放入或取出书籍。"
    override val storageScan = "扫描文件夹里的新书"
    override val storageMoving = "正在迁移书库…"
    override val storageMoveFailed = "迁移失败，书库仍留在原位置"
    override fun storageScanned(n: Int) = if (n == 0) "没有发现新书" else "已导入 $n 本新书"
    override val unknown = "未知"
    override val untitled = "无标题"

    override val save = "保存"
    override val saved = "已保存"
    override val titleField = "书名"
    override val authorsHint = "一个或多个，用逗号分隔"
    override val seriesIndex = "丛书序号"
    override val descriptionField = "简介"

    override val smallerText = "缩小字号"
    override val largerText = "放大字号"
    override val readerFailed = "这本书无法打开"
    override val readerToc = "目录"
    override val readerAnnotations = "批注"
    override val readerLook = "界面"
    override fun annotationCount(n: Int) = "批注 ($n)"
    override val noToc = "这本书没有目录信息。"
    override val noAnnotations = "还没有批注。选中正文文字即可划线并写下想法。"
    override val pageMode = "翻页方式"
    override val modeScroll = "上下滚动"
    override val modePaged = "左右翻页"
    override val modeScrollDesc = "连续滚动，手指滑动阅读"
    override val modePagedDesc = "仿真书页，点两侧或滑动翻页"
    override val fontSize = "字体大小"
    override val readingTheme = "阅读主题"
    override val readerThemeLight = "素白"
    override val readerThemeSepia = "米黄"
    override val readerThemeGray = "纸灰"
    override val readerThemeNight = "夜间"
    override val brightness = "亮度"
    override val systemDefault = "跟随系统"
    override val lineHeight = "行距"
    override val fontFamily = "字体"
    override val fontSerif = "衬线"
    override val fontSans = "无衬线"
    override val shareText = "分享"
    override val makeNote = "批注"
    override val copyText = "复制"
    override val deselect = "取消"
    override val copied = "已复制"
    override val noteHint = "写点想法（可留空）"
    override val noteSaved = "批注已保存"
    override val noteFailed = "未能添加批注，请重新选择文字"
    override val exportNotes = "导出"
    override val exportDone = "批注已导出"
    override val exportFail = "导出失败"
    override val exportEmpty = "还没有可导出的批注"
    override val delete = "删除"
    override val quoteLabel = "原文"
    override val noteLabel = "笔记"
    override fun chapterPos(cur: Int, total: Int) = "$cur/$total 章 · "
    override fun pagePos(cur: Int, total: Int) = "第 $cur / $total 页"
    override val readerSearch = "搜索全书"
    override val readerSearchHint = "输入要查找的文字"
    override val searchGo = "查找"
    override val searchPrev = "上一处"
    override val searchNext = "下一处"
    override fun searchCount(n: Int) = "找到 $n 处"
    override val searchNone = "没有找到"
    override val prevChapter = "上一章"
    override val nextChapter = "下一章"
    override val readerNight = "夜间"
    override val readerLight = "亮色"
    override val readerCollapse = "收起"
    override val jumpBookStart = "跳到书首"
    override val readerColors = "颜色"
    override fun readTo(pct: Int) = "读到 ${pct}%"
    override val autoTurn = "自动翻页"
    override val autoOff = "关"
    override val autoSlow = "慢速"
    override val autoMid = "中速"
    override val autoFast = "快速"
    override val autoFinished = "已到全书末尾，自动翻页停止"
    override val readerProgress = "进度"
    override val readerChapter = "章节"
    override val readerPage = "页码"
    override val jumpBookEnd = "去底部"
    override fun pdfPageOf(cur: Int, total: Int) = "第 $cur / $total 页"

    override val getBooksTitle = "导入"
    override val urlImport = "链接导入"
    override val urlHint = "粘贴 EPUB / PDF / TXT 文件直链"
    override val urlHint2 = "下载完成后自动进入书架"
    override val urlImportAction = "下载并导入"
    override val urlFailed = "下载失败"
    override val importFromDevice = "本机文件导入"
    override val importFromDeviceNote = "支持 EPUB、PDF、TXT、MOBI、HTML、MD、FB2"
    override val localImportTitle = "本机文件导入"
    override val localImportNoPerm = "浏览并导入本机书籍"
    override val localImportChange = "更换文件夹"
    override val localImportPick = "选择书籍文件夹"
    override val localImportPickNote =
        "授权一次「下载」等常用文件夹后，即可直接在应用内浏览、勾选并导入，无需任何手机权限。"
    override val localImportSelectFiles = "直接从系统选择文件"
    override val localImportFormats = "支持 EPUB、PDF、TXT、MOBI、HTML、MD、FB2"
    override val localImportEmpty = "此文件夹没有可导入的书籍"
    override fun localImportSelected(n: Int) = "已选 $n 本"
    override val localImportAction = "导入"
    override fun imported(n: Int) = "已导入 $n 本"
    override val importNone = "没有导入任何文件"
    override fun importProgress(done: Int, total: Int) = "正在导入 ${done + 1}/$total"
    override fun importFailed(name: String) = "无法导入：$name"
    override val builtinNote = "公版中文样书已随应用内置，无需联网"
    override val nothingFound = "没有找到"
    override fun nothingFoundBody(query: String) = "没有找到匹配“$query”的内容。"
    override val filedToast = "这本书无法入库"
    override val tappingDownloads = "点按即可下载"

    override val settingsTitle = "设置"
    override val appearance = "外观"
    override val theme = "主题"
    override val themeSystem = "跟随系统"
    override val themeLight = "浅色"
    override val themeDark = "深色"
    override val languageSetting = "界面语言"
    override val langSystem = "跟随系统"
    override val defaultView = "默认视图"
    override val viewShelf = "封面墙"
    override val viewList = "列表"
    override val librarySection = "书库"
    override val bookCountLabel = "藏书"
    override val statReading = "在读"
    override val statReadingTime = "阅读时长"
    override fun minutesShort(n: Long) = "$n 分钟"
    override fun hoursShort(x: String) = "$x 小时"
    override val about = "关于"
    override val aboutBody = "读写 calibre 书库（metadata.db），可直接打开电脑上已有的 calibre 书库文件夹。"
    override val version = "版本"
    override val coverUpdated = "封面已更新"
    override val coverFail = "这张图片无法读取"

    override val browseAuthors = "作者"
    override val browseSeries = "丛书"
    override val browseTags = "标签"
    override val browsePublishers = "出版社"
}

object StringsEn : AppStrings {
    override val appName = "Paperlight"
    override val navLibrary = "Library"
    override val navReading = "Reading"
    override val navMine = "Me"

    override val titleAll = "All books"
    override val titleRecent = "Recently added"
    override val titleReading = "Reading"
    override val titleFavorites = "Favorites"
    override fun titleByFormat(fmt: String) = "$fmt books"
    override fun titleByAuthor(name: String) = "Author · $name"
    override fun titleBySeries(name: String) = "Series · $name"
    override fun titleByTag(name: String) = "Tag · $name"
    override fun titleByPublisher(name: String) = "Publisher · $name"
    override fun bookCount(n: Int) = if (n == 1) "1 book" else "$n books"
    override val searchHint = "Search title, author, tag"
    override val closeSearch = "Close search"
    override val sortBy = "Sort by"
    override val sortAsc = "Ascending"
    override val sortDesc = "Descending"
    override val sortDate = "Date added"
    override val sortTitle = "Title"
    override val sortAuthor = "Author"
    override val sortRating = "Rating"
    override val sortSize = "File size"
    override val sortSeries = "Series index"
    override val filterAll = "All"
    override val emptyNoBooks = "The shelf is empty"
    override val emptyNoBooksBody = "Bring books in from files or a link in Me."
    override val emptyNoMatch = "No books match"
    override fun emptyNoMatchBody(query: String) = "Nothing here is titled, written or tagged “$query”."
    override val addBooks = "Add books"
    override val shelfTitle = "Shelf"
    override val shelfImport = "Import"
    override val shelfSelect = "Select"
    override val shelfSortDefault = "Default"
    override val shelfSortUpdated = "Recent"
    override val shelfSortProgress = "Progress"
    override val shelfSortRating = "Rating"
    override val shelfSortTitle = "Title"
    override val shelfSortCategory = "Category"
    override val shelfSortWords = "Length"
    override val shelfUncategorized = "Uncategorized"
    override val importChooseTitle = "Add books"
    override val importOnline = "Online import"
    override val importOnlineNote = "Paste a direct file link, it lands on the shelf"
    override val importLocal = "On this device"
    override val importLocalNote = "Browse a local folder and pick books"
    override val importScanNote = "Scan the MyBooks folder for books added by hand"
    override val onlineImportTitle = "Online import"

    override val continueReading = "Continue Reading"
    override val recentlyAdded = "Recently Added"
    override val finished = "Finished"
    override val nothingInProgress = "Nothing in progress"
    override val nothingInProgressBody = "Open any book and the progress is kept here."
    override val progressOf = "%1\$d%% read"
    override val readingPageNote = "Sorted by what you read last. Tap to pick up where you left off."
    override val finishedPageNote = "Tap any book to read it again: progress starts over, the number of times stays."
    override val emptyFinished = "Nothing finished yet"
    override val emptyFinishedBody = "A book that reaches its last page shows up here."
    override fun readTimes(n: Int) = if (n == 1) "Read once" else "Read $n times"

    override val details = "Details"
    override val edit = "Edit"
    override val editMetadata = "Edit metadata"
    override val changeCover = "Change cover"
    override val remove = "Remove from library"
    override fun removeTitle(title: String) = "Remove “$title”?"
    override val deleteKeepNote =
        "Only the shelf entry goes. The file stays in MyBooks and can be scanned back in."
    override val deleteFilesWarn = "The shelf entry and the local file are both deleted. This cannot be undone."
    override val deleteFilesOption = "Also delete the local file"
    override fun deleteFilesHint(size: String) = "$size in MyBooks — ticked, it is erased from the phone"
    override val deleteFilesHintBare = "Ticked, the file is erased from the phone for good"
    override val cancel = "Cancel"
    override val viewMoreHistory = "Show more"
    override fun selectedCount(n: Int) = if (n == 1) "1 selected" else "$n selected"
    override val selectAll = "Select All"
    override val deselectAll = "Deselect All"
    override fun deleteBooksTitle(n: Int) = if (n == 1) "Delete 1 book?" else "Delete $n books?"
    override val deleteAction = "Delete"
    override val accept = "Accept"
    override val read = "Read"
    override val formats = "Formats"
    override val description = "Description"
    override val rating = "Rating"
    override val language = "Language"
    override val publisher = "Publisher"
    override val published = "Published"
    override val isbn = "ISBN"
    override val added = "Added"
    override val series = "Series"
    override val tags = "Tags"
    override val addTag = "Add a tag"
    override val authors = "Authors"
    override val folder = "Folder"
    override val onDisk = "On disk"
    override val storageToPublic = "Create MyBooks in phone storage"
    override val storageNote = "Books live in MyBooks at the root of phone storage. Grant 所有文件访问 once and any file manager can reach them."
    override val storageScan = "Scan folder for new books"
    override val storageMoving = "Moving the library…"
    override val storageMoveFailed = "Could not move — the library stays where it was"
    override fun storageScanned(n: Int) =
        if (n == 0) "No new books found" else if (n == 1) "1 new book added" else "$n new books added"
    override val unknown = "Unknown"
    override val untitled = "Untitled"

    override val save = "Save"
    override val saved = "Saved"
    override val titleField = "Title"
    override val authorsHint = "One name, or several separated by commas"
    override val seriesIndex = "Series index"
    override val descriptionField = "Description"

    override val smallerText = "Smaller text"
    override val largerText = "Larger text"
    override val readerFailed = "This book could not be opened"
    override val readerToc = "Contents"
    override val readerAnnotations = "Notes"
    override val readerLook = "Display"
    override fun annotationCount(n: Int) = "Notes ($n)"
    override val noToc = "This book has no table of contents."
    override val noAnnotations = "No notes yet. Select text in the book to highlight it and write a note."
    override val pageMode = "Page turning"
    override val modeScroll = "Scroll"
    override val modePaged = "Flip pages"
    override val modeScrollDesc = "Continuous scrolling with your finger"
    override val modePagedDesc = "Book-like pages, tap or swipe to turn"
    override val fontSize = "Text size"
    override val readingTheme = "Reading theme"
    override val readerThemeLight = "White"
    override val readerThemeSepia = "Sepia"
    override val readerThemeGray = "Gray"
    override val readerThemeNight = "Night"
    override val brightness = "Brightness"
    override val systemDefault = "System"
    override val lineHeight = "Line spacing"
    override val fontFamily = "Typeface"
    override val fontSerif = "Serif"
    override val fontSans = "Sans"
    override val shareText = "Share"
    override val makeNote = "Note"
    override val copyText = "Copy"
    override val deselect = "Clear"
    override val copied = "Copied"
    override val noteHint = "A thought (optional)"
    override val noteSaved = "Note saved"
    override val noteFailed = "Could not add the note, select the text again"
    override val exportNotes = "Export"
    override val exportDone = "Notes exported"
    override val exportFail = "The export failed"
    override val exportEmpty = "Nothing to export yet"
    override val delete = "Delete"
    override val quoteLabel = "Quote"
    override val noteLabel = "Note"
    override fun chapterPos(cur: Int, total: Int) = "Ch. $cur/$total · "
    override fun pagePos(cur: Int, total: Int) = "Page $cur of $total"
    override val readerSearch = "Search in book"
    override val readerSearchHint = "Text to find"
    override val searchGo = "Find"
    override val searchPrev = "Prev"
    override val searchNext = "Next"
    override fun searchCount(n: Int) = "$n matches"
    override val searchNone = "No matches"
    override val prevChapter = "Previous chapter"
    override val nextChapter = "Next chapter"
    override val readerNight = "Night"
    override val readerLight = "Light"
    override val readerCollapse = "Collapse"
    override val jumpBookStart = "Jump to start"
    override val readerColors = "Colors"
    override fun readTo(pct: Int) = "Read to ${pct}%"
    override val autoTurn = "Auto page turn"
    override val autoOff = "Off"
    override val autoSlow = "Slow"
    override val autoMid = "Mid"
    override val autoFast = "Fast"
    override val autoFinished = "Reached the end; auto turn stopped"
    override val readerProgress = "Progress"
    override val readerChapter = "Chapter"
    override val readerPage = "Page"
    override val jumpBookEnd = "Go to end"
    override fun pdfPageOf(cur: Int, total: Int) = "Page $cur of $total"

    override val getBooksTitle = "Import"
    override val urlImport = "Import from a link"
    override val urlHint = "Paste a direct EPUB / PDF / TXT link"
    override val urlHint2 = "Downloaded books land on your shelf"
    override val urlImportAction = "Download and import"
    override val urlFailed = "That download failed"
    override val importFromDevice = "Import from this device"
    override val importFromDeviceNote = "EPUB, PDF, TXT, MOBI, HTML, MD, FB2"
    override val localImportTitle = "On this device"
    override val localImportNoPerm = "Browse and import local books"
    override val localImportChange = "Change folder"
    override val localImportPick = "Choose a book folder"
    override val localImportPickNote =
        "Grant a folder like Downloads once, then browse, pick and import books right here — no storage permission needed."
    override val localImportSelectFiles = "Pick files from the system picker"
    override val localImportFormats = "EPUB, PDF, TXT, MOBI, HTML, MD, FB2"
    override val localImportEmpty = "No importable books in this folder"
    override fun localImportSelected(n: Int) = "$n selected"
    override val localImportAction = "Import"
    override fun imported(n: Int) = if (n == 1) "1 book added" else "$n books added"
    override val importNone = "Nothing was added"
    override fun importProgress(done: Int, total: Int) = "Importing ${done + 1}/$total"
    override fun importFailed(name: String) = "Could not file: $name"
    override val builtinNote = "Sample Chinese classics ship with the app, no network needed"
    override val nothingFound = "Nothing found"
    override fun nothingFoundBody(query: String) = "Nothing matching “$query” was found."
    override val filedToast = "That download could not be filed"
    override val tappingDownloads = "Tap to download"

    override val settingsTitle = "Settings"
    override val appearance = "Appearance"
    override val theme = "Theme"
    override val themeSystem = "System"
    override val themeLight = "Light"
    override val themeDark = "Dark"
    override val languageSetting = "Language"
    override val langSystem = "System"
    override val defaultView = "Default view"
    override val viewShelf = "Covers"
    override val viewList = "List"
    override val librarySection = "Library"
    override val bookCountLabel = "Books"
    override val statReading = "Reading"
    override val statReadingTime = "Time read"
    override fun minutesShort(n: Long) = if (n == 1L) "1 min" else "$n mins"
    override fun hoursShort(x: String) = "$x hrs"
    override val about = "About"
    override val aboutBody =
        "Reads and writes calibre libraries (metadata.db). Point it at a calibre folder you already have."
    override val version = "Version"
    override val coverUpdated = "Cover updated"
    override val coverFail = "That image could not be read"

    override val browseAuthors = "Authors"
    override val browseSeries = "Series"
    override val browseTags = "Tags"
    override val browsePublishers = "Publishers"
}

val LocalStrings = staticCompositionLocalOf<AppStrings> { StringsZh }

/** The interface strings for the language currently selected. */
val strings: AppStrings
    @Composable get() = LocalStrings.current

/** Resolves a preference against the system locale. */
fun stringsFor(pref: LangPref, systemLanguageTag: String): AppStrings = when (pref) {
    LangPref.ZH -> StringsZh
    LangPref.EN -> StringsEn
    LangPref.SYSTEM -> if (systemLanguageTag.startsWith("zh")) StringsZh else StringsEn
}

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

    // ---- reading now ----
    val continueReading: String
    val recentlyAdded: String
    val finished: String
    val nothingInProgress: String
    val nothingInProgressBody: String
    val progressOf: String

    // ---- detail ----
    val details: String
    val edit: String
    val editMetadata: String
    val changeCover: String
    val remove: String
    fun removeTitle(title: String): String
    val removeBody: String
    val cancel: String
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
    val listStars: String
    val librarySection: String
    val bookCountLabel: String
    val statReading: String
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

    override val continueReading = "继续阅读"
    override val recentlyAdded = "最近添加"
    override val finished = "已读完"
    override val nothingInProgress = "还没有在读的书"
    override val nothingInProgressBody = "打开任意一本书开始阅读，进度会记在这里。"
    override val progressOf = "读至 %1\$d%%"

    override val details = "详细信息"
    override val edit = "编辑"
    override val editMetadata = "编辑元数据"
    override val changeCover = "更换封面"
    override val remove = "移出书库"
    override fun removeTitle(title: String) = "移除《$title》？"
    override val removeBody = "书籍记录与文件会一并删除，此操作无法撤销。"
    override val cancel = "取消"
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

    override val getBooksTitle = "导入"
    override val urlImport = "链接导入"
    override val urlHint = "粘贴 EPUB / PDF / TXT 文件直链"
    override val urlHint2 = "下载完成后自动进入书架"
    override val urlImportAction = "下载并导入"
    override val urlFailed = "下载失败"
    override val importFromDevice = "本机文件导入"
    override val importFromDeviceNote = "支持 EPUB、PDF、MOBI、AZW3、TXT"
    override val localImportTitle = "本机文件导入"
    override val localImportNoPerm = "浏览并导入本机书籍"
    override val localImportChange = "更换文件夹"
    override val localImportPick = "选择书籍文件夹"
    override val localImportPickNote =
        "授权一次「下载」等常用文件夹后，即可直接在应用内浏览、勾选并导入，无需任何手机权限。"
    override val localImportSelectFiles = "直接从系统选择文件"
    override val localImportFormats = "支持 EPUB、PDF、MOBI、AZW3、TXT"
    override val localImportEmpty = "此文件夹没有可导入的书籍"
    override fun localImportSelected(n: Int) = "已选 $n 本"
    override val localImportAction = "导入"
    override fun imported(n: Int) = "已导入 $n 本"
    override val importNone = "没有导入任何文件"
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
    override val listStars = "列表中显示评分"
    override val librarySection = "书库"
    override val bookCountLabel = "藏书"
    override val statReading = "在读"
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

    override val continueReading = "Continue Reading"
    override val recentlyAdded = "Recently Added"
    override val finished = "Finished"
    override val nothingInProgress = "Nothing in progress"
    override val nothingInProgressBody = "Open any book and the progress is kept here."
    override val progressOf = "%1\$d%% read"

    override val details = "Details"
    override val edit = "Edit"
    override val editMetadata = "Edit metadata"
    override val changeCover = "Change cover"
    override val remove = "Remove from library"
    override fun removeTitle(title: String) = "Remove “$title”?"
    override val removeBody = "The book and its files are deleted. This cannot be undone."
    override val cancel = "Cancel"
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

    override val getBooksTitle = "Import"
    override val urlImport = "Import from a link"
    override val urlHint = "Paste a direct EPUB / PDF / TXT link"
    override val urlHint2 = "Downloaded books land on your shelf"
    override val urlImportAction = "Download and import"
    override val urlFailed = "That download failed"
    override val importFromDevice = "Import from this device"
    override val importFromDeviceNote = "EPUB, PDF, MOBI, AZW3, TXT"
    override val localImportTitle = "On this device"
    override val localImportNoPerm = "Browse and import local books"
    override val localImportChange = "Change folder"
    override val localImportPick = "Choose a book folder"
    override val localImportPickNote =
        "Grant a folder like Downloads once, then browse, pick and import books right here — no storage permission needed."
    override val localImportSelectFiles = "Pick files from the system picker"
    override val localImportFormats = "EPUB, PDF, MOBI, AZW3, TXT"
    override val localImportEmpty = "No importable books in this folder"
    override fun localImportSelected(n: Int) = "$n selected"
    override val localImportAction = "Import"
    override fun imported(n: Int) = if (n == 1) "1 book added" else "$n books added"
    override val importNone = "Nothing was added"
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
    override val listStars = "Stars in the list"
    override val librarySection = "Library"
    override val bookCountLabel = "Books"
    override val statReading = "Reading"
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

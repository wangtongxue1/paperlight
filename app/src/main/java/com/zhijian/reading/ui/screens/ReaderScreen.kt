package com.zhijian.reading.ui.screens

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.zhijian.reading.R
import com.zhijian.reading.data.Annotation
import com.zhijian.reading.data.Book
import com.zhijian.reading.data.ReadingNotes
import com.zhijian.reading.data.Settings
import com.zhijian.reading.reader.BookContent
import com.zhijian.reading.reader.BookDecoder
import com.zhijian.reading.reader.TocItem
import com.zhijian.reading.ui.strings
import com.zhijian.reading.ui.theme.Radius
import com.zhijian.reading.ui.theme.Type
import com.zhijian.reading.ui.theme.palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private class ReaderBridge(private val sink: (String, String) -> Unit) {
    @JavascriptInterface
    fun onEvent(type: String, data: String) {
        sink(type, data)
    }
}

/** Late-wired funnel from the WebView's bridge into the composable's state. */
private class Sink {
    var receive: ((String, String) -> Unit)? = null
}

/** What the page engine last reported about where the reader is. */
private data class PageInfo(
    val mode: String = "scroll",
    val page: Int = 0,
    val pages: Int = 0,
    val chapter: Int = 0,
    val ratio: Float = 0f,
)

private enum class Panel { Toc, Notes, Progress, Bright, Font }

/** The paper color of a reading theme, as an ARGB int for the WebView. */
private fun themeBgColor(theme: String): Int = when (theme) {
    "dark" -> 0xFF17181A.toInt()
    "sepia" -> 0xFFF5EEDC.toInt()
    "gray" -> 0xFFE4E2DD.toInt()
    else -> 0xFFFBF9F4.toInt()
}

/**
 * Colors for the bottom-sheet panels. They follow the *page* theme rather
 * than the app theme, 微信读书-style: a night paper gets night panels.
 */
private data class SheetPal(
    val bg: Color,
    val ink: Color,
    val faint: Color,
    val raised: Color,
    val border: Color,
    val accent: Color,
    val accentSubtle: Color,
)

private fun sheetPal(theme: String, pal: com.zhijian.reading.ui.theme.Palette): SheetPal =
    if (theme == "dark") {
        SheetPal(
            bg = Color(0xFF1F2023),
            ink = Color(0xFFB5B3AC),
            faint = Color(0xFF7E7C75),
            raised = Color(0xFF26272A),
            border = Color(0xFF35373A),
            accent = Color(0xFFD8A55A),
            accentSubtle = Color(0x33D8A55A),
        )
    } else {
        SheetPal(
            bg = pal.surface,
            ink = pal.text,
            faint = pal.textTertiary,
            raised = pal.surfaceRaised,
            border = pal.borderSubtle,
            accent = Color(0xFFA57938),
            accentSubtle = pal.accentSubtle,
        )
    }

/** One selected passage waiting for the reader to act on it, with where it sits on screen. */
private data class Selection(
    val text: String,
    val chapter: Int,
    val topY: Float = -1f,
    val botY: Float = -1f,
    val cxD: Float = -1f,
)

/** Highlight looks offered in the selection pill, in tap order. */
private val HL_STYLES = listOf("yellow", "green", "blue", "pink", "underline")

/** Reader page themes, in panel order. */
private val READER_THEMES = listOf("light", "sepia", "gray", "dark")

/** Reads any flowable book format: one scroll or one page at a time. */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun ReaderScreen(
    book: Book,
    file: File,
    format: String,
    dark: Boolean,
    initialProgress: Float,
    onProgress: (Float) -> Unit,
    onBack: () -> Unit,
    onStripColor: (Color) -> Unit = {},
) {
    val pal = palette
    val strings = strings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notes = remember { ReadingNotes(context) }
    val appSettings = remember { Settings(context) }

    var fontScale by remember { mutableFloatStateOf(appSettings.readerFontScale) }
    var paged by remember { mutableStateOf(appSettings.readerMode == "paged") }
    var theme by remember { mutableStateOf(appSettings.readerTheme) }
    var lineHeight by remember { mutableFloatStateOf(appSettings.readerLineHeight) }
    var fontFamily by remember { mutableStateOf(appSettings.readerFontFamily) }
    var brightness by remember { mutableFloatStateOf(appSettings.readerBrightness) }
    var content by remember { mutableStateOf<BookContent?>(null) }
    var loading by remember { mutableStateOf(true) }
    var engineReady by remember { mutableStateOf(false) }
    var pageReady by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(initialProgress) }
    var restoreTo by remember { mutableFloatStateOf(initialProgress) }
    var seek by remember { mutableStateOf<Float?>(null) }

    var chromeOn by remember { mutableStateOf(true) }
    var info by remember { mutableStateOf(PageInfo(ratio = initialProgress)) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    var selection by remember { mutableStateOf<Selection?>(null) }
    var noteFor by remember { mutableStateOf<Selection?>(null) }
    var annos by remember { mutableStateOf(notes.annotations(book.id)) }

    // Full-text search: the query lives here, the hits live in the page.
    var searchOpen by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var searchCount by remember { mutableIntStateOf(0) }
    var searchAt by remember { mutableIntStateOf(-1) }

    // Automatic page turning, 0 = off, else the interval in seconds.
    var autoSpeed by remember { mutableIntStateOf(0) }

    val ui = remember { Handler(Looper.getMainLooper()) }
    val sink = remember { Sink() }
    val readerJs = remember {
        runCatching { context.assets.open("reader.js").bufferedReader().use { it.readText() } }
            .getOrDefault("")
    }

    // Opaque, theme-matched WebView background: a TRANSPARENT WebView under
    // floating chrome composites black on some devices (the page "goes dark"
    // whenever the bars show). The HTML body paints the same color anyway.
    val webViewBg: Int = when (appSettings.readerTheme) {
        "dark" -> 0xFF17181A.toInt()
        "sepia" -> 0xFFF5EEDC.toInt()
        "gray" -> 0xFFE4E2DD.toInt()
        else -> 0xFFFBF9F4.toInt()
    }
    val webView = remember {
        WebView(context).apply {
            setBackgroundColor(webViewBg)
            settings.apply {
                javaScriptEnabled = true
                allowFileAccess = true
                allowContentAccess = false
                builtInZoomControls = false
                displayZoomControls = false
                setSupportZoom(false)
                textZoom = 100
            }
            isVerticalScrollBarEnabled = false
            addJavascriptInterface(
                ReaderBridge { type, data -> ui.post { sink.receive?.invoke(type, data) } },
                "AndroidReader",
            )
            webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (readerJs.isNotEmpty()) view?.evaluateJavascript(readerJs, null)
                    // The engine needs a beat to install itself.
                    ui.postDelayed({ pageReady++ }, 220)
                }
            }
        }
    }

    fun runJs(js: String) {
        if (pageReady > 0) webView.evaluateJavascript(js, null)
    }

    fun clearSelection() {
        runJs("window.__pl.clearSelection()")
        selection = null
    }

    /** Side taps: turn pages when paged, slide a screen when scrolling. */
    fun tapZone(dir: Int) {
        if (paged && info.pages > 0) {
            runJs("window.__pl.${if (dir > 0) "next" else "prev"}()")
        } else {
            runJs("window.__pl.scrollScreens($dir)")
        }
    }

    /** One tap on a pill color: save the passage with that highlight look. */
    fun applyHighlightStyle(s: Selection, style: String) {
        val saved = runCatching {
            val a = notes.addAnnotation(book.id, s.chapter, s.text, "", info.ratio, style)
            runJs("window.__pl.applyHighlight('${a.id}', '$style')")
            runJs("window.__pl.clearSelection()")
            a
        }.getOrNull()
        annos = notes.annotations(book.id)
        selection = null
        if (saved == null) {
            Toast.makeText(context, strings.noteFailed, Toast.LENGTH_SHORT).show()
        }
    }

    fun onBridge(type: String, data: String) {
        val obj = runCatching { JSONObject(data) }.getOrNull() ?: return
        val d = context.resources.displayMetrics.density
        when (type) {
            "tap" -> {
                // The engine reports CSS pixels; width is in device pixels.
                val x = obj.optDouble("x", 0.0).toFloat() * d
                when {
                    x < webView.width * 0.32f -> tapZone(-1)
                    x > webView.width * 0.68f -> tapZone(1)
                    else -> chromeOn = !chromeOn
                }
            }
            "metrics" -> {
                val ratio = obj.optDouble("ratio", 0.0).toFloat().coerceIn(0f, 1f)
                progress = ratio
                info = PageInfo(
                    mode = obj.optString("mode", "scroll"),
                    page = obj.optInt("page", 0),
                    pages = obj.optInt("pages", 0),
                    chapter = obj.optInt("chapter", 0),
                    ratio = ratio,
                )
            }
            "search" -> {
                searchCount = obj.optInt("count", 0)
                searchAt = if (searchCount > 0) 0 else -1
            }
            "autoStop" -> {
                autoSpeed = 0
                if (obj.optString("reason") == "end") {
                    Toast.makeText(context, strings.autoFinished, Toast.LENGTH_SHORT).show()
                }
            }
            "selection" -> {
                val text = obj.optString("text", "")
                if (text.isBlank()) {
                    if (noteFor == null) selection = null
                } else {
                    selection = Selection(
                        text = text,
                        chapter = obj.optInt("chapter", info.chapter),
                        topY = obj.optDouble("top", -1.0).toFloat() * d,
                        botY = obj.optDouble("bottom", -1.0).toFloat() * d,
                        cxD = obj.optDouble("cx", -1.0).toFloat() * d,
                    )
                }
            }
        }
    }
    sink.receive = { type, data -> onBridge(type, data) }

    fun searchSubmit(q: String) {
        searchText = q
        searchAt = -1
        runJs("window.__pl.search(${JSONObject.quote(q)})")
    }

    fun searchStep(delta: Int) {
        if (searchCount <= 0) return
        val next = (searchAt + delta).mod(searchCount)
        searchAt = next
        runJs("window.__pl.searchStep($next)")
    }

    LaunchedEffect(book.id) {
        loading = true
        val prepared = withContext(Dispatchers.IO) {
            BookDecoder.prepare(file, cacheRoot = File(context.cacheDir, "reader"), theme = theme)
        }
        content = prepared
        if (prepared != null) {
            webView.loadDataWithBaseURL(prepared.baseUrl, prepared.html, "text/html", "UTF-8", null)
        } else {
            loading = false
        }
    }

    // Everything the engine needs on first paint: theme, text size, page
    // mode, the book's highlights, and the place the reader stopped.
    LaunchedEffect(pageReady) {
        if (pageReady == 0) return@LaunchedEffect
        val c = content ?: run { loading = false; return@LaunchedEffect }
        runJs("window.__pl.setTheme('$theme')")
        runJs("window.__pl.setFontScale($fontScale)")
        runJs("window.__pl.setLineHeight($lineHeight)")
        runJs("window.__pl.setFontFamily('$fontFamily')")
        runJs("window.__pl.setMode('${if (paged) "paged" else "scroll"}')")
        annos.forEach { a ->
            runJs(
                "window.__pl.highlightByText(" +
                    "'${a.id}', ${JSONObject.quote(a.quote)}, ${a.chapter}, '${a.style}')",
            )
        }
        if (restoreTo > 0f) runJs("window.__pl.jumpRatio($restoreTo)")
        runJs("window.__pl.getState()")
        engineReady = true
        loading = false
    }

    // Changes while reading go straight to the engine, no reload.
    LaunchedEffect(fontScale) {
        if (engineReady) {
            runJs("window.__pl.setFontScale($fontScale)")
            appSettings.readerFontScale = fontScale
        }
    }
    LaunchedEffect(paged) {
        if (engineReady) {
            runJs("window.__pl.setMode('${if (paged) "paged" else "scroll"}')")
            appSettings.readerMode = if (paged) "paged" else "scroll"
        }
    }
    LaunchedEffect(dark) {
        if (engineReady) runJs("window.__pl.setTheme('$theme')")
    }
    LaunchedEffect(theme) {
        if (engineReady) {
            runJs("window.__pl.setTheme('$theme')")
            webView.setBackgroundColor(themeBgColor(theme))
            appSettings.readerTheme = theme
        }
        // The status/navigation strips follow the reading paper, 微信读书
        // style: a night page gets night strips. AppRoot paints the strips
        // itself, so hand the paper color up.
        onStripColor(
            when (theme) {
                "dark" -> Color(0xFF17181A)
                "sepia" -> Color(0xFFF5EEDC)
                "gray" -> Color(0xFFE4E2DD)
                else -> Color(0xFFFBF9F4)
            },
        )
    }
    DisposableEffect(Unit) {
        val appBg = pal.bgApp
        onDispose { onStripColor(appBg) }
    }
    LaunchedEffect(lineHeight) {
        if (engineReady) {
            runJs("window.__pl.setLineHeight($lineHeight)")
            appSettings.readerLineHeight = lineHeight
        }
    }
    LaunchedEffect(fontFamily) {
        if (engineReady) {
            runJs("window.__pl.setFontFamily('$fontFamily')")
            appSettings.readerFontFamily = fontFamily
        }
    }
    LaunchedEffect(brightness) {
        appSettings.readerBrightness = brightness
        val win = (context as? android.app.Activity)?.window ?: return@LaunchedEffect
        win.attributes = win.attributes.apply {
            screenBrightness = if (brightness > 0f) brightness.coerceIn(0.01f, 1f) else -1f
        }
    }
    DisposableEffect(Unit) {
        val win = (context as? android.app.Activity)?.window
        val old = win?.attributes?.screenBrightness ?: -1f
        onDispose {
            if (win != null) {
                win.attributes = win.attributes.apply { screenBrightness = old }
            }
        }
    }

    // Automatic page turning rides the engine's timer; zero switches it off.
    LaunchedEffect(autoSpeed) {
        if (engineReady) runJs("window.__pl.setAuto(${autoSpeed * 1000})")
    }

    // Keep the place even if the reader is closed by the system.
    DisposableEffect(book.id) {
        onDispose {
            runCatching {
                runJs("window.__pl.setAuto(0)")
                runJs("window.__pl.searchClear()")
                webView.destroy()
            }
            onProgress(progress.coerceIn(0f, 1f))
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(4000)
            onProgress(progress)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val list = annos
        if (uri == null) return@rememberLauncherForActivityResult
        if (list.isEmpty()) {
            Toast.makeText(context, strings.exportEmpty, Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(exportText(book, content?.toc.orEmpty(), list, strings).toByteArray(Charsets.UTF_8))
                    } ?: error("no stream")
                }.isSuccess
            }
            Toast.makeText(
                context,
                if (ok) strings.exportDone else strings.exportFail,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val toc = content?.toc.orEmpty()

    // Chrome shares the page's paper so the bars feel part of the sheet,
    // 番茄-style, instead of floating app chrome above it.
    val contentBg = when (theme) {
        "dark" -> Color(0xFF17181A)
        "sepia" -> Color(0xFFF5EEDC)
        "gray" -> Color(0xFFE4E2DD)
        else -> Color(0xFFFBF9F4)
    }
    val chromeBg = contentBg
    val chromeInk = when (theme) {
        "dark" -> Color(0xFFB5B3AC)
        "sepia" -> Color(0xFF55483A)
        "gray" -> Color(0xFF3B3936)
        else -> Color(0xFF35322B)
    }
    val chromeInkFaint = when (theme) {
        "dark" -> Color(0xFF75736C)
        "sepia" -> Color(0xFFA08F72)
        "gray" -> Color(0xFF84817A)
        else -> Color(0xFF9C968D)
    }
    val chromeAccent = when (theme) {
        "dark" -> Color(0xFFD8A55A)
        // 微信读书 earth-gold: the dock's active tint and highlighted marks.
        else -> Color(0xFFA57938)
    }
    val shown = (seek ?: progress).coerceIn(0f, 1f)

    Box(
        Modifier
            .fillMaxSize()
            .background(contentBg),
    ) {
        // ---- the page: full-bleed; chrome floats above it, never reflows it
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize(),
        )

        if (loading || content == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (content == null && !loading) strings.readerFailed else "…",
                    style = Type.small,
                    color = pal.textTertiary,
                )
            }
        }

        // Compact dark action pill hugging the selection, Apple Books style.
        selection?.let { sel ->
            val config = LocalConfiguration.current
            val dens = LocalDensity.current
            val yTop = with(dens) { sel.topY.toDp() }
            val yBot = with(dens) { sel.botY.toDp() }
            val xC = with(dens) { sel.cxD.toDp() }
            val pillW = 218.dp
            val x = (xC - pillW / 2).coerceIn(8.dp, config.screenWidthDp.dp - pillW - 8.dp)
            val placeAbove = yTop >= 128.dp
            val y = if (placeAbove) yTop - 106.dp else yBot + 12.dp
            Column(
                Modifier
                    .offset(x = x, y = y)
                    .zIndex(3f)
                    .shadow(10.dp, RoundedCornerShape(12.dp))
                    .background(Color(0xE625221F), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HL_STYLES.forEach { st ->
                        StyleDot(style = st, onClick = { applyHighlightStyle(sel, st) })
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PillIcon(R.drawable.ic_r_note, strings.makeNote, Color(0xFFEDE7DD)) {
                        if (engineReady) noteFor = sel
                    }
                    PillIcon(R.drawable.ic_r_copy, strings.copyText, Color(0xFFEDE7DD)) {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("quote", sel.text))
                        Toast.makeText(context, strings.copied, Toast.LENGTH_SHORT).show()
                        clearSelection()
                    }
                    PillIcon(R.drawable.ic_a_link, strings.shareText, Color(0xFFEDE7DD)) {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, sel.text)
                        }
                        runCatching {
                            context.startActivity(android.content.Intent.createChooser(send, null))
                        }
                        clearSelection()
                    }
                    PillIcon(R.drawable.ic_r_close, strings.deselect, Color(0xFF9C968D)) {
                        clearSelection()
                    }
                }
            }
        }

        // ---- top chrome: floats over the page, paper-on-paper -------------
        Box(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            AnimatedVisibility(visible = chromeOn) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(chromeBg)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    // NOTE: no statusBars padding here — MainActivity already
                    // pads the whole NavHost below the status bar; adding it
                    // again doubles the blank strip above the bar.
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(Radius.md.dp))
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_r_back),
                            contentDescription = null,
                            tint = chromeInk,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    // Full-text search, the 起点/番茄 staple, quiet styling.
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(Radius.md.dp))
                            .clickable { searchOpen = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_a_search),
                            contentDescription = strings.readerSearch,
                            tint = chromeInk,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(Radius.md.dp))
                            .clickable { moreMenu = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_a_more),
                            contentDescription = null,
                            tint = chromeInk,
                            modifier = Modifier.size(19.dp),
                        )
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(strings.jumpBookStart) },
                                onClick = {
                                    moreMenu = false
                                    runJs("window.__pl.jumpChapter(0)")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(strings.exportNotes) },
                                onClick = {
                                    moreMenu = false
                                    exportLauncher.launch("《${book.title}》批注.txt")
                                },
                            )
                        }
                    }
                }
            }
        }

        // ---- quiet marks, 微信读书 style: chapter top-left, page bottom-right
        if (!chromeOn) {
            val markInk = chromeInkFaint
            if (info.mode == "paged") {
                Text(
                    text = toc.getOrNull(info.chapter)?.title ?: book.title,
                    style = Type.numeric.copy(fontSize = 11.sp),
                    color = markInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 22.dp, top = 6.dp)
                        .fillMaxWidth(0.6f),
                )
            }
            Text(
                text = if (info.mode == "paged" && info.pages > 0) {
                    strings.pagePos(info.page, info.pages)
                } else {
                    "${(info.ratio * 100).toInt()}%"
                },
                style = Type.numeric.copy(fontSize = 11.sp),
                color = markInk,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 22.dp, bottom = navBottom + 6.dp),
            )
        }

        // ---- bottom chrome: chapter nav row + dock, paper-on-paper --------
        AnimatedVisibility(
            visible = chromeOn,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(chromeBg)
                    .padding(bottom = navBottom + 6.dp),
            ) {
                // The dock itself, 微信读书 style: 目录 / 批注 / 进度 / 亮度 / 字体,
                // icons only — no word labels, active icon tinted the earth-gold.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NavItem(
                        R.drawable.ic_r_toc,
                        strings.readerToc,
                        panel == Panel.Toc,
                        ink = chromeInk,
                        faint = chromeInkFaint,
                        activeTint = chromeAccent,
                        showLabel = false,
                        modifier = Modifier.weight(1f),
                    ) {
                        panel = if (panel == Panel.Toc) null else Panel.Toc
                    }
                    NavItem(
                        R.drawable.ic_r_annotate,
                        strings.readerAnnotations,
                        panel == Panel.Notes,
                        ink = chromeInk,
                        faint = chromeInkFaint,
                        activeTint = chromeAccent,
                        showLabel = false,
                        modifier = Modifier.weight(1f),
                    ) {
                        panel = if (panel == Panel.Notes) null else Panel.Notes
                    }
                    NavItem(
                        R.drawable.ic_r_progress,
                        strings.readerProgress,
                        panel == Panel.Progress,
                        ink = chromeInk,
                        faint = chromeInkFaint,
                        activeTint = chromeAccent,
                        showLabel = false,
                        modifier = Modifier.weight(1f),
                    ) {
                        panel = if (panel == Panel.Progress) null else Panel.Progress
                    }
                    NavItem(
                        R.drawable.ic_r_bright,
                        strings.brightness,
                        panel == Panel.Bright,
                        ink = chromeInk,
                        faint = chromeInkFaint,
                        activeTint = chromeAccent,
                        showLabel = false,
                        modifier = Modifier.weight(1f),
                    ) {
                        panel = if (panel == Panel.Bright) null else Panel.Bright
                    }
                    NavItem(
                        iconRes = 0,
                        iconText = "A",
                        label = strings.fontFamily,
                        active = panel == Panel.Font,
                        ink = chromeInk,
                        faint = chromeInkFaint,
                        activeTint = chromeAccent,
                        showLabel = false,
                        modifier = Modifier.weight(1f),
                    ) {
                        panel = if (panel == Panel.Font) null else Panel.Font
                    }
                }
            }
        }
    }

    // ---- panels --------------------------------------------------------------
    panel?.let { which ->
        val sp = sheetPal(theme, pal)
        ModalBottomSheet(
            onDismissRequest = { panel = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = sp.bg,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = navBottom + 16.dp),
            ) {
                when (which) {
                    Panel.Toc -> TocSheet(
                        sp = sp,
                        bookTitle = book.title,
                        authorLine = book.authorLine,
                        toc = toc,
                        current = info.chapter,
                        ratio = info.ratio,
                        onJumpChapter = { i ->
                            runJs("window.__pl.jumpChapter($i)")
                            panel = null
                        },
                        onJumpEnd = {
                            runJs("window.__pl.jumpRatio(1f)")
                            panel = null
                        },
                    )
                    Panel.Notes -> NotesSheet(
                        sp = sp,
                        bookTitle = book.title,
                        authorLine = book.authorLine,
                        annos = annos,
                        onJumpAnno = { a ->
                            runJs("window.__pl.jumpRatio(${a.progress})")
                            panel = null
                        },
                        onDeleteAnno = { a ->
                            notes.removeAnnotation(a.id)
                            annos = notes.annotations(book.id)
                            runJs("window.__pl.removeHighlight('${a.id}')")
                        },
                        onExport = { exportLauncher.launch("《${book.title}》批注.txt") },
                    )
                    Panel.Progress -> ProgressSheet(
                        sp = sp,
                        ratio = info.ratio,
                        shown = shown,
                        mode = info.mode,
                        page = info.page,
                        pages = info.pages,
                        chapter = info.chapter,
                        chapterTotal = toc.size,
                        annoCount = annos.size,
                        autoSpeed = autoSpeed,
                        onSeek = { r -> runJs("window.__pl.jumpRatio($r)") },
                        onJumpStart = {
                            runJs("window.__pl.jumpChapter(0)")
                            panel = null
                        },
                        onJumpEnd = {
                            runJs("window.__pl.jumpRatio(1f)")
                            panel = null
                        },
                        onAuto = { autoSpeed = it },
                    )
                    Panel.Bright -> BrightSheet(
                        sp = sp,
                        brightness = brightness,
                        theme = theme,
                        paged = paged,
                        onBrightness = { brightness = it },
                        onTheme = { theme = it },
                        onMode = { paged = it },
                    )
                    Panel.Font -> FontSheet(
                        sp = sp,
                        fontScale = fontScale,
                        lineHeight = lineHeight,
                        fontFamily = fontFamily,
                        onFont = { fontScale = it },
                        onLineHeight = { lineHeight = it },
                        onFontFamily = { fontFamily = it },
                    )
                }
            }
        }
    }

    // ---- full-text search -------------------------------------------------------
    if (searchOpen) {
        AlertDialog(
            onDismissRequest = {
                searchOpen = false
                runJs("window.__pl.searchClear()")
            },
            containerColor = pal.surfaceRaised,
            titleContentColor = pal.text,
            textContentColor = pal.textSecondary,
            title = { Text(strings.readerSearch) },
            text = {
                Column {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        placeholder = { Text(strings.readerSearchHint, style = Type.small) },
                        singleLine = true,
                        textStyle = Type.body,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = when {
                            searchCount > 0 -> strings.searchCount(searchCount)
                            searchText.isNotBlank() -> strings.searchNone
                            else -> ""
                        },
                        style = Type.small,
                        color = pal.textTertiary,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { searchSubmit(searchText) }, enabled = searchText.isNotBlank()) {
                    Text(strings.searchGo, color = if (searchText.isNotBlank()) pal.accent else pal.textTertiary)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { searchStep(-1) }, enabled = searchCount > 0) {
                        Text(strings.searchPrev, color = if (searchCount > 0) pal.accent else pal.textTertiary)
                    }
                    TextButton(onClick = { searchStep(1) }, enabled = searchCount > 0) {
                        Text(strings.searchNext, color = if (searchCount > 0) pal.accent else pal.textTertiary)
                    }
                }
            },
        )
    }

    // ---- the note dialog -------------------------------------------------------
    noteFor?.let { sel ->
        var note by remember(sel) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { noteFor = null },
            containerColor = pal.surfaceRaised,
            titleContentColor = pal.text,
            textContentColor = pal.textSecondary,
            title = { Text(strings.makeNote) },
            text = {
                Column {
                    Text(
                        text = sel.text.take(120) + if (sel.text.length > 120) "…" else "",
                        style = Type.small,
                        color = pal.textSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.md.dp))
                            .background(pal.accentSubtle)
                            .padding(10.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        placeholder = { Text(strings.noteHint, style = Type.small) },
                        textStyle = Type.body,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val saved = runCatching {
                        val a = notes.addAnnotation(book.id, sel.chapter, sel.text, note, info.ratio)
                        runJs("window.__pl.applyHighlight('${a.id}')")
                        runJs("window.__pl.clearSelection()")
                        a
                    }.getOrNull()
                    annos = notes.annotations(book.id)
                    noteFor = null
                    selection = null
                    Toast.makeText(
                        context,
                        if (saved != null) strings.noteSaved else strings.noteFailed,
                        Toast.LENGTH_SHORT,
                    ).show()
                }) { Text(strings.save, color = pal.accent) }
            },
            dismissButton = {
                TextButton(onClick = {
                    noteFor = null
                    clearSelection()
                }) { Text(strings.cancel, color = pal.textSecondary) }
            },
        )
    }
}

/** One labeled destination in the dock: icon (or letter) on top, word below.
 *  微信读书 runs the dock icons-only; pass showLabel = false for that look. */
@Composable
private fun NavItem(
    iconRes: Int,
    label: String,
    active: Boolean,
    ink: Color,
    faint: Color,
    activeTint: Color,
    modifier: Modifier = Modifier,
    iconText: String? = null,
    showLabel: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(Radius.md.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (iconText != null) {
            Text(
                text = iconText,
                style = Type.title,
                color = if (active) activeTint else ink,
            )
        } else {
            Icon(
                painterResource(iconRes),
                contentDescription = label,
                tint = if (active) activeTint else ink,
                modifier = Modifier.size(21.dp),
            )
        }
        if (showLabel) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = label,
                style = Type.numeric,
                color = if (active) activeTint else faint,
            )
        }
    }
}

/** 上一章 / 下一章 at the ends of the chapter strip. */
@Composable
private fun ChapterJump(
    label: String,
    enabled: Boolean,
    ink: Color,
    faint: Color,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = Type.body,
        color = if (enabled) ink else faint,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.md.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    )
}

/** One tight dark button inside the selection pill. */
@Composable
private fun PillIcon(iconRes: Int, desc: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(iconRes), desc, tint = tint, modifier = Modifier.size(17.dp))
    }
}

/**
 * The 目录 sheet, 微信读书 style: book header, the chapter list open on where
 * the reader actually is, and a 去底部 pill at the bottom. Notes live in their
 * own panel; the seek strip lives in the 进度 panel.
 */
@Composable
private fun TocSheet(
    sp: SheetPal,
    bookTitle: String,
    authorLine: String,
    toc: List<TocItem>,
    current: Int,
    ratio: Float,
    onJumpChapter: (Int) -> Unit,
    onJumpEnd: () -> Unit,
) {
    val strings = strings
    Text(
        text = bookTitle,
        style = Type.title,
        color = sp.ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    if (authorLine.isNotBlank()) {
        Spacer(Modifier.height(2.dp))
        Text(text = authorLine, style = Type.small, color = sp.faint, maxLines = 1)
    }
    Spacer(Modifier.height(10.dp))
    if (toc.isEmpty()) {
        Text(strings.noToc, style = Type.small, color = sp.faint)
    } else {
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        // Open on where the reader actually is, like the 微信读书 目录.
        LaunchedEffect(toc, current) {
            val row = toc.indexOfFirst { it.index == current }
            if (row >= 0) listState.scrollToItem(row.coerceAtLeast(0))
        }
        LazyColumn(modifier = Modifier.height(420.dp), state = listState) {
            items(toc, key = { it.index }) { item ->
                val isCurrent = item.index == current
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.md.dp))
                        .clickable { onJumpChapter(item.index) }
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.title,
                            style = Type.body,
                            color = if (isCurrent) sp.accent else sp.ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (isCurrent) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = strings.readTo((ratio * 100).toInt()),
                                style = Type.small,
                                color = sp.accent,
                            )
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    // 去底部 pill, the 微信读书 floating jump.
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = strings.jumpBookEnd,
            style = Type.body,
            color = sp.ink,
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(sp.raised)
                .border(1.dp, sp.border, RoundedCornerShape(18.dp))
                .clickable(onClick = onJumpEnd)
                .padding(horizontal = 22.dp, vertical = 9.dp),
        )
    }
}

/** The draggable whole-book progress strip: tap to jump, drag to scrub. */
@Composable
private fun SeekStrip(
    shown: Float,
    accent: Color,
    track: Color,
    onSeek: (Float) -> Unit,
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val pos = drag ?: shown
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .pointerInput(Unit) {
                detectTapGestures { p ->
                    val r = (p.x / size.width.toFloat()).coerceIn(0f, 1f)
                    drag = r
                    onSeek(r)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { p ->
                        drag = (p.x / size.width.toFloat()).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        drag?.let(onSeek)
                        drag = null
                    },
                    onDragCancel = { drag = null },
                ) { change, _ ->
                    drag = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    change.consume()
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(track),
        )
        Box(
            Modifier
                .fillMaxWidth(pos)
                .height(4.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = (maxWidth * pos - 12.dp).coerceAtLeast(0.dp))
                .size(12.dp)
                .clip(CircleShape)
                .background(accent),
        )
    }
}

@Composable
private fun AnnotationPanel(
    sp: SheetPal,
    annos: List<Annotation>,
    onJump: (Annotation) -> Unit,
    onDelete: (Annotation) -> Unit,
    onExport: () -> Unit,
) {
    val strings = strings
    val pal = palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(strings.annotationCount(annos.size), style = Type.title, color = sp.ink, modifier = Modifier.weight(1f))
        Text(
            text = strings.exportNotes,
            style = Type.small,
            color = if (annos.isEmpty()) sp.faint else sp.accent,
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.md.dp))
                .clickable(enabled = annos.isNotEmpty(), onClick = onExport)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
    Spacer(Modifier.height(10.dp))
    if (annos.isEmpty()) {
        Text(strings.noAnnotations, style = Type.small, color = sp.faint)
        return
    }
    LazyColumn(Modifier.height(420.dp)) {
        items(annos, key = { it.id }) { a ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.md.dp))
                    .clickable { onJump(a) }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            ) {
                Text(
                    text = a.quote,
                    style = Type.small,
                    color = sp.ink,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.sm.dp))
                        .background(annoColor(a.style, pal))
                        .padding(8.dp),
                )
                if (a.note.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = a.note,
                        style = Type.small,
                        color = sp.faint,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatTime(a.createdAt),
                        style = Type.numeric,
                        color = sp.faint,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        painterResource(R.drawable.ic_r_delete),
                        strings.delete,
                        tint = sp.faint,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .clickable { onDelete(a) }
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

/**
 * The 批注 sheet, 微信读书想法-style: a quiet book header card, then the
 * annotation list with jump / delete / export.
 */
@Composable
private fun NotesSheet(
    sp: SheetPal,
    bookTitle: String,
    authorLine: String,
    annos: List<Annotation>,
    onJumpAnno: (Annotation) -> Unit,
    onDeleteAnno: (Annotation) -> Unit,
    onExport: () -> Unit,
) {
    val strings = strings
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg.dp))
            .background(sp.raised)
            .padding(14.dp),
    ) {
        Text(
            text = bookTitle,
            style = Type.title,
            color = sp.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (authorLine.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(text = authorLine, style = Type.small, color = sp.faint, maxLines = 1)
        }
    }
    Spacer(Modifier.height(12.dp))
    AnnotationPanel(
        sp = sp,
        annos = annos,
        onJump = onJumpAnno,
        onDelete = onDeleteAnno,
        onExport = onExport,
    )
}

/**
 * The 进度 sheet, 微信读书 style: three quiet stats, the whole-book scrub
 * strip, start/end jumps, and automatic turning.
 */
@Composable
private fun ProgressSheet(
    sp: SheetPal,
    ratio: Float,
    shown: Float,
    mode: String,
    page: Int,
    pages: Int,
    chapter: Int,
    chapterTotal: Int,
    annoCount: Int,
    autoSpeed: Int,
    onSeek: (Float) -> Unit,
    onJumpStart: () -> Unit,
    onJumpEnd: () -> Unit,
    onAuto: (Int) -> Unit,
) {
    val strings = strings

    // Three stats, 微信读书: big number over a small gray label.
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StatCell(
            value = "${(ratio * 100).toInt()}%",
            label = strings.readerProgress,
            ink = sp.ink,
            faint = sp.faint,
            modifier = Modifier.weight(1f),
        )
        StatCell(
            value = if (mode == "paged" && pages > 0) {
                "$page/$pages"
            } else if (chapterTotal > 0) {
                "${chapter + 1}/$chapterTotal"
            } else {
                "-"
            },
            label = if (mode == "paged" && pages > 0) strings.readerPage else strings.readerChapter,
            ink = sp.ink,
            faint = sp.faint,
            modifier = Modifier.weight(1f),
        )
        StatCell(
            value = "$annoCount",
            label = strings.readerAnnotations,
            ink = sp.ink,
            faint = sp.faint,
            modifier = Modifier.weight(1f),
        )
    }

    Spacer(Modifier.height(18.dp))
    // The whole-book scrub strip, tap or drag to jump.
    SeekStrip(shown = shown, accent = sp.accent, track = sp.border, onSeek = onSeek)

    Spacer(Modifier.height(16.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = strings.jumpBookStart,
            style = Type.body,
            color = sp.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(sp.raised)
                .clickable(onClick = onJumpStart)
                .padding(vertical = 10.dp),
        )
        Text(
            text = strings.jumpBookEnd,
            style = Type.body,
            color = sp.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(sp.raised)
                .clickable(onClick = onJumpEnd)
                .padding(vertical = 10.dp),
        )
    }

    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        RowLabel(sp = sp, text = strings.autoTurn)
        Spacer(Modifier.width(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(sp = sp, selected = autoSpeed == 0, title = strings.autoOff) { onAuto(0) }
            Chip(sp = sp, selected = autoSpeed == 14, title = strings.autoSlow) { onAuto(14) }
            Chip(sp = sp, selected = autoSpeed == 9, title = strings.autoMid) { onAuto(9) }
            Chip(sp = sp, selected = autoSpeed == 5, title = strings.autoFast) { onAuto(5) }
        }
    }
}

/** One big-number stat with a gray caption, the 微信读书 进度 header cell. */
@Composable
private fun StatCell(
    value: String,
    label: String,
    ink: Color,
    faint: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = Type.title.copy(fontSize = 22.sp), color = ink)
        Spacer(Modifier.height(3.dp))
        Text(text = label, style = Type.small, color = faint, maxLines = 1)
    }
}

/**
 * 亮度 sheet, 微信读书 style: brightness slider on top, then the four paper
 * color swatches (night included, moon icon on the dark one), then page mode.
 */
@Composable
private fun BrightSheet(
    sp: SheetPal,
    brightness: Float,
    theme: String,
    paged: Boolean,
    onBrightness: (Float) -> Unit,
    onTheme: (String) -> Unit,
    onMode: (Boolean) -> Unit,
) {
    val strings = strings

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(R.drawable.ic_r_bright),
            contentDescription = strings.brightness,
            tint = sp.faint,
            modifier = Modifier.size(18.dp),
        )
        Slider(
            value = brightness,
            onValueChange = { onBrightness(Math.round(it * 20f) / 20f) },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = sp.accent,
                activeTrackColor = sp.accent,
                inactiveTrackColor = sp.border,
            ),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        )
        Text(
            text = if (brightness > 0f) "${(brightness * 100).toInt()}%" else strings.systemDefault,
            style = Type.numeric,
            color = sp.faint,
        )
    }

    Spacer(Modifier.height(14.dp))
    Text(text = strings.readerColors, style = Type.small, color = sp.faint)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        READER_THEMES.forEach { key ->
            val selected = key == theme
            Box(
                Modifier
                    .size(width = 52.dp, height = 40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(paperSwatch(key))
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) sp.accent else sp.border,
                        shape = RoundedCornerShape(10.dp),
                    )
                    .clickable { onTheme(key) },
                contentAlignment = Alignment.Center,
            ) {
                if (key == "dark") {
                    Icon(
                        painterResource(R.drawable.ic_r_moon),
                        contentDescription = strings.readerThemeNight,
                        tint = Color(0xFF7EB3E8),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        RowLabel(sp = sp, text = strings.pageMode)
        Spacer(Modifier.width(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(sp = sp, selected = !paged, title = strings.modeScroll) { onMode(false) }
            Chip(sp = sp, selected = paged, title = strings.modePaged) { onMode(true) }
        }
    }
}

/** The paper color behind a theme swatch, matching the page background. */
private fun paperSwatch(key: String): Color = when (key) {
    "dark" -> Color(0xFF1F2124)
    "sepia" -> Color(0xFFF5EEDC)
    "gray" -> Color(0xFFE4E2DD)
    else -> Color(0xFFFBF9F4)
}

/** 字体 sheet: size stepper + face + line spacing. */
@Composable
private fun FontSheet(
    sp: SheetPal,
    fontScale: Float,
    lineHeight: Float,
    fontFamily: String,
    onFont: (Float) -> Unit,
    onLineHeight: (Float) -> Unit,
    onFontFamily: (String) -> Unit,
) {
    val strings = strings

    Row(verticalAlignment = Alignment.CenterVertically) {
        RowLabel(sp = sp, text = strings.fontSize)
        StepChip(sp = sp, label = "A−") { onFont((fontScale - 0.05f).coerceAtLeast(0.8f)) }
        Text(
            text = "${(fontScale * 100).toInt()}%",
            style = Type.numeric,
            color = sp.faint,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        StepChip(sp = sp, label = "A＋") { onFont((fontScale + 0.05f).coerceAtMost(1.6f)) }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(sp = sp, selected = fontFamily == "serif", title = strings.fontSerif) { onFontFamily("serif") }
            Chip(sp = sp, selected = fontFamily == "sans", title = strings.fontSans) { onFontFamily("sans") }
        }
    }

    Spacer(Modifier.height(16.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        RowLabel(sp = sp, text = strings.lineHeight)
        Slider(
            value = lineHeight,
            onValueChange = { onLineHeight(Math.round(it * 20f) / 20f) },
            valueRange = 1.4f..2.2f,
            colors = SliderDefaults.colors(
                thumbColor = sp.accent,
                activeTrackColor = sp.accent,
                inactiveTrackColor = sp.border,
            ),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        )
        Text(
            text = String.format(java.util.Locale.US, "%.2f", lineHeight),
            style = Type.numeric,
            color = sp.faint,
        )
    }
}

/** Left-hand label of a settings row, fixed width so chips align. */
@Composable
private fun RowLabel(sp: SheetPal, text: String) {
    Text(text, style = Type.body, color = sp.ink, modifier = Modifier.width(64.dp))
}

/** One compact pill choice, the 番茄 settings chip. */
@Composable
private fun Chip(sp: SheetPal, selected: Boolean, title: String, onClick: () -> Unit) {
    Text(
        text = title,
        style = Type.small,
        color = if (selected) sp.ink else sp.faint,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) sp.accentSubtle else sp.raised)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) sp.accent else sp.border,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

/** One A−/A＋ stepper chip for the font size. */
@Composable
private fun StepChip(sp: SheetPal, label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = Type.body,
        color = sp.ink,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.md.dp))
            .background(sp.raised)
            .border(1.dp, sp.border, RoundedCornerShape(Radius.md.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** One color dot in the selection pill: paper-yellow, green, blue, pink, or an underline. */
@Composable
private fun StyleDot(style: String, onClick: () -> Unit) {
    val color = when (style) {
        "green" -> Color(0xFF66CC66)
        "blue" -> Color(0xFF609FFF)
        "pink" -> Color(0xFFFF66A3)
        "underline" -> Color(0xFFEDE7DD)
        else -> Color(0xFFF3B93C)
    }
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (style == "underline") {
            Text(
                text = "U",
                color = color,
                style = Type.body,
                textDecoration = TextDecoration.Underline,
            )
        } else {
            Box(
                Modifier
                    .size(17.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

private fun formatTime(t: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(t))

/** The list-tint that shows a highlight's color inside the annotation panel. */
private fun annoColor(style: String, pal: com.zhijian.reading.ui.theme.Palette): Color =
    if (pal.dark) {
        when (style) {
            "green" -> Color(0x2666CC66)
            "blue" -> Color(0x26609FFF)
            "pink" -> Color(0x26FF66A3)
            "underline" -> pal.accentSubtle
            else -> Color(0x26F3B93C)
        }
    } else {
        when (style) {
            "green" -> Color(0xFFDFF0DC)
            "blue" -> Color(0xFFDCE8FA)
            "pink" -> Color(0xFFFAE0EA)
            "underline" -> pal.accentSubtle
            else -> Color(0xFFFAF0D7)
        }
    }

/** A plain text sheet of every annotation on the book, for sharing out. */
private fun exportText(
    book: Book,
    toc: List<TocItem>,
    annos: List<Annotation>,
    strings: com.zhijian.reading.ui.AppStrings,
): String {
    val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val sb = StringBuilder()
    sb.append("《${book.title}》 · ${strings.readerAnnotations}\n")
    if (book.authorLine.isNotBlank()) sb.append("${book.authorLine}\n")
    sb.append("${df.format(Date())} · ${annos.size}\n")
    annos.forEachIndexed { i, a ->
        val ch = toc.firstOrNull { it.index == a.chapter }?.title.orEmpty()
        sb.append("\n―――― ${i + 1} ――――\n")
        if (ch.isNotBlank()) sb.append("${ch} · ${(a.progress * 100).toInt()}%\n")
        sb.append("${strings.quoteLabel}: ${a.quote}\n")
        if (a.note.isNotBlank()) sb.append("${strings.noteLabel}: ${a.note}\n")
        sb.append("${df.format(Date(a.createdAt))}\n")
    }
    return sb.toString()
}

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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.zhijian.reading.R
import com.zhijian.reading.data.Annotation
import com.zhijian.reading.data.Book
import com.zhijian.reading.data.Bookmark
import com.zhijian.reading.data.ReadingNotes
import com.zhijian.reading.data.Settings
import com.zhijian.reading.reader.EpubReader
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

private enum class Panel { Toc, Annos, Look }

/** One selected passage waiting for the reader to act on it, with where it sits on screen. */
private data class Selection(
    val text: String,
    val chapter: Int,
    val topY: Float = -1f,
    val botY: Float = -1f,
    val cxD: Float = -1f,
)

/** A frozen shot of the page flying away during a flip. */
private data class FlipAnim(val bitmap: ImageBitmap, val forward: Boolean)

/** Reads an EPUB: the spine in one scroll, with where you stopped kept. */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun ReaderScreen(
    book: Book,
    file: File,
    dark: Boolean,
    initialProgress: Float,
    onProgress: (Float) -> Unit,
    onBack: () -> Unit,
) {
    val pal = palette
    val strings = strings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notes = remember { ReadingNotes(context) }
    val appSettings = remember { Settings(context) }

    var fontScale by remember { mutableFloatStateOf(appSettings.readerFontScale) }
    var paged by remember { mutableStateOf(appSettings.readerMode == "paged") }
    var content by remember { mutableStateOf<EpubReader.Content?>(null) }
    var loading by remember { mutableStateOf(true) }
    var engineReady by remember { mutableStateOf(false) }
    var pageReady by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(initialProgress) }
    var restoreTo by remember { mutableFloatStateOf(initialProgress) }

    var chromeOn by remember { mutableStateOf(true) }
    var info by remember { mutableStateOf(PageInfo(ratio = initialProgress)) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    var selection by remember { mutableStateOf<Selection?>(null) }
    var noteFor by remember { mutableStateOf<Selection?>(null) }
    var marks by remember { mutableStateOf(notes.bookmarks(book.id)) }
    var annos by remember { mutableStateOf(notes.annotations(book.id)) }

    var flip by remember { mutableStateOf<FlipAnim?>(null) }
    val flipAnim = remember { Animatable(0f) }

    val ui = remember { Handler(Looper.getMainLooper()) }
    val sink = remember { Sink() }
    val readerJs = remember {
        runCatching { context.assets.open("reader.js").bufferedReader().use { it.readText() } }
            .getOrDefault("")
    }

    val webView = remember {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
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

    fun capturePage(): ImageBitmap? = runCatching {
        val b = android.graphics.Bitmap.createBitmap(
            webView.width, webView.height, android.graphics.Bitmap.Config.ARGB_8888,
        )
        webView.draw(android.graphics.Canvas(b))
        b.asImageBitmap()
    }.getOrNull()

    /** Freezes the current page, turns the engine, then flies the shot off like a leaf. */
    fun flipPage(forward: Boolean) {
        if (flip != null || info.pages <= 0) return
        val bmp = capturePage() ?: return
        flip = FlipAnim(bmp, forward)
        runJs("window.__pl.${if (forward) "next" else "prev"}()")
    }

    LaunchedEffect(flip) {
        val f = flip ?: return@LaunchedEffect
        delay(90) // let the engine land on the new page and paint it
        flipAnim.snapTo(0f)
        flipAnim.animateTo(1f, tween(durationMillis = 300, easing = FastOutSlowInEasing))
        flip = null
    }

    fun onBridge(type: String, data: String) {
        val obj = runCatching { JSONObject(data) }.getOrNull() ?: return
        val d = context.resources.displayMetrics.density
        when (type) {
            "tap" -> {
                if (flip != null) return
                // The engine reports CSS pixels; width is in device pixels.
                val x = obj.optDouble("x", 0.0).toFloat() * d
                if (paged && info.pages > 0) {
                    when {
                        x < webView.width * 0.32f -> flipPage(false)
                        x > webView.width * 0.68f -> flipPage(true)
                        else -> chromeOn = !chromeOn
                    }
                } else {
                    chromeOn = !chromeOn
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

    LaunchedEffect(book.id) {
        loading = true
        val prepared = withContext(Dispatchers.IO) {
            EpubReader.prepare(epub = file, cacheRoot = File(context.cacheDir, "reader"), dark = dark)
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
        runJs("window.__pl.setTheme($dark)")
        runJs("window.__pl.setFontScale($fontScale)")
        runJs("window.__pl.setMode('${if (paged) "paged" else "scroll"}')")
        annos.forEach { a ->
            runJs(
                "window.__pl.highlightByText(" +
                    "'${a.id}', ${JSONObject.quote(a.quote)}, ${a.chapter})",
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
        if (engineReady) runJs("window.__pl.setTheme($dark)")
    }

    // Keep the place even if the reader is closed by the system.
    DisposableEffect(book.id) {
        onDispose {
            onProgress(progress.coerceIn(0f, 1f))
            webView.destroy()
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

    Box(
        Modifier
            .fillMaxSize()
            .background(if (dark) pal.bgApp else pal.surface),
    ) {
        Column(Modifier.fillMaxSize()) {
            // ---- top chrome ------------------------------------------------
            AnimatedVisibility(visible = chromeOn) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (dark) pal.bgApp else pal.surface)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
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
                            tint = pal.icon,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = book.title,
                            style = Type.label,
                            color = pal.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = Type.numeric,
                            color = pal.textTertiary,
                        )
                    }
                }
            }

            // ---- the page ----------------------------------------------------
            Box(Modifier.weight(1f).clipToBounds()) {
                AndroidView(
                    factory = { webView },
                    modifier = Modifier.fillMaxSize(),
                )

                // The previous page flying away while a flip plays out.
                flip?.let { f ->
                    val dir = if (f.forward) -1f else 1f
                    Box(
                        Modifier
                            .fillMaxSize()
                            .zIndex(1f)
                            .graphicsLayer { translationX = dir * flipAnim.value * webView.width },
                    ) {
                        Row(Modifier.fillMaxSize()) {
                            if (!f.forward) {
                                Box(
                                    Modifier
                                        .width(22.dp)
                                        .fillMaxHeight()
                                        .background(
                                            Brush.horizontalGradient(
                                                0f to Color.Black.copy(alpha = 0.30f),
                                                1f to Color.Transparent,
                                            ),
                                        ),
                                )
                            }
                            Image(
                                bitmap = f.bitmap,
                                contentDescription = null,
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                            if (f.forward) {
                                Box(
                                    Modifier
                                        .width(22.dp)
                                        .fillMaxHeight()
                                        .background(
                                            Brush.horizontalGradient(
                                                0f to Color.Transparent,
                                                1f to Color.Black.copy(alpha = 0.30f),
                                            ),
                                        ),
                                )
                            }
                        }
                    }
                }

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
                    val pillW = 118.dp
                    val x = (xC - pillW / 2).coerceIn(8.dp, config.screenWidthDp.dp - pillW - 8.dp)
                    val placeAbove = yTop >= 96.dp
                    val y = if (placeAbove) yTop - 52.dp else yBot + 10.dp
                    Row(
                        Modifier
                            .offset(x = x, y = y)
                            .zIndex(3f)
                            .shadow(10.dp, RoundedCornerShape(12.dp))
                            .background(Color(0xE625221F), RoundedCornerShape(12.dp))
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PillIcon(R.drawable.ic_r_note, strings.makeNote, Color(0xFFEDE7DD)) {
                            if (engineReady) noteFor = sel
                        }
                        PillIcon(R.drawable.ic_r_copy, strings.copyText, Color(0xFFEDE7DD)) {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("quote", sel.text))
                            Toast.makeText(context, strings.copied, Toast.LENGTH_SHORT).show()
                            clearSelection()
                        }
                        PillIcon(R.drawable.ic_r_close, strings.deselect, Color(0xFF9C968D)) {
                            clearSelection()
                        }
                    }
                }
            }

            // ---- progress line -----------------------------------------------
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(pal.borderSubtle),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(2.dp)
                        .background(pal.accent),
                )
            }

            // ---- bottom chrome: info line + labeled nav row -------------------
            AnimatedVisibility(visible = chromeOn) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(if (dark) pal.bgApp else pal.surface),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (info.mode == "paged" && info.pages > 0) {
                                strings.pagePos(info.page, info.pages)
                            } else {
                                strings.chapterPos(info.chapter + 1, toc.size) + "${(info.ratio * 100).toInt()}%"
                            },
                            style = Type.numeric,
                            color = pal.textTertiary,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = Type.numeric,
                            color = pal.textTertiary,
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .padding(bottom = navBottom + 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NavItem(
                            R.drawable.ic_r_toc,
                            strings.readerToc,
                            panel == Panel.Toc,
                            modifier = Modifier.weight(1f),
                        ) {
                            panel = Panel.Toc
                        }
                        NavItem(
                            R.drawable.ic_r_bookmark,
                            strings.readerBookmarks,
                            false,
                            modifier = Modifier.weight(1f),
                        ) {
                            val title = toc.firstOrNull { it.index == info.chapter }?.title.orEmpty()
                            val label = buildString {
                                if (title.isNotBlank()) append(title.take(16))
                                if (isNotEmpty()) append(" · ")
                                append("${(info.ratio * 100).toInt()}%")
                            }
                            notes.addBookmark(book.id, info.ratio, info.chapter, label)
                            marks = notes.bookmarks(book.id)
                            Toast.makeText(context, strings.bookmarkAdded, Toast.LENGTH_SHORT).show()
                        }
                        NavItem(
                            R.drawable.ic_r_annotate,
                            strings.readerAnnotations,
                            panel == Panel.Annos,
                            modifier = Modifier.weight(1f),
                        ) {
                            panel = Panel.Annos
                        }
                        NavItem(
                            R.drawable.ic_r_look,
                            strings.readerLook,
                            panel == Panel.Look,
                            modifier = Modifier.weight(1f),
                        ) {
                            panel = Panel.Look
                        }
                    }
                }
            }
        }
    }

    // ---- panels --------------------------------------------------------------
    panel?.let { which ->
        ModalBottomSheet(
            onDismissRequest = { panel = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = pal.surface,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = navBottom + 16.dp),
            ) {
                when (which) {
                    Panel.Toc -> TocMarksPanel(
                        toc = toc,
                        marks = marks,
                        current = info.chapter,
                        onJumpChapter = { i ->
                            runJs("window.__pl.jumpChapter($i)")
                            panel = null
                        },
                        onJumpBookmark = { b ->
                            runJs("window.__pl.jumpRatio(${b.progress})")
                            panel = null
                        },
                        onDeleteBookmark = { b ->
                            notes.removeBookmark(b.id)
                            marks = notes.bookmarks(book.id)
                        },
                    )
                    Panel.Annos -> AnnotationPanel(
                        annos = annos,
                        onJump = { a ->
                            runJs("window.__pl.jumpRatio(${a.progress})")
                            panel = null
                        },
                        onDelete = { a ->
                            notes.removeAnnotation(a.id)
                            annos = notes.annotations(book.id)
                            runJs("window.__pl.removeHighlight('${a.id}')")
                        },
                        onExport = { exportLauncher.launch("《${book.title}》批注.txt") },
                    )
                    Panel.Look -> LookPanel(
                        fontScale = fontScale,
                        paged = paged,
                        onFont = { fontScale = it },
                        onMode = { paged = it },
                    )
                }
            }
        }
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

/** One labeled destination in the bottom chrome: icon on top, word below. */
@Composable
private fun NavItem(
    iconRes: Int,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val pal = palette
    Column(
        modifier
            .clip(RoundedCornerShape(Radius.md.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = label,
            tint = if (active) pal.accent else pal.icon,
            modifier = Modifier.size(21.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = Type.numeric,
            color = if (active) pal.accent else pal.textTertiary,
        )
    }
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

@Composable
private fun TocMarksPanel(
    toc: List<EpubReader.TocItem>,
    marks: List<Bookmark>,
    current: Int,
    onJumpChapter: (Int) -> Unit,
    onJumpBookmark: (Bookmark) -> Unit,
    onDeleteBookmark: (Bookmark) -> Unit,
) {
    val pal = palette
    val strings = strings
    var tab by remember { mutableStateOf(0) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text(strings.readerToc) })
        FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text(strings.readerBookmarks) })
    }
    Spacer(Modifier.height(12.dp))
    if (tab == 0) {
        if (toc.isEmpty()) {
            Text(strings.noToc, style = Type.small, color = pal.textTertiary)
            return
        }
        LazyColumn(Modifier.height(420.dp)) {
            items(toc, key = { it.index }) { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.md.dp))
                        .clickable { onJumpChapter(item.index) }
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.title,
                        style = Type.body,
                        color = if (item.index == current) pal.accent else pal.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    } else {
        if (marks.isEmpty()) {
            Text(strings.noBookmarks, style = Type.small, color = pal.textTertiary)
            return
        }
        LazyColumn(Modifier.height(420.dp)) {
            items(marks, key = { it.id }) { b ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.md.dp))
                        .clickable { onJumpBookmark(b) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_r_bookmark),
                        null,
                        tint = pal.accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = b.label.ifBlank { "${(b.progress * 100).toInt()}%" },
                            style = Type.body,
                            color = pal.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = formatTime(b.createdAt),
                            style = Type.numeric,
                            color = pal.textTertiary,
                        )
                    }
                    Icon(
                        painterResource(R.drawable.ic_r_delete),
                        strings.delete,
                        tint = pal.textTertiary,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .clickable { onDeleteBookmark(b) }
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AnnotationPanel(
    annos: List<Annotation>,
    onJump: (Annotation) -> Unit,
    onDelete: (Annotation) -> Unit,
    onExport: () -> Unit,
) {
    val pal = palette
    val strings = strings
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(strings.annotationCount(annos.size), style = Type.title, color = pal.text, modifier = Modifier.weight(1f))
        Text(
            text = strings.exportNotes,
            style = Type.small,
            color = if (annos.isEmpty()) pal.textTertiary else pal.accent,
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.md.dp))
                .clickable(enabled = annos.isNotEmpty(), onClick = onExport)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
    Spacer(Modifier.height(10.dp))
    if (annos.isEmpty()) {
        Text(strings.noAnnotations, style = Type.small, color = pal.textTertiary)
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
                    color = pal.text,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.sm.dp))
                        .background(pal.accentSubtle)
                        .padding(8.dp),
                )
                if (a.note.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = a.note,
                        style = Type.small,
                        color = pal.textSecondary,
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
                        color = pal.textTertiary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        painterResource(R.drawable.ic_r_delete),
                        strings.delete,
                        tint = pal.textTertiary,
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

@Composable
private fun LookPanel(fontScale: Float, paged: Boolean, onFont: (Float) -> Unit, onMode: (Boolean) -> Unit) {
    val pal = palette
    val strings = strings

    // ---- section 1: text size -------------------------------------------------
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(R.drawable.ic_r_look),
            null,
            tint = pal.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(strings.fontSize, style = Type.title, color = pal.text)
        Spacer(Modifier.weight(1f))
        Text(
            text = "${(fontScale * 100).toInt()}%",
            style = Type.numeric,
            color = pal.textTertiary,
        )
    }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("A", style = Type.small, color = pal.textSecondary)
        Slider(
            value = fontScale,
            onValueChange = { onFont(Math.round(it * 20f) / 20f) },
            valueRange = 0.8f..1.6f,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        )
        Text("A", style = Type.title, color = pal.textSecondary)
    }

    Spacer(Modifier.height(18.dp))
    HorizontalDivider(color = pal.borderSubtle)
    Spacer(Modifier.height(18.dp))

    // ---- section 2: page mode, as two big cards --------------------------------
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(R.drawable.ic_r_paged),
            null,
            tint = pal.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(strings.pageMode, style = Type.title, color = pal.text)
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ModeCard(
            selected = !paged,
            iconRes = R.drawable.ic_r_scroll,
            title = strings.modeScroll,
            desc = strings.modeScrollDesc,
            modifier = Modifier.weight(1f),
        ) { onMode(false) }
        ModeCard(
            selected = paged,
            iconRes = R.drawable.ic_r_paged,
            title = strings.modePaged,
            desc = strings.modePagedDesc,
            modifier = Modifier.weight(1f),
        ) { onMode(true) }
    }
}

/** A large, unmistakable choice card for the page-turning mode. */
@Composable
private fun ModeCard(
    selected: Boolean,
    iconRes: Int,
    title: String,
    desc: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val pal = palette
    Column(
        modifier
            .clip(RoundedCornerShape(Radius.md.dp))
            .background(if (selected) pal.accentSubtle else pal.surfaceRaised)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) pal.accent else pal.borderSubtle,
                shape = RoundedCornerShape(Radius.md.dp),
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(iconRes),
                null,
                tint = if (selected) pal.accent else pal.textSecondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.weight(1f))
            if (selected) {
                Icon(
                    painterResource(R.drawable.ic_r_check),
                    null,
                    tint = pal.accent,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(text = title, style = Type.body, color = pal.text)
        Spacer(Modifier.height(2.dp))
        Text(
            text = desc,
            style = Type.numeric,
            color = pal.textTertiary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatTime(t: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(t))

/** A plain text sheet of every annotation on the book, for sharing out. */
private fun exportText(
    book: Book,
    toc: List<EpubReader.TocItem>,
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

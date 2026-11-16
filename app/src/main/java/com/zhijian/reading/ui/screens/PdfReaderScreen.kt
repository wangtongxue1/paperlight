package com.zhijian.reading.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhijian.reading.R
import com.zhijian.reading.data.Book
import com.zhijian.reading.ui.strings
import com.zhijian.reading.ui.theme.Radius
import com.zhijian.reading.ui.theme.Type
import com.zhijian.reading.ui.theme.palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * PDFs are fixed-layout: pages render at the screen's pixel width through
 * PdfRenderer and read as one continuous column, with the same quiet
 * chrome as the flowable reader — tap the sides to turn a page, drag the
 * line to scrub. A small LRU holds rendered pages; far pages re-render on
 * demand, so a 1000-page document costs only a few bitmaps at a time.
 */
private class PdfEngine(file: File) {
    val renderer: PdfRenderer? = runCatching {
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
    }.getOrNull()

    private val lock = Any()

    /** Rendered pages, bounded; far pages are cheap to re-render. */
    val cache = object : android.util.LruCache<Int, Bitmap>(96 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
    }

    val pageCount: Int get() = renderer?.pageCount ?: 0

    /** Each page's aspect in [width, height] points, for row heights. */
    fun measure(): List<Pair<Int, Int>> {
        val r = renderer ?: return emptyList()
        return synchronized(lock) {
            List(r.pageCount) { i ->
                r.openPage(i).use { p -> p.width to p.height }
            }
        }
    }

    fun get(index: Int): Bitmap? = cache.get(index)

    fun render(index: Int, widthPx: Int): Bitmap? {
        cache.get(index)?.let { return it }
        val r = renderer ?: return null
        return synchronized(lock) {
            cache.get(index)?.let { return it }
            runCatching {
                r.openPage(index).use { page ->
                    val w = widthPx.coerceIn(360, 1440)
                    val h = Math.max(1, (w.toLong() * page.height / page.width).toInt())
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    cache.put(index, bmp)
                    bmp
                }
            }.getOrNull()
        }
    }

    fun close() {
        runCatching { renderer?.close() }
    }
}

@Composable
fun PdfReaderScreen(
    book: Book,
    file: File,
    dark: Boolean,
    initialProgress: Float,
    onProgress: (Float) -> Unit,
    onReadingSeconds: (Int) -> Unit = {},
    onBack: () -> Unit,
) {
    val pal = palette
    val strings = strings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var chromeOn by remember { mutableStateOf(true) }
    var progress by remember { mutableStateOf(initialProgress) }
    var pages by remember { mutableStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var sizes by remember { mutableStateOf(listOf<Pair<Int, Int>>()) }
    var seek by remember { mutableStateOf<Float?>(null) }

    val engine = remember(file.path) { PdfEngine(file) }
    val list = rememberLazyListState()
    val screenWidth = context.resources.displayMetrics.widthPixels

    DisposableEffect(file.path) {
        onDispose {
            onProgress(progress.coerceIn(0f, 1f))
            engine.close()
        }
    }

    LaunchedEffect(engine) {
        if (engine.renderer == null) {
            failed = true
            return@LaunchedEffect
        }
        val measured = withContext(Dispatchers.IO) { engine.measure() }
        sizes = measured
        pages = measured.size
        if (initialProgress > 0f && measured.size > 1) {
            list.scrollToItem((initialProgress * (measured.size - 1)).roundToInt().coerceIn(0, measured.size - 1))
        }
    }

    // Keep the shelf's progress fresh while the reader stands open.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            if (pages > 0 && !list.isScrollInProgress) {
                // Same scale jumpRatio uses: page 1 is 0%, the last page is
                // 100%, so resume lands on the very page it left.
                val p = if (pages > 1) list.firstVisibleItemIndex / (pages - 1f) else 0f
                progress = p
                onProgress(p)
            }
        }
    }

    // 读书时长：与流式阅读器同一套口径——只在阅读器前台停留时累计（暂停
    // 即停表），每分钟落一次盘，退出时补记尾巴。返回桌面、锁屏都不计。
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(file.path) {
        var acc = 0
        var resumedAt = android.os.SystemClock.elapsedRealtime()
        var running = true
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    acc += ((android.os.SystemClock.elapsedRealtime() - resumedAt) / 1000).toInt()
                    resumedAt = android.os.SystemClock.elapsedRealtime()
                    running = false
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    resumedAt = android.os.SystemClock.elapsedRealtime()
                    running = true
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        val job = scope.launch {
            while (true) {
                delay(60_000)
                if (!running) continue
                val now = android.os.SystemClock.elapsedRealtime()
                acc += ((now - resumedAt) / 1000).toInt()
                resumedAt = now
                if (acc >= 60) {
                    onReadingSeconds(acc)
                    acc = 0
                }
            }
        }
        onDispose {
            job.cancel()
            if (running) acc += ((android.os.SystemClock.elapsedRealtime() - resumedAt) / 1000).toInt()
            if (acc > 0) onReadingSeconds(acc)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun jump(delta: Int) {
        if (pages <= 0) return
        val target = (list.firstVisibleItemIndex + delta).coerceIn(0, pages - 1)
        scope.launch { list.animateScrollToItem(target) }
    }

    fun jumpRatio(r: Float) {
        if (pages <= 0) return
        scope.launch { list.scrollToItem((r * (pages - 1)).toInt().coerceIn(0, pages - 1)) }
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val shown = (seek ?: progress).coerceIn(0f, 1f)
    val chromeBg = if (dark) pal.bgApp else pal.surface
    val pageBg = if (dark) Color(0xFF17181A) else Color(0xFFFBF9F4)

    Box(
        Modifier
            .fillMaxSize()
            .background(pageBg),
    ) {
        when {
            failed -> Text(
                text = strings.readerFailed,
                style = Type.small,
                color = pal.textTertiary,
                modifier = Modifier.align(Alignment.Center),
            )
            pages == 0 -> Text(
                text = "…",
                style = Type.small,
                color = pal.textTertiary,
                modifier = Modifier.align(Alignment.Center),
            )
            else -> LazyColumn(
                state = list,
                // The same frame the flowable reader keeps its text in: the
                // pages flow between the two bars instead of running to the
                // screen edges, in either chrome state.
                contentPadding = PaddingValues(top = 61.dp, bottom = 57.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { p ->
                            when {
                                p.x < size.width * 0.3f -> jump(-1)
                                p.x > size.width * 0.7f -> jump(1)
                                else -> chromeOn = !chromeOn
                            }
                        }
                    },
            ) {
                items(pages) { idx ->
                    PdfPage(
                        index = idx,
                        sizes = sizes,
                        engine = engine,
                        screenWidth = screenWidth,
                    )
                }
            }
        }

        // ---- top chrome ----
        Box(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            AnimatedVisibility(visible = chromeOn) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(chromeBg)
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
                            text = if (pages > 0) strings.pdfPageOf(list.firstVisibleItemIndex + 1, pages) else "PDF",
                            style = Type.numeric,
                            color = pal.textTertiary,
                        )
                    }
                }
            }
        }

        // ---- bottom chrome: seek line + label, or a quiet footer ----
        Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
            if (chromeOn && pages > 0) {
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .background(chromeBg)
                        .padding(start = 10.dp, end = 10.dp, top = 10.dp)
                        .height(28.dp)
                        .pointerInput(pages) {
                            detectTapGestures { p ->
                                jumpRatio((p.x / size.width.toFloat()).coerceIn(0f, 1f))
                            }
                        }
                        .pointerInput(pages) {
                            detectHorizontalDragGestures(
                                onDragStart = { p -> seek = (p.x / size.width.toFloat()).coerceIn(0f, 1f) },
                                onDragEnd = {
                                    seek?.let { jumpRatio(it) }
                                    seek = null
                                },
                                onDragCancel = { seek = null },
                            ) { change, _ ->
                                seek = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                change.consume()
                            }
                        },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(pal.borderSubtle),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(shown)
                            .height(3.dp)
                            .background(pal.accent),
                    )
                    if (seek != null) {
                        Box(
                            Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = (maxWidth * shown - 7.dp).coerceAtLeast(0.dp))
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(pal.accent),
                        )
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(chromeBg)
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp + navBottom),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = strings.pdfPageOf(list.firstVisibleItemIndex + 1, pages),
                        style = Type.numeric,
                        color = pal.textTertiary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${(shown * 100).toInt()}%",
                        style = Type.numeric,
                        color = pal.textTertiary,
                    )
                }
            } else if (pages > 0) {
                // Quiet footer, Apple Books style, riding the page margin —
                // bottom-right, where the flowable reader parks its mark.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 22.dp, end = 28.dp, bottom = navBottom + 10.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        text = strings.pdfPageOf(list.firstVisibleItemIndex + 1, pages),
                        style = Type.numeric.copy(fontSize = 11.sp),
                        color = if (dark) Color(0xFF6E6C66) else Color(0xFF9C968D),
                    )
                }
            }
        }
    }
}

/** One rendered page row: reserves its height, then draws the bitmap. */
@Composable
private fun PdfPage(
    index: Int,
    sizes: List<Pair<Int, Int>>,
    engine: PdfEngine,
    screenWidth: Int,
) {
    val size = sizes.getOrNull(index)
    val aspect = if (size != null && size.first > 0) size.second.toFloat() / size.first else 1.5f
    var bmp by remember(index) { mutableStateOf(engine.get(index)) }
    // Reserve the height the rendered bitmap will actually occupy at the
    // screen's width — a hardcoded 400dp guess leaves a visible gap (or an
    // overlap) under every page on any screen that isn't exactly 400dp wide.

    LaunchedEffect(index, size) {
        if (bmp == null && size != null) {
            val rendered = withContext(Dispatchers.IO) { engine.render(index, screenWidth) }
            if (rendered != null) bmp = rendered
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(LocalConfiguration.current.screenWidthDp.dp * aspect),
        contentAlignment = Alignment.Center,
    ) {
        bmp?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

package com.zhijian.reading.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.TextDecrease
import androidx.compose.material.icons.outlined.TextIncrease
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.zhijian.reading.data.Book
import com.zhijian.reading.reader.EpubReader
import com.zhijian.reading.ui.strings
import com.zhijian.reading.ui.theme.Radius
import com.zhijian.reading.ui.theme.Type
import com.zhijian.reading.ui.theme.palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/** Reads an EPUB: the spine in one scroll, with where you stopped kept. */
@SuppressLint("SetJavaScriptEnabled")
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
    val context = LocalContext.current
    var fontScale by remember { mutableFloatStateOf(1f) }
    var content by remember { mutableStateOf<EpubReader.Content?>(null) }
    var loading by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(initialProgress) }
    var restoreTo by remember { mutableFloatStateOf(initialProgress) }

    val webView = remember {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            settings.apply {
                javaScriptEnabled = false
                allowFileAccess = true
                allowContentAccess = false
                builtInZoomControls = false
                displayZoomControls = false
                textZoom = 100
            }
            isVerticalScrollBarEnabled = false
            webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val v = view ?: return
                    val range = (v.contentHeight * v.scale).toInt() - v.height
                    if (range > 0 && restoreTo > 0f) v.scrollTo(0, (restoreTo * range).toInt())
                    loading = false
                }
            }
            setOnScrollChangeListener { v, _, _, _, _ ->
                val wv = v as? WebView ?: return@setOnScrollChangeListener
                val range = (wv.contentHeight * wv.scale).toInt() - wv.height
                if (range > 0) progress = (wv.scrollY.toFloat() / range).coerceIn(0f, 1f)
            }
        }
    }

    LaunchedEffect(book.id, fontScale, dark) {
        loading = true
        val prepared = withContext(Dispatchers.IO) {
            EpubReader.prepare(
                epub = file,
                cacheRoot = File(context.cacheDir, "reader"),
                dark = dark,
                fontScale = fontScale,
            )
        }
        content = prepared
        if (prepared != null) {
            webView.loadDataWithBaseURL(prepared.baseUrl, prepared.html, "text/html", "UTF-8", null)
        } else {
            loading = false
        }
    }

    // Keep the place even if the reader is closed by the system.
    DisposableEffect(book.id) {
        onDispose {
            val range = (webView.contentHeight * webView.scale).toInt() - webView.height
            val p = if (range > 0) webView.scrollY.toFloat() / range else progress
            onProgress(p.coerceIn(0f, 1f))
            webView.destroy()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(4000)
            onProgress(progress)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(if (dark) pal.bgApp else pal.surface),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .zIndex(2f)
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
                Icon(Icons.Outlined.ArrowBack, "Back", tint = pal.icon, modifier = Modifier.size(21.dp))
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
                    text = "${(progress * 100).toInt()}% · ${
                        book.formats.firstOrNull { it.format.equals("EPUB", true) }?.format ?: "EPUB"
                    }",
                    style = Type.numeric,
                    color = pal.textTertiary,
                )
            }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(Radius.md.dp))
                    .clickable {
                        restoreTo = progress
                        fontScale = (fontScale - 0.12f).coerceAtLeast(0.8f)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.TextDecrease, strings.smallerText, tint = pal.icon, modifier = Modifier.size(20.dp))
            }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(Radius.md.dp))
                    .clickable {
                        restoreTo = progress
                        fontScale = (fontScale + 0.12f).coerceAtMost(1.6f)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.TextIncrease, strings.largerText, tint = pal.icon, modifier = Modifier.size(20.dp))
            }
        }

        Box(Modifier.weight(1f).clipToBounds()) {
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
        }

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
    }
}

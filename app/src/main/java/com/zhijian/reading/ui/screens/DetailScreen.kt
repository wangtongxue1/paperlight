package com.zhijian.reading.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhijian.reading.data.Book
import com.zhijian.reading.data.humanSize
import com.zhijian.reading.data.trimIndex
import com.zhijian.reading.ui.components.BookCover
import com.zhijian.reading.ui.components.FormatBadge
import com.zhijian.reading.ui.components.Hairline
import com.zhijian.reading.ui.components.InfoRow
import com.zhijian.reading.ui.components.Overline
import com.zhijian.reading.ui.components.TagPill
import com.zhijian.reading.ui.strings
import com.zhijian.reading.ui.theme.Radius
import com.zhijian.reading.ui.theme.Type
import com.zhijian.reading.ui.theme.palette
import java.io.File

/** The book's page: its jacket, what is known about it, and its files. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    book: Book,
    root: File,
    progress: Float,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPickCover: () -> Unit,
    onAuthor: (String) -> Unit,
    onSeries: (String) -> Unit,
    onTag: (String) -> Unit,
) {
    val pal = palette
    val strings = strings
    var menu by remember { mutableStateOf(false) }
    var openMenu by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(pal.bgApp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
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
                    androidx.compose.ui.res.painterResource(com.zhijian.reading.R.drawable.ic_r_back),
                    "Back", tint = pal.icon, modifier = Modifier.size(21.dp),
                )
            }
            Text(
                text = strings.navLibrary,
                style = Type.small,
                color = pal.textTertiary,
                modifier = Modifier.weight(1f),
            )
            Box {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(Radius.md.dp))
                        .clickable { menu = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        androidx.compose.ui.res.painterResource(com.zhijian.reading.R.drawable.ic_a_dots),
                        "More", tint = pal.icon, modifier = Modifier.size(21.dp),
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { DetailMenuRow(strings.editMetadata, com.zhijian.reading.R.drawable.ic_a_edit) },
                        onClick = { menu = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { DetailMenuRow(strings.changeCover, com.zhijian.reading.R.drawable.ic_a_image) },
                        onClick = { menu = false; onPickCover() },
                    )
                    DropdownMenuItem(
                        text = { DetailMenuRow(strings.remove, com.zhijian.reading.R.drawable.ic_r_delete, danger = true) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            // The jacket, centered, on the page itself — no stage card.
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                BookCover(book, root, width = 148.dp, height = 222.dp)
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = book.title,
                style = Type.title.copy(fontSize = 21.sp, lineHeight = 27.sp),
                color = pal.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (book.authorLine.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    book.authors.forEachIndexed { i, name ->
                        Text(
                            text = name,
                            style = Type.body.copy(fontSize = 14.sp),
                            color = pal.textAccent,
                            modifier = Modifier.clickable { onAuthor(name) },
                        )
                        if (i < book.authors.lastIndex) {
                            Text("  ·  ", style = Type.body, color = pal.textTertiary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // Read: one file straight away, several from a menu.
            if (book.formats.isNotEmpty()) {
                Box {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(Radius.pill.dp))
                            .background(pal.accent)
                            .clickable {
                                if (book.formats.size > 1) openMenu = true
                                else onOpen(book.formats.first().format)
                            },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            androidx.compose.ui.res.painterResource(com.zhijian.reading.R.drawable.ic_a_open), null,
                            tint = pal.textOnAccent, modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            strings.read,
                            style = Type.body.copy(fontWeight = FontWeight.Medium),
                            color = pal.textOnAccent,
                        )
                    }
                    DropdownMenu(expanded = openMenu, onDismissRequest = { openMenu = false }) {
                        book.formats.forEach { f ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        FormatBadge(f.format)
                                        Spacer(Modifier.width(10.dp))
                                        Text(f.format, style = Type.body, color = pal.text)
                                        Spacer(Modifier.weight(1f))
                                        Text(humanSize(f.size), style = Type.numeric, color = pal.textTertiary)
                                    }
                                },
                                onClick = { openMenu = false; onOpen(f.format) },
                            )
                        }
                    }
                }
                if (progress > 0f) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(3.dp)
                                .background(pal.subtle, RoundedCornerShape(Radius.pill.dp)),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                                    .height(3.dp)
                                    .background(pal.accent, RoundedCornerShape(Radius.pill.dp)),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            strings.progressOf.format((progress * 100).toInt()),
                            style = Type.numeric,
                            color = pal.textTertiary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Hairline()
            Spacer(Modifier.height(18.dp))

            // Details.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Overline(strings.details, Modifier.weight(1f))
                Text(
                    strings.edit,
                    style = Type.smallStrong,
                    color = pal.textAccent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.xs.dp))
                        .clickable(onClick = onEdit)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!book.series.isNullOrBlank()) {
                    InfoRow(strings.series, "${book.series} · ${trimIndex(book.seriesIndex)}") {
                        onSeries(book.series!!)
                    }
                }
                // Only the rows the file actually carries: placeholder-free.
                if (!book.publisher.isNullOrBlank()) InfoRow(strings.publisher, book.publisher)
                if (book.pubdate.isNotBlank() && book.pubdate != "2000-01-01") {
                    InfoRow(strings.published, book.pubdate, mono = true)
                }
                if (book.languages.isNotEmpty()) {
                    InfoRow(
                        strings.language,
                        book.languages.joinToString(", ") { languageName(strings, it) },
                    )
                }
                book.identifiers.forEach { (k, v) ->
                    InfoRow(
                        if (k.equals("isbn", true)) strings.isbn else k.replaceFirstChar { it.uppercase() },
                        v, mono = true,
                    )
                }
                InfoRow(strings.added, javaDate(book.timestamp), mono = true)
            }

            if (book.tags.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    book.tags.forEach { tag -> TagPill(tag, onClick = { onTag(tag) }) }
                }
            }

            Spacer(Modifier.height(20.dp))
            Hairline()
            Spacer(Modifier.height(18.dp))

            // Formats on disk.
            Overline(strings.formats)
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                book.formats.forEach { f ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(Radius.sm.dp))
                            .background(pal.surface)
                            .clickable { onOpen(f.format) }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FormatBadge(f.format)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = f.fileName,
                            style = Type.small,
                            color = pal.textSecondary,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        Text(humanSize(f.size), style = Type.numeric, color = pal.textTertiary)
                    }
                }
                Row(
                    Modifier
                        .wrapContentWidth()
                        .clip(RoundedCornerShape(Radius.sm.dp))
                        .clickable { onEdit() }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        androidx.compose.ui.res.painterResource(com.zhijian.reading.R.drawable.ic_a_add),
                        null, tint = pal.textAccent, modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(strings.editMetadata, style = Type.smallStrong, color = pal.textAccent)
                }
            }

            if (book.comments.isNotBlank()) {
                Spacer(Modifier.height(20.dp))
                Hairline()
                Spacer(Modifier.height(18.dp))
                Overline(strings.description)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = htmlToText(book.comments),
                    style = Type.body.copy(fontSize = 13.sp, lineHeight = 21.sp),
                    color = pal.textSecondary,
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun DetailMenuRow(label: String, iconRes: Int, danger: Boolean = false) {
    val pal = palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            androidx.compose.ui.res.painterResource(iconRes), null,
            tint = if (danger) pal.danger else pal.icon,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = Type.body, color = if (danger) pal.danger else pal.text)
    }
}

fun languageName(strings: com.zhijian.reading.ui.AppStrings, code: String): String =
    when (code.lowercase()) {
        "zh" -> if (strings === com.zhijian.reading.ui.StringsZh) "中文" else "Chinese"
        "en" -> if (strings === com.zhijian.reading.ui.StringsZh) "英语" else "English"
        "fr" -> if (strings === com.zhijian.reading.ui.StringsZh) "法语" else "French"
        "de" -> if (strings === com.zhijian.reading.ui.StringsZh) "德语" else "German"
        "ja" -> if (strings === com.zhijian.reading.ui.StringsZh) "日语" else "Japanese"
        "es" -> if (strings === com.zhijian.reading.ui.StringsZh) "西班牙语" else "Spanish"
        "ru" -> if (strings === com.zhijian.reading.ui.StringsZh) "俄语" else "Russian"
        "it" -> if (strings === com.zhijian.reading.ui.StringsZh) "意大利语" else "Italian"
        "pt" -> if (strings === com.zhijian.reading.ui.StringsZh) "葡萄牙语" else "Portuguese"
        "ko" -> if (strings === com.zhijian.reading.ui.StringsZh) "韩语" else "Korean"
        else -> code.ifBlank { "—" }
    }

fun javaDate(epochMillis: Long): String {
    if (epochMillis <= 0) return "—"
    return java.time.Instant.ofEpochMilli(epochMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}

private val blockTags = Regex("(?i)</?(p|div|br|li|h[1-6]|blockquote)[^>]*>")
private val anyTag = Regex("<[^>]*>")

fun htmlToText(s: String): String {
    var out = s.replace(blockTags, "\n").replace(anyTag, "")
    out = android.text.Html.fromHtml(out.replace("\n", "<br>"), android.text.Html.FROM_HTML_MODE_LEGACY).toString()
    return out.replace(Regex("\n{3,}"), "\n\n").trim()
}

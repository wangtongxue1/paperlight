package com.zhijian.reading.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhijian.reading.data.Book
import com.zhijian.reading.data.humanSize
import com.zhijian.reading.ui.theme.Radius
import com.zhijian.reading.ui.theme.Type
import com.zhijian.reading.ui.theme.palette
import java.io.File

/** A book as a row of the list view: jacket, title, the rest of it small. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookRow(
    book: Book,
    root: File,
    selected: Boolean = false,
    progress: Float = 0f,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val pal = palette
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                when {
                    selectionMode && isSelected -> pal.selected
                    selected -> pal.selected
                    else -> androidx.compose.ui.graphics.Color.Transparent
                },
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(book, root, width = 42.dp, height = 62.dp, shadow = false)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = Type.body.copy(fontSize = 14.sp, lineHeight = 19.sp),
                color = pal.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = listOfNotNull(
                        book.authors.firstOrNull()?.takeIf { it.isNotBlank() },
                        book.series?.let { "$it ${com.zhijian.reading.data.trimIndex(book.seriesIndex)}" },
                    ).joinToString(" · "),
                    style = Type.small,
                    color = pal.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (book.rating > 0) {
                    Spacer(Modifier.width(8.dp))
                    Stars(rating = book.rating, starSize = 8.dp)
                }
            }
            if (progress > 0f) {
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(pal.subtle, RoundedCornerShape(Radius.pill.dp)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(2.dp)
                            .background(pal.accent, RoundedCornerShape(Radius.pill.dp)),
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        if (selectionMode) {
            // The round pick badge replaces the format column while selecting.
            Box(
                Modifier
                    .size(22.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (isSelected) pal.accent else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.88f))
                    .border(
                        1.5.dp,
                        if (isSelected) androidx.compose.ui.graphics.Color.Transparent else pal.textTertiary,
                        androidx.compose.foundation.shape.CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    androidx.compose.material3.Icon(
                        androidx.compose.ui.res.painterResource(com.zhijian.reading.R.drawable.ic_r_check),
                        null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        } else {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                book.formats.take(2).forEach { FormatBadge(it.format) }
            }
            if (book.size > 0) {
                Text(
                    text = humanSize(book.size),
                    style = Type.numeric,
                    color = pal.textTertiary,
                )
            }
        }
        }
    }
}

/** A counted thing in the sidebar: an author, a series, a tag, a publisher. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ValueRow(name: String, count: Int, onClick: () -> Unit) {
    val pal = palette
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = Type.body,
            color = pal.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(text = count.toString(), style = Type.numeric, color = pal.textTertiary)
    }
}

@Composable
fun EmptyState(title: String, body: String?, icon: @Composable (() -> Unit)? = null) {
    val pal = palette
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(pal.subtle, RoundedCornerShape(Radius.pill.dp)),
                contentAlignment = Alignment.Center,
            ) { icon() }
            Spacer(Modifier.height(16.dp))
        }
        Text(text = title, style = Type.title.copy(fontSize = 16.sp), color = pal.text)
        if (body != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                style = Type.body.copy(fontSize = 13.sp),
                color = pal.textSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

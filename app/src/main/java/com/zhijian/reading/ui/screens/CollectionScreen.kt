package com.zhijian.reading.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhijian.reading.R
import com.zhijian.reading.data.Book
import com.zhijian.reading.ui.LibraryViewModel
import com.zhijian.reading.ui.components.BookCover
import com.zhijian.reading.ui.components.EmptyState
import com.zhijian.reading.ui.strings
import com.zhijian.reading.ui.theme.Radius
import com.zhijian.reading.ui.theme.Type
import com.zhijian.reading.ui.theme.palette

/** Which shelf a [CollectionScreen] is showing. */
enum class CollectionKind { Reading, Finished }

/**
 * The books that did not fit on the 阅读 page: everything under way, or
 * everything finished. A page of its own rather than a longer row, so the
 * home page keeps its shape however many books are in play.
 *
 * Tapping a book under way continues it; tapping a finished one starts it
 * over from the first page — progress is recalculated, the number of
 * read-throughs is kept.
 */
@Composable
fun CollectionScreen(
    vm: LibraryViewModel,
    kind: CollectionKind,
    onBack: () -> Unit,
    onRead: (Book) -> Unit,
    bottomInset: Dp = 32.dp,
) {
    val pal = palette
    val strings = strings
    val progress = vm.progress
    val lastOpen = vm.lastOpen
    val counts = vm.readCounts

    val books = remember(vm.books, progress, lastOpen, kind) {
        vm.books
            .filter { book ->
                val p = progress[book.id] ?: 0f
                when (kind) {
                    CollectionKind.Reading -> p >= 0.01f && p < LibraryViewModel.FINISHED_AT
                    CollectionKind.Finished -> p >= LibraryViewModel.FINISHED_AT
                }
            }
            .sortedByDescending { lastOpen[it.id] ?: 0L }
    }

    val title = when (kind) {
        CollectionKind.Reading -> strings.titleReading
        CollectionKind.Finished -> strings.finished
    }
    val note = when (kind) {
        CollectionKind.Reading -> strings.readingPageNote
        CollectionKind.Finished -> strings.finishedPageNote
    }

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
                    painterResource(R.drawable.ic_r_back),
                    "Back",
                    tint = pal.icon,
                    modifier = Modifier.size(21.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = Type.display.copy(fontSize = 24.sp), color = pal.text)
                Text(strings.bookCount(books.size), style = Type.small, color = pal.textTertiary)
            }
        }

        if (books.isEmpty()) {
            EmptyState(
                title = when (kind) {
                    CollectionKind.Reading -> strings.nothingInProgress
                    CollectionKind.Finished -> strings.emptyFinished
                },
                body = when (kind) {
                    CollectionKind.Reading -> strings.nothingInProgressBody
                    CollectionKind.Finished -> strings.emptyFinishedBody
                },
            )
            return@Column
        }

        Text(
            note,
            style = Type.small,
            color = pal.textTertiary,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 2.dp),
        )

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = 3
            val colW = maxWidth / columns
            val coverW = (colW * 0.82f).coerceIn(72.dp, 150.dp)
            val coverH = coverW * 1.5f

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 6.dp, bottom = bottomInset),
            ) {
                items(books, key = { it.id }) { book ->
                    CollectionBook(
                        book = book,
                        root = vm.library.root,
                        coverW = coverW,
                        coverH = coverH,
                        caption = when (kind) {
                            CollectionKind.Reading ->
                                strings.progressOf.format(((progress[book.id] ?: 0f) * 100).toInt())
                            CollectionKind.Finished ->
                                strings.readTimes(counts[book.id]?.coerceAtLeast(1) ?: 1)
                        },
                        onClick = { onRead(book) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionBook(
    book: Book,
    root: java.io.File,
    coverW: Dp,
    coverH: Dp,
    caption: String,
    onClick: () -> Unit,
) {
    val pal = palette
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        BookCover(book = book, root = root, width = coverW, height = coverH, shadow = true)
        Spacer(Modifier.height(8.dp))
        Text(
            text = book.title,
            style = Type.label.copy(fontSize = 13.sp, lineHeight = 17.sp),
            color = pal.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(coverW),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = caption,
            style = Type.small,
            color = pal.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(coverW),
        )
    }
}

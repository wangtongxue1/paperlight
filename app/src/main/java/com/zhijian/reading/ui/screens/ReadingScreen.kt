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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhijian.reading.data.Book
import com.zhijian.reading.ui.LibraryViewModel
import com.zhijian.reading.ui.components.BookCover
import com.zhijian.reading.ui.components.EmptyState
import com.zhijian.reading.ui.strings
import com.zhijian.reading.ui.theme.Radius
import com.zhijian.reading.ui.theme.Type
import com.zhijian.reading.ui.theme.palette

/**
 * Reading Now: the book in hand at the top — or the newest arrival when
 * nothing is under way — then the books under way, what came in lately
 * as a cover grid that fills the page, and what is finished. The shape
 * Apple Books opens on.
 */
@Composable
fun ReadingScreen(
    vm: LibraryViewModel,
    onOpenBook: (Book) -> Unit,
    onContinue: (Book) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val pal = palette
    val strings = strings
    val progress = vm.progress
    val inProgress = remember(vm.books, progress) {
        vm.books.filter { (progress[it.id] ?: 0f) in 0.01f..0.98f }
            .sortedByDescending { progress[it.id] ?: 0f }
    }
    val finished = remember(vm.books, progress) {
        vm.books.filter { (progress[it.id] ?: 0f) >= 0.98f }
    }
    // "Recently added" is capped at ten jackets so the grid stays a tidy
    // block instead of running on forever.
    val recent = remember(vm.books) {
        vm.books.sortedByDescending { it.timestamp }
    }
    val hero = inProgress.firstOrNull()
    val featured = hero ?: recent.firstOrNull()
    val others = remember(inProgress, hero) {
        inProgress.filter { it.id != hero?.id }
    }
    val recentRest = remember(recent, featured) {
        recent.filter { it.id != featured?.id }.take(10)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(pal.bgApp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = strings.navReading,
                    style = Type.display.copy(fontSize = 28.sp),
                    color = pal.text,
                )
                Text(
                    text = strings.bookCount(vm.books.size),
                    style = Type.small,
                    color = pal.textTertiary,
                )
            }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(Radius.md.dp))
                    .clickable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Settings, null, tint = pal.icon, modifier = Modifier.size(21.dp))
            }
        }

        if (featured == null) {
            EmptyState(
                title = strings.emptyNoBooks,
                body = strings.emptyNoBooksBody,
            )
            return@Column
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = (maxWidth.value / 118f).toInt().coerceIn(3, 4)
            val gap = 12.dp
            val coverW = ((maxWidth - 40.dp - gap * (columns - 1)) / columns).coerceAtMost(150.dp)
            val coverH = coverW * 1.5f

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                item {
                    if (hero != null) {
                        HeroCard(
                            book = hero,
                            root = vm.library.root,
                            progress = progress[hero.id] ?: 0f,
                            onContinue = { onContinue(hero) },
                            onOpen = { onOpenBook(hero) },
                        )
                    } else {
                        FeaturedCard(
                            book = featured,
                            root = vm.library.root,
                            onRead = { onContinue(featured) },
                            onOpen = { onOpenBook(featured) },
                        )
                    }
                }

                if (others.isNotEmpty()) {
                    item { SectionTitle(strings.titleReading) }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(others, key = { it.id }) { book ->
                                MiniBook(
                                    book = book,
                                    root = vm.library.root,
                                    caption = strings.progressOf.format(((progress[book.id] ?: 0f) * 100).toInt()),
                                    onClick = { onContinue(book) },
                                )
                            }
                        }
                    }
                }

                if (recentRest.isNotEmpty()) {
                    item { SectionTitle(strings.recentlyAdded) }
                    itemsIndexed(
                        recentRest.chunked(columns),
                        key = { _, row -> row.first().id },
                    ) { _, row ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            row.forEach { book ->
                                GridBook(
                                    book = book,
                                    root = vm.library.root,
                                    coverW = coverW,
                                    coverH = coverH,
                                    onClick = { onOpenBook(book) },
                                )
                            }
                            repeat(columns - row.size) {
                                Spacer(Modifier.width(coverW))
                            }
                        }
                    }
                }

                if (finished.isNotEmpty()) {
                    item { SectionTitle(strings.finished) }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(finished, key = { it.id }) { book ->
                                MiniBook(
                                    book = book,
                                    root = vm.library.root,
                                    caption = book.authorLine,
                                    onClick = { onOpenBook(book) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    val pal = palette
    Text(
        text = text,
        style = Type.title.copy(fontSize = 19.sp),
        color = pal.text,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 12.dp),
    )
}

/** The book in hand: overline, jacket, progress and the way back in. */
@Composable
private fun HeroCard(
    book: Book,
    root: java.io.File,
    progress: Float,
    onContinue: () -> Unit,
    onOpen: () -> Unit,
) {
    val pal = palette
    val strings = strings
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .background(pal.surface, RoundedCornerShape(Radius.xl.dp))
            .clickable(onClick = onOpen)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(book = book, root = root, width = 84.dp, height = 126.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(strings.continueReading, style = Type.overline, color = pal.textAccent)
            Spacer(Modifier.height(6.dp))
            Text(
                book.title,
                style = Type.title.copy(fontSize = 18.sp),
                color = pal.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                book.authorLine,
                style = Type.small,
                color = pal.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(pal.subtle, RoundedCornerShape(Radius.pill.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(4.dp)
                        .background(pal.accent, RoundedCornerShape(Radius.pill.dp)),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(strings.progressOf.format((progress * 100).toInt()), style = Type.numeric, color = pal.textTertiary)
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(Radius.pill.dp))
                    .background(pal.accent)
                    .clickable(onClick = onContinue)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(strings.continueReading, style = Type.smallStrong, color = pal.textOnAccent)
            }
        }
    }
}

/**
 * When nothing is under way the newest arrival takes the stage, so the
 * page opens on a book rather than an empty hero.
 */
@Composable
private fun FeaturedCard(
    book: Book,
    root: java.io.File,
    onRead: () -> Unit,
    onOpen: () -> Unit,
) {
    val pal = palette
    val strings = strings
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .background(pal.surface, RoundedCornerShape(Radius.xl.dp))
            .clickable(onClick = onOpen)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(book = book, root = root, width = 84.dp, height = 126.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(strings.recentlyAdded, style = Type.overline, color = pal.textAccent)
            Spacer(Modifier.height(6.dp))
            Text(
                book.title,
                style = Type.title.copy(fontSize = 18.sp),
                color = pal.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                book.authorLine,
                style = Type.small,
                color = pal.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(Radius.pill.dp))
                    .background(pal.accent)
                    .clickable(onClick = onRead)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(strings.read, style = Type.smallStrong, color = pal.textOnAccent)
            }
        }
    }
}

/** One jacket in the recent grid, sized to its column. */
@Composable
private fun GridBook(
    book: Book,
    root: java.io.File,
    coverW: Dp,
    coverH: Dp,
    onClick: () -> Unit,
) {
    val pal = palette
    Column(
        Modifier
            .width(coverW)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BookCover(book = book, root = root, width = coverW, height = coverH)
        Spacer(Modifier.height(10.dp))
        Text(
            book.title,
            style = Type.label.copy(fontSize = 13.sp, lineHeight = 17.sp),
            color = pal.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            book.authorLine,
            style = Type.small,
            color = pal.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MiniBook(
    book: Book,
    root: java.io.File,
    caption: String,
    onClick: () -> Unit,
) {
    val pal = palette
    Column(
        Modifier
            .width(96.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BookCover(book = book, root = root, width = 96.dp, height = 144.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            book.title,
            style = Type.small,
            color = pal.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            caption,
            style = Type.numeric,
            color = pal.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

package com.zhijian.reading.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.ViewList
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhijian.reading.data.Book
import com.zhijian.reading.data.Counts
import com.zhijian.reading.data.Filter
import com.zhijian.reading.data.SortKey
import com.zhijian.reading.ui.LibraryViewModel
import com.zhijian.reading.ui.components.BookRow
import com.zhijian.reading.ui.components.EmptyState
import com.zhijian.reading.ui.components.Pill
import com.zhijian.reading.ui.components.ShelfGrid
import com.zhijian.reading.ui.strings
import com.zhijian.reading.ui.theme.Radius
import com.zhijian.reading.ui.theme.Type
import com.zhijian.reading.ui.theme.palette

/** The counted lists the browse pages show. */
enum class BrowseKind { Authors, Series, Tags, Publishers }

@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    lastOpened: Long?,
    onOpenBook: (Book) -> Unit,
    onBrowse: (BrowseKind) -> Unit,
    onOpenSettings: () -> Unit,
    onImport: () -> Unit,
) {
    val pal = palette
    val strings = strings
    var searching by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val shown = remember(vm.books, vm.query, vm.readingIds) { vm.shown() }
    val counts = remember(vm.books, vm.readingIds) { vm.counts() }
    val chips = remember(vm.books) { vm.chips() }

    Column(
        Modifier
            .fillMaxSize()
            .background(pal.bgApp),
    ) {
        // The large title bar, the Apple Books way.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = titleOf(strings, vm.query.filter),
                    style = Type.display.copy(fontSize = 28.sp),
                    color = pal.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = strings.bookCount(shown.size),
                    style = Type.small,
                    color = pal.textTertiary,
                )
            }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(Radius.md.dp))
                    .clickable { searching = !searching },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (searching) Icons.Outlined.Close else Icons.Outlined.Search,
                    null,
                    tint = pal.icon,
                    modifier = Modifier.size(21.dp),
                )
            }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(Radius.md.dp))
                    .clickable { menu = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.MoreVert, null, tint = pal.icon, modifier = Modifier.size(21.dp))
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        leadingIcon = { Icon(if (vm.viewMode == 0) Icons.Outlined.ViewList else Icons.Outlined.GridView, null) },
                        text = { Text(if (vm.viewMode == 0) strings.viewList else strings.viewShelf) },
                        onClick = {
                            vm.chooseViewMode(if (vm.viewMode == 0) 1 else 0)
                            menu = false
                        },
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Outlined.Sort, null) },
                        text = { Text(strings.sortBy + " · " + sortLabel(strings, vm.query.sort)) },
                        onClick = {
                            val keys = SortKey.entries
                            val next = keys[(keys.indexOf(vm.query.sort) + 1) % keys.size]
                            vm.setSort(next)
                            menu = false
                        },
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Outlined.Sort, null) },
                        text = { Text(if (vm.query.descending) strings.sortDesc else strings.sortAsc) },
                        onClick = {
                            vm.toggleSortDirection()
                            menu = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(strings.browseAuthors) },
                        onClick = { menu = false; onBrowse(BrowseKind.Authors) },
                    )
                    DropdownMenuItem(
                        text = { Text(strings.browseSeries) },
                        onClick = { menu = false; onBrowse(BrowseKind.Series) },
                    )
                    DropdownMenuItem(
                        text = { Text(strings.browseTags) },
                        onClick = { menu = false; onBrowse(BrowseKind.Tags) },
                    )
                    DropdownMenuItem(
                        text = { Text(strings.browsePublishers) },
                        onClick = { menu = false; onBrowse(BrowseKind.Publishers) },
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Outlined.Add, null) },
                        text = { Text(strings.addBooks) },
                        onClick = { menu = false; onImport() },
                    )
                    DropdownMenuItem(
                        text = { Text(strings.settingsTitle) },
                        onClick = { menu = false; onOpenSettings() },
                    )
                }
            }
        }

        if (searching) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .height(40.dp)
                    .background(pal.surface, RoundedCornerShape(Radius.md.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, null, tint = pal.iconMuted, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (vm.query.text.isEmpty()) {
                        Text(strings.searchHint, style = Type.body, color = pal.textTertiary)
                    }
                    BasicTextField(
                        value = vm.query.text,
                        onValueChange = vm::setText,
                        singleLine = true,
                        textStyle = Type.body.copy(color = pal.text),
                        cursorBrush = SolidColor(pal.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (vm.query.text.isNotEmpty()) {
                    Icon(
                        Icons.Outlined.Close,
                        strings.closeSearch,
                        tint = pal.iconMuted,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { vm.setText("") },
                    )
                }
            }
        }

        // Filters: the four shelves, then the busiest tags.
        val chipList = buildList {
            add(Chip(strings.filterAll, vm.query.filter is Filter.All) { vm.setFilter(Filter.All) })
            add(Chip(strings.titleRecent, vm.query.filter is Filter.RecentlyAdded) { vm.setFilter(Filter.RecentlyAdded) })
            add(Chip(strings.titleReading, vm.query.filter is Filter.Reading) { vm.setFilter(Filter.Reading) })
            add(Chip(strings.titleFavorites, vm.query.filter is Filter.Favorites) { vm.setFilter(Filter.Favorites) })
            chips.forEach { tag ->
                add(Chip(tag, vm.query.tagChip == tag) {
                    vm.setTagChip(if (vm.query.tagChip == tag) null else tag)
                })
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(chipList) { chip ->
                Pill(
                    text = chip.label,
                    selected = chip.selected,
                    onClick = chip.onClick,
                )
            }
        }

        when {
            vm.books.isEmpty() -> EmptyState(
                title = strings.emptyNoBooks,
                body = strings.emptyNoBooksBody,
                icon = {
                    Icon(Icons.Outlined.Add, null, tint = pal.iconMuted, modifier = Modifier.size(20.dp))
                },
            )

            shown.isEmpty() -> EmptyState(
                title = strings.emptyNoMatch,
                body = strings.emptyNoMatchBody(vm.query.text),
            )

            vm.viewMode == 0 -> ShelfGrid(
                books = shown,
                root = vm.library.root,
                selectedId = lastOpened,
                onSelect = onOpenBook,
                onLongPress = onOpenBook,
                modifier = Modifier.fillMaxSize(),
            )

            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.id }) { book ->
                    BookRow(
                        book = book,
                        root = vm.library.root,
                        selected = book.id == lastOpened,
                        progress = vm.progress[book.id] ?: 0f,
                        onClick = { onOpenBook(book) },
                    )
                }
                item { Spacer(Modifier.height(120.dp)) }
            }
        }
    }
}

private class Chip(val label: String, val selected: Boolean, val onClick: () -> Unit)

@Composable
private fun titleOf(strings: com.zhijian.reading.ui.AppStrings, filter: Filter): String = when (filter) {
    is Filter.All -> strings.titleAll
    is Filter.RecentlyAdded -> strings.titleRecent
    is Filter.Reading -> strings.titleReading
    is Filter.Favorites -> strings.titleFavorites
    is Filter.ByFormat -> strings.titleByFormat(filter.format)
    is Filter.ByAuthor -> strings.titleByAuthor(filter.name)
    is Filter.BySeries -> strings.titleBySeries(filter.name)
    is Filter.ByTag -> strings.titleByTag(filter.name)
    is Filter.ByPublisher -> strings.titleByPublisher(filter.name)
}

@Composable
private fun sortLabel(strings: com.zhijian.reading.ui.AppStrings, key: SortKey): String = when (key) {
    SortKey.DateAdded -> strings.sortDate
    SortKey.Title -> strings.sortTitle
    SortKey.Author -> strings.sortAuthor
    SortKey.Rating -> strings.sortRating
    SortKey.Size -> strings.sortSize
    SortKey.Series -> strings.sortSeries
}

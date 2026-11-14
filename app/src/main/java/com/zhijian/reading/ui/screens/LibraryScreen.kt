package com.zhijian.reading.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhijian.reading.data.Book
import com.zhijian.reading.data.Filter
import com.zhijian.reading.ui.LibraryViewModel
import com.zhijian.reading.ui.components.EmptyState
import com.zhijian.reading.ui.components.ShelfGrid
import com.zhijian.reading.ui.strings
import com.zhijian.reading.ui.theme.Radius
import com.zhijian.reading.ui.theme.Type
import com.zhijian.reading.ui.theme.palette
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

/** The counted lists the browse pages show. */
enum class BrowseKind { Authors, Series, Tags, Publishers }

/**
 * The shelf, 微信读书 style: one search pill up top, the 书架 row with
 * 导入 / 选择, a row of sort tabs, and the covers in a fixed three-column
 * grid with a small progress chip pinned to each started book.
 */
@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    lastOpened: Long?,
    onOpenBook: (Book) -> Unit,
    onBrowse: (BrowseKind) -> Unit,
    onOpenSettings: () -> Unit,
    onImport: () -> Unit,
    onImportOnline: () -> Unit,
) {
    val pal = palette
    val strings = strings
    val shown = remember(vm.books, vm.query, vm.readingIds) { vm.shown() }

    // Long-press selection: pick covers one by one, then remove them in bulk.
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var importChoose by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    fun toggle(id: Long) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    fun enterSelection(book: Book) {
        if (!selecting) {
            selecting = true
            selectedIds = setOf(book.id)
        }
    }

    val allShownSelected = shown.isNotEmpty() && selectedIds.containsAll(shown.map { it.id })

    // The sort tabs reorder locally: 默认 follows the library order, 更新 is
    // newest import first, 进度 puts books in progress on top, 推荐值 ranks by
    // rating, 书名 uses the Chinese collation, 字数 puts the longest books
    // first. 分类 opens the group cards instead of reordering.
    var sortTab by remember { mutableIntStateOf(0) }
    // The 分类 tab opens a second level: first the group cards, then the
    // books of the tapped group.
    var category by remember(sortTab) { mutableStateOf<String?>(null) }
    val sorted = remember(shown, sortTab, vm.progress) {
        when (sortTab) {
            1 -> shown.sortedByDescending { it.timestamp }
            2 -> shown.sortedByDescending { vm.progress[it.id] ?: 0f }
            3 -> shown.sortedWith(
                compareByDescending<Book> { it.rating }
                    .thenBy(Collator.getInstance(Locale.CHINA)) { it.title },
            )
            4 -> {
                val collator = Collator.getInstance(Locale.CHINA)
                shown.sortedWith(compareBy(collator) { it.title })
            }
            6 -> shown.sortedByDescending { it.size }
            else -> shown
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(pal.bgApp),
    ) {
        if (selecting) {
            // Selection mode replaces the header, one aligned row: exit,
            // 全选 on the left, the count centered, 删除 on the right.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(Radius.md.dp))
                        .clickable {
                            selecting = false
                            selectedIds = emptySet()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        androidx.compose.ui.res.painterResource(com.zhijian.reading.R.drawable.ic_r_close),
                        null,
                        tint = pal.icon,
                        modifier = Modifier.size(21.dp),
                    )
                }
                Text(
                    text = if (allShownSelected) strings.deselectAll else strings.selectAll,
                    style = Type.body,
                    color = pal.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.md.dp))
                        .clickable {
                            selectedIds = if (allShownSelected) emptySet() else shown.map { it.id }.toSet()
                        }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = strings.selectedCount(selectedIds.size),
                    style = Type.title.copy(fontSize = 17.sp),
                    color = pal.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = strings.deleteAction,
                    style = Type.body,
                    color = if (selectedIds.isEmpty()) pal.iconMuted else pal.danger,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.md.dp))
                        .clickable(enabled = selectedIds.isNotEmpty()) { confirmDelete = true }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                )
            }
        } else {
            // Search pill, always visible, 微信读书 style.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(40.dp)
                    .background(pal.surface, RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    androidx.compose.ui.res.painterResource(com.zhijian.reading.R.drawable.ic_a_search),
                    null,
                    tint = pal.iconMuted,
                    modifier = Modifier.size(17.dp),
                )
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
                        androidx.compose.ui.res.painterResource(com.zhijian.reading.R.drawable.ic_r_close),
                        strings.closeSearch,
                        tint = pal.iconMuted,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { vm.setText("") },
                    )
                }
            }

            // 书架 row: the big title on the left, 导入 / 选择 on the right.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = strings.shelfTitle,
                        style = Type.display.copy(fontSize = 24.sp),
                        color = pal.text,
                    )
                    Text(
                        text = strings.bookCount(shown.size),
                        style = Type.small,
                        color = pal.textTertiary,
                    )
                }
                ShelfAction(
                    iconRes = com.zhijian.reading.R.drawable.ic_a_add_circle,
                    label = strings.shelfImport,
                    onClick = { importChoose = true },
                )
                Spacer(Modifier.width(18.dp))
                ShelfAction(
                    iconRes = com.zhijian.reading.R.drawable.ic_a_check_circle,
                    label = strings.shelfSelect,
                    onClick = { selecting = true },
                )
            }

            // Sort tabs, the 微信读书 way: one size for every word, the
            // active one in the accent, evenly spaced on a scrollable line:
            // 默认 / 更新 / 进度 / 推荐值 / 书名 / 分类 / 字数.
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                val tabs = listOf(
                    strings.shelfSortDefault,
                    strings.shelfSortUpdated,
                    strings.shelfSortProgress,
                    strings.shelfSortRating,
                    strings.shelfSortTitle,
                    strings.shelfSortCategory,
                    strings.shelfSortWords,
                )
                tabs.forEachIndexed { i, label ->
                    val active = i == sortTab
                    Text(
                        text = label,
                        style = Type.body.copy(
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        color = if (active) pal.accent else pal.textSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.sm.dp))
                            .clickable { sortTab = i }
                            .padding(horizontal = 2.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // 导入进度: files picked on the import screens land here while they
        // are still copying, the 微信读书 way — a quiet line under the tabs.
        if (vm.importTotal > 0) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = pal.accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    strings.importProgress(vm.importDone, vm.importTotal),
                    style = Type.small,
                    color = pal.textSecondary,
                )
            }
        }

        when {
            vm.books.isEmpty() -> EmptyState(
                title = strings.emptyNoBooks,
                body = strings.emptyNoBooksBody,
                icon = {
                    Icon(
                        androidx.compose.ui.res.painterResource(com.zhijian.reading.R.drawable.ic_a_add),
                        null,
                        tint = pal.iconMuted,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )

            shown.isEmpty() -> EmptyState(
                title = strings.emptyNoMatch,
                body = strings.emptyNoMatchBody(vm.query.text),
            )

            sortTab == 5 && category == null -> {
                // 分类: the group cards, 微信读书 style — one tile per
                // category, the book count under the name, biggest first.
                val groups = remember(shown, strings) {
                    shown.groupBy { it.tags.firstOrNull() ?: "" }
                        .map { it.toPair() }
                        .sortedWith(
                            compareByDescending<Pair<String, List<Book>>> { it.second.size }
                                .thenBy(Collator.getInstance(Locale.CHINA)) { it.first },
                        )
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(groups, key = { it.first }) { (name, booksIn) ->
                        CategoryCard(
                            name = if (name.isEmpty()) strings.shelfUncategorized else name,
                            count = booksIn.size,
                            onClick = { category = name },
                        )
                    }
                }
            }

            sortTab == 5 -> {
                // The books of one category, with a way back to the cards.
                val booksIn = remember(shown, category) {
                    shown.filter {
                        (it.tags.firstOrNull() ?: "") == category
                    }
                }
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(Radius.md.dp))
                                .clickable { category = null },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                androidx.compose.ui.res.painterResource(com.zhijian.reading.R.drawable.ic_r_back),
                                null,
                                tint = pal.icon,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = category ?: "",
                            style = Type.title.copy(fontSize = 17.sp),
                            color = pal.text,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(strings.bookCount(booksIn.size), style = Type.small, color = pal.textTertiary)
                    }
                    ShelfGrid(
                        books = booksIn,
                        root = vm.library.root,
                        progress = vm.progress,
                        selectedId = lastOpened,
                        onSelect = { book ->
                            if (selecting) toggle(book.id) else onOpenBook(book)
                        },
                        onLongPress = { book -> enterSelection(book) },
                        selectionMode = selecting,
                        selectedIds = selectedIds,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            else -> ShelfGrid(
                books = sorted,
                root = vm.library.root,
                progress = vm.progress,
                selectedId = lastOpened,
                onSelect = { book ->
                    if (selecting) toggle(book.id) else onOpenBook(book)
                },
                onLongPress = { book -> enterSelection(book) },
                selectionMode = selecting,
                selectedIds = selectedIds,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (importChoose) {
        // 导入 books two ways: online (a direct file link) or this device.
        AlertDialog(
            onDismissRequest = { importChoose = false },
            containerColor = pal.surfaceRaised,
            titleContentColor = pal.text,
            textContentColor = pal.textSecondary,
            title = { Text(strings.importChooseTitle) },
            text = {
                Column {
                    ImportChoice(
                        iconRes = com.zhijian.reading.R.drawable.ic_a_link,
                        title = strings.importOnline,
                        note = strings.importOnlineNote,
                        tintAccent = true,
                    ) {
                        importChoose = false
                        onImportOnline()
                    }
                    Spacer(Modifier.height(10.dp))
                    ImportChoice(
                        iconRes = com.zhijian.reading.R.drawable.ic_a_import,
                        title = strings.importLocal,
                        note = strings.importLocalNote,
                        tintAccent = false,
                    ) {
                        importChoose = false
                        onImport()
                    }
                }
            },
            confirmButton = { },
            dismissButton = { },
        )
    }

    if (confirmDelete) {
        val toDelete = vm.books.filter { it.id in selectedIds }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = pal.surfaceRaised,
            titleContentColor = pal.text,
            textContentColor = pal.textSecondary,
            title = { Text(strings.deleteBooksTitle(toDelete.size)) },
            text = { Text(strings.deleteBooksBody) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val doomed = toDelete
                    selecting = false
                    selectedIds = emptySet()
                    scope.launch { vm.deleteAll(doomed) }
                }) { Text(strings.remove, color = pal.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(strings.cancel, color = pal.textSecondary)
                }
            },
        )
    }
}

/** ⊕ 导入 / ✓ 选择 in the 书架 row: a quiet icon-plus-word action. */
@Composable
private fun ShelfAction(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
) {
    val pal = palette
    Row(
        Modifier
            .clip(RoundedCornerShape(Radius.md.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            androidx.compose.ui.res.painterResource(iconRes),
            null,
            tint = pal.textSecondary,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = Type.body,
            color = pal.textSecondary,
        )
    }
}

/** One row of the 导入 chooser: icon tile, what it does, how. */
@Composable
private fun ImportChoice(
    iconRes: Int,
    title: String,
    note: String,
    tintAccent: Boolean,
    onClick: () -> Unit,
) {
    val pal = palette
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(if (tintAccent) pal.accentSubtle else pal.subtle, RoundedCornerShape(Radius.md.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                androidx.compose.ui.res.painterResource(iconRes),
                null,
                tint = if (tintAccent) pal.accent else pal.icon,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.bodyStrong, color = pal.text)
            Text(note, style = Type.small, color = pal.textTertiary)
        }
        Icon(
            androidx.compose.ui.res.painterResource(com.zhijian.reading.R.drawable.ic_a_chevron_right),
            null,
            tint = pal.iconMuted,
            modifier = Modifier.size(17.dp),
        )
    }
}

/** One category tile in the 分类 view: the name, how many books live there. */
@Composable
private fun CategoryCard(
    name: String,
    count: Int,
    onClick: () -> Unit,
) {
    val pal = palette
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg.dp))
            .background(pal.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        Text(
            text = name,
            style = Type.title.copy(fontSize = 17.sp),
            color = pal.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(strings.bookCount(count), style = Type.small, color = pal.textTertiary)
    }
}

package com.zhijian.reading.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhijian.reading.data.Filter
import com.zhijian.reading.ui.LibraryViewModel
import com.zhijian.reading.ui.components.EmptyState
import com.zhijian.reading.ui.components.ValueRow
import com.zhijian.reading.ui.strings
import com.zhijian.reading.ui.theme.Radius
import com.zhijian.reading.ui.theme.Type
import com.zhijian.reading.ui.theme.palette

/** Every author, series, tag or publisher the library holds, counted. */
@Composable
fun BrowseScreen(
    vm: LibraryViewModel,
    kind: BrowseKind,
    onBack: () -> Unit,
    onPick: (Filter) -> Unit,
) {
    val pal = palette
    val strings = strings
    val kindLabel = when (kind) {
        BrowseKind.Authors -> strings.browseAuthors
        BrowseKind.Series -> strings.browseSeries
        BrowseKind.Tags -> strings.browseTags
        BrowseKind.Publishers -> strings.browsePublishers
    }
    val entries = remember(vm.books, kind) {
        val map = when (kind) {
            BrowseKind.Authors -> vm.books.flatMap { it.authors }.groupingBy { it }.eachCount()
            BrowseKind.Series -> vm.books.mapNotNull { it.series }.groupingBy { it }.eachCount()
            BrowseKind.Tags -> vm.books.flatMap { it.tags }.groupingBy { it }.eachCount()
            BrowseKind.Publishers -> vm.books.mapNotNull { it.publisher }.groupingBy { it }.eachCount()
        }
        map.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
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
                Icon(androidx.compose.ui.res.painterResource(com.zhijian.reading.R.drawable.ic_r_back), "Back", tint = pal.icon, modifier = Modifier.size(21.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(kindLabel, style = Type.title.copy(fontSize = 20.sp), color = pal.text)
                Text(strings.bookCount(entries.size), style = Type.numeric, color = pal.textTertiary)
            }
        }

        if (entries.isEmpty()) {
            EmptyState(
                title = strings.nothingFound,
                body = strings.nothingFoundBody(kindLabel),
                icon = { Icon(androidx.compose.ui.res.painterResource(com.zhijian.reading.R.drawable.ic_a_search), null, tint = pal.iconMuted, modifier = Modifier.size(20.dp)) },
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(entries, key = { it.key }) { e ->
                    ValueRow(e.key, e.value) {
                        val filter = when (kind) {
                            BrowseKind.Authors -> Filter.ByAuthor(e.key)
                            BrowseKind.Series -> Filter.BySeries(e.key)
                            BrowseKind.Tags -> Filter.ByTag(e.key)
                            BrowseKind.Publishers -> Filter.ByPublisher(e.key)
                        }
                        onPick(filter)
                    }
                }
            }
        }
    }
}

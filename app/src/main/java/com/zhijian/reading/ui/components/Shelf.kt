package com.zhijian.reading.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhijian.reading.data.Book
import com.zhijian.reading.ui.theme.Radius
import com.zhijian.reading.ui.theme.Type
import com.zhijian.reading.ui.theme.palette
import java.io.File

/**
 * The library as covers on paper: a calm grid, a soft shadow under every
 * jacket, the title and author printed underneath. The chosen book takes
 * a vermilion ring.
 */
@Composable
fun ShelfGrid(
    books: List<Book>,
    root: File,
    selectedId: Long?,
    onSelect: (Book) -> Unit,
    onLongPress: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val columns = (maxWidth.value / 118f).toInt().coerceIn(3, 5)
        val colW = maxWidth / columns
        val coverW = (colW * 0.86f).coerceIn(72.dp, 150.dp)
        val coverH = coverW * 1.5f
        val labelH = 52.dp

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
        ) {
            items(books, key = { it.id }) { book ->
                CoverSlot(
                    book = book,
                    root = root,
                    coverW = coverW,
                    coverH = coverH,
                    labelH = labelH,
                    selected = book.id == selectedId,
                    onSelect = { onSelect(book) },
                    onLongPress = { onLongPress(book) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CoverSlot(
    book: Book,
    root: File,
    coverW: Dp,
    coverH: Dp,
    labelH: Dp,
    selected: Boolean,
    onSelect: () -> Unit,
    onLongPress: () -> Unit,
) {
    val pal = palette
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onSelect, onLongClick = onLongPress)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.width(coverW)) {
            BookCover(
                book = book,
                root = root,
                width = coverW,
                height = coverH,
                shadow = true,
                modifier = if (selected) {
                    Modifier
                        .clip(CoverShape)
                        .border(2.dp, pal.accent, CoverShape)
                } else Modifier,
            )
        }
        Spacer(Modifier.height(10.dp))
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
            text = book.authorLine,
            style = Type.small,
            color = pal.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(coverW),
        )
    }
}

package com.zhijian.reading.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.zhijian.reading.data.Book
import com.zhijian.reading.data.Covers
import com.zhijian.reading.ui.theme.Radius
import java.io.File

/** A jacket: softly rounded corners, like a book laid on a table. */
val CoverShape = RoundedCornerShape(Radius.md.dp)

/**
 * A book's jacket: its cover.jpg when it has one, and otherwise the
 * typographic placeholder — the book's ink, its author above and its
 * title set large in serif.
 */
@Composable
fun BookCover(
    book: Book,
    root: File,
    width: Dp,
    height: Dp? = null,
    modifier: Modifier = Modifier,
    shadow: Boolean = true,
) {
    val context = LocalContext.current
    val file = book.coverFile(root)
    val sized = if (height != null) Modifier.size(width, height) else Modifier.width(width).aspectRatio(2f / 3f)
    val lifted = if (shadow) {
        Modifier.shadow(
            10.dp, CoverShape,
            ambientColor = Color.Black.copy(alpha = 0.18f),
            spotColor = Color.Black.copy(alpha = 0.26f),
        )
    } else Modifier

    Box(modifier.then(lifted).then(sized).clip(CoverShape)) {
        if (book.hasCover && file.exists()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(file)
                    .memoryCacheKey("cover-${book.id}-${file.lastModified()}")
                    .crossfade(false)
                    .build(),
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CoverPlaceholder(book, Modifier.fillMaxSize(), widthHint = width)
        }
    }
}

/** The block a coverless book shows: ink, author, title. */
@Composable
fun CoverPlaceholder(book: Book, modifier: Modifier = Modifier, widthHint: Dp = 132.dp) {
    val (ink, onInk) = Covers.placeholderColors(book)
    val scale = (widthHint.value / 132f).coerceIn(0.42f, 1.5f)
    val showAuthor = widthHint >= 60.dp
    Box(modifier.background(Color(ink))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = (11 * scale).dp.coerceAtLeast(2.dp),
                    vertical = (13 * scale).dp.coerceAtLeast(3.dp),
                ),
            verticalArrangement = if (showAuthor) Arrangement.SpaceBetween else Arrangement.Bottom,
        ) {
            if (showAuthor) {
                Text(
                    text = book.authors.firstOrNull().orEmpty(),
                    color = Color(onInk).copy(alpha = 0.72f),
                    fontFamily = FontFamily.Serif,
                    fontSize = (9 * scale).sp,
                    lineHeight = (12 * scale).sp,
                    letterSpacing = (0.3 * scale).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = book.title,
                color = Color(onInk),
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = (15 * scale).sp,
                lineHeight = (19 * scale).sp,
                maxLines = if (showAuthor) 5 else 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

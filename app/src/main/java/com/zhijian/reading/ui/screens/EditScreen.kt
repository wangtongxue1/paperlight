package com.zhijian.reading.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhijian.reading.data.Book
import com.zhijian.reading.ui.components.Overline
import com.zhijian.reading.ui.components.Stars
import com.zhijian.reading.ui.strings
import com.zhijian.reading.ui.theme.Radius
import com.zhijian.reading.ui.theme.Type
import com.zhijian.reading.ui.theme.palette

/** The metadata editor: what calibre would let you change about a book. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EditScreen(
    book: Book,
    onBack: () -> Unit,
    onSave: (Book) -> Unit,
) {
    val pal = palette
    val strings = strings
    var title by remember(book.id) { mutableStateOf(book.title) }
    var authors by remember(book.id) { mutableStateOf(book.authors.joinToString(", ")) }
    var series by remember(book.id) { mutableStateOf(book.series.orEmpty()) }
    var seriesIndex by remember(book.id) { mutableStateOf(com.zhijian.reading.data.trimIndex(book.seriesIndex)) }
    var publisher by remember(book.id) { mutableStateOf(book.publisher.orEmpty()) }
    var pubdate by remember(book.id) { mutableStateOf(book.pubdate) }
    var language by remember(book.id) { mutableStateOf(book.languages.firstOrNull().orEmpty()) }
    var rating by remember(book.id) { mutableStateOf(book.rating) }
    var comments by remember(book.id) { mutableStateOf(book.comments) }
    var tags by remember(book.id) { mutableStateOf(book.tags.toList()) }
    var tagDraft by remember(book.id) { mutableStateOf("") }
    var isbn by remember(book.id) { mutableStateOf(book.identifiers["isbn"].orEmpty()) }

    Column(
        Modifier
            .fillMaxSize()
            .background(pal.bgApp)
            .imePadding(),
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
                Icon(Icons.Outlined.ArrowBack, "Back", tint = pal.icon, modifier = Modifier.size(21.dp))
            }
            Text(
                text = strings.editMetadata,
                style = Type.title.copy(fontSize = 17.sp),
                color = pal.text,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = strings.save,
                style = Type.body.copy(fontWeight = FontWeight.Medium),
                color = pal.textAccent,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.sm.dp))
                    .clickable {
                        onSave(
                            book.copy(
                                title = title.ifBlank { strings.untitled },
                                sort = title.ifBlank { strings.untitled },
                                authors = authors.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                series = series.ifBlank { null },
                                seriesIndex = seriesIndex.toDoubleOrNull() ?: 1.0,
                                publisher = publisher.ifBlank { null },
                                pubdate = pubdate,
                                languages = listOf(language).filter { it.isNotBlank() },
                                rating = rating,
                                comments = comments,
                                tags = tags,
                                identifiers = book.identifiers.toMutableMap().apply {
                                    if (isbn.isBlank()) remove("isbn") else put("isbn", isbn)
                                },
                            ),
                        )
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Field(strings.titleField, title) { title = it }
            Field(strings.authors, authors, hint = strings.authorsHint) { authors = it }
            Field(strings.series, series) { series = it }
            Field(strings.seriesIndex, seriesIndex, keyboard = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)) { seriesIndex = it }
            Field(strings.publisher, publisher) { publisher = it }
            Field(strings.published, pubdate, hint = "YYYY-MM-DD") { pubdate = it }
            Field(strings.language, language, hint = "en, zh, fr…") { language = it }
            Field(strings.isbn, isbn) { isbn = it }

            Spacer(Modifier.height(18.dp))
            Overline(strings.rating)
            Spacer(Modifier.height(8.dp))
            Stars(rating = rating, starSize = 24.dp, gap = 6.dp) { rating = if (it == rating) 0 else it }

            Spacer(Modifier.height(20.dp))
            Overline(strings.tags)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    Input(
                        value = tagDraft,
                        onValue = { tagDraft = it },
                        hint = strings.addTag,
                        imeAction = ImeAction.Done,
                        onDone = {
                            val t = tagDraft.trim()
                            if (t.isNotEmpty() && !tags.contains(t)) tags = tags + t
                            tagDraft = ""
                        },
                    )
                }
            }
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    tags.forEach { tag ->
                        Row(
                            Modifier
                                .height(26.dp)
                                .background(pal.subtle, RoundedCornerShape(Radius.pill.dp))
                                .padding(start = 10.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(tag, style = Type.small, color = pal.textSecondary)
                            Spacer(Modifier.width(2.dp))
                            Box(
                                Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(Radius.pill.dp))
                                    .clickable { tags = tags - tag },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Outlined.Close, "Remove", tint = pal.iconMuted, modifier = Modifier.size(13.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Overline(strings.description)
            Spacer(Modifier.height(8.dp))
            Input(
                value = comments,
                onValue = { comments = it },
                hint = strings.descriptionField,
                singleLine = false,
                minHeight = 120.dp,
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    hint: String = "",
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    onChange: (String) -> Unit,
) {
    Column(Modifier.padding(top = 16.dp)) {
        Overline(label)
        Spacer(Modifier.height(6.dp))
        Input(value, onChange, hint = hint, keyboard = keyboard)
    }
}

@Composable
private fun Input(
    value: String,
    onValue: (String) -> Unit,
    hint: String = "",
    singleLine: Boolean = true,
    minHeight: androidx.compose.ui.unit.Dp = 42.dp,
    imeAction: ImeAction = ImeAction.Next,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    onDone: (() -> Unit)? = null,
) {
    val pal = palette
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .background(pal.surface, RoundedCornerShape(Radius.md.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        if (value.isEmpty() && hint.isNotEmpty()) {
            Text(hint, style = Type.body, color = pal.textTertiary)
        }
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = singleLine,
            textStyle = Type.body.copy(color = pal.text),
            cursorBrush = SolidColor(pal.accent),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboard.keyboardType,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

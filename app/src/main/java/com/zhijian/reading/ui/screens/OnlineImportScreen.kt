package com.zhijian.reading.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhijian.reading.ui.LibraryViewModel
import com.zhijian.reading.ui.strings
import com.zhijian.reading.ui.theme.Radius
import com.zhijian.reading.ui.theme.Type
import com.zhijian.reading.ui.theme.palette

/**
 * Online import: one page, one field. Paste a direct file link (EPUB, PDF,
 * MOBI, ...), the library downloads it and the shelf refreshes. Reached from
 * the shelf's 导入 chooser.
 */
@Composable
fun OnlineImportScreen(
    vm: LibraryViewModel,
    onBack: () -> Unit,
) {
    val pal = palette
    val strings = strings
    var url by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }

    fun doImport() {
        if (url.isBlank()) return
        // Hand off to the ViewModel and go home; the download keeps running
        // there and the shelf shows the progress (the 微信读书 way).
        vm.startUrlImport(url) { ok -> note = if (ok) null else strings.urlFailed }
        onBack()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(pal.bgApp),
    ) {
        // The pushed-page header: back chevron, page title.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 20.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(Radius.md.dp))
                    .clickable(onClick = onBack),                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    androidx.compose.ui.res.painterResource(com.zhijian.reading.R.drawable.ic_r_back),
                    null,
                    tint = pal.icon,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = strings.onlineImportTitle,
                style = Type.title.copy(fontSize = 19.sp),
                color = pal.text,
            )
        }

        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            // One row: icon · text field · action button tucked at the end.
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .background(pal.surface, RoundedCornerShape(Radius.lg.dp))
                    .padding(start = 12.dp, end = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    androidx.compose.ui.res.painterResource(com.zhijian.reading.R.drawable.ic_a_link),
                    null,
                    tint = pal.accent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(9.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (url.isEmpty()) {
                        Text(strings.urlHint, style = Type.small, color = pal.textTertiary)
                    }
                    BasicTextField(
                        value = url,
                        onValueChange = { url = it; note = null },
                        singleLine = true,
                        textStyle = Type.small.copy(color = pal.text, fontSize = 13.sp),
                        cursorBrush = SolidColor(pal.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { doImport() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(Radius.pill.dp))
                        .background(if (url.isBlank()) pal.subtle else pal.accent)
                        .clickable(enabled = url.isNotBlank()) { doImport() }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        strings.urlImportAction,
                        style = Type.smallStrong,
                        color = if (url.isBlank()) pal.textTertiary else pal.textOnAccent,
                    )
                }
            }
            // One quiet line under the field: hint at rest, result on outcome.
            Spacer(Modifier.height(8.dp))
            Text(
                text = note ?: strings.urlHint2,
                style = Type.small,
                color = when {
                    note == null -> pal.textTertiary
                    note == strings.urlFailed -> pal.danger
                    else -> pal.success
                },
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

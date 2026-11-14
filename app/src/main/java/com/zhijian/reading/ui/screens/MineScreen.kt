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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.zhijian.reading.data.humanSize
import com.zhijian.reading.ui.LibraryViewModel
import com.zhijian.reading.ui.components.Hairline
import com.zhijian.reading.ui.components.Overline
import com.zhijian.reading.ui.components.SegmentRow
import com.zhijian.reading.ui.strings
import com.zhijian.reading.ui.theme.Radius
import com.zhijian.reading.ui.theme.Type
import com.zhijian.reading.ui.theme.palette
import kotlinx.coroutines.launch

/**
 * Me: everything that is about this copy of the app — how the shelf looks,
 * where the books live, and every way to bring new ones in (the old
 * "Get Books" tab folded into here). Online sources open their own page.
 */
@Composable
fun MineScreen(
    vm: LibraryViewModel,
    onImport: () -> Unit,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
) {
    val pal = palette
    val strings = strings

    Column(
        Modifier
            .fillMaxSize()
            .background(pal.bgApp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            if (showBack) {
                // Opened as a pushed page (from a settings entry): give it an
                // explicit way out, a tap on the chevron goes back.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(Radius.md.dp))
                            .background(pal.surface, RoundedCornerShape(Radius.md.dp))
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            androidx.compose.ui.res.painterResource(com.zhijian.reading.R.drawable.ic_r_back),
                            null,
                            tint = pal.icon,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }
            Text(strings.navMine, style = Type.display.copy(fontSize = 28.sp), color = pal.text)
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            StatsCard(
                books = vm.books.size,
                reading = vm.readingIds.size,
                size = humanSize(vm.books.sumOf { it.size }),
            )

            // ---- bring books in (collapsed by default) ----
            Spacer(Modifier.height(18.dp))
            var importOpen by remember { mutableStateOf(false) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { importOpen = !importOpen }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(strings.getBooksTitle, style = Type.title.copy(fontSize = 17.sp), color = pal.text)
                Spacer(Modifier.weight(1f))
                Icon(
                    androidx.compose.ui.res.painterResource(
                        if (importOpen) com.zhijian.reading.R.drawable.ic_a_chevron_up
                        else com.zhijian.reading.R.drawable.ic_a_chevron_down,
                    ),
                    null,
                    tint = pal.icon,
                    modifier = Modifier.size(20.dp),
                )
            }
            AnimatedVisibility(visible = importOpen) {
                Column {
                    LinkImportCard(vm)
                    ActionRow(
                        icon = { Icon(androidx.compose.ui.res.painterResource(com.zhijian.reading.R.drawable.ic_a_import), null, tint = pal.accent, modifier = Modifier.size(19.dp)) },
                        tintAccent = true,
                        title = strings.importFromDevice,
                        note = strings.localImportNoPerm,
                        onClick = onImport,
                    )
                    Text(
                        text = strings.builtinNote,
                        style = Type.small,
                        color = pal.textTertiary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            }

            // ---- appearance ----
            Spacer(Modifier.height(10.dp))
            Hairline(Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(18.dp))
            Column(Modifier.padding(horizontal = 20.dp)) {
                Overline(strings.appearance)
                Spacer(Modifier.height(10.dp))

                SettingRow(strings.theme) {
                    SegmentRow(
                        selected = vm.themeMode,
                        options = listOf(strings.themeSystem, strings.themeLight, strings.themeDark),
                        onSelect = { vm.setTheme(it) },
                    )
                }
                SettingRow(strings.languageSetting) {
                    val current = when (vm.langPref) {
                        "zh" -> 1
                        "en" -> 2
                        else -> 0
                    }
                    SegmentRow(
                        selected = current,
                        options = listOf(strings.langSystem, "中文", "English"),
                        onSelect = { vm.setLang(listOf("system", "zh", "en")[it]) },
                    )
                }
                SettingRow(strings.defaultView) {
                    SegmentRow(
                        selected = vm.viewMode,
                        options = listOf(strings.viewShelf, strings.viewList),
                        onSelect = { vm.chooseViewMode(it) },
                    )
                }
                SettingRow(strings.listStars) {
                    Switch(
                        checked = vm.showRatings,
                        onCheckedChange = { vm.chooseShowRatings(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = pal.textOnAccent,
                            checkedTrackColor = pal.accent,
                            uncheckedThumbColor = pal.textTertiary,
                            uncheckedTrackColor = pal.subtle,
                        ),
                    )
                }
            }

            // ---- library ----
            Spacer(Modifier.height(18.dp))
            Hairline(Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(18.dp))
            Column(Modifier.padding(horizontal = 20.dp)) {
                Overline(strings.librarySection)
                Spacer(Modifier.height(10.dp))
                InfoLine(strings.bookCountLabel, "${vm.books.size}")
                InfoLine(strings.onDisk, humanSize(vm.books.sumOf { it.size }))
                InfoLine(strings.folder, vm.library.root.absolutePath, mono = true)
            }

            // ---- about ----
            Spacer(Modifier.height(18.dp))
            Hairline(Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(18.dp))
            Column(Modifier.padding(horizontal = 20.dp)) {
                Overline(strings.about)
                Spacer(Modifier.height(10.dp))
                InfoLine(strings.appName, strings.version + " 1.5.2")
                Spacer(Modifier.height(8.dp))
                Text(strings.aboutBody, style = Type.small, color = pal.textSecondary)
            }

            Spacer(Modifier.height(120.dp))
        }
    }
}

@Composable
private fun StatsCard(books: Int, reading: Int, size: String) {
    val pal = palette
    val strings = strings
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .background(pal.surface, RoundedCornerShape(Radius.lg.dp))
            .padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatCell(strings.bookCountLabel, "$books", Modifier.weight(1f))
        StatDivider()
        StatCell(strings.statReading, "$reading", Modifier.weight(1f))
        StatDivider()
        StatCell(strings.onDisk, size, Modifier.weight(1.2f))
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    val pal = palette
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Type.display.copy(fontSize = 22.sp, lineHeight = 26.sp), color = pal.text)
        Spacer(Modifier.height(3.dp))
        Text(label, style = Type.small, color = pal.textTertiary)
    }
}

@Composable
private fun StatDivider() {
    val pal = palette
    Box(
        Modifier
            .width(1.dp)
            .height(34.dp)
            .background(pal.borderSubtle),
    )
}

@Composable
private fun ActionRow(
    icon: @Composable () -> Unit,
    tintAccent: Boolean,
    title: String,
    note: String,
    onClick: () -> Unit,
) {
    val pal = palette
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .background(if (tintAccent) pal.accentSubtle else pal.subtle, RoundedCornerShape(Radius.md.dp)),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.bodyStrong, color = pal.text)
            Text(note, style = Type.small, color = pal.textTertiary)
        }
        Icon(androidx.compose.ui.res.painterResource(com.zhijian.reading.R.drawable.ic_a_chevron_right), null, tint = pal.iconMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun LinkImportCard(vm: LibraryViewModel) {
    val pal = palette
    val strings = strings
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    suspend fun doImport() {
        if (url.isBlank()) return
        busy = true
        note = vm.importFromUrl(url).fold(
            onSuccess = { strings.imported(1) },
            onFailure = { strings.urlFailed },
        )
        busy = false
        if (note == strings.imported(1)) url = ""
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        // One row: icon · text field · action button tucked at the end.
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(pal.surface, RoundedCornerShape(Radius.lg.dp))
                .padding(start = 12.dp, end = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(androidx.compose.ui.res.painterResource(com.zhijian.reading.R.drawable.ic_a_link), null, tint = pal.accent, modifier = Modifier.size(17.dp))
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
                    keyboardActions = KeyboardActions(onGo = { scope.launch { doImport() } }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(Radius.pill.dp))
                    .background(if (url.isBlank()) pal.subtle else pal.accent)
                    .clickable(enabled = !busy && url.isNotBlank()) { scope.launch { doImport() } }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        color = pal.textOnAccent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp),
                    )
                } else {
                    Text(
                        strings.urlImportAction,
                        style = Type.smallStrong,
                        color = if (url.isBlank()) pal.textTertiary else pal.textOnAccent,
                    )
                }
            }
        }
        // One quiet line under the field: hint at rest, result on outcome.
        Spacer(Modifier.height(6.dp))
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

@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    val pal = palette
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(label, style = Type.label, color = pal.text, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

@Composable
private fun InfoLine(label: String, value: String, mono: Boolean = false) {
    val pal = palette
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(label, style = Type.label, color = pal.textTertiary, modifier = Modifier.width(84.dp))
        Text(
            value,
            style = if (mono) Type.numeric.copy(fontSize = 12.sp, lineHeight = 17.sp) else Type.label,
            color = pal.text,
            modifier = Modifier.weight(1f),
        )
    }
}

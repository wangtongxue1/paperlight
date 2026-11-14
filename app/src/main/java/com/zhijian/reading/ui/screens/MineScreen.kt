package com.zhijian.reading.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as SysSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhijian.reading.BuildConfig
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
 * Me: who this library is — the numbers, the look, and where the books
 * live. Groups read as cards on the paper, one quiet block each: stats,
 * appearance, shelf, library, about. Books come in from the shelf's
 * 导入 chooser, so there is no import entry here.
 */
@Composable
fun MineScreen(
    vm: LibraryViewModel,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
    /** Space kept free at the bottom for the floating dock. */
    bottomInset: Dp = 0.dp,
    /** The three numbers open what they count: shelf, reading, finished. */
    onOpenLibrary: () -> Unit = {},
    onOpenReading: () -> Unit = {},
    onOpenFinished: () -> Unit = {},
) {
    val pal = palette
    val strings = strings
    // Same cut the 正在阅读 page uses: started but not finished, so the
    // number and the page it opens always agree.
    val reading = vm.progress.values.count { it >= 0.01f && it < LibraryViewModel.FINISHED_AT }
    val finished = vm.progress.values.count { it >= LibraryViewModel.FINISHED_AT }
    val context = LocalContext.current

    // The move only takes effect once the system grants 所有文件访问, so it
    // happens when that page comes back — not when it is opened. Books live
    // in MyBooks by default; the only reason to land here is the first run
    // before that one-time grant.
    val allFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { vm.onStoragePermissionResult() }
    val writeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.onStoragePermissionResult() }

    fun requestStorage() {
        if (vm.requestPublicStorage()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(SysSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                .setData(Uri.parse("package:${context.packageName}"))
            runCatching { allFilesLauncher.launch(intent) }.onFailure {
                allFilesLauncher.launch(Intent(SysSettings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            writeLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

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
                .verticalScroll(rememberScrollState())
                .padding(bottom = bottomInset),
        ) {
            // ---- the library in three numbers, plus the time spent ----
            StatsCard(
                books = vm.books.size,
                reading = reading,
                finished = finished,
                timeText = readingTimeLabel(vm.readSeconds, strings),
                onBooks = onOpenLibrary,
                onReading = onOpenReading,
                onFinished = onOpenFinished,
            )

            // ---- appearance ----
            GroupCard(strings.appearance) {
                SettingRow(strings.theme) {
                    SegmentRow(
                        selected = vm.themeMode,
                        options = listOf(strings.themeSystem, strings.themeLight, strings.themeDark),
                        onSelect = { vm.setTheme(it) },
                    )
                }
                Hairline()
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
            }

            // ---- shelf ----
            GroupCard(strings.shelfTitle) {
                SettingRow(strings.defaultView) {
                    SegmentRow(
                        selected = vm.viewMode,
                        options = listOf(strings.viewShelf, strings.viewList),
                        onSelect = { vm.chooseViewMode(it) },
                    )
                }
            }

            // ---- library ----
            GroupCard(strings.librarySection) {
                InfoLine(strings.onDisk, humanSize(vm.books.sumOf { it.size }))
                Hairline()
                InfoLine(strings.folder, vm.libraryPath, mono = true)
                if (!vm.usingPublicStorage) {
                    // Only on the first run, before 所有文件访问 is granted:
                    // the books wait in the app folder until then.
                    Hairline()
                    ActionLine(
                        title = if (vm.storageBusy) strings.storageMoving else strings.storageToPublic,
                        note = strings.storageNote,
                        enabled = !vm.storageBusy,
                        onClick = { requestStorage() },
                    )
                }
            }

            // ---- about ----
            GroupCard(strings.about) {
                InfoLine(strings.appName, strings.version + " " + BuildConfig.VERSION_NAME)
                Spacer(Modifier.height(8.dp))
                Text(
                    strings.aboutBody,
                    style = Type.small,
                    color = pal.textSecondary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            Spacer(Modifier.height(120.dp))
        }
    }
}

/** One white card holding a titled group, the iOS grouped-list look. */
@Composable
private fun GroupCard(
    title: String,
    content: @Composable () -> Unit,
) {
    val pal = palette
    Column(Modifier.padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(14.dp))
        Overline(title)
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .background(pal.surface, RoundedCornerShape(Radius.lg.dp))
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            content()
        }
    }
}

/** Minutes under an hour read as 分钟, longer spans as 小时. */
private fun readingTimeLabel(totalSeconds: Long, strings: com.zhijian.reading.ui.AppStrings): String {
    val minutes = totalSeconds / 60
    return if (minutes < 60) strings.minutesShort(minutes)
    else strings.hoursShort(String.format(java.util.Locale.US, "%.1f", minutes / 60.0))
}

@Composable
private fun StatsCard(
    books: Int,
    reading: Int,
    finished: Int,
    timeText: String,
    onBooks: () -> Unit = {},
    onReading: () -> Unit = {},
    onFinished: () -> Unit = {},
) {
    val pal = palette
    val strings = strings
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .background(pal.surface, RoundedCornerShape(Radius.lg.dp))
            .padding(vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatCell(strings.bookCountLabel, "$books", Modifier.weight(1f), onClick = onBooks)
        StatDivider()
        StatCell(strings.statReading, "$reading", Modifier.weight(1f), onClick = onReading)
        StatDivider()
        StatCell(strings.finished, "$finished", Modifier.weight(1f), onClick = onFinished)
        StatDivider()
        StatCell(strings.statReadingTime, timeText, Modifier.weight(1.25f), valueSize = 17)
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueSize: Int = 24,
    onClick: (() -> Unit)? = null,
) {
    val pal = palette
    Column(
        modifier
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(Radius.md.dp)).clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            style = Type.display.copy(fontSize = valueSize.sp, lineHeight = (valueSize + 4).sp),
            color = pal.text,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text(label, style = Type.small, color = pal.textTertiary)
    }
}

@Composable
private fun StatDivider() {
    val pal = palette
    Box(
        Modifier
            .width(1.dp)
            .height(36.dp)
            .background(pal.borderSubtle),
    )
}

@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    val pal = palette
    Column(Modifier.padding(vertical = 10.dp)) {
        Text(label, style = Type.label, color = pal.textTertiary, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

@Composable
private fun InfoLine(label: String, value: String, mono: Boolean = false) {
    val pal = palette
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Type.body, color = pal.textSecondary, modifier = Modifier.width(84.dp))
        Text(
            value,
            style = if (mono) Type.numeric.copy(fontSize = 12.sp, lineHeight = 17.sp) else Type.body,
            color = pal.text,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A tappable row: the action in the accent colour, an optional note under it. */
@Composable
private fun ActionLine(
    title: String,
    note: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val pal = palette
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = Type.body,
                color = if (enabled) pal.accent else pal.textTertiary,
            )
            if (note != null) {
                Spacer(Modifier.height(4.dp))
                Text(note, style = Type.small, color = pal.textTertiary)
            }
        }
        Text("›", style = Type.body.copy(fontSize = 18.sp), color = pal.textTertiary)
    }
}

package com.zhijian.reading

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zhijian.reading.data.Book
import com.zhijian.reading.ui.LangPref
import com.zhijian.reading.ui.LibraryViewModel
import com.zhijian.reading.ui.LocalStrings
import com.zhijian.reading.ui.strings
import com.zhijian.reading.ui.stringsFor
import com.zhijian.reading.ui.screens.BrowseKind
import com.zhijian.reading.ui.screens.BrowseScreen
import com.zhijian.reading.ui.screens.DetailScreen
import com.zhijian.reading.ui.screens.EditScreen
import com.zhijian.reading.ui.screens.LibraryScreen
import com.zhijian.reading.ui.screens.LocalImportScreen
import com.zhijian.reading.ui.screens.MineScreen
import com.zhijian.reading.ui.screens.ReadingScreen
import com.zhijian.reading.ui.screens.ReaderScreen
import com.zhijian.reading.ui.theme.AppTheme
import com.zhijian.reading.ui.theme.Type
import com.zhijian.reading.ui.theme.palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: LibraryViewModel = viewModel()
            val systemDark = isSystemInDarkTheme()
            val dark = when (vm.themeMode) {
                1 -> false
                2 -> true
                else -> systemDark
            }
            val pref = when (vm.langPref) {
                "en" -> LangPref.EN
                "system" -> LangPref.SYSTEM
                else -> LangPref.ZH
            }
            val strings = stringsFor(pref, java.util.Locale.getDefault().language)
            AppTheme(dark = dark) {
                CompositionLocalProvider(LocalStrings provides strings) {
                    // The interface reads left to right in both languages.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        AppRoot(vm, dark)
                    }
                }
            }
        }
    }
}

private class Tab(
    val route: String,
    val icon: ImageVector,
    val iconSel: ImageVector,
    val label: String,
)

@Composable
private fun AppRoot(vm: LibraryViewModel, dark: Boolean) {
    val pal = palette
    val strings = strings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val nav = rememberNavController()
    var lastOpened by remember { mutableStateOf<Long?>(null) }
    var pendingDelete by remember { mutableStateOf<Book?>(null) }
    var pendingCover by remember { mutableStateOf<Book?>(null) }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    val tabs = remember(strings) {
        listOf(
            Tab("reading", Icons.Outlined.MenuBook, Icons.Filled.MenuBook, strings.navReading),
            Tab("library", Icons.Outlined.LibraryBooks, Icons.Filled.LibraryBooks, strings.navLibrary),
            Tab("mine", Icons.Outlined.Person, Icons.Filled.Person, strings.navMine),
        )
    }
    val onTab = tabs.any { it.route == route }

    val coverLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        val book = pendingCover
        pendingCover = null
        if (uri == null || book == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { shrinkCover(context.contentResolver, uri) }
            if (bytes != null) {
                vm.setCover(book, bytes)
                Toast.makeText(context, strings.coverUpdated, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, strings.coverFail, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun openFormat(book: Book, format: String) {
        when (format.uppercase()) {
            "EPUB" -> nav.navigate("read/${book.id}/EPUB")
            else -> {
                val file = vm.fileOf(book, format)
                if (file == null) {
                    Toast.makeText(context, "${strings.formats} $format", Toast.LENGTH_SHORT).show()
                    return
                }
                val opened = runCatching {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                    val mime = when (format.uppercase()) {
                        "PDF" -> "application/pdf"
                        "MOBI" -> "application/x-mobipocket-ebook"
                        "AZW3", "AZW" -> "application/vnd.amazon.ebook"
                        "TXT" -> "text/plain"
                        else -> "*/*"
                    }
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, mime)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        },
                    )
                }.isSuccess
                if (!opened) {
                    Toast.makeText(context, "${strings.formats} $format", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(pal.bgApp),
    ) {
        Box(Modifier.padding(top = statusTop)) {
            NavHost(navController = nav, startDestination = "library") {
                composable("library") {
                    LibraryScreen(
                        vm = vm,
                        lastOpened = lastOpened,
                        onOpenBook = { book ->
                            lastOpened = book.id
                            nav.navigate("book/${book.id}")
                        },
                        onBrowse = { kind -> nav.navigate("browse/${kind.name}") },
                        onOpenSettings = { nav.navigate("mine") },
                        onImport = { nav.navigate("localimport") },
                    )
                }

                composable(
                    "book/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    val id = entry.arguments?.getLong("id") ?: -1L
                    val book = vm.byId(id)
                    if (book == null) {
                        LaunchedEffect(Unit) { nav.popBackStack() }
                    } else {
                        DetailScreen(
                            book = book,
                            root = vm.library.root,
                            progress = vm.progress[book.id] ?: 0f,
                            onBack = { nav.popBackStack() },
                            onOpen = { format -> openFormat(book, format) },
                            onEdit = { nav.navigate("edit/${book.id}") },
                            onDelete = { pendingDelete = book },
                            onPickCover = {
                                pendingCover = book
                                coverLauncher.launch("image/*")
                            },
                            onRate = { rating -> scope.launch { vm.rate(book, rating) } },
                            onAuthor = { name ->
                                vm.setFilter(com.zhijian.reading.data.Filter.ByAuthor(name))
                                nav.popBackStack("library", inclusive = false)
                            },
                            onSeries = { name ->
                                vm.setFilter(com.zhijian.reading.data.Filter.BySeries(name))
                                nav.popBackStack("library", inclusive = false)
                            },
                            onTag = { tag ->
                                vm.setFilter(com.zhijian.reading.data.Filter.ByTag(tag))
                                nav.popBackStack("library", inclusive = false)
                            },
                        )
                    }
                }

                composable(
                    "edit/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    val id = entry.arguments?.getLong("id") ?: -1L
                    val book = vm.byId(id)
                    if (book == null) {
                        LaunchedEffect(Unit) { nav.popBackStack() }
                    } else {
                        EditScreen(
                            book = book,
                            onBack = { nav.popBackStack() },
                            onSave = { updated ->
                                scope.launch {
                                    vm.save(updated)
                                    Toast.makeText(context, strings.saved, Toast.LENGTH_SHORT).show()
                                    nav.popBackStack()
                                }
                            },
                        )
                    }
                }

                composable(
                    "read/{id}/{format}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType },
                        navArgument("format") { type = NavType.StringType },
                    ),
                ) { entry ->
                    val id = entry.arguments?.getLong("id") ?: -1L
                    val format = entry.arguments?.getString("format") ?: "EPUB"
                    val book = vm.byId(id)
                    val file = book?.let { vm.fileOf(it, format) }
                    if (book == null || file == null) {
                        LaunchedEffect(Unit) { nav.popBackStack() }
                    } else {
                        ReaderScreen(
                            book = book,
                            file = file,
                            dark = dark,
                            initialProgress = vm.progress[book.id] ?: 0f,
                            onProgress = { vm.markRead(book.id, it) },
                            onBack = { nav.popBackStack() },
                        )
                    }
                }

                composable("browse/{kind}") { entry ->
                    val kind = runCatching {
                        BrowseKind.valueOf(entry.arguments?.getString("kind") ?: "Authors")
                    }.getOrDefault(BrowseKind.Authors)
                    BrowseScreen(
                        vm = vm,
                        kind = kind,
                        onBack = { nav.popBackStack() },
                        onPick = { filter ->
                            vm.setFilter(filter)
                            nav.popBackStack("library", inclusive = false)
                        },
                    )
                }

                composable("localimport") {
                    LocalImportScreen(
                        vm = vm,
                        onBack = { nav.popBackStack() },
                    )
                }

                composable("reading") {
                    ReadingScreen(
                        vm = vm,
                        onOpenBook = { book ->
                            lastOpened = book.id
                            nav.navigate("book/${book.id}")
                        },
                        onContinue = { book ->
                            lastOpened = book.id
                            val fmt = book.formats.firstOrNull { it.format.equals("EPUB", true) }
                            if (fmt != null) nav.navigate("read/${book.id}/EPUB")
                            else nav.navigate("book/${book.id}")
                        },
                        onOpenSettings = { nav.navigate("mine") },
                    )
                }

                composable("mine") {
                    MineScreen(
                        vm = vm,
                        onImport = { nav.navigate("localimport") },
                    )
                }
            }
        }

        if (onTab) {
            // iOS style floating dock: a rounded capsule lifted off the
            // bottom edge, soft shadow, filled icon when selected.
            Row(
                Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .padding(start = 26.dp, end = 26.dp, bottom = navBottom + 12.dp)
                    .fillMaxWidth()
                    .height(60.dp)
                    .shadow(14.dp, RoundedCornerShape(30.dp), clip = false)
                    .background(pal.surface, RoundedCornerShape(30.dp))
                    .border(1.dp, pal.borderSubtle, RoundedCornerShape(30.dp)),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                tabs.forEach { tab ->
                    val selected = route == tab.route
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable {
                                if (!selected) {
                                    nav.navigate(tab.route) {
                                        popUpTo("library") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            if (selected) tab.iconSel else tab.icon,
                            contentDescription = tab.label,
                            modifier = Modifier.size(23.dp),
                            tint = if (selected) pal.accent else pal.iconMuted,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            tab.label,
                            style = Type.small.copy(fontSize = 10.sp, lineHeight = 12.sp),
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                            color = if (selected) pal.accent else pal.textTertiary,
                        )
                    }
                }
            }
        }

        // The confirmations, drawn over whatever page asked for them.
        pendingDelete?.let { book ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                containerColor = pal.surfaceRaised,
                titleContentColor = pal.text,
                textContentColor = pal.textSecondary,
                title = { Text(strings.removeTitle(book.title)) },
                text = { Text(strings.removeBody) },
                confirmButton = {
                    TextButton(onClick = {
                        pendingDelete = null
                        scope.launch {
                            vm.delete(book)
                            if (lastOpened == book.id) lastOpened = null
                            nav.popBackStack()
                        }
                    }) { Text(strings.remove, color = pal.danger) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text(strings.cancel, color = pal.textSecondary)
                    }
                },
            )
        }
    }
}

/** Reads a picked image and stores it no wider than 600px, as covers are. */
private fun shrinkCover(resolver: android.content.ContentResolver, uri: Uri): ByteArray? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val w = bounds.outWidth
    val h = bounds.outHeight
    if (w <= 0 || h <= 0) return null

    var sample = 1
    while (w / sample > 1200) sample *= 2

    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: return null

    val targetW = 600
    val scaled = if (bitmap.width > targetW) {
        val targetH = (bitmap.height.toFloat() * targetW / bitmap.width).toInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(bitmap, targetW, targetH, true).also { if (it != bitmap) bitmap.recycle() }
    } else bitmap

    ByteArrayOutputStream().use { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
        scaled.recycle()
        out.toByteArray()
    }
}.getOrNull()

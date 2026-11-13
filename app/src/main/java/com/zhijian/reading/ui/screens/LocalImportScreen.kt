package com.zhijian.reading.ui.screens

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhijian.reading.data.humanSize
import com.zhijian.reading.ui.LibraryViewModel
import com.zhijian.reading.ui.components.Hairline
import com.zhijian.reading.ui.strings
import com.zhijian.reading.ui.theme.Radius
import com.zhijian.reading.ui.theme.Type
import com.zhijian.reading.ui.theme.palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class TreeEntry(
    val docId: String,
    val name: String,
    val isDir: Boolean,
    val size: Long,
)

/**
 * In-app browser for books on this device. The system folder picker is
 * asked once ("choose your Downloads folder" kind of thing); after that
 * everything — browsing, multi-select, import — happens right here, so
 * there is no jumping out to a file manager and no storage permission
 * to grant. A plain one-shot file picker stays as a fallback.
 */
@Composable
fun LocalImportScreen(
    vm: LibraryViewModel,
    onBack: () -> Unit,
) {
    val pal = palette
    val strings = strings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var treeUri by remember { mutableStateOf(vm.importTreeUri?.let(Uri::parse)) }
    var rootName by remember { mutableStateOf<String?>(null) }
    var path by remember { mutableStateOf(listOf<Pair<String, String>>()) } // docId → name
    var entries by remember { mutableStateOf<List<TreeEntry>>(emptyList()) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var loading by remember { mutableStateOf(false) }

    suspend fun reload() {
        val uri = treeUri ?: return
        loading = true
        val parentId = path.lastOrNull()?.first
            ?: DocumentsContract.getTreeDocumentId(uri)
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val loaded = loadChildren(context.contentResolver, uri, parentId)
                val root = if (rootName == null) {
                    queryName(context.contentResolver, uri, DocumentsContract.getTreeDocumentId(uri))
                } else rootName
                loaded to root
            }
        }
        result.onSuccess { (loaded, root) ->
            entries = loaded
            rootName = root
        }.onFailure {
            // The grant was revoked upstream — start over at the picker.
            vm.setImportTree(null)
            treeUri = null
            entries = emptyList()
        }
        loading = false
    }

    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            vm.setImportTree(uri)
            treeUri = uri
            rootName = null
            path = emptyList()
            selected = emptySet()
            scope.launch { reload() }
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val result = vm.import(context.contentResolver, uris)
            val text = when {
                result.added > 0 -> strings.imported(result.added)
                else -> strings.importNone
            }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(treeUri, path) {
        if (treeUri != null) reload()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(pal.bgApp),
    ) {
        // ---- top bar ----
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
                Text(strings.localImportTitle, style = Type.title.copy(fontSize = 18.sp), color = pal.text)
                Text(
                    rootName ?: strings.localImportNoPerm,
                    style = Type.numeric,
                    color = pal.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (treeUri != null) {
                Text(
                    strings.localImportChange,
                    style = Type.smallStrong,
                    color = pal.accent,
                    modifier = Modifier
                        .clickable { treeLauncher.launch(null) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        if (treeUri == null) {
            // ---- first run: pick a folder once ----
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(84.dp))
                Box(
                    Modifier
                        .size(76.dp)
                        .background(pal.accentSubtle, RoundedCornerShape(Radius.lg.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        androidx.compose.ui.res.painterResource(com.zhijian.reading.R.drawable.ic_a_folder_open),
                        null,
                        tint = pal.accent,
                        modifier = Modifier.size(34.dp),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    strings.localImportPick,
                    style = Type.title.copy(fontSize = 19.sp),
                    color = pal.text,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    strings.localImportPickNote,
                    style = Type.small,
                    color = pal.textSecondary,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(26.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(Radius.pill.dp))
                        .background(pal.accent)
                        .clickable { treeLauncher.launch(null) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(strings.localImportPick, style = Type.bodyStrong, color = pal.textOnAccent)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    strings.localImportSelectFiles,
                    style = Type.smallStrong,
                    color = pal.accent,
                    modifier = Modifier
                        .clickable { pickLauncher.launch(arrayOf("*/*")) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
                Spacer(Modifier.height(28.dp))
                Hairline()
                Spacer(Modifier.height(16.dp))
                Text(
                    strings.localImportFormats,
                    style = Type.small,
                    color = pal.textTertiary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            return@Column
        }

        // ---- granted: browse in-app ----
        if (path.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        path = path.dropLast(1)
                        selected = emptySet()
                    }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    androidx.compose.ui.res.painterResource(com.zhijian.reading.R.drawable.ic_a_folder),
                    null,
                    tint = pal.iconMuted,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("… / ${path.last().second}", style = Type.small, color = pal.textSecondary)
            }
            Hairline(Modifier.padding(horizontal = 20.dp))
        }

        Box(Modifier.weight(1f)) {
            when {
                loading -> {
                    CircularProgressIndicator(
                        color = pal.accent,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(26.dp),
                    )
                }
                entries.isEmpty() -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(top = 80.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            androidx.compose.ui.res.painterResource(com.zhijian.reading.R.drawable.ic_a_folder_new),
                            null,
                            tint = pal.iconMuted,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(strings.localImportEmpty, style = Type.small, color = pal.textTertiary)
                    }
                }
                else -> {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(entries, key = { it.docId }) { entry ->
                            EntryRow(
                                entry = entry,
                                checked = entry.docId in selected,
                                onToggle = {
                                    selected = if (entry.docId in selected) selected - entry.docId
                                    else selected + entry.docId
                                },
                                onOpenFolder = {
                                    path = path + (entry.docId to entry.name)
                                    selected = emptySet()
                                },
                            )
                        }
                    }
                }
            }
        }

        // ---- import bar ----
        if (selected.isNotEmpty()) {
            Hairline()
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    strings.localImportSelected(selected.size),
                    style = Type.small,
                    color = pal.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(Radius.pill.dp))
                        .background(pal.accent)
                        .clickable {
                            val uris = selected.map {
                                DocumentsContract.buildDocumentUriUsingTree(treeUri!!, it)
                            }
                            selected = emptySet()
                            scope.launch {
                                val result = vm.import(context.contentResolver, uris)
                                val text = when {
                                    result.added > 0 -> strings.imported(result.added)
                                    else -> strings.importNone
                                }
                                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 22.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(strings.localImportAction, style = Type.bodyStrong, color = pal.textOnAccent)
                }
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: TreeEntry,
    checked: Boolean,
    onToggle: () -> Unit,
    onOpenFolder: () -> Unit,
) {
    val pal = palette
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = if (entry.isDir) onOpenFolder else onToggle)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(
                    if (entry.isDir) pal.subtle else pal.bgApp,
                    RoundedCornerShape(Radius.md.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (entry.isDir) androidx.compose.ui.res.painterResource(com.zhijian.reading.R.drawable.ic_a_folder)
                        else androidx.compose.ui.res.painterResource(com.zhijian.reading.R.drawable.ic_a_file),
                null,
                tint = if (entry.isDir) pal.iconMuted else pal.icon,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                style = Type.body,
                color = pal.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!entry.isDir && entry.size > 0) {
                Text(humanSize(entry.size), style = Type.small, color = pal.textTertiary)
            }
        }
        if (!entry.isDir) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (checked) pal.accent else pal.bgApp)
                    .border1dp(pal.borderSubtle, checked)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) {
                    Icon(
                        androidx.compose.ui.res.painterResource(com.zhijian.reading.R.drawable.ic_r_check),
                        null,
                        tint = pal.textOnAccent,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        } else {
            Spacer(Modifier.size(22.dp))
        }
    }
}

/** Hairline outline only when the checkbox is empty. */
private fun Modifier.border1dp(color: androidx.compose.ui.graphics.Color, filled: Boolean): Modifier =
    if (filled) this else border(1.dp, color, RoundedCornerShape(7.dp))

/** Direct children of one folder: directories first, then importable files. */
private fun loadChildren(resolver: ContentResolver, treeUri: Uri, parentDocId: String): List<TreeEntry> {
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
    val out = mutableListOf<TreeEntry>()
    resolver.query(
        children,
        arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        ),
        null,
        null,
        null,
    )?.use { c ->
        while (c.moveToNext()) {
            val docId = c.getString(0) ?: continue
            val name = c.getString(1) ?: continue
            val mime = c.getString(2) ?: ""
            val size = c.getLong(3)
            val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
            val ext = name.substringAfterLast('.', "").uppercase()
            if (isDir || ext in LibraryViewModel.SUPPORTED_FORMATS) {
                out += TreeEntry(docId, name, isDir, size)
            }
        }
    }
    return out.sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
}

private fun queryName(resolver: ContentResolver, treeUri: Uri, docId: String): String? =
    runCatching {
        val doc = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        resolver.query(
            doc,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

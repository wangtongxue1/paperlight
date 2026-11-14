package com.zhijian.reading.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.zhijian.reading.R
import com.zhijian.reading.ui.strings
import com.zhijian.reading.ui.theme.Radius
import com.zhijian.reading.ui.theme.Type
import com.zhijian.reading.ui.theme.palette

/**
 * The confirmation standing between a book and its files. Taking the book off
 * the shelf and erasing it from the phone are two separate wishes, so the
 * second one is an explicit tick: unticked, the file stays in MyBooks and can
 * be scanned back in.
 */
@Composable
fun DeleteConfirmDialog(
    title: String,
    /** "约 12.3 MB", or null when the sizes are unknown. */
    sizeNote: String?,
    onDismiss: () -> Unit,
    onConfirm: (deleteFiles: Boolean) -> Unit,
) {
    val pal = palette
    val s = strings
    var deleteFiles by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = pal.surfaceRaised,
        titleContentColor = pal.text,
        textContentColor = pal.textSecondary,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = if (deleteFiles) s.deleteFilesWarn else s.deleteKeepNote,
                    style = Type.body,
                    color = pal.textSecondary,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.sm.dp))
                        .clickable { deleteFiles = !deleteFiles }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CheckSquare(deleteFiles)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.deleteFilesOption, style = Type.label, color = pal.text)
                        Text(
                            text = if (sizeNote == null) s.deleteFilesHintBare
                            else s.deleteFilesHint(sizeNote),
                            style = Type.small,
                            color = pal.textTertiary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(deleteFiles) }) {
                Text(s.remove, color = pal.danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.cancel, color = pal.textSecondary) }
        },
    )
}

/** A 20dp tick box: quiet outline at rest, accent when armed. */
@Composable
private fun CheckSquare(on: Boolean) {
    val pal = palette
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .size(20.dp)
            .clip(shape)
            .background(if (on) pal.accent else Color.Transparent)
            .then(
                if (on) Modifier
                else Modifier.border(1.dp, pal.textTertiary.copy(alpha = 0.55f), shape)
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (on) {
            Icon(
                painter = painterResource(R.drawable.ic_r_check),
                contentDescription = null,
                tint = pal.textOnAccent,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

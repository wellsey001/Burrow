package com.wellsey.burrow.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.wellsey.burrow.data.FileEntry
import com.wellsey.burrow.data.Progress
import com.wellsey.burrow.ui.FilesViewModel
import com.wellsey.burrow.util.formatDate
import com.wellsey.burrow.util.formatSize
import com.wellsey.burrow.util.mimeType
import com.wellsey.burrow.util.validFileName

/** A dialog with one text box: used for rename, new folder and zip name. */
@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    selectBaseName: Boolean = false,
) {
    val end = if (selectBaseName && initial.contains('.')) initial.lastIndexOf('.') else initial.length
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, end))) }
    val focus = remember { FocusRequester() }
    val valid = validFileName(value.text.trim())
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                isError = value.text.isNotEmpty() && !valid,
                supportingText = if (value.text.isNotEmpty() && !valid) {
                    { Text("Names can't contain / \\ : * ? \" < > |") }
                } else null,
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(value.text.trim()) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDeleteDialog(files: List<FileEntry>, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val what = if (files.size == 1) "\"${files[0].name}\"" else "${files.size} items"
    val hasFolder = files.any { it.isDir }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $what?") },
        text = {
            Text(
                "This can't be undone." +
                    if (hasFolder) " Everything inside the selected folders will be deleted too." else ""
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun DetailsDialog(vm: FilesViewModel, entry: FileEntry, onDismiss: () -> Unit) {
    val folderInfo by produceState<Pair<Long, Int>?>(null, entry.path) {
        if (entry.isDir) value = vm.folderSize(entry)
    }
    val rows = buildList {
        add("Name" to entry.name)
        add("Location" to (entry.file.parent ?: ""))
        if (entry.isDir) {
            val info = folderInfo
            add("Size" to if (info == null) "Calculating…" else formatSize(info.first))
            add("Contains" to if (info == null) "…" else FilesViewModel.plural(info.second, "file"))
        } else {
            add("Size" to "${formatSize(entry.size)} (${"%,d".format(entry.size)} bytes)")
            add("Type" to mimeType(entry.file))
        }
        add("Modified" to formatDate(entry.lastModified))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Details") },
        text = {
            Column {
                rows.forEach { (label, value) ->
                    Row(Modifier.padding(vertical = 5.dp)) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(84.dp),
                        )
                        Text(value, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Shown while copying, moving, zipping or extracting. */
@Composable
fun ProgressBanner(progress: Progress, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val pct = if (progress.total > 0) " · ${(progress.fraction * 100).toInt()}%" else ""
                    Text("${progress.title}…$pct", style = MaterialTheme.typography.titleSmall)
                    if (progress.detail.isNotBlank()) {
                        Text(progress.detail, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
            if (progress.total > 0) {
                LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

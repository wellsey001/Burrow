package com.wellsey.burrow.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.wellsey.burrow.data.FileEntry
import com.wellsey.burrow.ui.FilesViewModel
import com.wellsey.burrow.ui.components.ConfirmDeleteDialog
import com.wellsey.burrow.ui.components.DetailsDialog
import com.wellsey.burrow.ui.components.NameDialog
import com.wellsey.burrow.util.openWith
import com.wellsey.burrow.util.shareFiles

/**
 * Top bar while files are selected. [canPasteHere] is false on list screens (search,
 * categories, cleanup), where "Compress" has no folder to put the zip in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    vm: FilesViewModel,
    chosen: List<FileEntry>,
    allCount: Int,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    canPasteHere: Boolean = true,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<FileEntry?>(null) }
    var zipping by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<FileEntry?>(null) }
    val single = chosen.singleOrNull()

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        navigationIcon = { IconButton(onClick = onClear) { Icon(Icons.Filled.Close, "Clear selection") } },
        title = { Text("${chosen.size} selected") },
        actions = {
            IconButton(onClick = { vm.copy(chosen); onClear() }) { Icon(Icons.Filled.ContentCopy, "Copy") }
            IconButton(onClick = { vm.cut(chosen); onClear() }) { Icon(Icons.Filled.ContentCut, "Move") }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (chosen.none { it.isDir }) {
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Filled.Share, null) },
                        onClick = { menu = false; shareFiles(context, chosen.map { it.file }) },
                    )
                }
                if (single != null && !single.isDir) {
                    DropdownMenuItem(
                        text = { Text("Open with…") },
                        leadingIcon = { Icon(Icons.Filled.OpenInNew, null) },
                        onClick = { menu = false; openWith(context, single.file) },
                    )
                }
                if (single != null) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, null) },
                        onClick = { menu = false; renaming = single },
                    )
                    DropdownMenuItem(
                        text = { Text("Details") },
                        leadingIcon = { Icon(Icons.Filled.Info, null) },
                        onClick = { menu = false; details = single },
                    )
                }
                if (canPasteHere) {
                    DropdownMenuItem(
                        text = { Text("Compress to zip") },
                        leadingIcon = { Icon(Icons.Filled.FolderZip, null) },
                        onClick = { menu = false; zipping = true },
                    )
                }
                if (chosen.size < allCount) {
                    DropdownMenuItem(
                        text = { Text("Select all") },
                        leadingIcon = { Icon(Icons.Filled.SelectAll, null) },
                        onClick = { menu = false; onSelectAll() },
                    )
                }
            }
        },
    )

    if (confirmDelete) {
        ConfirmDeleteDialog(
            files = chosen,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                vm.delete(chosen)
                onClear()
            },
        )
    }
    renaming?.let { e ->
        NameDialog(
            title = "Rename",
            initial = e.name,
            confirmLabel = "Rename",
            selectBaseName = !e.isDir,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                renaming = null
                if (name != e.name) vm.rename(e, name)
                onClear()
            },
        )
    }
    if (zipping) {
        NameDialog(
            title = "Compress to zip",
            initial = (single?.file?.nameWithoutExtension ?: "Archive"),
            confirmLabel = "Compress",
            onDismiss = { zipping = false },
            onConfirm = { name ->
                zipping = false
                vm.zip(chosen, name)
                onClear()
            },
        )
    }
    details?.let { e -> DetailsDialog(vm, e, onDismiss = { details = null }) }
}

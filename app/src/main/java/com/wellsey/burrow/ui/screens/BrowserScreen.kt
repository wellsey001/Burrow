package com.wellsey.burrow.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wellsey.burrow.data.FileEntry
import com.wellsey.burrow.data.SortBy
import com.wellsey.burrow.ui.FilesViewModel
import com.wellsey.burrow.ui.components.FileCell
import com.wellsey.burrow.ui.components.FileRow
import com.wellsey.burrow.ui.components.NameDialog
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    vm: FilesViewModel,
    onOpenFile: (FileEntry) -> Unit,
    onSearch: () -> Unit,
    onLeave: () -> Unit,
) {
    val dir by vm.dir.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val clip by vm.clip.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(setOf<String>()) }
    var sortMenu by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }

    LaunchedEffect(dir) { selected = emptySet() }

    val root = remember(dir) { vm.repo.rootOf(dir) }
    val atRoot = dir.absolutePath == root.absolutePath
    val chosen = entries.filter { it.path in selected }

    BackHandler {
        when {
            selected.isNotEmpty() -> selected = emptySet()
            !vm.up() -> onLeave()
        }
    }

    val toggle: (FileEntry) -> Unit = { e ->
        selected = if (e.path in selected) selected - e.path else selected + e.path
    }
    val click: (FileEntry) -> Unit = { e ->
        when {
            selected.isNotEmpty() -> toggle(e)
            e.isDir -> vm.open(e.file)
            else -> onOpenFile(e)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            if (selected.isNotEmpty()) {
                SelectionTopBar(
                    vm = vm,
                    chosen = chosen,
                    allCount = entries.size,
                    onSelectAll = { selected = entries.map { it.path }.toSet() },
                    onClear = { selected = emptySet() },
                )
            } else {
                TopAppBar(
                    navigationIcon = {
                        if (!atRoot) {
                            IconButton(onClick = { vm.up() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Up") }
                        }
                    },
                    title = { Text(if (atRoot) rootLabel(vm, root) else dir.name, maxLines = 1) },
                    actions = {
                        IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, "Search") }
                        IconButton(onClick = { vm.updatePrefs { it.copy(grid = !it.grid) } }) {
                            Icon(if (prefs.grid) Icons.Filled.ViewList else Icons.Filled.GridView, "Change view")
                        }
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort") }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            SortBy.entries.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.label) },
                                    leadingIcon = { RadioButton(selected = prefs.sortBy == s, onClick = null) },
                                    onClick = { vm.updatePrefs { it.copy(sortBy = s) } },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Reverse order") },
                                leadingIcon = { Checkbox(checked = prefs.descending, onCheckedChange = null) },
                                onClick = { vm.updatePrefs { it.copy(descending = !it.descending) } },
                            )
                            DropdownMenuItem(
                                text = { Text("Folders first") },
                                leadingIcon = { Checkbox(checked = prefs.foldersFirst, onCheckedChange = null) },
                                onClick = { vm.updatePrefs { it.copy(foldersFirst = !it.foldersFirst) } },
                            )
                            DropdownMenuItem(
                                text = { Text("Show hidden files") },
                                leadingIcon = { Checkbox(checked = prefs.showHidden, onCheckedChange = null) },
                                onClick = { vm.updatePrefs { it.copy(showHidden = !it.showHidden) } },
                            )
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (selected.isEmpty() && clip == null) {
                FloatingActionButton(onClick = { newFolder = true }) {
                    Icon(Icons.Filled.CreateNewFolder, "New folder")
                }
            }
        },
        bottomBar = {
            clip?.let { c ->
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, tonalElevation = 3.dp) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    ) {
                        Icon(Icons.Filled.ContentPaste, null)
                        Text(
                            "${FilesViewModel.plural(c.files.size, "item")} to ${if (c.cut) "move" else "copy"}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp),
                        )
                        TextButton(onClick = { vm.clearClip() }) { Text("Cancel") }
                        TextButton(onClick = { vm.paste() }) {
                            Text("Paste here", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Breadcrumbs(vm, dir, root)
            Box(Modifier.fillMaxSize()) {
                when {
                    loading && entries.isEmpty() ->
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    entries.isEmpty() -> Text(
                        "This folder is empty",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    prefs.grid -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(96.dp),
                        contentPadding = PaddingValues(8.dp, 4.dp, 8.dp, 88.dp),
                    ) {
                        items(entries, key = { it.path }) { e ->
                            FileCell(
                                entry = e,
                                selected = e.path in selected,
                                selectionMode = selected.isNotEmpty(),
                                onClick = { click(e) },
                                onLongClick = { toggle(e) },
                            )
                        }
                    }
                    else -> {
                        val listState = rememberLazyListState()
                        LaunchedEffect(dir) { listState.scrollToItem(0) }
                        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 88.dp)) {
                            items(entries, key = { it.path }) { e ->
                                FileRow(
                                    entry = e,
                                    selected = e.path in selected,
                                    selectionMode = selected.isNotEmpty(),
                                    onClick = { click(e) },
                                    onLongClick = { toggle(e) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (newFolder) {
        NameDialog(
            title = "New folder",
            initial = "",
            confirmLabel = "Create",
            onDismiss = { newFolder = false },
            onConfirm = { newFolder = false; vm.createFolder(it) },
        )
    }
}

private fun rootLabel(vm: FilesViewModel, root: File): String =
    vm.volumes.value.firstOrNull { it.root.absolutePath == root.absolutePath }?.name ?: "Internal storage"

/** "Internal storage › DCIM › Camera" — tap any part to jump there. */
@Composable
private fun Breadcrumbs(vm: FilesViewModel, dir: File, root: File) {
    val parts = remember(dir, root) {
        val list = mutableListOf<File>()
        var f: File? = dir
        while (f != null) {
            list.add(0, f)
            if (f.absolutePath == root.absolutePath) break
            f = f.parentFile
        }
        list
    }
    val state = rememberLazyListState()
    LaunchedEffect(parts.size) { if (parts.isNotEmpty()) state.scrollToItem(parts.lastIndex) }
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(parts) { i, f ->
            val last = i == parts.lastIndex
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (i == 0) rootLabel(vm, root) else f.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (last) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .clickable(enabled = !last) { vm.open(f) }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                )
                if (!last) {
                    Icon(
                        Icons.Filled.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

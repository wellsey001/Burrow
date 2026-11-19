package com.wellsey.burrow.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wellsey.burrow.data.FileEntry
import com.wellsey.burrow.data.ListKind
import com.wellsey.burrow.ui.FilesViewModel
import com.wellsey.burrow.ui.components.ConfirmDeleteDialog
import com.wellsey.burrow.ui.components.FileRow
import com.wellsey.burrow.util.formatSize

private val cleanupKinds = setOf(ListKind.Large, ListKind.Duplicates, ListKind.OldDownloads, ListKind.EmptyFolders, ListKind.Apks)

/** A list of files for a category, recent files, or a cleanup check. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    vm: FilesViewModel,
    kind: ListKind,
    onBack: () -> Unit,
    onOpenFile: (FileEntry) -> Unit,
    onShowInFolder: (FileEntry) -> Unit,
) {
    LaunchedEffect(kind) { vm.loadList(kind) }
    val lists by vm.lists.collectAsStateWithLifecycle()
    val listProgress by vm.listProgress.collectAsStateWithLifecycle()
    val scanCount by vm.scanCount.collectAsStateWithLifecycle()
    val sections = lists[kind]
    val all = sections?.flatMap { it.files }.orEmpty()
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirm by remember { mutableStateOf(false) }
    val chosen = all.filter { it.path in selected }
    val isCleanup = kind in cleanupKinds

    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

    val toggle: (FileEntry) -> Unit = { e ->
        selected = if (e.path in selected) selected - e.path else selected + e.path
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            if (selected.isNotEmpty()) {
                SelectionTopBar(
                    vm = vm,
                    chosen = chosen,
                    allCount = all.size,
                    onSelectAll = { selected = all.map { it.path }.toSet() },
                    onClear = { selected = emptySet() },
                    canPasteHere = false,
                )
            } else {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    title = {
                        Column {
                            Text(kind.title)
                            if (sections != null) {
                                Text(
                                    "${FilesViewModel.plural(all.size, if (kind == ListKind.EmptyFolders) "folder" else "file")} · " +
                                        formatSize(all.sumOf { it.size }),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    actions = {
                        if (kind == ListKind.Duplicates && sections != null && sections.isNotEmpty()) {
                            // Keep the oldest copy in each group, select the rest.
                            TextButton(onClick = {
                                selected = sections.flatMap { it.files.drop(1) }.map { it.path }.toSet()
                            }) { Text("Select extras") }
                        } else if (isCleanup && all.isNotEmpty()) {
                            TextButton(onClick = { selected = all.map { it.path }.toSet() }) { Text("Select all") }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (isCleanup && selected.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = { confirm = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Icon(Icons.Filled.Delete, null)
                        Text(
                            "  Delete ${FilesViewModel.plural(chosen.size, "item")} · ${formatSize(chosen.sumOf { it.size })}",
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                sections == null -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    val count = scanCount
                    Text(
                        listProgress ?: if (count != null) "Scanning storage… ${"%,d".format(count)} files" else "Loading…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                all.isEmpty() -> Text(
                    if (isCleanup) "Nothing to clean up here" else "No files found",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (kind == ListKind.Duplicates) {
                        item {
                            Text(
                                "Each group holds files with exactly the same contents. " +
                                    "\"Select extras\" keeps the oldest copy and selects the rest.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    sections.forEach { section ->
                        if (section.title.isNotEmpty()) {
                            item(key = "h:" + section.title + section.files.first().path) {
                                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
                                    Text(
                                        section.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        formatSize(section.files.first().size) + " each",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        items(section.files, key = { section.title + "|" + it.path }) { e ->
                            FileRow(
                                entry = e,
                                selected = e.path in selected,
                                selectionMode = selected.isNotEmpty(),
                                showPath = true,
                                onClick = {
                                    when {
                                        selected.isNotEmpty() -> toggle(e)
                                        e.isDir -> onShowInFolder(e)
                                        else -> onOpenFile(e)
                                    }
                                },
                                onLongClick = { toggle(e) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirm) {
        ConfirmDeleteDialog(
            files = chosen,
            onDismiss = { confirm = false },
            onConfirm = {
                confirm = false
                vm.delete(chosen) { selected = emptySet() }
            },
        )
    }
}

package com.wellsey.burrow.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wellsey.burrow.data.ListKind
import com.wellsey.burrow.ui.FilesViewModel
import com.wellsey.burrow.util.formatSize

private data class Check(val kind: ListKind, val icon: ImageVector, val description: String)

private val checks = listOf(
    Check(ListKind.Large, Icons.Filled.Straighten, "Files over 50 MB, biggest first"),
    Check(ListKind.Duplicates, Icons.Filled.ContentCopy, "Files with exactly the same contents"),
    Check(ListKind.OldDownloads, Icons.Filled.History, "Downloads you haven't touched in 3 months"),
    Check(ListKind.Apks, Icons.Filled.Android, "App installers you probably don't need any more"),
    Check(ListKind.EmptyFolders, Icons.Filled.FolderOff, "Folders with nothing in them"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanupScreen(vm: FilesViewModel, onOpenList: (ListKind) -> Unit) {
    val scan by vm.scan.collectAsStateWithLifecycle()
    val scanCount by vm.scanCount.collectAsStateWithLifecycle()
    val summary = remember(scan) { vm.cleanupSummary() }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("Clean up") },
                actions = { IconButton(onClick = { vm.rescan() }) { Icon(Icons.Filled.Refresh, "Scan again") } },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 4.dp, bottom = 24.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                val count = scanCount
                Text(
                    if (count != null) "Scanning storage… ${"%,d".format(count)} files so far"
                    else "Free up space by reviewing these. Nothing is deleted until you choose.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            items(checks, key = { it.kind.name }) { c ->
                val stat = summary[c.kind]
                Surface(
                    onClick = { onOpenList(c.kind) },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Icon(c.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.kind.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                c.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (stat != null) {
                            Text(
                                when {
                                    c.kind == ListKind.EmptyFolders -> "${stat.count}"
                                    stat.count == 0 -> "None"
                                    else -> formatSize(stat.bytes)
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Icon(Icons.Filled.ChevronRight, null)
                    }
                }
            }
        }
    }
}

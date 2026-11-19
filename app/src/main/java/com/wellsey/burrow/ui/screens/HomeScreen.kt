package com.wellsey.burrow.ui.screens

import android.os.Environment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wellsey.burrow.data.Category
import com.wellsey.burrow.data.FileEntry
import com.wellsey.burrow.data.ListKind
import com.wellsey.burrow.data.StorageVolume
import com.wellsey.burrow.ui.FilesViewModel
import com.wellsey.burrow.ui.components.FileIcon
import com.wellsey.burrow.util.formatSize
import com.wellsey.burrow.util.typeStyle
import java.io.File

private data class Shortcut(val label: String, val icon: ImageVector, val dir: File)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: FilesViewModel,
    onOpenDir: (File) -> Unit,
    onOpenList: (ListKind) -> Unit,
    onOpenFile: (FileEntry) -> Unit,
    onSearch: () -> Unit,
) {
    val volumes by vm.volumes.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val scanCount by vm.scanCount.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val recent = lists[ListKind.Recent]?.firstOrNull()?.files.orEmpty().take(20)
    val scan by vm.scan.collectAsStateWithLifecycle()
    LaunchedEffect(scan) { if (scan != null) vm.loadList(ListKind.Recent) }

    val root = vm.repo.internalRoot
    val shortcuts = listOf(
        Shortcut("Downloads", Icons.Filled.Download, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)),
        Shortcut("Camera", Icons.Filled.CameraAlt, File(root, "DCIM/Camera")),
        Shortcut("Pictures", Icons.Filled.Image, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)),
        Shortcut("Documents", Icons.Filled.Description, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)),
        Shortcut("Music", Icons.Filled.MusicNote, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)),
        Shortcut("Movies", Icons.Filled.Movie, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)),
    ).filter { it.dir.isDirectory }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("Burrow") },
                actions = {
                    IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, "Search") }
                    IconButton(onClick = { vm.refreshVolumes(); vm.rescan() }) { Icon(Icons.Filled.Refresh, "Refresh") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(volumes, key = { it.root.path }) { v ->
                VolumeCard(v, onClick = { onOpenDir(v.root) })
            }

            item { SectionTitle("Folders") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(shortcuts, key = { it.label }) { s ->
                        Surface(
                            onClick = { onOpenDir(s.dir) },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp).width(64.dp),
                            ) {
                                Icon(s.icon, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(6.dp))
                                Text(s.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Categories", Modifier.weight(1f))
                    val count = scanCount
                    if (count != null) {
                        Text(
                            "Scanning… ${"%,d".format(count)} files",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                }
            }
            val cats = Category.entries.chunked(2)
            items(cats) { pair ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    pair.forEach { c ->
                        val kind = ListKind.entries.first { it.category == c }
                        val stat = stats[c]
                        val style = typeStyle(FileEntry("x.${sampleExt(c)}", "x.${sampleExt(c)}", false, 0, 0))
                        Surface(
                            onClick = { onOpenList(kind) },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.weight(1f),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp),
                            ) {
                                Icon(style.icon, null, tint = style.tint, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(c.label, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        if (stat == null) "…" else "${stat.count} · ${formatSize(stat.bytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (recent.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("Recent", Modifier.weight(1f))
                        TextButton(onClick = { onOpenList(ListKind.Recent) }, modifier = Modifier.padding(end = 8.dp)) {
                            Text("See all")
                        }
                    }
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(recent, key = { it.path }) { f ->
                            Surface(
                                onClick = { onOpenFile(f) },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Column(Modifier.padding(8.dp).width(96.dp)) {
                                    FileIcon(f, 96.dp)
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        f.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun sampleExt(c: Category) = when (c) {
    Category.Images -> "jpg"; Category.Videos -> "mp4"; Category.Audio -> "mp3"
    Category.Documents -> "docx"; Category.Archives -> "zip"; Category.Apks -> "apk"
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun VolumeCard(v: StorageVolume, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (v.name == "Internal storage") Icons.Filled.Smartphone else Icons.Filled.SdStorage,
                    null,
                )
                Spacer(Modifier.width(10.dp))
                Text(v.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "${formatSize(v.free)} free",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { if (v.total > 0) v.used.toFloat() / v.total else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${formatSize(v.used)} used of ${formatSize(v.total)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

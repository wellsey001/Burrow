package com.wellsey.burrow.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wellsey.burrow.data.FileEntry
import com.wellsey.burrow.ui.FilesViewModel
import com.wellsey.burrow.ui.components.FileRow
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement

/** Searches file and folder names, either everywhere or just inside the folder you were in. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    vm: FilesViewModel,
    onBack: () -> Unit,
    onOpenFile: (FileEntry) -> Unit,
    onOpenFolder: (FileEntry) -> Unit,
) {
    val dir by vm.dir.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var everywhere by rememberSaveable { mutableStateOf(true) }
    var results by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val root = if (everywhere) vm.repo.internalRoot else dir

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(query, everywhere, prefs.showHidden) {
        if (query.isBlank()) {
            results = emptyList(); searching = false; return@LaunchedEffect
        }
        delay(250) // wait for typing to pause
        searching = true
        vm.repo.search(root, query, prefs.showHidden).collect { results = it }
        searching = false
    }
    BackHandler { onBack() }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search file names") },
            leadingIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Clear") }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .focusRequester(focus),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            FilterChip(selected = everywhere, onClick = { everywhere = true }, label = { Text("Everywhere") })
            if (dir.absolutePath != vm.repo.internalRoot.absolutePath) {
                FilterChip(
                    selected = !everywhere,
                    onClick = { everywhere = false },
                    label = { Text("In \"${dir.name}\"") },
                )
            }
        }
        if (searching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        Box(Modifier.fillMaxSize()) {
            when {
                query.isBlank() -> {}
                results.isEmpty() && !searching -> Text(
                    "No matches for \"$query\"",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp, top = 4.dp)) {
                    items(results, key = { it.path }) { e ->
                        FileRow(
                            entry = e,
                            selected = false,
                            selectionMode = false,
                            showPath = true,
                            onClick = { if (e.isDir) onOpenFolder(e) else onOpenFile(e) },
                            onLongClick = { onOpenFolder(FileEntry.from(e.file.parentFile ?: e.file)) },
                        )
                    }
                }
            }
        }
    }
}

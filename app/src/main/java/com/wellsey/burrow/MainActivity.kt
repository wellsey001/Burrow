package com.wellsey.burrow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wellsey.burrow.data.FileEntry
import com.wellsey.burrow.data.ListKind
import com.wellsey.burrow.ui.FilesViewModel
import com.wellsey.burrow.ui.components.ProgressBanner
import com.wellsey.burrow.ui.screens.BrowserScreen
import com.wellsey.burrow.ui.screens.CleanupScreen
import com.wellsey.burrow.ui.screens.HomeScreen
import com.wellsey.burrow.ui.screens.ListScreen
import com.wellsey.burrow.ui.screens.PermissionScreen
import com.wellsey.burrow.ui.screens.SearchScreen
import com.wellsey.burrow.ui.screens.hasAllFilesAccess
import com.wellsey.burrow.ui.theme.BurrowTheme
import com.wellsey.burrow.util.openFile
import com.wellsey.burrow.util.openWith
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: FilesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BurrowTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    BurrowRoot(vm)
                }
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home("Home", Icons.Outlined.Home, Icons.Filled.Home),
    Browse("Browse", Icons.Outlined.Folder, Icons.Filled.Folder),
    Cleanup("Clean up", Icons.Outlined.CleaningServices, Icons.Filled.CleaningServices),
}

@Composable
private fun BurrowRoot(vm: FilesViewModel) {
    var granted by remember { mutableStateOf(hasAllFilesAccess()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = hasAllFilesAccess()
        if (granted) {
            vm.refreshVolumes()
            vm.reload()
        }
    }
    LaunchedEffect(granted) { if (granted) vm.start() }

    if (!granted) {
        PermissionScreen()
        return
    }

    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val progress by vm.progress.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var zipChoice by remember { mutableStateOf<FileEntry?>(null) }
    val nav = rememberNavController()

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val openEntry: (FileEntry) -> Unit = { e ->
        when {
            e.isDir -> {
                vm.open(e.file); tab = Tab.Browse
                nav.popBackStack("main", inclusive = false)
            }
            e.extension == "zip" -> zipChoice = e
            !openFile(context, e.file) -> openWith(context, e.file)
        }
    }
    val showInFolder: (FileEntry) -> Unit = { e ->
        vm.open(if (e.isDir) e.file else e.file.parentFile ?: e.file)
        tab = Tab.Browse
        nav.popBackStack("main", inclusive = false)
    }
    val openDir: (File) -> Unit = { d ->
        vm.open(d); tab = Tab.Browse
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                progress?.let { ProgressBanner(it, onCancel = { vm.cancelOp() }) }
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = {
                                tab = t
                                nav.popBackStack("main", inclusive = false)
                            },
                            icon = { Icon(if (tab == t) t.selectedIcon else t.icon, null) },
                            label = { Text(t.label) },
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
            NavHost(
                navController = nav,
                startDestination = "main",
                enterTransition = { fadeIn(tween(150)) },
                exitTransition = { fadeOut(tween(150)) },
            ) {
                composable("main") {
                    when (tab) {
                        Tab.Home -> HomeScreen(
                            vm = vm,
                            onOpenDir = openDir,
                            onOpenList = { nav.navigate("list/${it.name}") },
                            onOpenFile = openEntry,
                            onSearch = { nav.navigate("search") },
                        )
                        Tab.Browse -> BrowserScreen(
                            vm = vm,
                            onOpenFile = openEntry,
                            onSearch = { nav.navigate("search") },
                            onLeave = { tab = Tab.Home },
                        )
                        Tab.Cleanup -> CleanupScreen(vm, onOpenList = { nav.navigate("list/${it.name}") })
                    }
                }
                composable("list/{kind}") { entry ->
                    val kind = runCatching { ListKind.valueOf(entry.arguments?.getString("kind") ?: "") }
                        .getOrDefault(ListKind.Recent)
                    ListScreen(
                        vm = vm,
                        kind = kind,
                        onBack = { nav.popBackStack() },
                        onOpenFile = openEntry,
                        onShowInFolder = showInFolder,
                    )
                }
                composable("search") {
                    SearchScreen(
                        vm = vm,
                        onBack = { nav.popBackStack() },
                        onOpenFile = openEntry,
                        onOpenFolder = showInFolder,
                    )
                }
            }
        }
    }

    zipChoice?.let { e ->
        AlertDialog(
            onDismissRequest = { zipChoice = null },
            title = { Text(e.name) },
            text = { Text("Extract this zip into a new folder next to it, or open it in another app?") },
            confirmButton = {
                TextButton(onClick = { zipChoice = null; vm.unzip(e); tab = Tab.Browse }) { Text("Extract") }
            },
            dismissButton = {
                TextButton(onClick = { zipChoice = null; openWith(context, e.file) }) { Text("Open with…") }
            },
        )
    }
}

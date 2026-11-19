package com.wellsey.burrow.ui

import android.app.Application
import android.content.Context
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wellsey.burrow.data.Category
import com.wellsey.burrow.data.Clip
import com.wellsey.burrow.data.FileEntry
import com.wellsey.burrow.data.FileRepository
import com.wellsey.burrow.data.FileSection
import com.wellsey.burrow.data.ListKind
import com.wellsey.burrow.data.Progress
import com.wellsey.burrow.data.SortBy
import com.wellsey.burrow.data.StorageScan
import com.wellsey.burrow.data.StorageVolume
import com.wellsey.burrow.data.ViewPrefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class CategoryStat(val count: Int, val bytes: Long)

class FilesViewModel(app: Application) : AndroidViewModel(app) {

    val repo = FileRepository(app)
    private val prefsStore = app.getSharedPreferences("view", Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- browsing
    private val _dir = MutableStateFlow(repo.internalRoot)
    val dir: StateFlow<File> = _dir.asStateFlow()

    private val _entries = MutableStateFlow<List<FileEntry>>(emptyList())
    val entries: StateFlow<List<FileEntry>> = _entries.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _prefs = MutableStateFlow(loadPrefs())
    val prefs: StateFlow<ViewPrefs> = _prefs.asStateFlow()

    private val _volumes = MutableStateFlow<List<StorageVolume>>(emptyList())
    val volumes: StateFlow<List<StorageVolume>> = _volumes.asStateFlow()

    // ---------------------------------------------------------------- clipboard & progress
    private val _clip = MutableStateFlow<Clip?>(null)
    val clip: StateFlow<Clip?> = _clip.asStateFlow()

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress.asStateFlow()
    private var opJob: Job? = null

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    // ---------------------------------------------------------------- storage scan
    private val _scan = MutableStateFlow<StorageScan?>(null)
    val scan: StateFlow<StorageScan?> = _scan.asStateFlow()
    private val _scanCount = MutableStateFlow<Int?>(null)
    /** Files found so far while a scan is running (null when not scanning). */
    val scanCount: StateFlow<Int?> = _scanCount.asStateFlow()
    private var scanJob: Job? = null

    private val _stats = MutableStateFlow<Map<Category, CategoryStat>>(emptyMap())
    val stats: StateFlow<Map<Category, CategoryStat>> = _stats.asStateFlow()

    private val _lists = MutableStateFlow<Map<ListKind, List<FileSection>>>(emptyMap())
    val lists: StateFlow<Map<ListKind, List<FileSection>>> = _lists.asStateFlow()
    private val _listProgress = MutableStateFlow<String?>(null)
    val listProgress: StateFlow<String?> = _listProgress.asStateFlow()

    private var started = false

    fun start() {
        if (started) return
        started = true
        refreshVolumes()
        reload()
        rescan()
    }

    fun refreshVolumes() {
        viewModelScope.launch { _volumes.value = runCatching { repo.volumes() }.getOrDefault(emptyList()) }
    }

    // ---------------------------------------------------------------- navigation

    fun open(dir: File) {
        _dir.value = dir
        reload()
    }

    /** Goes up one folder. Returns false when already at the top of a storage volume. */
    fun up(): Boolean {
        val current = _dir.value
        val root = repo.rootOf(current)
        if (current.absolutePath == root.absolutePath) return false
        open(current.parentFile ?: return false)
        return true
    }

    fun reload() {
        viewModelScope.launch {
            _loading.value = true
            _entries.value = repo.list(_dir.value, _prefs.value)
            _loading.value = false
        }
    }

    fun updatePrefs(change: (ViewPrefs) -> ViewPrefs) {
        val next = change(_prefs.value)
        _prefs.value = next
        prefsStore.edit()
            .putString("sort", next.sortBy.name)
            .putBoolean("desc", next.descending)
            .putBoolean("hidden", next.showHidden)
            .putBoolean("grid", next.grid)
            .putBoolean("foldersFirst", next.foldersFirst)
            .apply()
        reload()
    }

    private fun loadPrefs() = ViewPrefs(
        sortBy = runCatching { SortBy.valueOf(prefsStore.getString("sort", "Name")!!) }.getOrDefault(SortBy.Name),
        descending = prefsStore.getBoolean("desc", false),
        showHidden = prefsStore.getBoolean("hidden", false),
        grid = prefsStore.getBoolean("grid", false),
        foldersFirst = prefsStore.getBoolean("foldersFirst", true),
    )

    // ---------------------------------------------------------------- operations

    fun message(text: String) {
        _messages.tryEmit(text)
    }

    fun copy(files: List<FileEntry>) {
        _clip.value = Clip(files, cut = false)
        message("${plural(files.size, "item")} ready to paste. Open a folder and tap Paste.")
    }

    fun cut(files: List<FileEntry>) {
        _clip.value = Clip(files, cut = true)
        message("${plural(files.size, "item")} ready to move. Open a folder and tap Paste.")
    }

    fun clearClip() {
        _clip.value = null
    }

    fun paste() {
        val clip = _clip.value ?: return
        val dest = _dir.value
        runOp(if (clip.cut) "Moving" else "Copying") {
            val ok = repo.copyOrMove(clip.files.map { it.file }, dest, clip.cut) { done, total, name ->
                _progress.value = Progress(if (clip.cut) "Moving" else "Copying", done, total, name)
            }
            _clip.value = null
            val verb = if (clip.cut) "Moved" else "Copied"
            message(if (ok == clip.files.size) "$verb ${plural(ok, "item")}" else "$verb $ok of ${clip.files.size}")
        }
    }

    fun delete(files: List<FileEntry>, onDone: () -> Unit = {}) {
        runOp("Deleting") {
            _progress.value = Progress("Deleting", 0, 0, plural(files.size, "item"))
            val ok = repo.delete(files.map { it.file })
            message(if (ok == files.size) "Deleted ${plural(ok, "item")}" else "Deleted $ok of ${files.size}")
            // Take deleted files out of any open lists straight away.
            val gone = files.map { it.path }.toSet()
            _lists.value = _lists.value.mapValues { (_, sections) ->
                sections.map { s -> s.copy(files = s.files.filterNot { it.path in gone }) }
                    .filter { it.files.isNotEmpty() }
            }
            _scan.value = _scan.value?.let { s ->
                s.copy(files = s.files.filterNot { it.path in gone }, emptyDirs = s.emptyDirs.filterNot { it.path in gone })
            }
            recomputeStats()
            onDone()
        }
    }

    fun rename(entry: FileEntry, newName: String) {
        viewModelScope.launch {
            val result = repo.rename(entry.file, newName)
            message(if (result != null) "Renamed to $newName" else "Couldn't rename: that name may already exist")
            reload()
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            val dir = repo.createFolder(_dir.value, name)
            if (dir == null) message("Couldn't create \"$name\"") else open(dir)
        }
    }

    fun zip(files: List<FileEntry>, name: String) {
        val target = repo.uniqueTarget(_dir.value, if (name.endsWith(".zip", true)) name else "$name.zip")
        runOp("Compressing") {
            val ok = repo.zip(files.map { it.file }, target) { done, total, current ->
                _progress.value = Progress("Compressing", done, total, current)
            }
            message(if (ok) "Created ${target.name}" else "Couldn't create the zip")
        }
    }

    fun unzip(entry: FileEntry) {
        runOp("Extracting") {
            val folder = repo.unzip(entry.file) { done, total, current ->
                _progress.value = Progress("Extracting", done, total, current)
            }
            if (folder != null) {
                message("Extracted to ${folder.name}")
                open(folder)
            } else {
                message("Couldn't extract ${entry.name}")
            }
        }
    }

    fun cancelOp() {
        opJob?.cancel()
        _progress.value = null
        message("Cancelled")
        reload()
    }

    private fun runOp(title: String, block: suspend () -> Unit) {
        if (opJob?.isActive == true) {
            message("Wait for the current task to finish")
            return
        }
        opJob = viewModelScope.launch {
            _progress.value = Progress(title, 0, 0)
            try {
                block()
            } finally {
                _progress.value = null
                reload()
                refreshVolumes()
            }
        }
    }

    suspend fun folderSize(entry: FileEntry): Pair<Long, Int> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            var bytes = 0L
            var count = 0
            entry.file.walkTopDown().forEach { if (it.isFile) { bytes += it.length(); count++ } }
            bytes to count
        }

    // ---------------------------------------------------------------- scan, categories, cleanup

    fun rescan() {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            _scanCount.value = 0
            val result = repo.scanAll { _scanCount.value = it }
            _scan.value = result
            _scanCount.value = null
            _lists.value = emptyMap()
            recomputeStats()
        }
    }

    private fun recomputeStats() {
        val files = _scan.value?.files ?: return
        val map = HashMap<Category, CategoryStat>()
        for (f in files) {
            val c = Category.of(f.extension) ?: continue
            val prev = map[c] ?: CategoryStat(0, 0)
            map[c] = CategoryStat(prev.count + 1, prev.bytes + f.size)
        }
        _stats.value = map
    }

    /** Works out the files for a list screen from the latest scan. */
    fun loadList(kind: ListKind) {
        viewModelScope.launch {
            val scan = _scan.value ?: run {
                rescan(); scanJob?.join(); _scan.value
            } ?: return@launch
            val files = scan.files
            val sections: List<FileSection> = when (kind) {
                ListKind.Duplicates -> {
                    _listProgress.value = "Comparing files…"
                    val groups = repo.findDuplicates(files) { done, total ->
                        _listProgress.value = "Comparing files… $done / $total"
                    }
                    _listProgress.value = null
                    groups
                }
                ListKind.Large -> listOf(FileSection("", files.filter { it.size >= 50L * 1024 * 1024 }
                    .sortedByDescending { it.size }.take(300)))
                ListKind.Recent -> {
                    val since = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
                    listOf(FileSection("", files.filter { it.lastModified >= since }
                        .sortedByDescending { it.lastModified }.take(300)))
                }
                ListKind.OldDownloads -> {
                    val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
                    val before = System.currentTimeMillis() - 90L * 24 * 3600 * 1000
                    listOf(FileSection("", files.filter { it.path.startsWith("$dl/") && it.lastModified < before }
                        .sortedBy { it.lastModified }))
                }
                ListKind.EmptyFolders -> listOf(FileSection("", scan.emptyDirs.sortedBy { it.path }))
                else -> {
                    val cat = kind.category
                    listOf(FileSection("", files.filter { Category.of(it.extension) == cat }
                        .sortedByDescending { it.lastModified }))
                }
            }
            _lists.value = _lists.value + (kind to sections.filter { it.files.isNotEmpty() })
        }
    }

    /** Quick numbers for the cleanup screen. */
    fun cleanupSummary(): Map<ListKind, CategoryStat> {
        val scan = _scan.value ?: return emptyMap()
        val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        val before = System.currentTimeMillis() - 90L * 24 * 3600 * 1000
        val large = scan.files.filter { it.size >= 50L * 1024 * 1024 }
        val old = scan.files.filter { it.path.startsWith("$dl/") && it.lastModified < before }
        val apks = scan.files.filter { Category.of(it.extension) == Category.Apks }
        return mapOf(
            ListKind.Large to CategoryStat(large.size, large.sumOf { it.size }),
            ListKind.OldDownloads to CategoryStat(old.size, old.sumOf { it.size }),
            ListKind.Apks to CategoryStat(apks.size, apks.sumOf { it.size }),
            ListKind.EmptyFolders to CategoryStat(scan.emptyDirs.size, 0),
        )
    }

    companion object {
        fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"
    }
}

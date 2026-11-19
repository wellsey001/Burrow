package com.wellsey.burrow.data

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext

/**
 * All the file work: listing, copying, moving, deleting, zipping, searching and the
 * storage scan behind categories and cleanup. Everything runs off the main thread.
 */
class FileRepository(context: Context) {

    private val appContext = context.applicationContext

    val internalRoot: File = Environment.getExternalStorageDirectory()

    fun volumes(): List<StorageVolume> {
        val sm = appContext.getSystemService(StorageManager::class.java)
        val result = mutableListOf<StorageVolume>()
        for (v in sm.storageVolumes) {
            val dir = v.directory ?: continue
            if (!dir.canRead()) continue
            runCatching {
                val stat = StatFs(dir.path)
                result += StorageVolume(
                    name = if (v.isPrimary) "Internal storage" else v.getDescription(appContext),
                    root = dir,
                    total = stat.totalBytes,
                    free = stat.availableBytes,
                )
            }
        }
        if (result.isEmpty()) {
            val stat = StatFs(internalRoot.path)
            result += StorageVolume("Internal storage", internalRoot, stat.totalBytes, stat.availableBytes)
        }
        return result
    }

    /** The storage root that contains [dir] (internal storage or an SD card). */
    fun rootOf(dir: File): File =
        volumes().map { it.root }.firstOrNull { dir.absolutePath.startsWith(it.absolutePath) } ?: internalRoot

    suspend fun list(dir: File, prefs: ViewPrefs): List<FileEntry> = withContext(Dispatchers.IO) {
        val children = dir.listFiles() ?: return@withContext emptyList()
        val entries = children
            .filter { prefs.showHidden || !it.name.startsWith(".") }
            .map { FileEntry.from(it, countChildren = true) }
        sort(entries, prefs)
    }

    fun sort(entries: List<FileEntry>, prefs: ViewPrefs): List<FileEntry> {
        val base: Comparator<FileEntry> = when (prefs.sortBy) {
            SortBy.Name -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortBy.Date -> compareBy { it.lastModified }
            SortBy.Size -> compareBy { if (it.isDir) it.childCount.toLong() else it.size }
            SortBy.Type -> compareBy<FileEntry> { it.extension }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        }
        val ordered = if (prefs.descending) base.reversed() else base
        val withFolders = if (prefs.foldersFirst) compareBy<FileEntry> { !it.isDir }.then(ordered) else ordered
        return entries.sortedWith(withFolders)
    }

    // ---------------------------------------------------------------- operations

    /** Picks a free name like "Photo (2).jpg" when [name] already exists in [dir]. */
    fun uniqueTarget(dir: File, name: String): File {
        var target = File(dir, name)
        if (!target.exists()) return target
        val dot = name.lastIndexOf('.')
        val (base, ext) = if (dot > 0) name.substring(0, dot) to name.substring(dot) else name to ""
        var n = 1
        while (target.exists()) {
            target = File(dir, "$base ($n)$ext")
            n++
        }
        return target
    }

    fun totalSize(files: List<File>): Long = files.sumOf { f ->
        if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else f.length()
    }

    /**
     * Copies (or moves) [sources] into [destDir]. Calls [onProgress] with bytes done / total.
     * Returns how many top-level items succeeded.
     */
    suspend fun copyOrMove(
        sources: List<File>,
        destDir: File,
        move: Boolean,
        onProgress: (done: Long, total: Long, current: String) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val total = totalSize(sources)
        var done = 0L
        var ok = 0
        val changed = mutableListOf<String>()
        for (src in sources) {
            coroutineContext.ensureActive()
            if (destDir.absolutePath.startsWith(src.absolutePath + "/") || destDir == src) continue // into itself
            val target = if (move && src.parentFile == destDir) continue else uniqueTarget(destDir, src.name)
            try {
                // A move on the same storage is just a rename: instant, no copying.
                if (move && src.renameTo(target)) {
                    done += if (src.isDirectory) 0 else target.length()
                    changed += src.absolutePath; changed += target.absolutePath
                    ok++
                    continue
                }
                copyRecursively(src, target) { bytes ->
                    done += bytes
                    onProgress(done, total, src.name)
                }
                if (move) src.deleteRecursively()
                changed += target.absolutePath
                if (move) changed += src.absolutePath
                ok++
            } catch (e: IOException) {
                target.deleteRecursively()
            }
            onProgress(done, total, src.name)
        }
        scan(changed)
        ok
    }

    private suspend fun copyRecursively(src: File, target: File, onBytes: (Long) -> Unit) {
        if (src.isDirectory) {
            if (!target.mkdirs() && !target.isDirectory) throw IOException("Can't create ${target.path}")
            for (child in src.listFiles().orEmpty()) {
                coroutineContext.ensureActive()
                copyRecursively(child, File(target, child.name), onBytes)
            }
        } else {
            FileInputStream(src).use { input ->
                FileOutputStream(target).use { output ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        onBytes(n.toLong())
                    }
                }
            }
            target.setLastModified(src.lastModified())
        }
    }

    suspend fun delete(files: List<File>): Int = withContext(Dispatchers.IO) {
        var ok = 0
        val changed = mutableListOf<String>()
        for (f in files) {
            val inside = if (f.isDirectory) f.walkTopDown().filter { it.isFile }.map { it.absolutePath }.toList() else emptyList()
            if (f.deleteRecursively()) {
                ok++
                changed += f.absolutePath
                changed += inside
            }
        }
        scan(changed)
        ok
    }

    suspend fun rename(file: File, newName: String): File? = withContext(Dispatchers.IO) {
        val target = File(file.parentFile, newName)
        if (target.exists()) return@withContext null
        if (file.renameTo(target)) {
            scan(listOf(file.absolutePath, target.absolutePath))
            target
        } else null
    }

    suspend fun createFolder(parent: File, name: String): File? = withContext(Dispatchers.IO) {
        val dir = File(parent, name)
        if (dir.exists()) null else if (dir.mkdirs()) dir else null
    }

    suspend fun zip(
        files: List<File>,
        target: File,
        onProgress: (done: Long, total: Long, current: String) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val total = totalSize(files)
        var done = 0L
        try {
            ZipOutputStream(FileOutputStream(target).buffered()).use { zos ->
                for (root in files) {
                    val base = root.parentFile ?: root
                    root.walkTopDown().forEach { f ->
                        coroutineContext.ensureActive()
                        val name = f.relativeTo(base).path.replace('\\', '/') + if (f.isDirectory) "/" else ""
                        val entry = ZipEntry(name).apply { time = f.lastModified() }
                        zos.putNextEntry(entry)
                        if (f.isFile) {
                            FileInputStream(f).use { input ->
                                val buf = ByteArray(128 * 1024)
                                while (true) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    zos.write(buf, 0, n)
                                    done += n
                                    onProgress(done, total, f.name)
                                }
                            }
                        }
                        zos.closeEntry()
                    }
                }
            }
            scan(listOf(target.absolutePath))
            true
        } catch (e: Exception) {
            target.delete()
            false
        }
    }

    /** Extracts a zip into a new folder next to it. Returns the folder, or null on failure. */
    suspend fun unzip(
        archive: File,
        onProgress: (done: Long, total: Long, current: String) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        val parent = archive.parentFile ?: return@withContext null
        val dest = uniqueTarget(parent, archive.nameWithoutExtension)
        val destCanonical = dest.canonicalPath + File.separator
        val total = archive.length()
        var done = 0L
        try {
            dest.mkdirs()
            ZipInputStream(FileInputStream(archive).buffered()).use { zis ->
                while (true) {
                    coroutineContext.ensureActive()
                    val entry = zis.nextEntry ?: break
                    val out = File(dest, entry.name)
                    // Refuse entries that try to escape the folder ("zip slip").
                    if (!out.canonicalPath.startsWith(destCanonical) && out.canonicalPath != dest.canonicalPath) {
                        throw IOException("Unsafe entry ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { output ->
                            val buf = ByteArray(128 * 1024)
                            while (true) {
                                val n = zis.read(buf)
                                if (n < 0) break
                                output.write(buf, 0, n)
                            }
                        }
                        if (entry.time > 0) out.setLastModified(entry.time)
                    }
                    done += entry.compressedSize.coerceAtLeast(0)
                    onProgress(done.coerceAtMost(total), total, entry.name.substringAfterLast('/'))
                }
            }
            scan(dest.walkTopDown().filter { it.isFile }.map { it.absolutePath }.toList())
            dest
        } catch (e: Exception) {
            dest.deleteRecursively()
            null
        }
    }

    // ---------------------------------------------------------------- search & scan

    /** Finds files and folders under [root] whose name contains [query]. Emits results as found. */
    fun search(root: File, query: String, showHidden: Boolean, limit: Int = 500): Flow<List<FileEntry>> = flow {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            emit(emptyList()); return@flow
        }
        val found = ArrayList<FileEntry>()
        var lastEmit = 0
        root.walkTopDown()
            .onEnter { dir -> (showHidden || !dir.name.startsWith(".") || dir == root) && dir.canRead() }
            .forEach { f ->
                coroutineContext.ensureActive()
                if (f != root && f.name.lowercase().contains(q) && (showHidden || !f.name.startsWith("."))) {
                    found += FileEntry.from(f)
                    if (found.size - lastEmit >= 20) {
                        emit(found.toList()); lastEmit = found.size
                    }
                    if (found.size >= limit) {
                        emit(found.toList()); return@flow
                    }
                }
            }
        emit(found.toList())
    }.flowOn(Dispatchers.IO)

    /**
     * Walks all of internal storage once, recording every file. Categories, recent files and
     * all the cleanup lists are worked out from this snapshot.
     */
    suspend fun scanAll(onProgress: (Int) -> Unit): StorageScan = withContext(Dispatchers.IO) {
        val files = ArrayList<FileEntry>(20_000)
        val empty = ArrayList<FileEntry>()
        val androidDir = File(internalRoot, "Android").absolutePath
        internalRoot.walkTopDown()
            .onEnter { dir ->
                // Android/data and Android/obb belong to other apps and can't be read anyway.
                !dir.absolutePath.startsWith(androidDir) && !dir.name.startsWith(".")
            }
            .forEach { f ->
                coroutineContext.ensureActive()
                if (f.isFile) {
                    if (!f.name.startsWith(".")) {
                        files += FileEntry(f.absolutePath, f.name, false, f.length(), f.lastModified())
                        if (files.size % 500 == 0) onProgress(files.size)
                    }
                } else if (f != internalRoot && f.list()?.isEmpty() == true) {
                    empty += FileEntry.from(f, countChildren = true)
                }
            }
        StorageScan(files, empty, System.currentTimeMillis())
    }

    /** Groups files with identical contents. Compares size first, then a fingerprint, then the full hash. */
    suspend fun findDuplicates(files: List<FileEntry>, onProgress: (Int, Int) -> Unit): List<FileSection> =
        withContext(Dispatchers.IO) {
            val candidates = files.filter { it.size >= 50 * 1024 }.groupBy { it.size }.values.filter { it.size > 1 }
            val total = candidates.sumOf { it.size }
            var done = 0
            val groups = mutableListOf<List<FileEntry>>()
            for (sameSize in candidates) {
                coroutineContext.ensureActive()
                val byHead = sameSize.groupBy { hash(it.file, headOnly = true) }.values.filter { it.size > 1 }
                for (g in byHead) {
                    groups += g.groupBy { hash(it.file, headOnly = false) }.values.filter { it.size > 1 }
                }
                done += sameSize.size
                onProgress(done, total)
            }
            groups.sortedByDescending { it.first().size * (it.size - 1) }.map { g ->
                FileSection("${g.size} copies · ${g.first().name}", g.sortedBy { it.lastModified })
            }
        }

    private fun hash(f: File, headOnly: Boolean): String = try {
        val md = MessageDigest.getInstance("MD5")
        FileInputStream(f).use { input ->
            val buf = ByteArray(64 * 1024)
            if (headOnly) {
                val n = input.read(buf)
                if (n > 0) md.update(buf, 0, n)
            } else {
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        "error:${f.path}" // unreadable: never matches anything else
    }

    /** Tells the media library about changes so galleries and music apps update. */
    private fun scan(paths: List<String>) {
        if (paths.isEmpty()) return
        MediaScannerConnection.scanFile(appContext, paths.distinct().toTypedArray(), null, null)
    }
}

package com.wellsey.burrow.data

import java.io.File

/** One file or folder. */
data class FileEntry(
    val path: String,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val lastModified: Long,
    /** Number of items inside a folder (-1 when unknown or for files). */
    val childCount: Int = -1,
) {
    val file: File get() = File(path)
    val extension: String get() = if (isDir) "" else name.substringAfterLast('.', "").lowercase()
    val isHidden: Boolean get() = name.startsWith(".")

    companion object {
        fun from(f: File, countChildren: Boolean = false): FileEntry {
            val dir = f.isDirectory
            return FileEntry(
                path = f.absolutePath,
                name = f.name,
                isDir = dir,
                size = if (dir) 0 else f.length(),
                lastModified = f.lastModified(),
                childCount = if (dir && countChildren) (f.list()?.size ?: 0) else -1,
            )
        }
    }
}

enum class SortBy(val label: String) { Name("Name"), Date("Date"), Size("Size"), Type("Type") }

data class ViewPrefs(
    val sortBy: SortBy = SortBy.Name,
    val descending: Boolean = false,
    val showHidden: Boolean = false,
    val grid: Boolean = false,
    val foldersFirst: Boolean = true,
)

data class StorageVolume(
    val name: String,
    val root: File,
    val total: Long,
    val free: Long,
) {
    val used: Long get() = total - free
}

/** Groups used on the home screen and for cleanup lists. */
enum class Category(val label: String) {
    Images("Images"), Videos("Videos"), Audio("Audio"), Documents("Documents"),
    Archives("Archives"), Apks("APKs");

    companion object {
        private val images = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "dng", "avif")
        private val videos = setOf("mp4", "mkv", "mov", "webm", "3gp", "avi", "m4v", "ts")
        private val audio = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "amr", "mid", "wma")
        private val docs = setOf(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp",
            "txt", "md", "rtf", "csv", "epub", "html", "htm", "json", "xml",
        )
        private val archives = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz")

        fun of(extension: String): Category? = when (extension) {
            in images -> Images
            in videos -> Videos
            in audio -> Audio
            in docs -> Documents
            in archives -> Archives
            "apk", "apks", "xapk" -> Apks
            else -> null
        }
    }
}

/** A list shown on its own screen: a category, recent files, or one of the cleanup lists. */
enum class ListKind(val title: String, val category: Category? = null) {
    Images("Images", Category.Images),
    Videos("Videos", Category.Videos),
    Audio("Audio", Category.Audio),
    Documents("Documents", Category.Documents),
    Archives("Archives", Category.Archives),
    Apks("APK files", Category.Apks),
    Recent("Recent files"),
    Large("Large files"),
    Duplicates("Duplicate files"),
    OldDownloads("Old downloads"),
    EmptyFolders("Empty folders"),
}

/** Files to show in a list screen, optionally split into groups (used for duplicates). */
data class FileSection(val title: String, val files: List<FileEntry>)

/** A snapshot of every file on internal storage, made by [FileRepository.scanAll]. */
data class StorageScan(
    val files: List<FileEntry>,
    val emptyDirs: List<FileEntry>,
    val scannedAt: Long,
)

data class Clip(val files: List<FileEntry>, val cut: Boolean)

data class Progress(val title: String, val done: Long, val total: Long, val detail: String = "") {
    val fraction: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
}

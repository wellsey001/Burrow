package com.wellsey.burrow.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.FileProvider
import com.wellsey.burrow.data.Category
import com.wellsey.burrow.data.FileEntry
import java.io.File
import java.text.DateFormat
import java.util.Date

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}

fun formatDate(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

fun formatShortDate(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

fun mimeType(file: File): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"

fun fileUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.files", file)

/** Opens a file in whichever app handles its type. Returns false if no app can. */
fun openFile(context: Context, file: File): Boolean {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(fileUri(context, file), mimeType(file))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent); true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

fun openWith(context: Context, file: File) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(fileUri(context, file), mimeType(file))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Open with"))
}

fun shareFiles(context: Context, files: List<File>) {
    val uris = ArrayList(files.filter { it.isFile }.map { fileUri(context, it) })
    if (uris.isEmpty()) return
    val types = files.map { mimeType(it) }.distinct()
    val type = if (types.size == 1) types[0] else "*/*"
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }.setType(type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, null))
}

data class TypeStyle(val icon: ImageVector, val tint: Color)

@Suppress("DEPRECATION")
fun typeStyle(entry: FileEntry): TypeStyle = when {
    entry.isDir -> TypeStyle(Icons.Filled.Folder, Color(0xFFE0A33C))
    entry.extension == "pdf" -> TypeStyle(Icons.Filled.PictureAsPdf, Color(0xFFD9534F))
    else -> when (Category.of(entry.extension)) {
        Category.Images -> TypeStyle(Icons.Filled.Image, Color(0xFF3C8DD9))
        Category.Videos -> TypeStyle(Icons.Filled.Movie, Color(0xFF8E5BD9))
        Category.Audio -> TypeStyle(Icons.Filled.AudioFile, Color(0xFFE07A3C))
        Category.Documents -> TypeStyle(
            if (entry.extension in setOf("txt", "md")) Icons.Filled.Article else Icons.Filled.Description,
            Color(0xFF3CA87A)
        )
        Category.Archives -> TypeStyle(Icons.Filled.FolderZip, Color(0xFF8A7A5C))
        Category.Apks -> TypeStyle(Icons.Filled.Android, Color(0xFF5CB85C))
        null -> TypeStyle(Icons.Filled.InsertDriveFile, Color(0xFF7D8590))
    }
}

/** Characters Android doesn't allow in file names. */
fun validFileName(name: String): Boolean =
    name.isNotBlank() && name != "." && name != ".." && name.none { it in "/\\:*?\"<>|" }

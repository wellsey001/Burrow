package com.wellsey.burrow.data

import android.graphics.drawable.BitmapDrawable
import android.media.ThumbnailUtils
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options
import java.io.File
import java.io.IOException

/** Coil model for a file's thumbnail: photos, video frames, album art and APK icons. */
data class FileThumb(val path: String, val lastModified: Long, val size: Int = 256)

fun hasThumbnail(entry: FileEntry): Boolean =
    !entry.isDir && when (Category.of(entry.extension)) {
        Category.Images, Category.Videos, Category.Audio, Category.Apks -> true
        else -> false
    }

class FileThumbFetcher(private val data: FileThumb, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val file = File(data.path)
        val ext = file.extension.lowercase()
        val size = Size(data.size, data.size)
        val res = options.context.resources
        val drawable = when (Category.of(ext)) {
            Category.Images -> BitmapDrawable(res, ThumbnailUtils.createImageThumbnail(file, size, null))
            Category.Videos -> BitmapDrawable(res, ThumbnailUtils.createVideoThumbnail(file, size, null))
            Category.Audio -> BitmapDrawable(res, ThumbnailUtils.createAudioThumbnail(file, size, null))
            Category.Apks -> {
                val pm = options.context.packageManager
                val info = pm.getPackageArchiveInfo(file.path, 0)?.applicationInfo
                    ?: throw IOException("Not an APK")
                info.sourceDir = file.path
                info.publicSourceDir = file.path
                info.loadIcon(pm)
            }
            else -> throw IOException("No thumbnail")
        }
        return DrawableResult(drawable, isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory : Fetcher.Factory<FileThumb> {
        override fun create(data: FileThumb, options: Options, imageLoader: ImageLoader): Fetcher =
            FileThumbFetcher(data, options)
    }
}

class FileThumbKeyer : Keyer<FileThumb> {
    override fun key(data: FileThumb, options: Options) = "file:${data.path}#${data.lastModified}#${data.size}"
}

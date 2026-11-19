package com.wellsey.burrow

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.wellsey.burrow.data.FileThumbFetcher
import com.wellsey.burrow.data.FileThumbKeyer

class BurrowApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(FileThumbFetcher.Factory())
                add(FileThumbKeyer())
            }
            .crossfade(120)
            .build()
}

package com.zai.avora

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.zai.avora.data.auth.AuthManager
import com.zai.avora.data.remote.AvoraDownloader
import org.schabi.newpipe.extractor.NewPipe

class AvoraApp : Application(), ImageLoaderFactory, ViewModelStoreOwner {

    override val viewModelStore: ViewModelStore = ViewModelStore()

    override fun onCreate() {
        super.onCreate()
        com.zai.avora.data.cache.CacheCleaner.cleanOnStart(this)
        AuthManager.init(this)
        com.zai.avora.data.network.NetworkMonitor.init(this)
        com.zai.avora.data.settings.SettingsManager.init(this)
        com.zai.avora.data.offline.OfflineManager.init(this)
        val downloader = AvoraDownloader.init(this)
        NewPipe.init(downloader)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .networkCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .respectCacheHeaders(false)
            .build()
    }
}

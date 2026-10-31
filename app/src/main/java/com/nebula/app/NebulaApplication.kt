package com.nebula.app

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder

class NebulaApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        // Starts before any screen: the chat send path asks this synchronously to decide
        // whether a message can leave the device at all.
        com.nebula.app.data.local.ConnectivityMonitor.start(this)
        try {
            val appCheck = com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
            val isDebuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (isDebuggable) {
                // The debug provider is a debugImplementation dependency, so it must be
                // loaded reflectively: a release build has no such class on its classpath.
                runCatching {
                    val factory = Class
                        .forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
                        .getMethod("getInstance")
                        .invoke(null) as com.google.firebase.appcheck.AppCheckProviderFactory
                    appCheck.installAppCheckProviderFactory(factory)
                }
            } else {
                appCheck.installAppCheckProviderFactory(
                    com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory.getInstance()
                )
            }
        } catch (_: Exception) {}
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
            else add(GifDecoder.Factory())
        }
        .memoryCache {
            coil.memory.MemoryCache.Builder(this)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            coil.disk.DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(250L * 1024 * 1024)
                .build()
        }
        .crossfade(true)
        .build()
}

package com.nebula.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.util.Base64
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Universal Nebula Profile Banner View.
 * Supports:
 * 1. Infinite looping silent video banners (MP4 / WebM / data:video / content:video).
 * 2. Static images & animated GIFs (HTTPS URL, Base64 data:image, content:image).
 * 3. Expanded default Nebula themes with custom gradients, geometric shapes & emojis.
 */
@Composable
fun NebulaBannerView(
    bannerUrl: String?,
    bannerIndex: Int = 0,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    borderColor: Color = Color.Transparent
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .clip(shape)
            .border(1.dp, borderColor, shape)
    ) {
        val hasCustomMedia = !bannerUrl.isNullOrBlank()

        if (!hasCustomMedia) {
            // Zero-latency background palette layer with shapes / emojis for default banners
            NebulaThemedBannerBackground(
                bannerIndex = bannerIndex,
                modifier = Modifier.fillMaxSize(),
                shape = shape,
                borderColor = Color.Transparent
            )
        } else {
            // Dark base background for custom media preventing palette theme flash
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0C0D14))
            )
        }

        if (hasCustomMedia && bannerUrl != null) {
            val nonNullUrl = bannerUrl
            val isVideo = remember(nonNullUrl) { isVideoBanner(nonNullUrl, context) }

            if (isVideo) {
                // Video Banner: Automatically plays in an endless loop with 0 audio volume
                VideoBannerPlayer(
                    videoUrl = nonNullUrl,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Image or GIF Banner
                val base64Bitmap by androidx.compose.runtime.produceState<Bitmap?>(initialValue = null, key1 = nonNullUrl) {
                    if (nonNullUrl.startsWith("data:image")) {
                        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                val data = nonNullUrl.substringAfter("base64,")
                                val decoded = Base64.decode(data, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
                            } catch (_: Exception) {
                                null
                            }
                        }
                    } else {
                        value = null
                    }
                }

                if (base64Bitmap != null) {
                    Image(
                        bitmap = base64Bitmap!!.asImageBitmap(),
                        contentDescription = "Profile Banner",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(context)
                            .data(nonNullUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Profile Banner",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}

/**
 * Checks whether a given banner URL or data string represents a video asset.
 */
fun isVideoBanner(url: String?, context: Context? = null): Boolean {
    if (url.isNullOrBlank()) return false
    val clean = url.lowercase().substringBefore('?')
    if (clean.startsWith("data:video/")) return true
    if (clean.endsWith(".mp4") || clean.endsWith(".webm") || clean.endsWith(".mov") || clean.endsWith(".mkv") || clean.endsWith(".3gp")) return true
    if (clean.contains(".mp4") || clean.contains(".webm") || clean.contains(".mov")) return true
    if (clean.contains("video%2f") || clean.contains("/video/") || clean.contains("_video_") || clean.contains("banner_video")) return true
    if (url.contains(".mp4") || url.contains(".webm")) return true

    if (url.startsWith("content://")) {
        if (context != null) {
            try {
                val type = context.contentResolver.getType(Uri.parse(url))
                if (type?.startsWith("video") == true) return true
                if (type?.startsWith("image") == true) return false
            } catch (_: Exception) {}
        }
        return false
    }

    if (url.startsWith("file://")) {
        val path = url.removePrefix("file://").lowercase()
        return path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".mov") || path.endsWith(".mkv")
    }

    if (url.contains("/storage/")) {
        try {
            val encodedKey = url.substringAfter("/storage/").substringBefore('?').substringBefore('.')
            val paddedKey = when (encodedKey.length % 4) {
                2 -> "$encodedKey=="
                3 -> "$encodedKey="
                else -> encodedKey
            }.replace('-', '+').replace('_', '/')
            val decodedKey = String(Base64.decode(paddedKey, Base64.DEFAULT), Charsets.UTF_8).lowercase()
            if (decodedKey.endsWith(".mp4") || decodedKey.endsWith(".webm") || decodedKey.contains("video") || decodedKey.contains("banner_video")) {
                return true
            }
        } catch (_: Exception) {}
    }
    return false
}

/**
 * Normalizes media URLs pointing to localhost or private storage endpoints.
 */
fun resolveMediaUrl(url: String): String {
    if (url.startsWith("/")) {
        return "${com.nebula.app.data.backend.NebulaApiClient.socketBaseUrl()}$url"
    }
    if (url.contains("localhost") || url.contains("127.0.0.1") || url.contains("10.0.2.2")) {
        try {
            val parsed = Uri.parse(url)
            val path = parsed.encodedPath ?: ""
            val query = if (parsed.encodedQuery != null) "?${parsed.encodedQuery}" else ""
            return "${com.nebula.app.data.backend.NebulaApiClient.socketBaseUrl()}$path$query"
        } catch (_: Exception) {}
    }
    return url
}

/**
 * Downloads a video file following HTTP 301/302 redirects to internal disk cache.
 */
private fun downloadVideoWithRedirects(initialUrl: String, destination: File) {
    var currentUrl = initialUrl
    var hops = 0
    val tempFile = File(destination.parentFile, "${destination.name}.tmp")
    while (hops < 5) {
        hops++
        var conn: java.net.HttpURLConnection? = null
        try {
            conn = (java.net.URL(currentUrl).openConnection() as java.net.HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("User-Agent", "Nebula/1.0")
            }
            val code = conn.responseCode
            if (code in 301..308) {
                val location = conn.getHeaderField("Location")
                if (!location.isNullOrBlank()) {
                    currentUrl = if (location.startsWith("http")) location else java.net.URL(java.net.URL(currentUrl), location).toString()
                    continue
                }
                break
            } else if (code in 200..299) {
                conn.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempFile.exists() && tempFile.length() > 0) {
                    tempFile.copyTo(destination, overwrite = true)
                    tempFile.delete()
                }
                break
            } else {
                break
            }
        } catch (e: Exception) {
            android.util.Log.e("NebulaBannerView", "Download error for $currentUrl", e)
            break
        } finally {
            conn?.disconnect()
        }
    }
}

/**
 * TextureView-backed MediaPlayer that renders a video in an endless loop with zero audio volume.
 * Resolves remote HTTPS video URLs via disk cache for 0ms replay and supports data:video base64 decoding.
 */
@Composable
fun VideoBannerPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val initialFile = remember(videoUrl) {
        try {
            if (videoUrl.startsWith("file://")) {
                val f = File(Uri.parse(videoUrl).path ?: videoUrl.removePrefix("file://"))
                if (f.exists() && f.length() > 0) f else null
            } else if (!videoUrl.startsWith("data:") && !videoUrl.startsWith("content:")) {
                val resolvedUrl = resolveMediaUrl(videoUrl)
                val cacheDir = File(context.cacheDir, "video_banners")
                val key = java.security.MessageDigest.getInstance("MD5")
                    .digest(resolvedUrl.toByteArray())
                    .joinToString("") { "%02x".format(it) } + ".mp4"
                val cachedFile = File(cacheDir, key)
                if (cachedFile.exists() && cachedFile.length() > 0) cachedFile else null
            } else null
        } catch (_: Exception) {
            null
        }
    }
    var localVideoFile by remember(videoUrl) { mutableStateOf<File?>(initialFile) }
    var isBuffering by remember(videoUrl) { mutableStateOf(false) }

    LaunchedEffect(videoUrl) {
        withContext(Dispatchers.IO) {
            try {
                if (videoUrl.startsWith("file://")) {
                    val f = File(Uri.parse(videoUrl).path ?: videoUrl.removePrefix("file://"))
                    if (f.exists() && f.length() > 0) localVideoFile = f
                } else if (videoUrl.startsWith("data:video")) {
                    val base64Data = videoUrl.substringAfter("base64,")
                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                    val cacheFile = File(context.cacheDir, "banner_data_video_${System.currentTimeMillis()}.mp4")
                    cacheFile.writeBytes(bytes)
                    localVideoFile = cacheFile
                } else if (videoUrl.startsWith("content://")) {
                    val cacheFile = File(context.cacheDir, "banner_picked_video_${System.currentTimeMillis()}.mp4")
                    context.contentResolver.openInputStream(Uri.parse(videoUrl))?.use { input ->
                        cacheFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (cacheFile.exists() && cacheFile.length() > 0) {
                        localVideoFile = cacheFile
                    }
                } else {
                    // Remote HTTPS / HTTP URL
                    val resolvedUrl = resolveMediaUrl(videoUrl)
                    val cacheDir = File(context.cacheDir, "video_banners").apply { if (!exists()) mkdirs() }
                    val key = java.security.MessageDigest.getInstance("MD5")
                        .digest(resolvedUrl.toByteArray())
                        .joinToString("") { "%02x".format(it) } + ".mp4"
                    val cachedFile = File(cacheDir, key)
                    if (cachedFile.exists() && cachedFile.length() > 0) {
                        localVideoFile = cachedFile
                    } else {
                        isBuffering = true
                        downloadVideoWithRedirects(resolvedUrl, cachedFile)
                        isBuffering = false
                        if (cachedFile.exists() && cachedFile.length() > 0) {
                            localVideoFile = cachedFile
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("NebulaBannerView", "Error resolving video file", e)
                isBuffering = false
            }
        }
    }

    val targetFile = localVideoFile
    if (targetFile == null || !targetFile.exists() || targetFile.length() == 0L) {
        if (isBuffering) {
            Box(
                modifier = modifier,
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        }
        return
    }

    key(targetFile.absolutePath) {
        var activeMediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

        DisposableEffect(targetFile.absolutePath) {
            onDispose {
                try {
                    activeMediaPlayer?.let { mp ->
                        if (mp.isPlaying) mp.stop()
                        mp.reset()
                        mp.release()
                    }
                } catch (_: Exception) {}
                activeMediaPlayer = null
            }
        }

        AndroidView(
            factory = { ctx ->
                val tv = TextureView(ctx)
                tv.isOpaque = true // Must be opaque for hardware video decoding!

                var mediaPlayer: MediaPlayer? = null
                var surfaceObj: Surface? = null
                var isPrepared = false

                fun releaseCurrentPlayer() {
                    try {
                        mediaPlayer?.let { mp ->
                            if (mp.isPlaying) mp.stop()
                            mp.reset()
                            mp.release()
                        }
                    } catch (_: Exception) {}
                    mediaPlayer = null
                    try {
                        surfaceObj?.release()
                    } catch (_: Exception) {}
                    surfaceObj = null
                    isPrepared = false
                    activeMediaPlayer = null
                }

                fun startPlayback(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                    try {
                        releaseCurrentPlayer()

                        val surf = Surface(surfaceTexture)
                        surfaceObj = surf

                        val mp = MediaPlayer()
                        mp.setSurface(surf)
                        mp.setDataSource(targetFile.absolutePath)
                        mp.setVolume(0f, 0f) // Strictly NO AUDIO
                        mp.isLooping = true  // Endless loop

                        mp.setOnVideoSizeChangedListener { _, vidWidth, vidHeight ->
                            val viewW = if (tv.width > 0) tv.width else width
                            val viewH = if (tv.height > 0) tv.height else height
                            if (vidWidth > 0 && vidHeight > 0 && viewW > 0 && viewH > 0) {
                                applyCenterCropMatrix(tv, vidWidth, vidHeight, viewW, viewH)
                            }
                        }

                        mp.setOnPreparedListener { player ->
                            isPrepared = true
                            try {
                                player.start()
                                val viewW = if (tv.width > 0) tv.width else width
                                val viewH = if (tv.height > 0) tv.height else height
                                if (player.videoWidth > 0 && player.videoHeight > 0 && viewW > 0 && viewH > 0) {
                                    applyCenterCropMatrix(tv, player.videoWidth, player.videoHeight, viewW, viewH)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("NebulaBannerView", "Error starting player in onPrepared", e)
                            }
                        }

                        mp.setOnErrorListener { _, what, extra ->
                            android.util.Log.e("NebulaBannerView", "MediaPlayer error: $what, extra: $extra")
                            true
                        }

                        mp.prepareAsync()
                        mediaPlayer = mp
                        activeMediaPlayer = mp
                    } catch (e: Exception) {
                        android.util.Log.e("NebulaBannerView", "Error initializing MediaPlayer", e)
                    }
                }

                tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        startPlayback(surface, width, height)
                    }

                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                        mediaPlayer?.let { player ->
                            if (isPrepared && player.videoWidth > 0 && player.videoHeight > 0 && width > 0 && height > 0) {
                                applyCenterCropMatrix(tv, player.videoWidth, player.videoHeight, width, height)
                            }
                        }
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        releaseCurrentPlayer()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                }

                if (tv.isAvailable && tv.surfaceTexture != null) {
                    startPlayback(tv.surfaceTexture!!, tv.width.coerceAtLeast(1), tv.height.coerceAtLeast(1))
                }
                tv
            },
            update = {
                // Deliberately no-op: MediaPlayer loops continuously and starts safely inside onPreparedListener.
                // Never call mp.start() here while preparing to avoid native state -38 errors.
            },
            modifier = modifier
        )
    }
}

/**
 * Calculates and applies a CenterCrop transformation matrix to ensure the video
 * fills the banner aspect ratio without distortion or letterboxing bars.
 */
private fun applyCenterCropMatrix(
    textureView: TextureView,
    videoWidth: Int,
    videoHeight: Int,
    viewWidth: Int,
    viewHeight: Int
) {
    if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) return
    val scaleX = viewWidth.toFloat() / videoWidth.toFloat()
    val scaleY = viewHeight.toFloat() / videoHeight.toFloat()
    val maxScale = maxOf(scaleX, scaleY)

    val scaledWidth = videoWidth * maxScale
    val scaledHeight = videoHeight * maxScale

    val matrix = Matrix()
    matrix.setScale(
        scaledWidth / viewWidth.toFloat(),
        scaledHeight / viewHeight.toFloat(),
        viewWidth / 2f,
        viewHeight / 2f
    )
    textureView.setTransform(matrix)
}

package com.nebula.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.nebula.app.BuildConfig
import com.nebula.app.data.backend.NebulaApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String,
    val forceUpdate: Boolean = false
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val info: AppUpdateInfo) : UpdateState
    data class Downloading(
        val progress: Float,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long = 0L,
        val info: AppUpdateInfo
    ) : UpdateState
    data class ReadyToInstall(val apkFile: File, val info: AppUpdateInfo) : UpdateState
    data class Error(val message: String) : UpdateState
}

/**
 * UpdateManager:
 * Manages checking, downloading with real-time progress, and triggering installation
 * of in-app updates for direct APK distribution outside Google Play.
 */
object UpdateManager {
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private var lastCheckTimestampMs: Long = 0L
    private var pendingInstallFile: File? = null

    fun dismissUpdate() {
        _updateState.value = UpdateState.Idle
    }

    suspend fun checkForUpdate(
        context: Context,
        isManual: Boolean = false,
        onUpToDate: (() -> Unit)? = null
    ): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        // Throttle automatic checks by 15 seconds to avoid spamming
        if (!isManual && (now - lastCheckTimestampMs < 15_000L)) {
            return@withContext null
        }
        lastCheckTimestampMs = now

        if (isManual) {
            _updateState.value = UpdateState.Checking
        }

        try {
            val baseUrl = NebulaApiClient.socketBaseUrl()
            val candidateEndpoints = listOf(
                "$baseUrl/api/v1/app/version",
                "$baseUrl/version.json",
                "https://nebula-73kb.onrender.com/api/v1/app/version",
                "https://nebula-73kb.onrender.com/version.json"
            ).distinct()

            var responseJson: JSONObject? = null
            for (endpoint in candidateEndpoints) {
                try {
                    val bustUrl = if (endpoint.contains("?")) "$endpoint&_t=$now" else "$endpoint?_t=$now"
                    val url = URL(bustUrl)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 8000
                        readTimeout = 8000
                        setRequestProperty("Accept", "application/json")
                        setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                        setRequestProperty("Pragma", "no-cache")
                    }
                    if (conn.responseCode in 200..299) {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        responseJson = JSONObject(body)
                        break
                    }
                } catch (_: Exception) {
                    // Try next candidate
                }
            }

            if (responseJson == null) {
                if (isManual) {
                    _updateState.value = UpdateState.Error("Unable to reach update server.")
                }
                return@withContext null
            }

            val latestVersionCode = responseJson.optInt("versionCode", -1)
            val latestVersionName = responseJson.optString("versionName", "")
            var apkUrl = responseJson.optString("apkUrl", "")
            val releaseNotes = responseJson.optString("releaseNotes", "Performance and stability improvements.")
            val forceUpdate = responseJson.optBoolean("forceUpdate", false)

            if (apkUrl.startsWith("/")) {
                apkUrl = "$baseUrl$apkUrl"
            }

            val currentVersionCode = BuildConfig.VERSION_CODE
            if (latestVersionCode > currentVersionCode) {
                val updateInfo = AppUpdateInfo(
                    versionCode = latestVersionCode,
                    versionName = latestVersionName,
                    apkUrl = apkUrl,
                    releaseNotes = releaseNotes,
                    forceUpdate = forceUpdate
                )
                _updateState.value = UpdateState.Available(updateInfo)
                return@withContext updateInfo
            } else {
                if (isManual) {
                    _updateState.value = UpdateState.Idle
                    withContext(Dispatchers.Main) {
                        onUpToDate?.invoke()
                    }
                }
                return@withContext null
            }
        } catch (e: Exception) {
            if (isManual) {
                _updateState.value = UpdateState.Error(e.message ?: "Update check failed.")
            }
            return@withContext null
        }
    }

    suspend fun downloadAndInstall(
        context: Context,
        info: AppUpdateInfo
    ) = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val destinationFile = File(updatesDir, "nebula-v${info.versionCode}.apk")

            // Fast-path: If already fully downloaded and valid APK
            if (destinationFile.exists() && destinationFile.length() > 500_000) {
                val archiveInfo = context.packageManager.getPackageArchiveInfo(destinationFile.absolutePath, 0)
                if (archiveInfo != null) {
                    _updateState.value = UpdateState.ReadyToInstall(destinationFile, info)
                    withContext(Dispatchers.Main) {
                        promptInstall(context, destinationFile)
                    }
                    return@withContext
                } else {
                    destinationFile.delete()
                }
            }

            val tempFile = File(updatesDir, "nebula-v${info.versionCode}.tmp")
            if (tempFile.exists()) tempFile.delete()

            _updateState.value = UpdateState.Downloading(
                progress = 0f,
                bytesDownloaded = 0L,
                totalBytes = -1L,
                speedBytesPerSec = 0L,
                info = info
            )

            val bustApkUrl = if (info.apkUrl.contains("?")) "${info.apkUrl}&_t=${System.currentTimeMillis()}" else "${info.apkUrl}?_t=${System.currentTimeMillis()}"
            val url = URL(bustApkUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 60000
                instanceFollowRedirects = true
                setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                setRequestProperty("Pragma", "no-cache")
            }

            val totalBytes = conn.contentLengthLong
            var bytesDownloaded = 0L

            conn.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(16384)
                    var read: Int
                    var lastEmitTime = 0L
                    val startTime = System.currentTimeMillis()

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesDownloaded += read

                        val now = System.currentTimeMillis()
                        if (now - lastEmitTime >= 50L || (totalBytes > 0 && bytesDownloaded == totalBytes)) {
                            val elapsedSec = (now - startTime).coerceAtLeast(1) / 1000f
                            val speed = if (elapsedSec > 0) (bytesDownloaded / elapsedSec).toLong() else 0L

                            val progress = if (totalBytes > 0) {
                                (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }

                            lastEmitTime = now
                            _updateState.value = UpdateState.Downloading(
                                progress = progress,
                                bytesDownloaded = bytesDownloaded,
                                totalBytes = totalBytes,
                                speedBytesPerSec = speed,
                                info = info
                            )
                        }
                    }
                }
            }

            // Verify integrity of downloaded APK before launching installer
            val pkgInfo = context.packageManager.getPackageArchiveInfo(tempFile.absolutePath, 0)
            if (pkgInfo == null) {
                tempFile.delete()
                _updateState.value = UpdateState.Error("Downloaded update is corrupted. Please try again.")
                return@withContext
            }

            if (destinationFile.exists()) destinationFile.delete()
            if (tempFile.renameTo(destinationFile)) {
                _updateState.value = UpdateState.ReadyToInstall(destinationFile, info)
                withContext(Dispatchers.Main) {
                    promptInstall(context, destinationFile)
                }
            } else {
                _updateState.value = UpdateState.Error("Failed to prepare installation file.")
            }
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error(e.message ?: "Failed to download update.")
        }
    }

    fun promptInstall(context: Context, apkFile: File) {
        pendingInstallFile = apkFile
        try {
            // Android 8.0+ Unknown App Sources Permission Check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(
                        context,
                        "Please allow Nebula to install app updates",
                        Toast.LENGTH_LONG
                    ).show()

                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Install failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun resumePendingInstallIfAllowed(context: Context) {
        val file = pendingInstallFile ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (context.packageManager.canRequestPackageInstalls() && file.exists()) {
                promptInstall(context, file)
            }
        }
    }
}

package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

/** Information needed to download and install a GitHub release APK. */
data class AppUpdateInfo(
    val versionName: String,
    val tagName: String,
    val assetName: String,
    val assetUrl: String,
    val releaseUrl: String,
    val releaseNotes: String
)

enum class UpdateStatus {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    AVAILABLE,
    DOWNLOADING,
    ERROR
}

data class AppUpdateUiState(
    val status: UpdateStatus = UpdateStatus.IDLE,
    val availableUpdate: AppUpdateInfo? = null,
    val downloadProgress: Int = 0,
    val message: String? = null
)

enum class ApkInstallResult {
    STARTED,
    PERMISSION_REQUIRED
}

/**
 * GitHub-only OTA client. This client never receives media paths or playback state; its only
 * network requests are the public latest-release metadata and the selected release APK.
 */
class GitHubUpdateRepository(private val context: Context) {

    suspend fun checkForUpdate(currentVersionName: String): Result<AppUpdateInfo?> =
        try {
            val release = withContext(Dispatchers.IO) { fetchLatestRelease() }
            val latestVersion = release.optString("tag_name").trim().removePrefix("v")
            if (latestVersion.isBlank()) {
                throw IOException("GitHub release has no version tag")
            }

            val apkAsset = findApkAsset(release)
            if (apkAsset == null) {
                Result.success(null)
            } else {
                val update = AppUpdateInfo(
                    versionName = latestVersion,
                    tagName = release.optString("tag_name"),
                    assetName = apkAsset.optString("name"),
                    assetUrl = apkAsset.optString("browser_download_url"),
                    releaseUrl = release.optString("html_url"),
                    releaseNotes = release.optString("body").trim()
                )
                if (update.assetUrl.isBlank()) {
                    throw IOException("GitHub release has no APK download URL")
                }
                Result.success(
                    update.takeIf { isVersionNewer(it.versionName, currentVersionName) }
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }

    suspend fun downloadApk(
        update: AppUpdateInfo,
        onProgress: (Int) -> Unit
    ): Result<File> = try {
        val file = withContext(Dispatchers.IO) {
            downloadReleaseAsset(update, onProgress)
        }
        Result.success(file)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    /** Starts Android's package installer, or opens the per-app unknown-sources setting first. */
    fun startInstaller(apkFile: File): ApkInstallResult {
        require(apkFile.isFile && apkFile.length() > 0L) { "Downloaded APK is missing" }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(permissionIntent)
            return ApkInstallResult.PERMISSION_REQUIRED
        }

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
        return ApkInstallResult.STARTED
    }

    private fun fetchLatestRelease(): JSONObject {
        val connection = openConnection(LATEST_RELEASE_URL, "application/vnd.github+json")
        return try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("GitHub release request failed ($responseCode)")
            }
            JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun findApkAsset(release: JSONObject): JSONObject? {
        val assets = release.optJSONArray("assets") ?: return null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name").lowercase()
            if (name.endsWith(".apk")) return asset
        }
        return null
    }

    private fun downloadReleaseAsset(
        update: AppUpdateInfo,
        onProgress: (Int) -> Unit
    ): File {
        val assetUrl = URL(update.assetUrl)
        require(isAllowedDownloadUrl(assetUrl)) { "APK download is not a GitHub URL" }

        val updateDirectory = File(context.cacheDir, "updates").apply { mkdirs() }
        val safeVersion = update.versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val apkFile = File(updateDirectory, "alt-player-$safeVersion.apk")
        if (apkFile.isFile && apkFile.length() > 0L) {
            onProgress(100)
            return apkFile
        }
        val partialFile = File(updateDirectory, "$safeVersion.apk.part")
        if (partialFile.exists()) partialFile.delete()

        val connection = openConnection(assetUrl.toString(), APK_MIME_TYPE)
        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("APK download failed ($responseCode)")
            }
            require(isAllowedDownloadUrl(connection.url)) { "APK redirected off GitHub" }
            val contentLength = connection.contentLengthLong
            if (contentLength > MAX_APK_BYTES) {
                throw IOException("APK is larger than the supported update limit")
            }

            var downloaded = 0L
            var lastProgress = -1
            connection.inputStream.use { input ->
                partialFile.outputStream().use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        downloaded += read
                        if (downloaded > MAX_APK_BYTES) {
                            throw IOException("APK is larger than the supported update limit")
                        }
                        output.write(buffer, 0, read)
                        val progress = if (contentLength > 0L) {
                            ((downloaded * 100L) / contentLength).toInt().coerceIn(0, 99)
                        } else {
                            0
                        }
                        if (progress != lastProgress) {
                            lastProgress = progress
                            onProgress(progress)
                        }
                    }
                }
            }
            require(downloaded > 0L) { "GitHub returned an empty APK" }
            if (contentLength > 0L && downloaded != contentLength) {
                throw IOException("APK download was incomplete")
            }
            if (apkFile.exists()) apkFile.delete()
            check(partialFile.renameTo(apkFile)) { "Could not finalize downloaded APK" }
            onProgress(100)
            return apkFile
        } finally {
            connection.disconnect()
            if (partialFile.exists()) partialFile.delete()
        }
    }

    private fun openConnection(url: String, accept: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            requestMethod = "GET"
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "Alt-Player-OTA")
        }

    private fun isAllowedDownloadUrl(url: URL): Boolean {
        if (url.protocol != "https") return false
        val host = url.host.lowercase()
        return host == "github.com" ||
            host.endsWith(".github.com") ||
            host == "githubusercontent.com" ||
            host.endsWith(".githubusercontent.com")
    }

    companion object {
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/emdademon/Alt-Player/releases/latest"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val NETWORK_TIMEOUT_MS = 10_000
        private const val DOWNLOAD_BUFFER_SIZE = 16 * 1024
        private const val MAX_APK_BYTES = 100L * 1024L * 1024L

        fun isVersionNewer(candidate: String, current: String): Boolean {
            val candidateParts = versionParts(candidate)
            val currentParts = versionParts(current)
            val size = max(candidateParts.size, currentParts.size)
            for (index in 0 until size) {
                val candidatePart = candidateParts.getOrElse(index) { 0 }
                val currentPart = currentParts.getOrElse(index) { 0 }
                if (candidatePart != currentPart) return candidatePart > currentPart
            }
            return false
        }

        private fun versionParts(version: String): List<Int> =
            version.trim().removePrefix("v").split(".").map { part ->
                part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
            }
    }
}

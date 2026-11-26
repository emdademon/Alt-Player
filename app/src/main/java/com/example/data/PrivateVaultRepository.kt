package com.example.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PrivateVaultRepository(private val context: Context) {

    private val vaultDir: File
        get() = File(context.filesDir, "private_vault").apply {
            if (!exists()) mkdirs()
        }

    suspend fun listVaultVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        val files = vaultDir.listFiles()?.filter { it.isFile && it.length() > 0L }.orEmpty()
        files.mapNotNull { file ->
            buildVaultVideoItem(file)
        }.sortedByDescending { it.dateModifiedSec }
    }

    suspend fun importToVault(sourceUri: Uri, preferredDisplayName: String? = null): VideoItem? =
        withContext(Dispatchers.IO) {
            val resolvedName = preferredDisplayName?.takeIf { it.isNotBlank() }
                ?: resolveDisplayName(sourceUri)
            val safeName = resolvedName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val targetName = if (File(vaultDir, safeName).exists()) {
                "${System.currentTimeMillis()}_$safeName"
            } else {
                safeName
            }
            val destFile = File(vaultDir, targetName)

            val copied = runCatching {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                } != null
            }.getOrDefault(false)

            if (!copied || !destFile.exists() || destFile.length() == 0L) {
                runCatching { destFile.delete() }
                return@withContext null
            }

            buildVaultVideoItem(destFile)
        }

    suspend fun deleteFromVault(video: VideoItem): Boolean = withContext(Dispatchers.IO) {
        val file = File(video.fullFilePath)
        if (file.exists() && file.parentFile?.canonicalPath == vaultDir.canonicalPath) {
            file.delete()
        } else {
            false
        }
    }

    suspend fun renameInVault(video: VideoItem, newTitle: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(video.fullFilePath)
        if (!file.exists()) return@withContext false
        val cleanBase = newTitle.trim().replace(Regex("[^A-Za-z0-9._ -]"), "_")
        if (cleanBase.isEmpty()) return@withContext false
        val ext = file.extension
        val targetName = if (ext.isNotEmpty()) "$cleanBase.$ext" else cleanBase
        val dest = File(vaultDir, targetName)
        if (dest.exists()) return@withContext false
        file.renameTo(dest)
    }

    fun getShareableUri(video: VideoItem): Uri {
        return if (video.isPrivateVault && video.fullFilePath.isNotBlank()) {
            val file = File(video.fullFilePath)
            runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }.getOrElse { video.uri }
        } else {
            video.uri
        }
    }

    private fun buildVaultVideoItem(file: File): VideoItem? {
        val displayName = file.name
        val ext = file.extension.uppercase().ifBlank { "MP4" }
        val title = displayName.substringBeforeLast('.', displayName)
        var durationMs = 0L
        var width = 0
        var height = 0

        val retriever = MediaMetadataRetriever()
        runCatching {
            retriever.setDataSource(file.absolutePath)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
        }
        runCatching { retriever.release() }

        return VideoItem(
            id = file.absolutePath.hashCode().toLong(),
            uri = Uri.fromFile(file),
            title = title,
            displayName = displayName,
            bucketId = "private_vault",
            folderName = "Private Vault",
            folderPath = "App Private Storage",
            fullFilePath = file.absolutePath,
            durationMs = durationMs,
            sizeBytes = file.length(),
            width = width,
            height = height,
            dateModifiedSec = file.lastModified() / 1000L,
            mimeType = "video/${ext.lowercase()}",
            extension = ext,
            isExternalPick = true,
            isPrivateVault = true
        )
    }

    private fun resolveDisplayName(uri: Uri): String {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "vault_video.mp4"
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        cursor.getString(idx)?.takeIf { it.isNotBlank() }?.let { name = it }
                    }
                }
            }
        }
        return name
    }
}

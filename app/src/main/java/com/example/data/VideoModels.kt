package com.example.data

import android.net.Uri
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

enum class SortOption {
    NAME,
    DATE,
    SIZE
}

enum class SortOrder {
    ASCENDING,
    DESCENDING
}

enum class LibraryTab {
    FOLDERS,
    ALL_VIDEOS,
    AUDIO
}

enum class MediaAccessState {
    UNKNOWN,
    GRANTED_ALL,
    GRANTED_PARTIAL,
    DENIED
}

enum class ResumeBehavior(val label: String, val description: String) {
    ASK("Ask Resume / Start Over", "Prompt when a video has saved progress"),
    ALWAYS_RESUME("Always Resume", "Automatically continue from last position"),
    START_OVER("Start Over", "Always begin playback from 00:00")
}

enum class DefaultOrientation(val label: String) {
    AUTO("Auto"),
    LANDSCAPE("Landscape"),
    PORTRAIT("Portrait")
}

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val displayName: String,
    val bucketId: String,
    val folderName: String,
    val folderPath: String,
    val fullFilePath: String = "",
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val dateModifiedSec: Long,
    val mimeType: String,
    val extension: String,
    val isExternalPick: Boolean = false,
    val isPrivateVault: Boolean = false,
    val isAudio: Boolean = false,
    val artist: String = "",
    val album: String = "",
    val albumId: Long = 0L
) {
    val resolutionLabel: String
        get() = if (isAudio) extension.ifBlank { "AUDIO" } else computeResolutionBadge(width, height)

    val dimensionsText: String
        get() = if (isAudio) {
            listOf(artist, album).filter { it.isNotBlank() }.joinToString(" • ")
        } else if (width > 0 && height > 0) {
            "${width}×${height}"
        } else {
            ""
        }
}

data class VideoFolder(
    val bucketId: String,
    val name: String,
    val path: String,
    val videos: List<VideoItem>,
    val isHidden: Boolean = false
) {
    val videoCount: Int get() = videos.size
    val totalSizeBytes: Long get() = videos.sumOf { it.sizeBytes }
    val totalDurationMs: Long get() = videos.sumOf { it.durationMs }
    val latestModifiedSec: Long get() = videos.maxOfOrNull { it.dateModifiedSec } ?: 0L
    val previewVideos: List<VideoItem> get() = videos.take(4)
}

data class VideoMediaInfo(
    val title: String,
    val filePath: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val resolution: String,
    val containerFormat: String,
    val videoCodec: String,
    val audioCodec: String,
    val audioChannels: String,
    val frameRate: String,
    val bitrateKbps: Int
)

fun computeResolutionBadge(width: Int, height: Int): String {
    if (width <= 0 || height <= 0) return "VIDEO"
    val shortEdge = min(width, height)
    val longEdge = max(width, height)
    return when {
        shortEdge >= 2160 || longEdge >= 3840 -> "4K"
        shortEdge >= 1440 || longEdge >= 2560 -> "2K"
        shortEdge >= 1080 || longEdge >= 1920 -> "1080p"
        shortEdge >= 720 || longEdge >= 1280 -> "720p"
        shortEdge >= 480 || longEdge >= 854 -> "480p"
        shortEdge >= 360 -> "360p"
        else -> "SD"
    }
}

fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "00:00"
    val totalSeconds = durationMs / 1000L
    val seconds = totalSeconds % 60L
    val minutes = (totalSeconds / 60L) % 60L
    val hours = totalSeconds / 3600L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.US, "%.0f KB", bytes / kb)
        else -> "$bytes B"
    }
}

fun sortVideos(
    videos: List<VideoItem>,
    option: SortOption,
    order: SortOrder
): List<VideoItem> {
    val comparator = when (option) {
        SortOption.NAME -> compareBy<VideoItem> { it.title.lowercase(Locale.getDefault()) }
        SortOption.DATE -> compareBy<VideoItem> { it.dateModifiedSec }.thenBy { it.title.lowercase(Locale.getDefault()) }
        SortOption.SIZE -> compareBy<VideoItem> { it.sizeBytes }.thenBy { it.title.lowercase(Locale.getDefault()) }
    }
    return if (order == SortOrder.ASCENDING) {
        videos.sortedWith(comparator)
    } else {
        videos.sortedWith(comparator.reversed())
    }
}

fun sortFolders(
    folders: List<VideoFolder>,
    option: SortOption,
    order: SortOrder
): List<VideoFolder> {
    val comparator = when (option) {
        SortOption.NAME -> compareBy<VideoFolder> { it.name.lowercase(Locale.getDefault()) }
        SortOption.DATE -> compareBy<VideoFolder> { it.latestModifiedSec }.thenBy { it.name.lowercase(Locale.getDefault()) }
        SortOption.SIZE -> compareBy<VideoFolder> { it.totalSizeBytes }.thenBy { it.name.lowercase(Locale.getDefault()) }
    }
    return if (order == SortOrder.ASCENDING) {
        folders.sortedWith(comparator)
    } else {
        folders.sortedWith(comparator.reversed())
    }
}

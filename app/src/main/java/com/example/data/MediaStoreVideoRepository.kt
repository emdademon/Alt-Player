package com.example.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class MediaStoreVideoRepository(private val context: Context) {

    fun hasVideoPermission(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_VIDEO
                ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                    ) == PackageManager.PERMISSION_GRANTED
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_VIDEO
                ) == PackageManager.PERMISSION_GRANTED
            }
            else -> {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    fun hasAudioPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun checkMediaAccessState(): MediaAccessState {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                val fullVideo = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_VIDEO
                ) == PackageManager.PERMISSION_GRANTED
                val fullAudio = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                val partialVisual = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                ) == PackageManager.PERMISSION_GRANTED
                when {
                    fullVideo || fullAudio -> MediaAccessState.GRANTED_ALL
                    partialVisual -> MediaAccessState.GRANTED_PARTIAL
                    else -> MediaAccessState.DENIED
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                val videoGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_VIDEO
                ) == PackageManager.PERMISSION_GRANTED
                val audioGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (videoGranted || audioGranted) MediaAccessState.GRANTED_ALL else MediaAccessState.DENIED
            }
            else -> {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) MediaAccessState.GRANTED_ALL else MediaAccessState.DENIED
            }
        }
    }

    suspend fun scanAllVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<VideoItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projectionList = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )
        @Suppress("DEPRECATION")
        projectionList.add(MediaStore.Video.Media.DATA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projectionList.add(MediaStore.Video.Media.RELATIVE_PATH)
        }

        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"

        runCatching {
            context.contentResolver.query(
                collection,
                projectionList.toTypedArray(),
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                @Suppress("DEPRECATION")
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
                } else {
                    -1
                }

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val displayName = cursor.getString(nameCol).orEmpty()
                    val mimeType = cursor.getString(mimeCol).orEmpty().lowercase()
                    val ext = displayName.substringAfterLast('.', "").lowercase()

                    if (!isSupportedVideoFormat(ext, mimeType)) continue

                    val sizeBytes = cursor.getLong(sizeCol)
                    if (sizeBytes <= 0L) continue

                    val rawTitle = cursor.getString(titleCol).orEmpty()
                    val cleanTitle = rawTitle.ifBlank {
                        displayName.substringBeforeLast('.', displayName)
                    }

                    val bucketId = cursor.getString(bucketIdCol).orEmpty().ifBlank { "root" }
                    val rawData = if (dataCol >= 0) cursor.getString(dataCol).orEmpty() else ""
                    val rawRelPath = if (relPathCol >= 0) cursor.getString(relPathCol).orEmpty() else ""
                    val folderPath = normalizeFolderPath(rawRelPath, rawData)
                    val fullFilePath = rawData.ifBlank {
                        if (rawRelPath.isNotBlank()) "/storage/emulated/0/${rawRelPath.trimEnd('/')}/$displayName"
                        else displayName
                    }
                    val folderName = cursor.getString(bucketNameCol).orEmpty().ifBlank {
                        folderPath.trimEnd('/').substringAfterLast('/', "Internal Storage")
                    }

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    videos.add(
                        VideoItem(
                            id = id,
                            uri = contentUri,
                            title = cleanTitle,
                            displayName = displayName,
                            bucketId = bucketId,
                            folderName = folderName,
                            folderPath = folderPath.ifBlank { folderName },
                            fullFilePath = fullFilePath,
                            durationMs = cursor.getLong(durationCol).coerceAtLeast(0L),
                            sizeBytes = sizeBytes,
                            width = cursor.getInt(widthCol).coerceAtLeast(0),
                            height = cursor.getInt(heightCol).coerceAtLeast(0),
                            dateModifiedSec = cursor.getLong(dateCol).coerceAtLeast(0L),
                            mimeType = mimeType,
                            extension = ext.uppercase().ifBlank { mimeToExt(mimeType) }
                        )
                    )
                }
            }
        }
        videos
    }

    suspend fun scanAllAudio(): List<VideoItem> = withContext(Dispatchers.IO) {
        if (!hasAudioPermission()) return@withContext emptyList()
        val tracks = mutableListOf<VideoItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projectionList = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.MIME_TYPE
        )
        @Suppress("DEPRECATION")
        projectionList.add(MediaStore.Audio.Media.DATA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projectionList.add(MediaStore.Audio.Media.RELATIVE_PATH)
        }

        val sortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"

        runCatching {
            context.contentResolver.query(
                collection,
                projectionList.toTypedArray(),
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                @Suppress("DEPRECATION")
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else {
                    -1
                }

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val displayName = cursor.getString(nameCol).orEmpty()
                    val mimeType = cursor.getString(mimeCol).orEmpty().lowercase()
                    val ext = displayName.substringAfterLast('.', "").lowercase()

                    if (!isSupportedAudioFormat(ext, mimeType)) continue

                    val sizeBytes = cursor.getLong(sizeCol)
                    if (sizeBytes <= 0L) continue

                    val rawTitle = cursor.getString(titleCol).orEmpty()
                    val cleanTitle = rawTitle.ifBlank {
                        displayName.substringBeforeLast('.', displayName)
                    }
                    val artist = if (artistCol >= 0) {
                        cursor.getString(artistCol).orEmpty().takeIf { it != "<unknown>" }.orEmpty()
                    } else ""
                    val album = if (albumCol >= 0) {
                        cursor.getString(albumCol).orEmpty().takeIf { it != "<unknown>" }.orEmpty()
                    } else ""
                    val albumId = if (albumIdCol >= 0) cursor.getLong(albumIdCol) else 0L

                    val rawData = if (dataCol >= 0) cursor.getString(dataCol).orEmpty() else ""
                    val rawRelPath = if (relPathCol >= 0) cursor.getString(relPathCol).orEmpty() else ""
                    val folderPath = normalizeFolderPath(rawRelPath, rawData)
                    val fullFilePath = rawData.ifBlank {
                        if (rawRelPath.isNotBlank()) "/storage/emulated/0/${rawRelPath.trimEnd('/')}/$displayName"
                        else displayName
                    }
                    val folderName = folderPath.trimEnd('/').substringAfterLast('/', "Music")

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    tracks.add(
                        VideoItem(
                            id = -id,
                            uri = contentUri,
                            title = cleanTitle,
                            displayName = displayName,
                            bucketId = "audio_${folderName.lowercase()}",
                            folderName = folderName,
                            folderPath = folderPath.ifBlank { folderName },
                            fullFilePath = fullFilePath,
                            durationMs = cursor.getLong(durationCol).coerceAtLeast(0L),
                            sizeBytes = sizeBytes,
                            width = 0,
                            height = 0,
                            dateModifiedSec = cursor.getLong(dateCol).coerceAtLeast(0L),
                            mimeType = mimeType,
                            extension = ext.uppercase().ifBlank { audioMimeToExt(mimeType) },
                            isAudio = true,
                            artist = artist.ifBlank { "Unknown Artist" },
                            album = album,
                            albumId = albumId
                        )
                    )
                }
            }
        }
        tracks
    }

    suspend fun resolveDocumentVideo(uri: Uri): VideoItem = withContext(Dispatchers.IO) {
        var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "Local Media"
        var sizeBytes = 0L

        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                    }
                    if (sizeIdx >= 0) {
                        sizeBytes = cursor.getLong(sizeIdx).coerceAtLeast(0L)
                    }
                }
            }
        }

        val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
        val rawExt = displayName.substringAfterLast('.', "").lowercase()
        val isAudio = isSupportedAudioFormat(rawExt, mime) && !mime.startsWith("video/")

        var durationMs = 0L
        var width = 0
        var height = 0
        var artist = ""
        var album = ""
        val retriever = MediaMetadataRetriever()
        runCatching {
            retriever.setDataSource(context, uri)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty()
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty()
        }
        runCatching { retriever.release() }

        val ext = rawExt.uppercase().ifBlank { if (isAudio) audioMimeToExt(mime) else "VIDEO" }
        val cleanTitle = displayName.substringBeforeLast('.', displayName)

        VideoItem(
            id = uri.toString().hashCode().toLong(),
            uri = uri,
            title = cleanTitle,
            displayName = displayName,
            bucketId = "external_pick",
            folderName = "Opened Files",
            folderPath = "Device Storage",
            fullFilePath = uri.path ?: displayName,
            durationMs = durationMs,
            sizeBytes = sizeBytes,
            width = width,
            height = height,
            dateModifiedSec = System.currentTimeMillis() / 1000L,
            mimeType = mime,
            extension = ext,
            isExternalPick = true,
            isAudio = isAudio,
            artist = artist.ifBlank { if (isAudio) "Unknown Artist" else "" },
            album = album
        )
    }

    suspend fun loadMediaInfo(video: VideoItem): VideoMediaInfo = withContext(Dispatchers.IO) {
        var videoCodec = "Unknown"
        var audioCodec = "None"
        var audioChannels = "—"
        var frameRate = "—"
        var bitrateKbps = 0

        val extractor = MediaExtractor()
        runCatching {
            extractor.setDataSource(context, video.uri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/") && videoCodec == "Unknown") {
                    videoCodec = friendlyCodecName(mime)
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        val fps = runCatching { format.getFloat(MediaFormat.KEY_FRAME_RATE) }
                            .getOrElse { format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }
                        if (fps > 0f) {
                            frameRate = String.format(Locale.US, "%.2f fps", fps)
                        }
                    }
                    if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                        bitrateKbps = format.getInteger(MediaFormat.KEY_BIT_RATE) / 1000
                    }
                } else if (mime.startsWith("audio/") && audioCodec == "None") {
                    audioCodec = friendlyCodecName(mime)
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        val ch = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        audioChannels = when (ch) {
                            1 -> "Mono (1 ch)"
                            2 -> "Stereo (2 ch)"
                            6 -> "5.1 (6 ch)"
                            8 -> "7.1 (8 ch)"
                            else -> "$ch ch"
                        }
                    }
                }
            }
        }
        runCatching { extractor.release() }

        if (bitrateKbps == 0 || frameRate == "—") {
            val retriever = MediaMetadataRetriever()
            runCatching {
                retriever.setDataSource(context, video.uri)
                if (bitrateKbps == 0) {
                    val bps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                        ?.toIntOrNull() ?: 0
                    bitrateKbps = bps / 1000
                }
                if (frameRate == "—" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val fps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                        ?.toFloatOrNull()
                    if (fps != null && fps > 0f) {
                        frameRate = String.format(Locale.US, "%.2f fps", fps)
                    }
                }
            }
            runCatching { retriever.release() }
        }

        VideoMediaInfo(
            title = video.displayName,
            filePath = video.fullFilePath.ifBlank { video.folderPath },
            sizeBytes = video.sizeBytes,
            durationMs = video.durationMs,
            resolution = video.dimensionsText.ifBlank { video.resolutionLabel },
            containerFormat = video.extension,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            audioChannels = audioChannels,
            frameRate = frameRate,
            bitrateKbps = bitrateKbps
        )
    }

    suspend fun renameMediaStoreVideo(uri: Uri, newTitleWithoutExt: String, currentDisplayName: String): Boolean =
        withContext(Dispatchers.IO) {
            val cleanBase = newTitleWithoutExt.trim().replace('/', '_').replace('\\', '_')
            if (cleanBase.isEmpty()) return@withContext false
            val ext = currentDisplayName.substringAfterLast('.', "")
            val finalDisplayName = if (ext.isNotEmpty() && !cleanBase.endsWith(".$ext", ignoreCase = true)) {
                "$cleanBase.$ext"
            } else {
                cleanBase
            }
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, finalDisplayName)
                    put(MediaStore.Video.Media.TITLE, cleanBase)
                }
                context.contentResolver.update(uri, values, null, null) > 0
            }.getOrDefault(false)
        }

    suspend fun deleteMediaStoreVideoLegacy(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.delete(uri, null, null) > 0
        }.getOrDefault(false)
    }

    suspend fun findMatchingSrtUri(video: VideoItem): Uri? = withContext(Dispatchers.IO) {
        val baseName = video.displayName.substringBeforeLast('.', video.title)
        val expectedSrtName = "$baseName.srt"

        // 1. Try direct sibling file if readable on device storage
        if (video.fullFilePath.isNotBlank() && video.fullFilePath.startsWith("/")) {
            val parent = File(video.fullFilePath).parentFile
            if (parent != null) {
                val candidate = File(parent, expectedSrtName)
                if (candidate.exists() && candidate.canRead()) {
                    return@withContext Uri.fromFile(candidate)
                }
            }
        }

        // 2. Try MediaStore.Files query for matching .srt file name
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val filesUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
                val projection = arrayOf(MediaStore.Files.FileColumns._ID)
                val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ?"
                context.contentResolver.query(
                    filesUri,
                    projection,
                    selection,
                    arrayOf(expectedSrtName),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(0)
                        return@withContext ContentUris.withAppendedId(filesUri, id)
                    }
                }
            }
        }
        null
    }

    fun groupVideosByFolder(
        videos: List<VideoItem>,
        hiddenBucketIds: Set<String>
    ): List<VideoFolder> {
        return videos.groupBy { it.bucketId }
            .map { (bucketId, folderVideos) ->
                val sample = folderVideos.first()
                VideoFolder(
                    bucketId = bucketId,
                    name = sample.folderName,
                    path = sample.folderPath,
                    videos = folderVideos,
                    isHidden = bucketId in hiddenBucketIds
                )
            }
    }

    private fun normalizeFolderPath(relPath: String, rawData: String): String {
        if (relPath.isNotBlank()) return relPath.trimEnd('/')
        if (rawData.isNotBlank()) {
            val root = Environment.getExternalStorageDirectory().absolutePath
            return File(rawData).parent?.removePrefix("$root/") ?: ""
        }
        return ""
    }

    private fun friendlyCodecName(mime: String): String {
        return when (mime.lowercase()) {
            "video/avc", "video/h264" -> "H.264 (AVC)"
            "video/hevc", "video/h265" -> "H.265 (HEVC)"
            "video/x-vnd.on2.vp9", "video/vp9" -> "VP9"
            "video/x-vnd.on2.vp8", "video/vp8" -> "VP8"
            "video/av01" -> "AV1"
            "video/mp4v-es" -> "MPEG-4"
            "audio/mp4a-latm", "audio/aac" -> "AAC"
            "audio/ac3" -> "AC-3 (Dolby)"
            "audio/eac3" -> "E-AC-3 (Dolby+)"
            "audio/opus" -> "Opus"
            "audio/vorbis" -> "Vorbis"
            "audio/mpeg" -> "MP3"
            "audio/flac" -> "FLAC"
            else -> mime.substringAfter('/').uppercase()
        }
    }

    companion object {
        private val SUPPORTED_EXTENSIONS = setOf("mp4", "mkv", "avi", "webm", "m4v", "mov")
        private val SUPPORTED_MIMES = setOf(
            "video/mp4",
            "video/x-matroska",
            "video/matroska",
            "video/avi",
            "video/x-msvideo",
            "video/msvideo",
            "video/webm",
            "video/quicktime"
        )

        private val SUPPORTED_AUDIO_EXTENSIONS = setOf(
            "mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "wma", "amr", "3ga"
        )
        private val SUPPORTED_AUDIO_MIMES = setOf(
            "audio/mpeg",
            "audio/mp3",
            "audio/mp4",
            "audio/m4a",
            "audio/x-m4a",
            "audio/aac",
            "audio/aac-adts",
            "audio/wav",
            "audio/x-wav",
            "audio/vnd.wave",
            "audio/flac",
            "audio/x-flac",
            "audio/ogg",
            "application/ogg",
            "application/x-ogg",
            "audio/opus",
            "audio/vorbis"
        )

        fun isSupportedVideoFormat(extension: String, mimeType: String): Boolean {
            val ext = extension.lowercase()
            val mime = mimeType.lowercase()
            return ext in SUPPORTED_EXTENSIONS ||
                mime in SUPPORTED_MIMES ||
                (ext.isBlank() && mime.startsWith("video/"))
        }

        fun isSupportedAudioFormat(extension: String, mimeType: String): Boolean {
            val ext = extension.lowercase()
            val mime = mimeType.lowercase()
            return ext in SUPPORTED_AUDIO_EXTENSIONS ||
                mime in SUPPORTED_AUDIO_MIMES ||
                (ext.isBlank() && mime.startsWith("audio/"))
        }

        private fun mimeToExt(mimeType: String): String {
            return when {
                mimeType.contains("mp4") -> "MP4"
                mimeType.contains("matroska") -> "MKV"
                mimeType.contains("avi") -> "AVI"
                mimeType.contains("webm") -> "WEBM"
                else -> "VIDEO"
            }
        }

        private fun audioMimeToExt(mimeType: String): String {
            return when {
                mimeType.contains("mpeg") || mimeType.contains("mp3") -> "MP3"
                mimeType.contains("mp4") || mimeType.contains("m4a") -> "M4A"
                mimeType.contains("aac") -> "AAC"
                mimeType.contains("wav") -> "WAV"
                mimeType.contains("flac") -> "FLAC"
                mimeType.contains("opus") -> "OPUS"
                mimeType.contains("ogg") -> "OGG"
                else -> "AUDIO"
            }
        }
    }
}

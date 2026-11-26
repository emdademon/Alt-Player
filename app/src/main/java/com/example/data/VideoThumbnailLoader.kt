package com.example.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object VideoThumbnailLoader {
    private const val MAX_CACHE_ENTRIES = 220
    private const val TARGET_THUMB_WIDTH = 400
    private const val TARGET_THUMB_HEIGHT = 225
    private const val TARGET_ART_SIZE = 320

    private val memoryCache = LruCache<String, ImageBitmap>(MAX_CACHE_ENTRIES)
    private val decodeSemaphore = Semaphore(permits = 3)
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<ImageBitmap?>>()

    fun getCached(uri: Uri): ImageBitmap? = memoryCache.get(uri.toString())

    suspend fun loadThumbnail(context: Context, video: VideoItem): ImageBitmap? {
        val key = video.uri.toString()
        memoryCache.get(key)?.let { return it }

        inFlight[key]?.let { existing ->
            return existing.await()
        }

        val deferred = CompletableDeferred<ImageBitmap?>()
        val raced = inFlight.putIfAbsent(key, deferred)
        if (raced != null) {
            return raced.await()
        }

        return try {
            val loaded = decodeSemaphore.withPermit {
                memoryCache.get(key)?.let { return@withPermit it }
                val rawBitmap: Bitmap? = withContext(Dispatchers.IO) {
                    if (video.isAudio) {
                        loadAudioAlbumArt(context, video)
                    } else {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !video.isExternalPick) {
                                context.contentResolver.loadThumbnail(
                                    video.uri,
                                    Size(TARGET_THUMB_WIDTH, TARGET_THUMB_HEIGHT),
                                    null
                                )
                            } else {
                                loadWithRetriever(context, video.uri)
                                    ?: loadLegacyMediaStoreThumb(context, video.id)
                            }
                        } catch (_: Throwable) {
                            runCatching { loadWithRetriever(context, video.uri) }.getOrNull()
                        }
                    }
                }

                val scaled = rawBitmap?.let { bmp ->
                    scaleBitmapIfNeeded(bmp, if (video.isAudio) TARGET_ART_SIZE else TARGET_THUMB_WIDTH)
                }
                scaled?.apply { prepareToDraw() }?.asImageBitmap()?.also { img ->
                    memoryCache.put(key, img)
                }
            }
            deferred.complete(loaded)
            loaded
        } catch (ce: CancellationException) {
            deferred.cancel(ce)
            throw ce
        } catch (t: Throwable) {
            deferred.complete(null)
            null
        } finally {
            inFlight.remove(key, deferred)
        }
    }

    private fun loadAudioAlbumArt(context: Context, item: VideoItem): Bitmap? {
        if (item.albumId > 0L) {
            val albumArtUri = ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                item.albumId
            )
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(albumArtUri)?.use { s ->
                    BitmapFactory.decodeStream(s, null, bounds)
                }
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, TARGET_ART_SIZE)
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                context.contentResolver.openInputStream(albumArtUri)?.use { s ->
                    BitmapFactory.decodeStream(s, null, opts)
                }
            }.getOrNull()?.let { return it }
        }

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, item.uri)
            val embedded = retriever.embeddedPicture
            if (embedded != null && embedded.isNotEmpty()) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(embedded, 0, embedded.size, bounds)
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, TARGET_ART_SIZE)
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                BitmapFactory.decodeByteArray(embedded, 0, embedded.size, opts)
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun loadWithRetriever(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            val targetUs = if (durationMs > 4_000L) 2_000_000L else (durationMs * 250L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    targetUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    TARGET_THUMB_WIDTH,
                    TARGET_THUMB_HEIGHT
                )
            } else {
                retriever.getFrameAtTime(targetUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(-1)
            }
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    @Suppress("DEPRECATION")
    private fun loadLegacyMediaStoreThumb(context: Context, videoId: Long): Bitmap? {
        if (videoId <= 0L) return null
        return runCatching {
            MediaStore.Video.Thumbnails.getThumbnail(
                context.contentResolver,
                videoId,
                MediaStore.Video.Thumbnails.MINI_KIND,
                null
            )
        }.getOrNull()
    }

    private fun computeSampleSize(width: Int, height: Int, targetMaxDim: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        val maxDim = maxOf(width, height)
        while (maxDim / (sample * 2) >= targetMaxDim) {
            sample *= 2
        }
        return sample
    }

    private fun scaleBitmapIfNeeded(source: Bitmap, maxDim: Int): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= maxDim && h <= maxDim) return source
        val ratio = maxDim.toFloat() / maxOf(w, h).toFloat()
        val dstW = (w * ratio).toInt().coerceAtLeast(1)
        val dstH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, dstW, dstH, true)
    }
}

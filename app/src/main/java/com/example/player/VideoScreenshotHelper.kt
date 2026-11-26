package com.example.player

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

object VideoScreenshotHelper {

    suspend fun captureAndSaveFrame(
        context: Context,
        rootView: View,
        videoUri: Uri,
        videoTitle: String,
        positionMs: Long,
        videoDimensions: Pair<Int, Int>
    ): Boolean {
        val capturedBitmap = captureFromSurfaceOrTexture(rootView, videoDimensions)
            ?: captureFromRetriever(context, videoUri, positionMs)
            ?: return false

        return try {
            withContext(Dispatchers.IO) {
                saveBitmapToPictures(context, capturedBitmap, videoTitle)
            }
        } finally {
            if (!capturedBitmap.isRecycled) {
                capturedBitmap.recycle()
            }
        }
    }

    private suspend fun captureFromSurfaceOrTexture(
        rootView: View,
        videoDimensions: Pair<Int, Int>
    ): Bitmap? {
        val surfaceView = findViewOfType<SurfaceView>(rootView)
        if (surfaceView != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            surfaceView.holder.surface?.isValid == true &&
            surfaceView.width > 0 &&
            surfaceView.height > 0
        ) {
            val (targetW, targetH) = computeCaptureSize(
                fallbackW = surfaceView.width,
                fallbackH = surfaceView.height,
                videoDimensions = videoDimensions
            )
            val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            val copied = suspendCancellableCoroutine { cont ->
                try {
                    PixelCopy.request(
                        surfaceView,
                        bitmap,
                        { copyResult ->
                            if (cont.isActive) {
                                cont.resume(copyResult == PixelCopy.SUCCESS)
                            }
                        },
                        Handler(Looper.getMainLooper())
                    )
                } catch (_: Throwable) {
                    if (cont.isActive) cont.resume(false)
                }
            }
            if (copied) return bitmap
            if (!bitmap.isRecycled) bitmap.recycle()
        }

        val textureView = findViewOfType<TextureView>(rootView)
        if (textureView != null && textureView.isAvailable) {
            return runCatching { textureView.bitmap }.getOrNull()
        }

        return null
    }

    private suspend fun captureFromRetriever(
        context: Context,
        videoUri: Uri,
        positionMs: Long
    ): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val timeUs = (positionMs.coerceAtLeast(0L)) * 1000L
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: retriever.getFrameAtTime(-1L)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun saveBitmapToPictures(
        context: Context,
        bitmap: Bitmap,
        videoTitle: String
    ): Boolean {
        val cleanTitle = videoTitle
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .trim('_')
            .take(36)
            .ifBlank { "Frame" }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "AltPlayer_${cleanTitle}_$timestamp.png"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/AltPlayer"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val itemUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            try {
                val written = resolver.openOutputStream(itemUri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } == true
                if (!written) {
                    resolver.delete(itemUri, null, null)
                    return false
                }
                val finishValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(itemUri, finishValues, null, null)
                true
            } catch (_: Throwable) {
                runCatching { resolver.delete(itemUri, null, null) }
                false
            }
        } else {
            val targetDir = run {
                @Suppress("DEPRECATION")
                val publicDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "AltPlayer"
                )
                if (publicDir.exists() || publicDir.mkdirs()) {
                    publicDir
                } else {
                    File(
                        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir,
                        "AltPlayer"
                    ).apply { mkdirs() }
                }
            }
            val outFile = File(targetDir, fileName)
            val saved = runCatching {
                outFile.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }.getOrDefault(false)
            if (saved) {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(outFile.absolutePath),
                    arrayOf("image/png"),
                    null
                )
            }
            saved
        }
    }

    private fun computeCaptureSize(
        fallbackW: Int,
        fallbackH: Int,
        videoDimensions: Pair<Int, Int>
    ): Pair<Int, Int> {
        val rawW = if (videoDimensions.first > 0) videoDimensions.first else fallbackW
        val rawH = if (videoDimensions.second > 0) videoDimensions.second else fallbackH
        val maxDim = 1920
        val largest = maxOf(rawW, rawH).coerceAtLeast(1)
        return if (largest <= maxDim) {
            rawW.coerceAtLeast(1) to rawH.coerceAtLeast(1)
        } else {
            val scale = maxDim.toFloat() / largest.toFloat()
            (rawW * scale).toInt().coerceAtLeast(1) to (rawH * scale).toInt().coerceAtLeast(1)
        }
    }

    private inline fun <reified T : View> findViewOfType(root: View): T? {
        val queue = ArrayDeque<View>()
        queue.addLast(root)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current is T) return current
            if (current is ViewGroup) {
                for (i in 0 until current.childCount) {
                    queue.addLast(current.getChildAt(i))
                }
            }
        }
        return null
    }
}

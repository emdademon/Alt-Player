package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.MediaStoreVideoRepository
import com.example.data.SortOption
import com.example.data.SortOrder
import com.example.data.VideoItem
import com.example.data.computeResolutionBadge
import com.example.data.formatDuration
import com.example.data.formatFileSize
import com.example.data.sortVideos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VeloPlayCoreTest {

    @Test
    fun `manifest has zero network permissions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requested = pkgInfo.requestedPermissions?.toList().orEmpty()
        assertFalse("INTERNET must not be requested", requested.contains(Manifest.permission.INTERNET))
        assertFalse(
            "ACCESS_NETWORK_STATE must not be requested",
            requested.contains(Manifest.permission.ACCESS_NETWORK_STATE)
        )
    }

    @Test
    fun `resolution badges and formatters work accurately`() {
        assertEquals("4K", computeResolutionBadge(3840, 2160))
        assertEquals("1080p", computeResolutionBadge(1920, 1080))
        assertEquals("720p", computeResolutionBadge(1280, 720))
        assertEquals("02:05", formatDuration(125_000L))
        assertEquals("1:01:05", formatDuration(3_665_000L))
        assertEquals("1.50 GB", formatFileSize((1.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun `supported video formats include mp4 mkv avi and webm`() {
        assertTrue(MediaStoreVideoRepository.isSupportedVideoFormat("mp4", "video/mp4"))
        assertTrue(MediaStoreVideoRepository.isSupportedVideoFormat("mkv", "video/x-matroska"))
        assertTrue(MediaStoreVideoRepository.isSupportedVideoFormat("avi", "video/x-msvideo"))
        assertTrue(MediaStoreVideoRepository.isSupportedVideoFormat("webm", "video/webm"))
        assertFalse(MediaStoreVideoRepository.isSupportedVideoFormat("jpg", "image/jpeg"))
    }

    @Test
    fun `sortVideos sorts by name date and size`() {
        val items = listOf(
            VideoItem(id = 1, uri = Uri.EMPTY, title = "Beta", displayName = "Beta.mp4", bucketId = "1", folderName = "Movies", folderPath = "", durationMs = 1000, sizeBytes = 500, width = 1920, height = 1080, dateModifiedSec = 10, mimeType = "video/mp4", extension = "MP4"),
            VideoItem(id = 2, uri = Uri.EMPTY, title = "Alpha", displayName = "Alpha.mkv", bucketId = "1", folderName = "Movies", folderPath = "", durationMs = 2000, sizeBytes = 900, width = 1920, height = 1080, dateModifiedSec = 30, mimeType = "video/x-matroska", extension = "MKV"),
            VideoItem(id = 3, uri = Uri.EMPTY, title = "Gamma", displayName = "Gamma.avi", bucketId = "1", folderName = "Movies", folderPath = "", durationMs = 3000, sizeBytes = 200, width = 1280, height = 720, dateModifiedSec = 20, mimeType = "video/avi", extension = "AVI")
        )

        assertEquals(
            listOf("Alpha", "Beta", "Gamma"),
            sortVideos(items, SortOption.NAME, SortOrder.ASCENDING).map { it.title }
        )
        assertEquals(
            listOf("Alpha", "Gamma", "Beta"),
            sortVideos(items, SortOption.DATE, SortOrder.DESCENDING).map { it.title }
        )
        assertEquals(
            listOf("Gamma", "Beta", "Alpha"),
            sortVideos(items, SortOption.SIZE, SortOrder.ASCENDING).map { it.title }
        )
    }

    @Test
    fun `private vault PIN hashing verifies matching pins and rejects mismatches`() {
        val stored = com.example.data.PlaybackPreferencesRepository.hashPin("2580")
        assertTrue(com.example.data.PlaybackPreferencesRepository.verifyPin("2580", stored))
        assertFalse(com.example.data.PlaybackPreferencesRepository.verifyPin("1234", stored))
        assertFalse(com.example.data.PlaybackPreferencesRepository.verifyPin("2580", null))
    }
}

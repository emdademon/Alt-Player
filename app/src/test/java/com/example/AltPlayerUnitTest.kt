package com.example

import com.example.data.MediaStoreVideoRepository
import com.example.data.PlaybackPreferencesRepository
import com.example.data.LicenseTier
import com.example.data.ProActivationResult
import com.example.data.ProLicenseManager
import com.example.data.ProLicenseState
import com.example.data.ProUnlockType
import com.example.data.computeResolutionBadge
import com.example.data.formatDuration
import com.example.data.formatFileSize
import com.example.player.AspectMode
import com.example.player.EqPreset
import com.example.player.EqualizerUiState
import com.example.player.LoopMode
import com.example.player.PlayerMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AltPlayerUnitTest {

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
    fun `private vault PIN hashing verifies matching pins and rejects mismatches`() {
        val stored = PlaybackPreferencesRepository.hashPin("2580")
        assertTrue(PlaybackPreferencesRepository.verifyPin("2580", stored))
        assertFalse(PlaybackPreferencesRepository.verifyPin("1234", stored))
        assertFalse(PlaybackPreferencesRepository.verifyPin("2580", null))
    }

    @Test
    fun `player math utilities clamp zoom subtitle sync loudness boost and pip aspect`() {
        assertEquals(0.25f, PlayerMath.clampZoomScale(0.10f), 0.001f)
        assertEquals(1.35f, PlayerMath.clampZoomScale(1.35f), 0.001f)
        assertEquals(2.00f, PlayerMath.clampZoomScale(3.50f), 0.001f)

        assertEquals(-30_000L, PlayerMath.clampSubtitleOffsetMs(-99_000L))
        assertEquals(30_000L, PlayerMath.clampSubtitleOffsetMs(99_000L))
        assertEquals("+0.3s", PlayerMath.formatSubtitleOffset(300L))
        assertEquals("-0.5s", PlayerMath.formatSubtitleOffset(-500L))
        assertEquals("0.0s", PlayerMath.formatSubtitleOffset(0L))

        assertEquals(0, PlayerMath.computeLoudnessGainMb(100))
        assertEquals(1500, PlayerMath.computeLoudnessGainMb(200))

        assertEquals(16 to 9, PlayerMath.clampPipAspectRatio(0, 0))
        val (wideNum, wideDen) = PlayerMath.clampPipAspectRatio(5000, 1000)
        assertTrue(wideNum.toFloat() / wideDen.toFloat() <= 2.21f)
        val (tallNum, tallDen) = PlayerMath.clampPipAspectRatio(100, 1000)
        assertTrue(tallNum.toFloat() / tallDen.toFloat() >= 0.44f)

        assertEquals(LoopMode.REPEAT_ONE, LoopMode.OFF.next())
        assertEquals(LoopMode.REPEAT_FOLDER, LoopMode.REPEAT_ONE.next())
        assertEquals(LoopMode.OFF, LoopMode.REPEAT_FOLDER.next())
        assertEquals(AspectMode.STRETCH, AspectMode.FIT.next())
    }

    @Test
    fun `supported audio formats include mp3 m4a aac wav flac ogg and opus`() {
        assertTrue(MediaStoreVideoRepository.isSupportedAudioFormat("mp3", "audio/mpeg"))
        assertTrue(MediaStoreVideoRepository.isSupportedAudioFormat("m4a", "audio/mp4"))
        assertTrue(MediaStoreVideoRepository.isSupportedAudioFormat("aac", "audio/aac"))
        assertTrue(MediaStoreVideoRepository.isSupportedAudioFormat("wav", "audio/wav"))
        assertTrue(MediaStoreVideoRepository.isSupportedAudioFormat("flac", "audio/flac"))
        assertTrue(MediaStoreVideoRepository.isSupportedAudioFormat("ogg", "audio/ogg"))
        assertTrue(MediaStoreVideoRepository.isSupportedAudioFormat("opus", "audio/opus"))
        assertFalse(MediaStoreVideoRepository.isSupportedAudioFormat("png", "image/png"))
    }

    @Test
    fun `offline pro license validates family vip deterministic buyer codes and 3 day trial`() {
        val vipResult = ProLicenseManager.validateCode("  alt-family-vip ")
        assertEquals(
            ProActivationResult.Success(ProUnlockType.FAMILY_VIP, "ALT-FAMILY-VIP"),
            vipResult
        )
        assertEquals(
            LicenseTier.FAMILY_VIP.value,
            (vipResult as ProActivationResult.Success).licenseTier
        )

        val generated = ProLicenseManager.generateBuyerCode("AB")
        assertTrue(generated.startsWith("ALT-PRO-AB"))
        assertEquals(12, generated.length)
        assertTrue(ProLicenseManager.isValidBuyerCode(generated))
        val buyerResult = ProLicenseManager.validateCode(generated.lowercase())
        assertEquals(
            ProActivationResult.Success(ProUnlockType.PRO_CODE, generated),
            buyerResult
        )
        assertEquals(
            LicenseTier.PRO_BUYER.value,
            (buyerResult as ProActivationResult.Success).licenseTier
        )

        val tampered = generated.dropLast(1) + if (generated.last() == '2') '3' else '2'
        assertFalse(ProLicenseManager.isValidBuyerCode(tampered))
        assertEquals(ProActivationResult.InvalidCode, ProLicenseManager.validateCode(tampered))

        val initial = ProLicenseState()
        assertTrue(initial.shouldShowActivationGate(1000L))
        assertFalse(initial.isAccessGranted(1000L))

        val startMs = 1_000_000L
        val activeTrial = ProLicenseState(
            unlockType = ProUnlockType.TRIAL,
            trialStartedAtMs = startMs,
            hasSeenActivation = true
        )
        assertTrue(activeTrial.isAccessGranted(startMs + 24L * 60L * 60L * 1000L))
        assertFalse(activeTrial.shouldShowActivationGate(startMs + 24L * 60L * 60L * 1000L))
        assertFalse(activeTrial.isTrialExpired(startMs + 24L * 60L * 60L * 1000L))

        val afterThreeDays = startMs + ProLicenseManager.TRIAL_DURATION_MS + 1L
        assertFalse(activeTrial.isAccessGranted(afterThreeDays))
        assertTrue(activeTrial.isTrialExpired(afterThreeDays))
        assertTrue(activeTrial.shouldShowActivationGate(afterThreeDays))

        val lifetime = ProLicenseState(
            unlockType = ProUnlockType.FAMILY_VIP,
            activatedCode = "ALT-FAMILY-VIP",
            hasSeenActivation = true
        )
        assertEquals(LicenseTier.FAMILY_VIP.value, lifetime.resolvedLicenseTier())
        assertTrue(lifetime.isUnlocked)
        assertTrue(lifetime.isLifetimePro())
        assertTrue(lifetime.isAccessGranted(afterThreeDays))
        assertFalse(lifetime.shouldShowActivationGate(afterThreeDays))
    }

    @Test
    fun `offline 5 band equalizer presets normalize and format frequency and db labels`() {
        assertEquals(5, EqPreset.selectablePresets.size)
        EqPreset.selectablePresets.forEach { preset ->
            assertEquals(5, preset.bandOffsetsMb.size)
        }

        val clamped = PlayerMath.normalizeEqBands(
            targetMb = listOf(-2500, 600, 0, 1200, 3000),
            minMb = -1500,
            maxMb = 1500,
            bandCount = 5
        )
        assertEquals(listOf(-1500, 600, 0, 1200, 1500), clamped)

        assertEquals("60Hz", PlayerMath.formatEqFrequency(60))
        assertEquals("910Hz", PlayerMath.formatEqFrequency(910))
        assertEquals("3.6kHz", PlayerMath.formatEqFrequency(3600))
        assertEquals("14kHz", PlayerMath.formatEqFrequency(14000))

        assertEquals("0.0 dB", PlayerMath.formatEqDb(0))
        assertEquals("+6.0 dB", PlayerMath.formatEqDb(600))
        assertEquals("-1.8 dB", PlayerMath.formatEqDb(-180))

        val defaultState = EqualizerUiState()
        assertFalse(defaultState.enabled)
        assertEquals(EqPreset.NORMAL, defaultState.preset)
        assertEquals(5, defaultState.bandLevelsMb.size)
    }
}

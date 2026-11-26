package com.example.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.Tracks

enum class AspectMode(
    val label: String,
    val contentScale: ContentScale
) {
    FIT("Fit", ContentScale.Fit),
    STRETCH("Stretch", ContentScale.FillBounds),
    CROP("Crop", ContentScale.Crop);

    fun next(): AspectMode {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }
}

enum class DecoderMode(val label: String, val fullName: String) {
    HW("HW", "Hardware Acceleration"),
    SW("SW", "Software Fallback")
}

enum class LoopMode(val label: String, val exoRepeatMode: Int) {
    OFF("Off", Player.REPEAT_MODE_OFF),
    REPEAT_ONE("Repeat One", Player.REPEAT_MODE_ONE),
    REPEAT_FOLDER("Repeat Folder", Player.REPEAT_MODE_ALL);

    fun next(): LoopMode = entries[(ordinal + 1) % entries.size]
}

enum class SleepTimerOption(val label: String, val minutes: Int?) {
    OFF("Off", null),
    MIN_15("15m", 15),
    MIN_30("30m", 30),
    MIN_45("45m", 45),
    MIN_60("60m", 60),
    END_OF_VIDEO("End of Video", -1)
}

enum class SubtitleFontSize(val label: String, val sp: TextUnit) {
    SMALL("Small", 14.sp),
    MEDIUM("Medium", 17.sp),
    LARGE("Large", 21.sp)
}

enum class SubtitleTextColor(val label: String, val color: Color) {
    WHITE("White", Color.White),
    YELLOW("Yellow", Color(0xFFFFD54F)),
    CYAN("Cyan", Color(0xFF00E5FF))
}

data class SubtitleStyleConfig(
    val fontSize: SubtitleFontSize = SubtitleFontSize.MEDIUM,
    val textColor: SubtitleTextColor = SubtitleTextColor.WHITE,
    val bgOpacity: Float = 0.72f
)

val PLAYBACK_SPEEDS = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f)

data class AudioTrackOption(
    val id: String,
    val label: String,
    val detail: String,
    val isSelected: Boolean,
    val trackGroup: Tracks.Group,
    val trackIndex: Int
)

sealed interface SubtitleSelection {
    data object Off : SubtitleSelection
    data class Embedded(
        val id: String,
        val label: String,
        val language: String,
        val isSelected: Boolean,
        val trackGroup: Tracks.Group,
        val trackIndex: Int
    ) : SubtitleSelection

    data class External(
        val track: ExternalSubtitleTrack
    ) : SubtitleSelection
}

sealed interface GestureFeedback {
    data class Brightness(val percent: Int) : GestureFeedback
    data class Volume(val percent: Int, val isMuted: Boolean) : GestureFeedback
    data class HorizontalSeek(
        val targetMs: Long,
        val deltaMs: Long,
        val durationMs: Long
    ) : GestureFeedback

    data class DoubleTapSeek(
        val isForward: Boolean,
        val totalDeltaSeconds: Int
    ) : GestureFeedback

    data class PinchZoom(val percent: Int) : GestureFeedback
}

enum class EqPreset(val label: String, val bandOffsetsMb: List<Int>) {
    NORMAL("Normal", listOf(0, 0, 0, 0, 0)),
    BASS_BOOST("Bass Boost", listOf(600, 380, 50, -80, -120)),
    VOCAL("Vocal", listOf(-180, 120, 480, 380, 100)),
    CINEMA("Cinema", listOf(420, 220, 140, 320, 460)),
    TREBLE("Treble", listOf(-160, -60, 120, 420, 620)),
    CUSTOM("Custom", listOf(0, 0, 0, 0, 0));

    companion object {
        val selectablePresets: List<EqPreset> = listOf(NORMAL, BASS_BOOST, VOCAL, CINEMA, TREBLE)
    }
}

data class EqualizerUiState(
    val enabled: Boolean = false,
    val preset: EqPreset = EqPreset.NORMAL,
    val bandLevelsMb: List<Int> = EqPreset.NORMAL.bandOffsetsMb,
    val bandCenterFreqsHz: List<Int> = listOf(60, 230, 910, 3600, 14000),
    val minLevelMb: Int = -1500,
    val maxLevelMb: Int = 1500
)

object PlayerMath {
    const val MIN_ZOOM_SCALE = 0.25f
    const val MAX_ZOOM_SCALE = 2.0f
    const val MIN_AUDIO_BOOST_PERCENT = 100
    const val MAX_AUDIO_BOOST_PERCENT = 200
    const val MIN_SUBTITLE_OFFSET_MS = -30_000L
    const val MAX_SUBTITLE_OFFSET_MS = 30_000L

    fun clampZoomScale(scale: Float): Float = scale.coerceIn(MIN_ZOOM_SCALE, MAX_ZOOM_SCALE)

    fun clampSubtitleOffsetMs(offsetMs: Long): Long =
        offsetMs.coerceIn(MIN_SUBTITLE_OFFSET_MS, MAX_SUBTITLE_OFFSET_MS)

    fun formatSubtitleOffset(offsetMs: Long): String {
        val sec = offsetMs / 1000.0
        return when {
            offsetMs > 0L -> String.format(java.util.Locale.US, "+%.1fs", sec)
            offsetMs < 0L -> String.format(java.util.Locale.US, "%.1fs", sec)
            else -> "0.0s"
        }
    }

    fun computeLoudnessGainMb(boostPercent: Int): Int {
        val clamped = boostPercent.coerceIn(MIN_AUDIO_BOOST_PERCENT, MAX_AUDIO_BOOST_PERCENT)
        return (clamped - 100) * 15
    }

    fun clampPipAspectRatio(width: Int, height: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 16 to 9
        val ratio = width.toFloat() / height.toFloat()
        val clamped = ratio.coerceIn(0.45f, 2.2f)
        return (clamped * 1000f).toInt().coerceAtLeast(1) to 1000
    }

    fun normalizeEqBands(
        targetMb: List<Int>,
        minMb: Int,
        maxMb: Int,
        bandCount: Int = 5
    ): List<Int> {
        val safeMin = minOf(minMb, maxMb)
        val safeMax = maxOf(minMb, maxMb)
        val safeCount = bandCount.coerceIn(1, 10)
        return List(safeCount) { idx ->
            val raw = targetMb.getOrElse(idx) { targetMb.lastOrNull() ?: 0 }
            raw.coerceIn(safeMin, safeMax)
        }
    }

    fun formatEqFrequency(freqHz: Int): String = when {
        freqHz >= 1000 -> {
            val khz = freqHz / 1000.0
            if (khz % 1.0 == 0.0) "${khz.toInt()}kHz"
            else String.format(java.util.Locale.US, "%.1fkHz", khz)
        }
        else -> "${freqHz}Hz"
    }

    fun formatEqDb(levelMb: Int): String {
        val db = levelMb / 100.0
        return when {
            levelMb > 0 -> String.format(java.util.Locale.US, "+%.1f dB", db)
            levelMb < 0 -> String.format(java.util.Locale.US, "%.1f dB", db)
            else -> "0.0 dB"
        }
    }
}


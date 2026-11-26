package com.example.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.formatDuration
import com.example.player.AspectMode
import com.example.player.DecoderMode
import com.example.player.EqualizerUiState
import com.example.player.GestureFeedback
import com.example.player.LoopMode
import com.example.player.SleepTimerOption
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.LuminousMint
import com.example.ui.theme.TealEmeraldDeep
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletAccent
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun PlayerControlsOverlay(
    title: String,
    subtitleLabel: String,
    isAudio: Boolean,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    playbackSpeed: Float,
    aspectMode: AspectMode,
    decoderMode: DecoderMode,
    zoomScale: Float,
    pipSupported: Boolean,
    backgroundPlayEnabled: Boolean,
    quickToolsExpanded: Boolean,
    audioBoostPercent: Int,
    eqState: EqualizerUiState,
    sleepTimerOption: SleepTimerOption,
    sleepRemainingSeconds: Long,
    loopMode: LoopMode,
    abStartMs: Long?,
    abEndMs: Long?,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onBack: () -> Unit,
    onToggleDecoder: () -> Unit,
    onToggleBackgroundPlay: () -> Unit,
    onEnterPip: () -> Unit,
    onToggleQuickTools: () -> Unit,
    onOpenTracks: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onCaptureScreenshot: () -> Unit,
    onOpenSpeed: () -> Unit,
    onCycleAspect: () -> Unit,
    onResetZoom: () -> Unit,
    onToggleRotation: () -> Unit,
    onLock: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRewind10: () -> Unit,
    onForward10: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onScrubChange: (Long) -> Unit,
    onScrubFinished: () -> Unit,
    onSelectAudioBoost: (Int) -> Unit,
    onSelectSleepTimer: (SleepTimerOption) -> Unit,
    onCycleLoopMode: () -> Unit,
    onToggleAbRepeat: () -> Unit,
    onClearAbRepeat: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Top scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(AmoledBlack.copy(alpha = 0.88f), Color.Transparent)
                    )
                )
        )

        // Bottom scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, AmoledBlack.copy(alpha = 0.92f))
                    )
                )
        )

        // Top Bar + Expandable Quick-Tools Tray
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("player_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = TextPrimary
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitleLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // HW / SW Decoder Switcher Badge
                Surface(
                    onClick = onToggleDecoder,
                    color = if (decoderMode == DecoderMode.SW) CyanContainer else Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(9.dp),
                    border = BorderStroke(
                        1.dp,
                        if (decoderMode == DecoderMode.SW) ElectricCyan else Color.White.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .testTag("player_decoder_button")
                ) {
                    Text(
                        text = decoderMode.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (decoderMode == DecoderMode.SW) ElectricCyan else TextPrimary,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                    )
                }

                // Background Audio Play Toggle
                IconButton(
                    onClick = onToggleBackgroundPlay,
                    modifier = Modifier.testTag("player_bg_play_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Headphones,
                        contentDescription = stringResource(R.string.background_play),
                        tint = if (backgroundPlayEnabled) ElectricCyan else TextPrimary
                    )
                }

                // Picture-in-Picture Button (video only)
                if (pipSupported && !isAudio) {
                    IconButton(
                        onClick = onEnterPip,
                        modifier = Modifier.testTag("player_pip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PictureInPictureAlt,
                            contentDescription = stringResource(R.string.pip_mode),
                            tint = TextPrimary
                        )
                    }
                }

                // Direct Equalizer Button on Audio Player top bar
                if (isAudio) {
                    IconButton(
                        onClick = onOpenEqualizer,
                        modifier = Modifier.testTag("audio_eq_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Equalizer,
                            contentDescription = "Equalizer",
                            tint = if (eqState.enabled) LuminousMint else TextPrimary
                        )
                    }
                } else {
                    // Subtitles & Audio Tracks (Video)
                    IconButton(
                        onClick = onOpenTracks,
                        modifier = Modifier.testTag("player_tracks_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Subtitles,
                            contentDescription = stringResource(R.string.audio_and_subtitles),
                            tint = TextPrimary
                        )
                    }
                }

                // Collapsible MX Quick-Tools Button
                IconButton(
                    onClick = onToggleQuickTools,
                    modifier = Modifier.testTag("player_quick_tools_button")
                ) {
                    val toolsActive = quickToolsExpanded ||
                        eqState.enabled ||
                        audioBoostPercent > 100 ||
                        sleepTimerOption != SleepTimerOption.OFF ||
                        loopMode != LoopMode.OFF ||
                        abStartMs != null
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = stringResource(R.string.quick_tools),
                        tint = if (toolsActive) ElectricCyan else TextPrimary
                    )
                }
            }

            AnimatedVisibility(
                visible = quickToolsExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                QuickToolsTray(
                    isAudio = isAudio,
                    audioBoostPercent = audioBoostPercent,
                    eqState = eqState,
                    sleepTimerOption = sleepTimerOption,
                    sleepRemainingSeconds = sleepRemainingSeconds,
                    loopMode = loopMode,
                    abStartMs = abStartMs,
                    abEndMs = abEndMs,
                    onSelectAudioBoost = onSelectAudioBoost,
                    onOpenEqualizer = onOpenEqualizer,
                    onCaptureScreenshot = onCaptureScreenshot,
                    onSelectSleepTimer = onSelectSleepTimer,
                    onCycleLoopMode = onCycleLoopMode,
                    onToggleAbRepeat = onToggleAbRepeat,
                    onClearAbRepeat = onClearAbRepeat,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
        }

        // Center Transport Controls
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = hasPrevious,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("player_prev_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = stringResource(R.string.previous_video),
                    tint = if (hasPrevious) TextPrimary else TextSecondary.copy(alpha = 0.3f),
                    modifier = Modifier.size(30.dp)
                )
            }

            IconButton(
                onClick = onRewind10,
                modifier = Modifier
                    .size(52.dp)
                    .testTag("player_rewind_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Replay10,
                    contentDescription = "Rewind 10 seconds",
                    tint = TextPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Surface(
                onClick = onTogglePlayPause,
                shape = CircleShape,
                color = Color.Transparent,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .size(68.dp)
                    .testTag("player_play_pause_button")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(TealEmeraldDeep, LuminousMint)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                        tint = AmoledBlack,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            IconButton(
                onClick = onForward10,
                modifier = Modifier
                    .size(52.dp)
                    .testTag("player_forward_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Forward10,
                    contentDescription = "Forward 10 seconds",
                    tint = TextPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(
                onClick = onNext,
                enabled = hasNext,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("player_next_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = stringResource(R.string.next_video),
                    tint = if (hasNext) TextPrimary else TextSecondary.copy(alpha = 0.3f),
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        // Bottom Bar: Seekbar + Lock + Speed + Zoom Reset + Aspect + Rotation
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = formatDuration(currentPositionMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary
                )

                val safeDuration = durationMs.coerceAtLeast(1L)
                Slider(
                    value = currentPositionMs.coerceIn(0L, safeDuration).toFloat(),
                    onValueChange = { onScrubChange(it.toLong()) },
                    onValueChangeFinished = onScrubFinished,
                    valueRange = 0f..safeDuration.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricCyan,
                        activeTrackColor = ElectricCyan,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("player_seekbar")
                )

                Text(
                    text = formatDuration(durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onLock,
                        modifier = Modifier.testTag("player_lock_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LockOpen,
                            contentDescription = stringResource(R.string.lock_screen),
                            tint = TextPrimary
                        )
                    }

                    // Speed pill
                    Surface(
                        onClick = onOpenSpeed,
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(9.dp),
                        modifier = Modifier.testTag("player_speed_button")
                    ) {
                        Text(
                            text = "${playbackSpeed}x",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    if (abs(zoomScale - 1f) > 0.02f) {
                        Surface(
                            onClick = onResetZoom,
                            color = CyanContainer,
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier.testTag("player_reset_zoom_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ZoomOutMap,
                                    contentDescription = "Reset zoom",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "${(zoomScale * 100f).roundToInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!isAudio) {
                        Surface(
                            onClick = onCycleAspect,
                            color = Color.White.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier.testTag("player_aspect_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AspectRatio,
                                    contentDescription = stringResource(R.string.aspect_mode),
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = aspectMode.label.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onToggleRotation,
                        modifier = Modifier.testTag("player_rotate_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ScreenRotation,
                            contentDescription = stringResource(R.string.rotate_screen),
                            tint = TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GestureFeedbackHud(
    feedback: GestureFeedback?,
    statusToastLabel: String?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        when (feedback) {
            is GestureFeedback.Brightness -> {
                VerticalMeterHud(
                    icon = Icons.Rounded.BrightnessHigh,
                    label = stringResource(R.string.brightness),
                    percent = feedback.percent,
                    accentColor = Color(0xFFFFD54F),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 32.dp)
                )
            }

            is GestureFeedback.Volume -> {
                VerticalMeterHud(
                    icon = if (feedback.isMuted) {
                        Icons.AutoMirrored.Rounded.VolumeOff
                    } else {
                        Icons.AutoMirrored.Rounded.VolumeUp
                    },
                    label = stringResource(R.string.volume),
                    percent = feedback.percent,
                    accentColor = ElectricCyan,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 32.dp)
                )
            }

            is GestureFeedback.HorizontalSeek -> {
                val deltaSec = feedback.deltaMs / 1000L
                val sign = if (deltaSec >= 0) "+" else "-"
                val absDeltaMs = abs(feedback.deltaMs)
                Surface(
                    color = AmoledBlack.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = formatDuration(feedback.targetMs),
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "$sign${formatDuration(absDeltaMs)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = ElectricCyan
                        )
                    }
                }
            }

            is GestureFeedback.DoubleTapSeek -> {
                Surface(
                    color = AmoledBlack.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .align(if (feedback.isForward) Alignment.CenterEnd else Alignment.CenterStart)
                        .padding(horizontal = 36.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!feedback.isForward) {
                            Icon(
                                imageVector = Icons.Rounded.FastRewind,
                                contentDescription = null,
                                tint = ElectricCyan
                            )
                        }
                        Text(
                            text = if (feedback.isForward) {
                                "+${feedback.totalDeltaSeconds}s"
                            } else {
                                "-${feedback.totalDeltaSeconds}s"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        if (feedback.isForward) {
                            Icon(
                                imageVector = Icons.Rounded.FastForward,
                                contentDescription = null,
                                tint = ElectricCyan
                            )
                        }
                    }
                }
            }

            is GestureFeedback.PinchZoom -> {
                Surface(
                    color = AmoledBlack.copy(alpha = 0.84f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("pinch_zoom_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ZoomOutMap,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "${feedback.percent}%",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            null -> Unit
        }

        if (statusToastLabel != null && feedback == null) {
            Surface(
                color = AmoledBlack.copy(alpha = 0.82f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text(
                    text = statusToastLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = ElectricCyan,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun VerticalMeterHud(
    icon: ImageVector,
    label: String,
    percent: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = AmoledBlack.copy(alpha = 0.82f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )

            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(110.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight((percent / 100f).coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(accentColor, accentColor.copy(alpha = 0.65f))
                            )
                        )
                )
            }

            Text(
                text = "$percent%",
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary
            )
        }
    }
}

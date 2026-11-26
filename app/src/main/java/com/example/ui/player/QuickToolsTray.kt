package com.example.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.formatDuration
import com.example.player.EqualizerUiState
import com.example.player.LoopMode
import com.example.player.SleepTimerOption
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.CardOutline
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.DeepNavySurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedNavy
import com.example.ui.theme.OnCyanContainer
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletAccent
import com.example.ui.theme.VioletContainer

@Composable
fun QuickToolsTray(
    isAudio: Boolean,
    audioBoostPercent: Int,
    eqState: EqualizerUiState,
    sleepTimerOption: SleepTimerOption,
    sleepRemainingSeconds: Long,
    loopMode: LoopMode,
    abStartMs: Long?,
    abEndMs: Long?,
    onSelectAudioBoost: (Int) -> Unit,
    onOpenEqualizer: () -> Unit,
    onCaptureScreenshot: () -> Unit,
    onSelectSleepTimer: (SleepTimerOption) -> Unit,
    onCycleLoopMode: () -> Unit,
    onToggleAbRepeat: () -> Unit,
    onClearAbRepeat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = DeepNavySurface.copy(alpha = 0.94f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, CardOutline)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Primary Quick Action Chips (Loop, A-B Repeat, Sleep Timer Summary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Loop Mode Pill
                QuickToolActionChip(
                    icon = if (loopMode == LoopMode.REPEAT_ONE) {
                        Icons.Rounded.RepeatOne
                    } else {
                        Icons.Rounded.Repeat
                    },
                    label = "Loop: ${loopMode.label}",
                    active = loopMode != LoopMode.OFF,
                    onClick = onCycleLoopMode,
                    modifier = Modifier.testTag("quick_tool_loop")
                )

                // A-B Repeat Pill
                val abLabel = when {
                    abStartMs == null -> "A-B: Set A"
                    abEndMs == null -> "A-B: ${formatDuration(abStartMs)} → Set B"
                    else -> "A-B: ${formatDuration(abStartMs)}–${formatDuration(abEndMs)} (Clear)"
                }
                QuickToolActionChip(
                    icon = Icons.Rounded.SettingsEthernet,
                    label = abLabel,
                    active = abStartMs != null,
                    onClick = {
                        if (abStartMs != null && abEndMs != null) {
                            onClearAbRepeat()
                        } else {
                            onToggleAbRepeat()
                        }
                    },
                    modifier = Modifier.testTag("quick_tool_ab_repeat")
                )

                // 5-Band Equalizer Pill
                QuickToolActionChip(
                    icon = Icons.Rounded.Equalizer,
                    label = if (eqState.enabled) "EQ: ${eqState.preset.label}" else "Equalizer",
                    active = eqState.enabled,
                    onClick = onOpenEqualizer,
                    modifier = Modifier.testTag("quick_tool_equalizer")
                )

                // One-Tap Video Screenshot Pill (video only)
                if (!isAudio) {
                    QuickToolActionChip(
                        icon = Icons.Rounded.CameraAlt,
                        label = "Screenshot",
                        active = false,
                        onClick = onCaptureScreenshot,
                        modifier = Modifier.testTag("quick_tool_screenshot")
                    )
                }

                // Audio Boost Summary Pill
                QuickToolActionChip(
                    icon = Icons.Rounded.GraphicEq,
                    label = "Boost: $audioBoostPercent%",
                    active = audioBoostPercent > 100,
                    onClick = {
                        val next = when {
                            audioBoostPercent < 125 -> 125
                            audioBoostPercent < 150 -> 150
                            audioBoostPercent < 175 -> 175
                            audioBoostPercent < 200 -> 200
                            else -> 100
                        }
                        onSelectAudioBoost(next)
                    },
                    modifier = Modifier.testTag("quick_tool_boost_cycle")
                )
            }

            // Audio Boost Step Chips (100% - 200%)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Boost",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.width(4.dp))
                listOf(100, 125, 150, 175, 200).forEach { pct ->
                    val selected = audioBoostPercent == pct
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectAudioBoost(pct) }
                            .testTag("boost_level_$pct"),
                        color = if (selected) ElectricCyan else ElevatedNavy,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            1.dp,
                            if (selected) ElectricCyan else CardOutline
                        )
                    ) {
                        Text(
                            text = "$pct%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) AmoledBlack else TextPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Sleep Timer Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bedtime,
                    contentDescription = null,
                    tint = if (sleepTimerOption != SleepTimerOption.OFF) AmberWarm else VioletAccent,
                    modifier = Modifier.size(16.dp)
                )
                val timerPrefix = when {
                    sleepTimerOption == SleepTimerOption.OFF -> "Sleep"
                    sleepTimerOption == SleepTimerOption.END_OF_VIDEO -> "Sleep (End)"
                    else -> {
                        val mins = sleepRemainingSeconds / 60L
                        val secs = sleepRemainingSeconds % 60L
                        String.format(java.util.Locale.US, "Sleep (%02d:%02d)", mins, secs)
                    }
                }
                Text(
                    text = timerPrefix,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (sleepTimerOption != SleepTimerOption.OFF) AmberWarm else TextSecondary
                )
                Spacer(modifier = Modifier.width(4.dp))
                SleepTimerOption.entries.forEach { option ->
                    val selected = sleepTimerOption == option
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectSleepTimer(option) }
                            .testTag("sleep_timer_${option.name}"),
                        color = if (selected) VioletContainer else ElevatedNavy,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            1.dp,
                            if (selected) VioletAccent else CardOutline
                        )
                    ) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) ElectricCyan else TextPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickToolActionChip(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        color = if (active) CyanContainer else ElevatedNavy,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (active) ElectricCyan else CardOutline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) ElectricCyan else TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) OnCyanContainer else TextPrimary,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.data.AppThemeConfig

/**
 * Compose colors derived from the small, remote-safe theme.json schema. The legacy names below
 * are kept as aliases so all existing player surfaces follow the active palette together.
 */
data class AltPlayerColors(
    val background: Color,
    val surface: Color,
    val elevatedSurface: Color,
    val surfaceHighlight: Color,
    val border: Color,
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val primaryContainer: Color,
    val secondaryContainer: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color
) {
    companion object {
        val DEFAULT = from(AppThemeConfig.DEFAULT)

        fun from(config: AppThemeConfig): AltPlayerColors {
            val background = parseHex(config.background)
            val surface = parseHex(config.surface)
            val border = parseHex(config.border)
            val primary = parseHex(config.primaryAccent)
            val secondary = parseHex(config.secondaryAccent)
            val textPrimary = parseHex(config.textPrimary)
            val textSecondary = parseHex(config.textSecondary)
            return AltPlayerColors(
                background = background,
                surface = surface,
                elevatedSurface = mix(surface, textPrimary, 0.055f),
                surfaceHighlight = mix(surface, textPrimary, 0.105f),
                border = border,
                primaryAccent = primary,
                secondaryAccent = secondary,
                primaryContainer = mix(surface, primary, 0.25f),
                secondaryContainer = mix(surface, secondary, 0.18f),
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                textMuted = mix(textSecondary, background, 0.38f)
            )
        }

        private fun parseHex(value: String): Color {
            val normalized = value.removePrefix("#")
            val raw = normalized.toLong(16)
            val redShift = if (normalized.length == 8) 24 else 16
            val greenShift = if (normalized.length == 8) 16 else 8
            val blueShift = if (normalized.length == 8) 8 else 0
            val red = ((raw shr redShift) and 0xFF) / 255f
            val green = ((raw shr greenShift) and 0xFF) / 255f
            val blue = ((raw shr blueShift) and 0xFF) / 255f
            val alpha = if (normalized.length == 8) (raw and 0xFF) / 255f else 1f
            return Color(red = red, green = green, blue = blue, alpha = alpha)
        }

        private fun mix(from: Color, to: Color, amount: Float): Color {
            val fraction = amount.coerceIn(0f, 1f)
            return Color(
                red = from.red + ((to.red - from.red) * fraction),
                green = from.green + ((to.green - from.green) * fraction),
                blue = from.blue + ((to.blue - from.blue) * fraction),
                alpha = from.alpha + ((to.alpha - from.alpha) * fraction)
            )
        }
    }
}

object ThemeColors {
    var current: AltPlayerColors = AltPlayerColors.DEFAULT
        private set

    fun apply(config: AppThemeConfig) {
        current = AltPlayerColors.from(config)
    }
}

// Base surfaces and borders.
val ObsidianBlack: Color get() = ThemeColors.current.background
val SoftCharcoal: Color get() = ThemeColors.current.surface
val ElevatedCharcoal: Color get() = ThemeColors.current.elevatedSurface
val SurfaceHighlight: Color get() = ThemeColors.current.surfaceHighlight
val SubtleBorder: Color get() = ThemeColors.current.border

// Accent and semantic colors.
val TealEmeraldDeep: Color get() = ThemeColors.current.primaryAccent
val LuminousMint: Color get() = ThemeColors.current.primaryAccent
val EmeraldPrimary: Color get() = ThemeColors.current.primaryAccent
val EmeraldMid: Color get() = ThemeColors.current.secondaryAccent
val ForestDeep: Color get() = ThemeColors.current.primaryContainer
val ForestContainer: Color get() = ThemeColors.current.secondaryContainer
val OnForestContainer: Color get() = ThemeColors.current.textPrimary
val SoftEmeraldStatus: Color get() = ThemeColors.current.secondaryAccent

val AmberWarm = Color(0xFFF59E0B)
val CoralError = Color(0xFFEF4444)

// Typography neutrals.
val TextPrimary: Color get() = ThemeColors.current.textPrimary
val TextSecondary: Color get() = ThemeColors.current.textSecondary
val TextMuted: Color get() = ThemeColors.current.textMuted

// Semantic aliases used throughout the existing UI.
val AmoledBlack: Color get() = ThemeColors.current.background
val DeepNavySurface: Color get() = ThemeColors.current.surface
val ElevatedNavy: Color get() = ThemeColors.current.elevatedSurface
val CardOutline: Color get() = ThemeColors.current.border
val ElectricCyan: Color get() = ThemeColors.current.primaryAccent
val CyanMuted: Color get() = ThemeColors.current.secondaryAccent
val CyanContainer: Color get() = ThemeColors.current.primaryContainer
val OnCyanContainer: Color get() = ThemeColors.current.textPrimary
val VioletAccent: Color get() = ThemeColors.current.primaryAccent
val VioletContainer: Color get() = ThemeColors.current.primaryContainer
val OnVioletContainer: Color get() = ThemeColors.current.textPrimary
val EmeraldOffline: Color get() = ThemeColors.current.secondaryAccent

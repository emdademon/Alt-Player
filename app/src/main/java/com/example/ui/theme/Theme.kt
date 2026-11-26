package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AltPlayerDarkColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = ObsidianBlack,
    primaryContainer = ForestDeep,
    onPrimaryContainer = OnForestContainer,
    secondary = EmeraldMid,
    onSecondary = TextPrimary,
    secondaryContainer = ForestContainer,
    onSecondaryContainer = OnForestContainer,
    tertiary = AmberWarm,
    onTertiary = ObsidianBlack,
    background = ObsidianBlack,
    onBackground = TextPrimary,
    surface = SoftCharcoal,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedCharcoal,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBlack,
    surfaceContainerLow = SoftCharcoal,
    surfaceContainer = ElevatedCharcoal,
    surfaceContainerHigh = SurfaceHighlight,
    surfaceContainerHighest = ColorTokens.SurfaceHighest,
    outline = SubtleBorder,
    outlineVariant = ColorTokens.SubtleDivider,
    error = CoralError,
    onError = TextPrimary
)

private object ColorTokens {
    val SurfaceHighest = Color(0xFF22342F)
    val SubtleDivider = Color(0xFF16211E)
}

val VeloShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun VeloPlayTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AltPlayerDarkColorScheme,
        typography = VeloTypography,
        shapes = VeloShapes,
        content = content
    )
}

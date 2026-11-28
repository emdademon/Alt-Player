package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.data.AppThemeConfig

val VeloShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun VeloPlayTheme(
    theme: AppThemeConfig = AppThemeConfig.DEFAULT,
    content: @Composable () -> Unit
) {
    // The outer state holder recomposes this function whenever theme.json changes. Updating the
    // alias palette before MaterialTheme evaluates its content keeps legacy custom surfaces in
    // sync with the Material 3 color scheme as well.
    ThemeColors.apply(theme)
    val colors = ThemeColors.current
    val colorScheme = darkColorScheme(
        primary = colors.primaryAccent,
        onPrimary = colors.textPrimary,
        primaryContainer = colors.primaryContainer,
        onPrimaryContainer = colors.textPrimary,
        secondary = colors.secondaryAccent,
        onSecondary = colors.background,
        secondaryContainer = colors.secondaryContainer,
        onSecondaryContainer = colors.textPrimary,
        tertiary = AmberWarm,
        onTertiary = colors.background,
        background = colors.background,
        onBackground = colors.textPrimary,
        surface = colors.surface,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.elevatedSurface,
        onSurfaceVariant = colors.textSecondary,
        surfaceContainerLowest = colors.background,
        surfaceContainerLow = colors.surface,
        surfaceContainer = colors.elevatedSurface,
        surfaceContainerHigh = colors.surfaceHighlight,
        surfaceContainerHighest = colors.surfaceHighlight,
        outline = colors.border,
        outlineVariant = colors.border.copy(alpha = 0.7f),
        error = CoralError,
        onError = colors.textPrimary
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VeloTypography,
        shapes = VeloShapes,
        content = content
    )
}

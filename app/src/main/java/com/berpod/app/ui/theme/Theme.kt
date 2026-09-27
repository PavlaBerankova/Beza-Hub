package com.berpod.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private fun DotColors.toMaterial(dark: Boolean) = (if (dark) darkColorScheme() else lightColorScheme()).copy(
    primary = accent,
    onPrimary = onAccent,
    primaryContainer = accent,
    onPrimaryContainer = onAccent,
    secondaryContainer = accent.copy(alpha = 0.16f),
    onSecondaryContainer = text,
    background = background,
    onBackground = text,
    surface = background,
    onSurface = text,
    surfaceVariant = divider,
    onSurfaceVariant = textMuted,
    surfaceContainer = surface,
    surfaceContainerLow = surface,
    surfaceContainerHigh = surface,
    surfaceContainerHighest = surface,
    inverseSurface = text,
    inverseOnSurface = background,
    outline = outline,
    outlineVariant = divider
)

/** Dot Matrix theme, light or dark following the system setting. */
@Composable
fun BerpodTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) DarkDotColors else LightDotColors
    CompositionLocalProvider(LocalDotColors provides colors) {
        MaterialTheme(colorScheme = colors.toMaterial(dark), typography = Typography, content = content)
    }
}

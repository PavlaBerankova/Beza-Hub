package com.berpod.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.SideEffect
import android.app.Activity
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

/** Whether the app is drawn dark: the user's choice, or the phone's setting until they have made one. */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** Flips light and dark; [isDark] is what is showing now. */
class ThemeController(val isDark: Boolean, val toggle: () -> Unit)

val LocalThemeController = staticCompositionLocalOf { ThemeController(false) {} }

/**
 * Dot Matrix theme, light or dark. [darkOverride] is the user's choice (null = follow the system setting); the
 * system bar icons follow it, and [onToggle] flips it.
 */
@Composable
fun BerpodTheme(darkOverride: Boolean? = null, onToggle: () -> Unit = {}, content: @Composable () -> Unit) {
    val dark = darkOverride ?: isSystemInDarkTheme()
    val view = LocalView.current
    SideEffect {
        val controller = (view.context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = !dark
        controller?.isAppearanceLightNavigationBars = !dark
    }
    CompositionLocalProvider(LocalDarkTheme provides dark, LocalThemeController provides ThemeController(dark, onToggle)) {
        TabAccentTheme(TabAccent.Podcasts, content)
    }
}

/** The theme in [tab]'s accent colour; wraps each tab (and its detail screens). */
@Composable
fun TabAccentTheme(tab: TabAccent, content: @Composable () -> Unit) {
    val dark = LocalDarkTheme.current
    val colors = (if (dark) DarkDotColors else LightDotColors).withAccent(tab, dark)
    CompositionLocalProvider(LocalDotColors provides colors) {
        MaterialTheme(colorScheme = colors.toMaterial(dark), typography = Typography, content = content)
    }
}

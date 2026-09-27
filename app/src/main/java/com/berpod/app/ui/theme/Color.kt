package com.berpod.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The Dot Matrix palette (spec table). */
data class DotColors(
    val background: Color,
    val surface: Color,
    val text: Color,
    val textMuted: Color,
    val textStrong: Color,
    val outline: Color,
    val divider: Color,
    val accent: Color,
    val onAccent: Color,
    val waveIdle: Color,
    val waveMuted: Color,
    val ringIdle: Color
)

val LightDotColors = DotColors(
    background = Color(0xFFF5F4F8),
    surface = Color(0xFFFFFFFF),
    text = Color(0xFF16141C),
    textMuted = Color(0xFF6B6875),
    textStrong = Color(0xFF3F3C48),
    outline = Color(0xFFC9C5D3),
    divider = Color(0xFFDCD9E3),
    accent = Color(0xFF5E2E91),
    onAccent = Color(0xFFFFFFFF),
    waveIdle = Color(0xFFECE9F1),
    waveMuted = Color(0xFFBDB8CA),
    ringIdle = Color(0xFFE0DCE7)
)

val DarkDotColors = DotColors(
    background = Color(0xFF0A0B0D),
    surface = Color(0xFF141519),
    text = Color(0xFFF2F2F4),
    textMuted = Color(0xFF9A9BA3),
    textStrong = Color(0xFFC4C5CC),
    outline = Color(0xFF3A3B42),
    divider = Color(0xFF2A2B31),
    accent = Color(0xFFA67CFF),
    onAccent = Color(0xFF0A0B0E),
    waveIdle = Color(0xFF17181C),
    waveMuted = Color(0xFF4A4B53),
    ringIdle = Color(0xFF26272D)
)

val LocalDotColors = staticCompositionLocalOf { LightDotColors }

object DotTheme {
    val colors: DotColors
        @Composable @ReadOnlyComposable get() = LocalDotColors.current
}

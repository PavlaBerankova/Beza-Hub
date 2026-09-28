package com.berpod.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Each bottom tab carries its own accent colour. The pairs below keep at least 4.5:1 against their
 * background and under their own on-accent text, in both themes (see TabAccentTest).
 */
enum class TabAccent(
    val lightAccent: Color,
    val darkAccent: Color
) {
    Podcasts(Color(0xFF5E2E91), Color(0xFFA67CFF)),
    YouTube(Color(0xFFB3261E), Color(0xFFFF6B6B)),
    Articles(Color(0xFF146B3A), Color(0xFF4ADE80))
}

/** The same palette with this tab's accent; on-accent text stays white on light, near-black on dark. */
fun DotColors.withAccent(tab: TabAccent, dark: Boolean): DotColors =
    copy(accent = if (dark) tab.darkAccent else tab.lightAccent)

/** Which tab a route belongs to; its detail screens keep the tab's colour. */
fun tabAccentFor(route: String?): TabAccent = when {
    route == null -> TabAccent.Podcasts
    route.startsWith("youtube") -> TabAccent.YouTube
    route.startsWith("article") -> TabAccent.Articles
    else -> TabAccent.Podcasts
}

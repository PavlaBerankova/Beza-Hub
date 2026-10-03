package com.berpod.app.ui.podcastlist

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.berpod.app.ui.theme.DotColors

/**
 * The colour of the header blocks of the podcast and episode pages: black, a little see-through so it reads as a
 * dark grey over the light background, whatever the cover looks like.
 */
val HeaderBlockColor = Color.Black.copy(alpha = 0.86f)

/** Rec. 601 luma, 0..1: how bright a colour looks. */
fun Color.luma(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

/** Whether text on [tint] should be dark; brighter than this and white text would be hard to read. */
fun isLightTint(tint: Color): Boolean = tint.luma() > 0.62f

/** The Dot colours for content drawn on [block]: text and outlines flip to white or near-black, buttons invert. */
fun colorsOnBlock(base: DotColors, block: Color): DotColors {
    val fg = if (isLightTint(block)) Color(0xFF16141C) else Color.White
    return base.copy(
        background = block,
        surface = lerp(block, fg, 0.10f),
        waveIdle = fg.copy(alpha = 0.10f),
        waveMuted = fg.copy(alpha = 0.28f),
        ringIdle = fg.copy(alpha = 0.22f),
        text = fg,
        textStrong = fg,
        textMuted = fg.copy(alpha = 0.72f),
        outline = fg.copy(alpha = 0.45f),
        divider = fg.copy(alpha = 0.28f),
        accent = fg,
        onAccent = block
    )
}

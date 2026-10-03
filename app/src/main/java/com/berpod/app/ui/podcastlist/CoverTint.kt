package com.berpod.app.ui.podcastlist

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.berpod.app.ui.theme.DotColors
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The header colour from the cover's palette swatches (ARGB): the vivid one Apple-style headers use, falling
 * back to darker, muted and finally the dominant colour; null if the palette found none.
 */
fun pickHeaderColor(vibrant: Int?, darkVibrant: Int?, muted: Int?, dominant: Int?): Int? =
    vibrant ?: darkVibrant ?: muted ?: dominant

/** The cover's signature colour, or null until it has loaded (or when there is no cover). */
@Composable
fun rememberCoverTint(imageUrl: String?): Color? {
    val context = LocalContext.current
    var tint by remember(imageUrl) { mutableStateOf<Color?>(null) }
    LaunchedEffect(imageUrl) {
        if (imageUrl == null) return@LaunchedEffect
        val request = ImageRequest.Builder(context).data(imageUrl).allowHardware(false).build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return@LaunchedEffect
        tint = withContext(Dispatchers.Default) { coverColor(result.image.toBitmap()) }
    }
    return tint
}

private fun coverColor(bitmap: Bitmap): Color? {
    val palette = Palette.from(bitmap).generate()
    return pickHeaderColor(
        vibrant = palette.vibrantSwatch?.rgb,
        darkVibrant = palette.darkVibrantSwatch?.rgb,
        muted = palette.mutedSwatch?.rgb,
        dominant = palette.dominantSwatch?.rgb
    )?.let { Color(it) }
}

/** Rec. 601 luma, 0..1: how bright a colour looks. */
fun Color.luma(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

/** Whether text on [tint] should be dark; brighter than this and white text would be hard to read. */
fun isLightTint(tint: Color): Boolean = tint.luma() > 0.62f

/** A solid, saturated header block from the cover's colour: bright ones are kept, mid ones deepened a little. */
fun headerBlockColor(cover: Color): Color =
    if (isLightTint(cover)) cover else lerp(cover, Color.Black, 0.12f)

/** The Dot colours for content drawn on [block]: text and outlines flip to white or near-black, buttons invert. */
fun colorsOnBlock(base: DotColors, block: Color): DotColors {
    val fg = if (isLightTint(block)) Color(0xFF16141C) else Color.White
    return base.copy(
        text = fg,
        textStrong = fg,
        textMuted = fg.copy(alpha = 0.72f),
        outline = fg.copy(alpha = 0.45f),
        divider = fg.copy(alpha = 0.28f),
        accent = fg,
        onAccent = block
    )
}

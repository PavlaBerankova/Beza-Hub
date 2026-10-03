package com.berpod.app.ui.podcastlist

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
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

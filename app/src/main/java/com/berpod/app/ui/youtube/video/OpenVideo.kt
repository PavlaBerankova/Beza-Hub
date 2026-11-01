package com.berpod.app.ui.youtube.video

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.berpod.app.data.remote.webUrlOrNull

private const val YOUTUBE_PACKAGE = "com.google.android.youtube"

/** Opens the video in the YouTube app; without it, in whatever the phone uses for links. */
fun openInYoutube(context: Context, url: String) {
    val uri = webUrlOrNull(url)?.let(Uri::parse) ?: return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(YOUTUBE_PACKAGE))
    } catch (_: ActivityNotFoundException) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }
}

/** Opens the video in a browser, offering the browsers installed (the YouTube app is not among them). */
fun openInBrowser(context: Context, url: String, chooserTitle: String) {
    val uri = webUrlOrNull(url)?.let(Uri::parse) ?: return
    val browsers = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER).setData(uri)
    try {
        context.startActivity(Intent.createChooser(browsers, chooserTitle))
    } catch (_: ActivityNotFoundException) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }
}

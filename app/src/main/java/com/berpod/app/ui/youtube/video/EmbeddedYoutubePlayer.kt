package com.berpod.app.ui.youtube.video

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.YouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/**
 * Hidden-chrome embedded YouTube player (the official IFrame Player API inside a WebView). The
 * caller draws its own Dot Matrix controls on top, driven by [listener]'s callbacks and issuing
 * commands through the [com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer]
 * that [listener] receives in onReady.
 */
@Composable
fun EmbeddedYoutubePlayer(listener: YouTubePlayerListener, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f),
        factory = { context ->
            YouTubePlayerView(context).apply {
                enableAutomaticInitialization = false
                initialize(listener, IFramePlayerOptions.Builder(context).controls(0).build())
            }
        },
        onRelease = { view -> view.release() }
    )
}

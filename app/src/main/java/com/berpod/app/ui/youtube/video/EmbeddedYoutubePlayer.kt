package com.berpod.app.ui.youtube.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.YouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/**
 * Embedded YouTube player (the official IFrame Player API inside a WebView), sized by the caller. Normally its
 * own controls are hidden and the caller draws Dot Matrix controls over it, driven by [listener]'s callbacks and
 * issuing commands through the [com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer] that
 * [listener] receives in onReady; in fullscreen ([nativeControls]) YouTube's own controls are used, so its ads can
 * be skipped and nothing covers the player.
 */
@Composable
fun EmbeddedYoutubePlayer(listener: YouTubePlayerListener, nativeControls: Boolean, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            YouTubePlayerView(context).apply {
                enableAutomaticInitialization = false
                // With YouTube's own controls (fullscreen) its fullscreen button is off: it would only try to go
                // fullscreen inside the web view; leaving fullscreen is the back button's job.
                val options = IFramePlayerOptions.Builder(context)
                    .controls(if (nativeControls) 1 else 0)
                    .fullscreen(0)
                    .build()
                initialize(listener, options)
            }
        },
        onRelease = { view -> view.release() }
    )
}

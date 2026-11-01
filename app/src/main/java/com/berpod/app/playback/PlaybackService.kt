package com.berpod.app.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.berpod.app.MainActivity

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build()

        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(OwnAndSystemControllersOnly(packageName))
            .setSessionActivity(sessionActivityIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

/**
 * The service must be exported for the system (notification, lock screen, Bluetooth), but any other app
 * could otherwise bind to it and play, pause or skip. Only the app itself and system-trusted controllers get in.
 */
private class OwnAndSystemControllersOnly(private val ownPackage: String) : MediaSession.Callback {
    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
        if (controller.packageName == ownPackage || controller.isTrusted) {
            MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
        } else {
            MediaSession.ConnectionResult.reject()
        }
}

package com.berpod.app.data.repository

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.berpod.app.data.local.EpisodeDao
import com.berpod.app.data.local.EpisodeEntity
import com.berpod.app.playback.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackUiState(
    val currentEpisodeId: Long? = null,
    val title: String = "",
    val podcastTitle: String = "",
    val artworkUrl: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

internal fun shouldPersistPosition(lastPersistedAtMs: Long, nowMs: Long, intervalMs: Long = 5_000L): Boolean =
    nowMs - lastPersistedAtMs >= intervalMs

class PlaybackRepository(context: Context, private val episodeDao: EpisodeDao) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private var controller: MediaController? = null
    private var pendingAction: (() -> Unit)? = null
    private var positionPollJob: Job? = null
    private var lastPersistedAtMs = 0L

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            restartPositionPolling(isPlaying)
            if (!isPlaying) {
                val episodeId = _uiState.value.currentEpisodeId
                val position = controller?.currentPosition ?: _uiState.value.positionMs
                if (episodeId != null) persistPosition(episodeId, position)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val previousEpisodeId = _uiState.value.currentEpisodeId
            val previousPosition = _uiState.value.positionMs
            if (previousEpisodeId != null) {
                persistPosition(previousEpisodeId, previousPosition)
            }
            val metadata = mediaItem?.mediaMetadata
            _uiState.update {
                it.copy(
                    currentEpisodeId = mediaItem?.mediaId?.toLongOrNull(),
                    title = metadata?.title?.toString().orEmpty(),
                    podcastTitle = metadata?.artist?.toString().orEmpty(),
                    artworkUrl = metadata?.artworkUri?.toString(),
                    positionMs = 0L
                )
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                val duration = controller?.duration ?: C.TIME_UNSET
                _uiState.update { it.copy(durationMs = if (duration == C.TIME_UNSET) 0L else duration) }
            }
        }
    }

    init {
        val sessionToken = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(appContext, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                val connectedController = controllerFuture.get()
                controller = connectedController
                connectedController.addListener(playerListener)
                _uiState.update { it.copy(isPlaying = connectedController.isPlaying) }
                restartPositionPolling(connectedController.isPlaying)
                pendingAction?.invoke()
                pendingAction = null
            },
            ContextCompat.getMainExecutor(appContext)
        )
    }

    fun play(episode: EpisodeEntity, podcastTitle: String, podcastImageUrl: String?) {
        val action: () -> Unit = {
            controller?.let { c ->
                val mediaItem = MediaItem.Builder()
                    .setMediaId(episode.id.toString())
                    .setUri(episode.audioUrl)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(episode.title)
                            .setArtist(podcastTitle)
                            .setArtworkUri((episode.imageUrl ?: podcastImageUrl)?.let(Uri::parse))
                            .build()
                    )
                    .build()
                // Start position goes with the item: a seekTo() issued right after setMediaItem() over the
                // controller was dropped, so resumed episodes restarted from 0.
                c.setMediaItem(mediaItem, episode.lastPositionMs.coerceAtLeast(0L))
                c.prepare()
                c.play()
            }
        }
        if (controller != null) action() else pendingAction = action
    }

    fun togglePlayPause() {
        controller?.let { c -> if (c.isPlaying) c.pause() else c.play() }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _uiState.update { it.copy(positionMs = positionMs) }
    }

    private fun restartPositionPolling(isPlaying: Boolean) {
        positionPollJob?.cancel()
        if (!isPlaying) return
        positionPollJob = scope.launch {
            while (isActive) {
                controller?.let { c ->
                    _uiState.update { it.copy(positionMs = c.currentPosition) }
                    val episodeId = _uiState.value.currentEpisodeId
                    if (episodeId != null && shouldPersistPosition(lastPersistedAtMs, System.currentTimeMillis())) {
                        persistPosition(episodeId, c.currentPosition)
                    }
                }
                delay(500)
            }
        }
    }

    private fun persistPosition(episodeId: Long, positionMs: Long) {
        lastPersistedAtMs = System.currentTimeMillis()
        scope.launch { runCatching { episodeDao.updatePosition(episodeId, positionMs) } }
    }
}

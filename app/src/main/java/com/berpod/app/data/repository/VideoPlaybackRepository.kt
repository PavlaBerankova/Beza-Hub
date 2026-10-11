package com.berpod.app.data.repository

import com.berpod.app.data.local.YoutubeVideoEntity
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VideoPlaybackUiState(
    val currentVideoId: Long? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

/**
 * Scoped to VideoDetailViewModel's viewModelScope, not an AppContainer singleton: leaving the Video
 * screen releases the embedded player, video isn't meant to keep playing in the background the way
 * podcast audio is. Position/duration persist straight to [YoutubeVideoEntity] as they change.
 */
class VideoPlaybackRepository(
    private val scope: CoroutineScope,
    private val youtubeRepository: YoutubeRepository,
    private val videoQueueRepository: VideoQueueRepository
) {

    private val _uiState = MutableStateFlow(VideoPlaybackUiState())
    val uiState: StateFlow<VideoPlaybackUiState> = _uiState.asStateFlow()

    private var player: YouTubePlayer? = null
    private var pendingVideo: YoutubeVideoEntity? = null
    private var currentVideo: YoutubeVideoEntity? = null
    private var lastPersistedAtMs = 0L
    // The video that just ended: its stored position stays 0 until it's played again.
    private var endedVideoId: Long? = null

    /** Passed to EmbeddedYoutubePlayer's initialize(); chrome is hidden, so only these callbacks matter. */
    val listener = object : AbstractYouTubePlayerListener() {
        override fun onReady(youTubePlayer: YouTubePlayer) {
            player = youTubePlayer
            pendingVideo?.let { loadInternal(it) }
        }

        override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
            when (state) {
                PlayerConstants.PlayerState.PLAYING -> _uiState.update { it.copy(isPlaying = true) }
                PlayerConstants.PlayerState.PAUSED -> {
                    _uiState.update { it.copy(isPlaying = false) }
                    persistCurrentPosition()
                }
                PlayerConstants.PlayerState.ENDED -> onVideoEnded()
                else -> Unit
            }
        }

        override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
            val positionMs = (second * 1000).toLong()
            _uiState.update { it.copy(positionMs = positionMs) }
            val videoId = _uiState.value.currentVideoId
            if (videoId != null && shouldPersistPosition(lastPersistedAtMs, System.currentTimeMillis())) {
                persistPosition(videoId, positionMs)
            }
        }

        override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
            val durationMs = (duration * 1000).toLong()
            _uiState.update { it.copy(durationMs = durationMs) }
            val videoId = _uiState.value.currentVideoId
            if (videoId != null && durationMs > 0) {
                scope.launch { runCatching { youtubeRepository.updateDuration(videoId, durationMs) } }
            }
        }
    }

    fun play(video: YoutubeVideoEntity) {
        if (video.id == endedVideoId) endedVideoId = null
        currentVideo = video
        _uiState.update {
            it.copy(currentVideoId = video.id, isPlaying = false, positionMs = video.lastPositionMs, durationMs = video.durationMs ?: 0L)
        }
        if (player != null) loadInternal(video) else pendingVideo = video
    }

    /**
     * The embedded player is about to be thrown away and created again (its controls can only be chosen when it is
     * created); the new one picks the video up where this one is.
     */
    fun prepareForPlayerRecreation() {
        val video = currentVideo ?: return
        pendingVideo = video.copy(lastPositionMs = _uiState.value.positionMs)
        player = null
    }

    private fun loadInternal(video: YoutubeVideoEntity) {
        val durationSeconds = video.durationMs?.let { (it / 1000).toInt() }
        val startSeconds = resumeStartMs(video.lastPositionMs, durationSeconds) / 1000f
        player?.loadVideo(video.videoId, startSeconds)
        pendingVideo = null
    }

    fun togglePlayPause() {
        val p = player ?: return
        if (_uiState.value.isPlaying) p.pause() else p.play()
    }

    fun seekTo(positionMs: Long) {
        // Seeking back into the ended video should save its position again.
        if (_uiState.value.currentVideoId == endedVideoId) endedVideoId = null
        player?.seekTo(positionMs / 1000f)
        _uiState.update { it.copy(positionMs = positionMs) }
    }

    private fun onVideoEnded() {
        val videoId = _uiState.value.currentVideoId ?: return
        endedVideoId = videoId
        _uiState.update { it.copy(isPlaying = false) }
        scope.launch {
            runCatching { youtubeRepository.setWatched(videoId, true) }
            runCatching { videoQueueRepository.removeVideo(videoId) }
        }
    }

    private fun persistCurrentPosition() {
        val videoId = _uiState.value.currentVideoId ?: return
        persistPosition(videoId, _uiState.value.positionMs)
    }

    private fun persistPosition(videoId: Long, positionMs: Long) {
        if (videoId == endedVideoId) return
        lastPersistedAtMs = System.currentTimeMillis()
        scope.launch { runCatching { youtubeRepository.updatePosition(videoId, positionMs) } }
    }
}

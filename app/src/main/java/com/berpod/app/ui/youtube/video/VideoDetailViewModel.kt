package com.berpod.app.ui.youtube.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berpod.app.data.local.QueueVideoItem
import com.berpod.app.data.local.VideoWithChannel
import com.berpod.app.data.repository.VideoPlaybackRepository
import com.berpod.app.data.repository.VideoQueueRepository
import com.berpod.app.data.repository.YoutubeRepository
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.YouTubePlayerListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VideoDetailUiState(
    val isLoaded: Boolean = false,
    /** null once loaded = the video no longer exists. */
    val item: VideoWithChannel? = null,
    val isQueued: Boolean = false,
    val isWatched: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    /** The queue entries after this video (or the whole queue, when this video isn't queued). */
    val nextInQueue: List<QueueVideoItem> = emptyList(),
    val snackbarMessage: String? = null
)

class VideoDetailViewModel(
    private val youtubeRepository: YoutubeRepository,
    private val videoQueueRepository: VideoQueueRepository,
    private val videoId: Long
) : ViewModel() {

    private val playbackRepository = VideoPlaybackRepository(viewModelScope, youtubeRepository, videoQueueRepository)
    val playerListener: YouTubePlayerListener get() = playbackRepository.listener

    private val _uiState = MutableStateFlow(VideoDetailUiState())
    val uiState: StateFlow<VideoDetailUiState> = _uiState.asStateFlow()

    // The video starts playing once, as soon as it's first loaded; later DB-triggered reloads (from
    // this same playback persisting position/duration) must not restart it.
    private var autoPlayed = false

    init {
        youtubeRepository.observeVideo(videoId)
            .onEach { item ->
                _uiState.update { it.copy(isLoaded = true, item = item, isWatched = item?.video?.watchedAtMs != null).withPlayback() }
                if (!autoPlayed && item != null) {
                    autoPlayed = true
                    playbackRepository.play(item.video)
                }
            }
            .launchIn(viewModelScope)
        videoQueueRepository.observeQueuedVideoIds()
            .onEach { ids -> _uiState.update { it.copy(isQueued = videoId in ids) } }
            .launchIn(viewModelScope)
        videoQueueRepository.observeQueue()
            .onEach { queue ->
                val index = queue.indexOfFirst { it.video.id == videoId }
                val next = if (index >= 0) queue.drop(index + 1) else queue
                _uiState.update { it.copy(nextInQueue = next) }
            }
            .launchIn(viewModelScope)
        playbackRepository.uiState
            .onEach { _uiState.update { it.withPlayback() } }
            .launchIn(viewModelScope)
    }

    private fun VideoDetailUiState.withPlayback(): VideoDetailUiState {
        val playback = playbackRepository.uiState.value
        val video = item?.video
        return if (playback.currentVideoId == videoId) {
            copy(isPlaying = playback.isPlaying, positionMs = playback.positionMs, durationMs = playback.durationMs.takeIf { it > 0 } ?: video?.durationMs ?: 0L)
        } else {
            copy(isPlaying = false, positionMs = video?.lastPositionMs ?: 0L, durationMs = video?.durationMs ?: 0L)
        }
    }

    fun onTogglePlayPause() = playbackRepository.togglePlayPause()

    fun onSeek(positionMs: Long) = playbackRepository.seekTo(positionMs)

    fun onToggleQueue() {
        viewModelScope.launch {
            val message = if (_uiState.value.isQueued) {
                videoQueueRepository.removeVideo(videoId)
                "Odebráno z fronty"
            } else {
                videoQueueRepository.addToQueue(videoId).fold({ "Přidáno do fronty" }, { it.message })
            }
            _uiState.update { it.copy(snackbarMessage = message) }
        }
    }

    fun onToggleWatched() {
        viewModelScope.launch { youtubeRepository.setWatched(videoId, !_uiState.value.isWatched) }
    }

    fun onSnackbarMessageShown() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    companion object {
        fun factory(youtubeRepository: YoutubeRepository, videoQueueRepository: VideoQueueRepository, videoId: Long) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return VideoDetailViewModel(youtubeRepository, videoQueueRepository, videoId) as T
                }
            }
    }
}

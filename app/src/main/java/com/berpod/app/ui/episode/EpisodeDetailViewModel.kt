package com.berpod.app.ui.episode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berpod.app.data.local.EpisodeDownloadEntity
import com.berpod.app.data.local.EpisodeWithFeed
import com.berpod.app.data.repository.DownloadRepository
import com.berpod.app.data.repository.DownloadToggle
import com.berpod.app.data.repository.EpisodeRepository
import com.berpod.app.data.repository.PlaybackRepository
import com.berpod.app.data.repository.QueueRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EpisodeDetailUiState(
    val isLoaded: Boolean = false,
    /** null once loaded = the episode no longer exists. */
    val item: EpisodeWithFeed? = null,
    val chapters: List<Chapter> = emptyList(),
    val isQueued: Boolean = false,
    /** The state of this episode's offline copy; null = never asked to download. */
    val download: EpisodeDownloadEntity? = null,
    /** This episode is the one loaded in the player. */
    val isCurrent: Boolean = false,
    val isPlaying: Boolean = false,
    /** The player's position/duration while [isCurrent]; otherwise the stored ones. */
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val snackbarMessage: String? = null,
    val downloadMessage: DownloadToggle? = null
)

class EpisodeDetailViewModel(
    private val episodeRepository: EpisodeRepository,
    private val queueRepository: QueueRepository,
    private val playbackRepository: PlaybackRepository,
    private val downloadRepository: DownloadRepository,
    private val episodeId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(EpisodeDetailUiState())
    val uiState: StateFlow<EpisodeDetailUiState> = _uiState.asStateFlow()

    init {
        episodeRepository.observeEpisode(episodeId)
            .onEach { item ->
                val chaptersChanged = item?.episode?.description != _uiState.value.item?.episode?.description
                _uiState.update {
                    it.copy(
                        isLoaded = true,
                        item = item,
                        chapters = if (chaptersChanged) parseChapters(item?.episode?.description) else it.chapters
                    ).withPosition()
                }
            }
            .launchIn(viewModelScope)
        queueRepository.observeQueuedEpisodeIds()
            .onEach { ids -> _uiState.update { it.copy(isQueued = episodeId in ids) } }
            .launchIn(viewModelScope)
        downloadRepository.downloads
            .onEach { all -> _uiState.update { it.copy(download = all[episodeId]) } }
            .launchIn(viewModelScope)
        playbackRepository.uiState
            .onEach { _uiState.update { it.withPosition() } }
            .launchIn(viewModelScope)
    }

    private fun EpisodeDetailUiState.withPosition(): EpisodeDetailUiState {
        val playback = playbackRepository.uiState.value
        val episode = item?.episode
        return if (playback.currentEpisodeId == episodeId) {
            copy(
                isCurrent = true,
                isPlaying = playback.isPlaying,
                positionMs = playback.positionMs,
                durationMs = playback.durationMs.takeIf { it > 0 } ?: episode?.durationSeconds?.let { it * 1000L } ?: 0L
            )
        } else {
            copy(
                isCurrent = false,
                isPlaying = false,
                positionMs = episode?.lastPositionMs ?: 0L,
                durationMs = episode?.durationSeconds?.let { it * 1000L } ?: 0L
            )
        }
    }

    fun onPrimary() {
        val state = _uiState.value
        val item = state.item ?: return
        if (state.isCurrent) playbackRepository.togglePlayPause()
        else playbackRepository.play(item.episode, item.podcastTitle, item.podcastImageUrl)
    }

    /** Jumps to the chapter: seeks within the loaded episode, otherwise starts the episode there. */
    fun onChapter(chapter: Chapter) {
        val state = _uiState.value
        val item = state.item ?: return
        if (state.isCurrent) {
            playbackRepository.seekTo(chapter.startMs)
            if (!state.isPlaying) playbackRepository.togglePlayPause()
        } else {
            playbackRepository.play(item.episode.copy(lastPositionMs = chapter.startMs), item.podcastTitle, item.podcastImageUrl)
        }
    }

    fun onToggleQueue() {
        viewModelScope.launch {
            val message = if (_uiState.value.isQueued) {
                queueRepository.removeEpisode(episodeId)
                "Odebráno z fronty"
            } else {
                queueRepository.addToQueue(episodeId).fold({ "Přidáno do fronty" }, { it.message })
            }
            _uiState.update { it.copy(snackbarMessage = message) }
        }
    }

    /** One button, one action for the state it is in: download, cancel, retry or delete the copy. */
    fun onDownloadAction() {
        viewModelScope.launch {
            val result = downloadRepository.toggle(episodeId)
            _uiState.update { it.copy(downloadMessage = result) }
        }
    }

    fun onDownloadMessageShown() {
        _uiState.update { it.copy(downloadMessage = null) }
    }

    fun onSnackbarMessageShown() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    companion object {
        fun factory(
            episodeRepository: EpisodeRepository,
            queueRepository: QueueRepository,
            playbackRepository: PlaybackRepository,
            downloadRepository: DownloadRepository,
            episodeId: Long
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return EpisodeDetailViewModel(episodeRepository, queueRepository, playbackRepository, downloadRepository, episodeId) as T
            }
        }
    }
}

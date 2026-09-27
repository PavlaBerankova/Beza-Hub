package com.berpod.app.ui.podcastlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berpod.app.data.local.EpisodeEntity
import com.berpod.app.data.repository.EpisodeRepository
import com.berpod.app.data.repository.PlaybackRepository
import com.berpod.app.data.repository.PodcastRepository
import com.berpod.app.data.repository.QueueRepository
import com.berpod.app.data.repository.TagRepository
import com.berpod.app.ui.common.TagAssignmentController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PodcastEpisodesUiState(
    val feedTitle: String = "",
    val feedImageUrl: String? = null,
    val episodes: List<EpisodeEntity> = emptyList(),
    val isRefreshing: Boolean = false,
    val snackbarMessage: String? = null,
    val currentlyPlayingEpisodeId: Long? = null,
    val isPlaying: Boolean = false
)

class PodcastEpisodesViewModel(
    private val podcastRepository: PodcastRepository,
    private val episodeRepository: EpisodeRepository,
    private val queueRepository: QueueRepository,
    private val playbackRepository: PlaybackRepository,
    private val tagRepository: TagRepository,
    private val feedId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(PodcastEpisodesUiState())
    val uiState: StateFlow<PodcastEpisodesUiState> = _uiState.asStateFlow()

    val tagAssignment = TagAssignmentController(tagRepository, viewModelScope)

    init {
        viewModelScope.launch {
            val feed = podcastRepository.getFeedById(feedId)
            _uiState.update { it.copy(feedTitle = feed?.title.orEmpty(), feedImageUrl = feed?.imageUrl) }
        }
        episodeRepository.observeEpisodesForFeed(feedId)
            .onEach { episodes -> _uiState.update { it.copy(episodes = episodes) } }
            .launchIn(viewModelScope)
        playbackRepository.uiState
            .onEach { playback ->
                _uiState.update {
                    it.copy(currentlyPlayingEpisodeId = playback.currentEpisodeId, isPlaying = playback.isPlaying)
                }
            }
            .launchIn(viewModelScope)
    }

    fun onPlay(episode: EpisodeEntity) {
        val state = _uiState.value
        playbackRepository.play(episode, state.feedTitle, state.feedImageUrl)
    }

    fun onRefresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            episodeRepository.syncEpisodesForFeedId(feedId)
                .onSuccess { _uiState.update { it.copy(isRefreshing = false) } }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isRefreshing = false, snackbarMessage = error.message ?: "Aktualizace selhala")
                    }
                }
        }
    }

    fun onOpenTagDialog() = tagAssignment.open(feedId, _uiState.value.feedTitle)

    fun onAddToQueue(episodeId: Long) {
        viewModelScope.launch {
            queueRepository.addToQueue(episodeId)
                .onSuccess { _uiState.update { it.copy(snackbarMessage = "Přidáno do fronty") } }
                .onFailure { error -> _uiState.update { it.copy(snackbarMessage = error.message) } }
        }
    }

    fun onSnackbarMessageShown() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    companion object {
        fun factory(
            podcastRepository: PodcastRepository,
            episodeRepository: EpisodeRepository,
            queueRepository: QueueRepository,
            playbackRepository: PlaybackRepository,
            tagRepository: TagRepository,
            feedId: Long
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PodcastEpisodesViewModel(
                    podcastRepository,
                    episodeRepository,
                    queueRepository,
                    playbackRepository,
                    tagRepository,
                    feedId
                ) as T
            }
        }
    }
}

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
import com.berpod.app.ui.common.PlayedUndo
import com.berpod.app.ui.common.TagAssignmentController
import com.berpod.app.ui.common.markPlayed
import com.berpod.app.ui.common.undoMarkPlayed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PodcastEpisodesUiState(
    val feedTitle: String = "",
    val feedImageUrl: String? = null,
    val feedAuthor: String? = null,
    /** Names of the tags this podcast carries, alphabetical. */
    val feedTags: List<String> = emptyList(),
    val episodes: List<EpisodeEntity> = emptyList(),
    val isRefreshing: Boolean = false,
    val snackbarMessage: String? = null,
    /** Set right after swiping an episode to played, while "Zpět" can undo it. */
    val playedUndo: PlayedUndo? = null,
    /** Episodes currently in the queue, so rows can show it. */
    val queuedEpisodeIds: Set<Long> = emptySet(),
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
        podcastRepository.observeFeed(feedId)
            .onEach { feed ->
                _uiState.update {
                    it.copy(feedTitle = feed?.title.orEmpty(), feedImageUrl = feed?.imageUrl, feedAuthor = feed?.author)
                }
            }
            .launchIn(viewModelScope)
        combine(tagRepository.observeTags(), tagRepository.observeTagIdsForPodcast(feedId)) { tags, ids ->
            tagNamesFor(tags, ids.toSet())
        }
            .onEach { names -> _uiState.update { it.copy(feedTags = names) } }
            .launchIn(viewModelScope)
        episodeRepository.observeEpisodesForFeed(feedId)
            .onEach { episodes -> _uiState.update { it.copy(episodes = episodes) } }
            .launchIn(viewModelScope)
        queueRepository.observeQueuedEpisodeIds()
            .onEach { ids -> _uiState.update { it.copy(queuedEpisodeIds = ids) } }
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

    /** Pauses/resumes the episode that is already loaded; starts any other episode. */
    fun onPlayOrPause(episode: EpisodeEntity) {
        if (_uiState.value.currentlyPlayingEpisodeId == episode.id) playbackRepository.togglePlayPause() else onPlay(episode)
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

    fun onOpenTagDialog() = with(_uiState.value) {
        tagAssignment.open(feedId, feedTitle, feedImageUrl, feedAuthor)
    }

    /** Swipe right: marks an unplayed episode played (with undo), or a played one unplayed again. */
    fun onTogglePlayed(episode: EpisodeEntity) {
        viewModelScope.launch {
            if (episode.playedAtMs != null) {
                episodeRepository.setPlayed(episode.id, played = false)
            } else {
                val undo = markPlayed(episode.id, episodeRepository, queueRepository)
                _uiState.update { it.copy(playedUndo = undo) }
            }
        }
    }

    fun onUndoMarkPlayed() {
        val undo = _uiState.value.playedUndo ?: return
        _uiState.update { it.copy(playedUndo = null) }
        viewModelScope.launch { undoMarkPlayed(undo, episodeRepository, queueRepository) }
    }

    fun onPlayedMessageDismissed() {
        _uiState.update { it.copy(playedUndo = null) }
    }

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

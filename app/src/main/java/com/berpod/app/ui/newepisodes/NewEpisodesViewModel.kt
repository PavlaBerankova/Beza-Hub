package com.berpod.app.ui.newepisodes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berpod.app.data.local.EpisodeWithFeed
import com.berpod.app.data.local.TagEntity
import com.berpod.app.data.repository.EpisodeRepository
import com.berpod.app.data.repository.PlaybackRepository
import com.berpod.app.data.repository.QueueRepository
import com.berpod.app.data.repository.TagRepository
import com.berpod.app.ui.common.PlayedUndo
import com.berpod.app.ui.common.filterByPodcastIds
import com.berpod.app.ui.common.markPlayed
import com.berpod.app.ui.common.resolveSelectedTagId
import com.berpod.app.ui.common.undoMarkPlayed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// TODO: read/unread tracking is out of scope for this iteration — "new" just means
// "all known episodes, newest published first".
data class NewEpisodesUiState(
    /** Episodes matching the selected tag; what the list shows. */
    val episodes: List<EpisodeWithFeed> = emptyList(),
    /** Whether there are any episodes at all, independent of the filter. */
    val hasAnyEpisodes: Boolean = false,
    val tags: List<TagEntity> = emptyList(),
    /** null = "Všechny". */
    val selectedTagId: Long? = null,
    val isRefreshing: Boolean = false,
    val snackbarMessage: String? = null,
    /** Set right after swiping an episode to played, while "Zpět" can undo it. */
    val playedUndo: PlayedUndo? = null,
    /** Episodes currently in the queue, so rows can show it. */
    val queuedEpisodeIds: Set<Long> = emptySet(),
    val currentlyPlayingEpisodeId: Long? = null,
    val isPlaying: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class NewEpisodesViewModel(
    private val episodeRepository: EpisodeRepository,
    private val queueRepository: QueueRepository,
    private val playbackRepository: PlaybackRepository,
    private val tagRepository: TagRepository
) : ViewModel() {

    private val selectedTagId = MutableStateFlow<Long?>(null)

    private val _uiState = MutableStateFlow(NewEpisodesUiState())
    val uiState: StateFlow<NewEpisodesUiState> = _uiState.asStateFlow()

    init {
        val taggedPodcastIds = selectedTagId.flatMapLatest { tagId ->
            if (tagId == null) flowOf(null) else tagRepository.observePodcastIdsWithTag(tagId).map { it.toSet() }
        }
        combine(episodeRepository.observeNewEpisodes(), taggedPodcastIds) { episodes, podcastIds ->
            episodes to filterByPodcastIds(episodes, podcastIds) { it.episode.feedId }
        }
            .onEach { (all, visible) -> _uiState.update { it.copy(episodes = visible, hasAnyEpisodes = all.isNotEmpty()) } }
            .launchIn(viewModelScope)
        tagRepository.observeTags()
            .onEach { tags ->
                selectedTagId.value = resolveSelectedTagId(selectedTagId.value, tags)
                _uiState.update { it.copy(tags = tags, selectedTagId = selectedTagId.value) }
            }
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

    fun onPlay(item: EpisodeWithFeed) {
        playbackRepository.play(item.episode, item.podcastTitle, item.podcastImageUrl)
    }

    /** Pauses/resumes the episode that is already loaded; starts any other one. */
    fun onPlayOrPause(item: EpisodeWithFeed) {
        if (_uiState.value.currentlyPlayingEpisodeId == item.episode.id) playbackRepository.togglePlayPause() else onPlay(item)
    }

    fun onMarkPlayed(episodeId: Long) {
        viewModelScope.launch {
            val undo = markPlayed(episodeId, episodeRepository, queueRepository)
            _uiState.update { it.copy(playedUndo = undo) }
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

    fun onSelectTag(tagId: Long?) {
        selectedTagId.value = tagId
        _uiState.update { it.copy(selectedTagId = tagId) }
    }

    fun onRefresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            episodeRepository.refreshAllFeeds()
                .onSuccess { failureCount ->
                    val message = if (failureCount > 0) {
                        "Aktualizace dokončena, $failureCount zdrojů se nepodařilo načíst"
                    } else {
                        null
                    }
                    _uiState.update { it.copy(isRefreshing = false, snackbarMessage = message) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isRefreshing = false, snackbarMessage = error.message ?: "Aktualizace selhala")
                    }
                }
        }
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
            episodeRepository: EpisodeRepository,
            queueRepository: QueueRepository,
            playbackRepository: PlaybackRepository,
            tagRepository: TagRepository
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return NewEpisodesViewModel(episodeRepository, queueRepository, playbackRepository, tagRepository) as T
            }
        }
    }
}

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
import com.berpod.app.ui.podcastlist.EpisodeSort
import com.berpod.app.ui.podcastlist.nextEpisodeSort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
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
    val period: NewPeriod = NewPeriod.All,
    val sort: EpisodeSort = EpisodeSort.NewestFirst,
    /** How many episodes "Odebrat vše z nových" is about to mark played; null while the confirmation is closed. */
    val confirmRemoveCount: Int? = null,
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
    private val period = MutableStateFlow(NewPeriod.All)
    private val sort = MutableStateFlow(EpisodeSort.NewestFirst)

    private val _uiState = MutableStateFlow(NewEpisodesUiState())
    val uiState: StateFlow<NewEpisodesUiState> = _uiState.asStateFlow()

    init {
        val taggedPodcastIds = selectedTagId.flatMapLatest { tagId ->
            if (tagId == null) flowOf(null) else tagRepository.observePodcastIdsWithTag(tagId).map { it.toSet() }
        }
        // An episode that is in the queue is no longer "new": it has been picked, and leaves again if it is taken out unplayed.
        val notQueued = combine(episodeRepository.observeNewEpisodes(), queueRepository.observeQueuedEpisodeIds()) { episodes, queued ->
            episodes.filter { it.episode.id !in queued }
        }
        combine(notQueued, taggedPodcastIds, period, sort) { episodes, podcastIds, selectedPeriod, selectedSort ->
            val now = System.currentTimeMillis()
            val inPeriod = filterByPeriod(filterByPodcastIds(episodes, podcastIds) { it.episode.feedId }, selectedPeriod, now) { it.episode.pubDateMillis }
            episodes to sortByPublished(inPeriod, selectedSort == EpisodeSort.OldestFirst) { it.episode.pubDateMillis }
        }
            // Filtering and sorting thousands of episodes happens off the main thread.
            .flowOn(Dispatchers.Default)
            .onEach { (all, visible) -> _uiState.update { it.copy(episodes = visible, hasAnyEpisodes = all.isNotEmpty()) } }
            .launchIn(viewModelScope)
        tagRepository.observeTagsInUse()
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

    fun onSelectPeriod(newPeriod: NewPeriod) {
        period.value = newPeriod
        _uiState.update { it.copy(period = newPeriod) }
    }

    fun onSelectSort(newSort: EpisodeSort) {
        sort.value = newSort
        _uiState.update { it.copy(sort = newSort) }
    }

    /** All → today → week → two weeks → month → all. */
    fun onCyclePeriod() = onSelectPeriod(nextPeriod(_uiState.value.period))

    /** Newest first ↔ oldest first. */
    fun onToggleSort() = onSelectSort(nextEpisodeSort(_uiState.value.sort))

    fun onRequestRemoveAll() = _uiState.update { it.copy(confirmRemoveCount = it.episodes.size) }

    fun onCancelRemoveAll() = _uiState.update { it.copy(confirmRemoveCount = null) }

    /** Marks everything Nové is listing right now (so the period and tag filters count) played, which takes it out of Nové. */
    fun onConfirmRemoveAll() {
        val ids = _uiState.value.episodes.map { it.episode.id }
        _uiState.update { it.copy(confirmRemoveCount = null) }
        viewModelScope.launch {
            // Nové never lists queued episodes, so there is nothing to take out of the queue.
            episodeRepository.markPlayed(ids)
            _uiState.update { it.copy(snackbarMessage = "Odebráno z nových: ${ids.size}") }
        }
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

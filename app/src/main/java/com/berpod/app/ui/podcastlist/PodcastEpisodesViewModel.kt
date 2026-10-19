package com.berpod.app.ui.podcastlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berpod.app.data.local.EpisodeEntity
import com.berpod.app.data.repository.DownloadRepository
import com.berpod.app.data.repository.DownloadToggle
import com.berpod.app.data.repository.EpisodeRepository
import com.berpod.app.data.repository.PlaybackRepository
import com.berpod.app.data.repository.PodcastRepository
import com.berpod.app.data.repository.QueueRepository
import com.berpod.app.data.repository.TagRepository
import com.berpod.app.ui.common.PlayedUndo
import com.berpod.app.ui.common.TagAssignmentController
import com.berpod.app.ui.common.feedLookupMessage
import com.berpod.app.ui.common.markAllPlayed
import com.berpod.app.ui.common.markPlayed
import com.berpod.app.ui.common.undoMarkPlayed
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
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
    val feedRssUrl: String = "",
    val episodeSort: EpisodeSort = EpisodeSort.NewestFirst,
    val episodeFilter: EpisodeFilter = EpisodeFilter.All,
    /** Set while the "mark all" confirmation is up: true to mark played, false to mark unplayed. */
    val confirmMarkAllPlayed: Boolean? = null,
    /** The edit-RSS dialog is open; [rssError] is why the last save was refused. */
    val isEditingRss: Boolean = false,
    val isSavingRss: Boolean = false,
    val rssError: String? = null,
    /** Names of the tags this podcast carries, alphabetical. */
    val feedTags: List<String> = emptyList(),
    val episodes: List<EpisodeEntity> = emptyList(),
    val isRefreshing: Boolean = false,
    val snackbarMessage: String? = null,
    /** What the last download action did, until its snackbar was shown. */
    val downloadMessage: DownloadToggle? = null,
    /** Set right after swiping an episode to played, while "Zpět" can undo it. */
    val playedUndo: PlayedUndo? = null,
    /** Episodes currently in the queue, so rows can show it. */
    val queuedEpisodeIds: Set<Long> = emptySet(),
    val currentlyPlayingEpisodeId: Long? = null,
    val isPlaying: Boolean = false,
    /** The "delete podcast" confirmation is up. */
    val confirmDelete: Boolean = false,
    /** The podcast is gone; the screen leaves. */
    val isDeleted: Boolean = false
)

class PodcastEpisodesViewModel(
    private val podcastRepository: PodcastRepository,
    private val episodeRepository: EpisodeRepository,
    private val queueRepository: QueueRepository,
    private val playbackRepository: PlaybackRepository,
    private val tagRepository: TagRepository,
    private val downloadRepository: DownloadRepository,
    private val feedId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(PodcastEpisodesUiState())
    val uiState: StateFlow<PodcastEpisodesUiState> = _uiState.asStateFlow()

    val tagAssignment = TagAssignmentController(tagRepository, viewModelScope)

    init {
        podcastRepository.observeFeed(feedId)
            .onEach { feed ->
                _uiState.update {
                    it.copy(
                        feedTitle = feed?.title.orEmpty(),
                        feedImageUrl = feed?.imageUrl,
                        feedAuthor = feed?.author,
                        feedRssUrl = feed?.rssUrl.orEmpty()
                    )
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

    /** Leaving the page for good: the new episodes shown here have been seen. Outlives the view model's own scope. */
    override fun onCleared() {
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch { runCatching { episodeRepository.markFeedSeen(feedId) } }
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

    /** All → unplayed → played → all. */
    fun onCycleFilter() = _uiState.update { it.copy(episodeFilter = nextEpisodeFilter(it.episodeFilter)) }

    /** Newest first ↔ oldest first. */
    fun onToggleSort() = _uiState.update { it.copy(episodeSort = nextEpisodeSort(it.episodeSort)) }

    fun onRequestMarkAll(played: Boolean) = _uiState.update { it.copy(confirmMarkAllPlayed = played) }

    fun onCancelMarkAll() = _uiState.update { it.copy(confirmMarkAllPlayed = null) }

    fun onConfirmMarkAll() {
        val played = _uiState.value.confirmMarkAllPlayed ?: return
        _uiState.update { it.copy(confirmMarkAllPlayed = null) }
        viewModelScope.launch {
            if (played) markAllPlayed(feedId, episodeRepository, queueRepository) else episodeRepository.markAllUnplayed(feedId)
            _uiState.update {
                it.copy(snackbarMessage = if (played) "Všechny epizody označeny jako přehrané" else "Všechny epizody označeny jako nepřehrané")
            }
        }
    }

    fun onRequestDelete() = _uiState.update { it.copy(confirmDelete = true) }

    fun onCancelDelete() = _uiState.update { it.copy(confirmDelete = false) }

    /** Deletes the podcast with its episodes and offline copies; a playing episode of it is paused first. */
    fun onConfirmDelete() {
        _uiState.update { it.copy(confirmDelete = false) }
        viewModelScope.launch {
            val ids = episodeRepository.idsForFeed(feedId).toSet()
            val playback = playbackRepository.uiState.value
            if (playback.isPlaying && playback.currentEpisodeId in ids) playbackRepository.togglePlayPause()
            downloadRepository.removeDownloadsOf(ids)
            podcastRepository.deleteFeed(feedId)
            _uiState.update { it.copy(isDeleted = true) }
        }
    }

    fun onEditRss() {
        _uiState.update { it.copy(isEditingRss = true, rssError = null) }
    }

    fun onEditRssDismissed() {
        _uiState.update { it.copy(isEditingRss = false, isSavingRss = false, rssError = null) }
    }

    /** Checks the new address and saves it, then pulls in whatever episodes it has. */
    fun onSaveRss(rawUrl: String) {
        if (_uiState.value.isSavingRss) return
        _uiState.update { it.copy(isSavingRss = true, rssError = null) }
        viewModelScope.launch {
            podcastRepository.updateRssUrl(feedId, rawUrl)
                .onSuccess {
                    _uiState.update { it.copy(isEditingRss = false, isSavingRss = false, snackbarMessage = "RSS adresa uložena") }
                    onRefresh()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSavingRss = false, rssError = feedLookupMessage(error)) }
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

    /** From the row's menu: download, cancel or delete the copy, whichever fits the episode's state. */
    fun onToggleDownload(episodeId: Long) {
        viewModelScope.launch {
            val result = downloadRepository.toggle(episodeId)
            _uiState.update { it.copy(downloadMessage = result) }
        }
    }

    fun onDownloadMessageShown() = _uiState.update { it.copy(downloadMessage = null) }

    fun onRemoveFromQueue(episodeId: Long) {
        viewModelScope.launch { queueRepository.removeEpisode(episodeId) }
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
            downloadRepository: DownloadRepository,
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
                    downloadRepository,
                    feedId
                ) as T
            }
        }
    }
}

package com.berpod.app.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berpod.app.data.local.DownloadStatus
import com.berpod.app.data.local.QueueItemWithEpisode
import com.berpod.app.data.local.RemovedQueueEntry
import com.berpod.app.data.local.TagEntity
import com.berpod.app.data.repository.DownloadRepository
import com.berpod.app.data.repository.DownloadToggle
import com.berpod.app.data.repository.EpisodeRepository
import com.berpod.app.data.repository.PlaybackRepository
import com.berpod.app.data.repository.QueueFilterRepository
import com.berpod.app.data.repository.QueueRepository
import com.berpod.app.data.repository.TagRepository
import com.berpod.app.ui.common.PlayedUndo
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

data class QueueUiState(
    /** The whole queue, independent of the filter. */
    val items: List<QueueItemWithEpisode> = emptyList(),
    /** Queue entries matching the selected tag; what the list shows. */
    val visibleItems: List<QueueItemWithEpisode> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    /** null = "Všechny". */
    val selectedTagId: Long? = null,
    val currentlyPlayingEpisodeId: Long? = null,
    val isPlaying: Boolean = false,
    /** The last swiped-away entry, while its "Zpět" snackbar can still restore it. */
    val removedEntry: RemovedQueueEntry? = null,
    /** The last episode swiped to "played", while its "Zpět" snackbar can still undo it. */
    val playedUndo: PlayedUndo? = null,
    /** The queue order before the last "Nejnovější/Nejstarší", while its "Zpět" snackbar can still restore it. */
    val sortUndo: List<Long>? = null,
    /** What the last download action did, until its snackbar was shown. */
    val downloadMessage: DownloadToggle? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class QueueViewModel(
    private val repository: QueueRepository,
    private val playbackRepository: PlaybackRepository,
    private val tagRepository: TagRepository,
    private val episodeRepository: EpisodeRepository,
    /** The selected tag is shared with playback, so next/previous/auto-advance stay inside the filter. */
    private val queueFilterRepository: QueueFilterRepository,
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QueueUiState())
    val uiState: StateFlow<QueueUiState> = _uiState.asStateFlow()

    private val selectedTagId = queueFilterRepository.selectedTagId

    init {
        val taggedPodcastIds = selectedTagId.flatMapLatest { tagId ->
            if (tagId == null) flowOf(null) else tagRepository.observePodcastIdsWithTag(tagId).map { it.toSet() }
        }
        combine(repository.observeQueue(), taggedPodcastIds) { items, podcastIds ->
            items to filterQueueByTag(items, podcastIds)
        }
            .onEach { (items, visible) -> _uiState.update { it.copy(items = items, visibleItems = visible) } }
            .launchIn(viewModelScope)
        tagRepository.observeTagsInUse()
            .onEach { tags ->
                val resolved = resolveSelectedTagId(selectedTagId.value, tags)
                queueFilterRepository.select(resolved)
                _uiState.update { it.copy(tags = tags, selectedTagId = resolved) }
            }
            .launchIn(viewModelScope)
        playbackRepository.uiState
            .onEach { playback ->
                _uiState.update {
                    it.copy(currentlyPlayingEpisodeId = playback.currentEpisodeId, isPlaying = playback.isPlaying)
                }
            }
            .launchIn(viewModelScope)
    }

    fun onSelectTag(tagId: Long?) {
        queueFilterRepository.select(tagId)
        _uiState.update { it.copy(selectedTagId = tagId) }
    }

    /** One tap flips the order: newest → oldest, oldest → newest; a hand-made order goes to newest first. */
    fun onToggleSort() = onSortQueue(oldestFirst = queueOrderOf(_uiState.value.items) == QueueOrder.NewestFirst)

    /** Re-orders the whole queue (playback follows the queue) and keeps the old order for "Zpět". */
    fun onSortQueue(oldestFirst: Boolean) {
        val items = _uiState.value.items
        viewModelScope.launch {
            val before = repository.orderedEntryIds()
            repository.applyOrder(sortedQueueEntryIds(items, oldestFirst))
            if (repository.orderedEntryIds() != before) _uiState.update { it.copy(sortUndo = before) }
        }
    }

    fun onUndoSort() {
        val before = _uiState.value.sortUndo ?: return
        _uiState.update { it.copy(sortUndo = null) }
        viewModelScope.launch { repository.applyOrder(before) }
    }

    /** Swipe left: downloads the episode unless it is already queued, running or finished. */
    fun onDownload(episodeId: Long) {
        viewModelScope.launch {
            if (downloadRepository.downloads.value[episodeId]?.status.let { it == null || it == DownloadStatus.FAILED }) {
                downloadRepository.download(episodeId)
                _uiState.update { it.copy(downloadMessage = DownloadToggle.Started) }
            }
        }
    }

    /** Downloads what the list shows (the tag filter included), in queue order. */
    fun onDownloadAll() {
        val ids = _uiState.value.visibleItems.map { it.episode.id }
        viewModelScope.launch {
            downloadRepository.downloadAll(ids)
            _uiState.update { it.copy(downloadMessage = DownloadToggle.Started) }
        }
    }

    /** From the row's menu: download, cancel or delete the copy, whichever fits the episode's state. */
    fun onToggleDownload(episodeId: Long) {
        viewModelScope.launch {
            val result = downloadRepository.toggle(episodeId)
            _uiState.update { it.copy(downloadMessage = result) }
        }
    }

    fun onDownloadMessageShown() = _uiState.update { it.copy(downloadMessage = null) }

    fun onSortMessageDismissed() = _uiState.update { it.copy(sortUndo = null) }

    fun onMoveEntry(movedId: Long, afterId: Long?, beforeId: Long?) {
        viewModelScope.launch { repository.moveEntry(movedId, afterId, beforeId) }
    }

    fun onRemove(queueEntryId: Long) {
        viewModelScope.launch {
            val removed = repository.removeFromQueue(queueEntryId) ?: return@launch
            _uiState.update { it.copy(removedEntry = removed) }
        }
    }

    fun onUndoRemove() {
        val removed = _uiState.value.removedEntry ?: return
        _uiState.update { it.copy(removedEntry = null) }
        viewModelScope.launch { repository.restore(removed) }
    }

    fun onRemovedMessageDismissed() {
        _uiState.update { it.copy(removedEntry = null) }
    }

    fun onMarkPlayed(episodeId: Long) {
        viewModelScope.launch {
            val undo = markPlayed(episodeId, episodeRepository, repository)
            _uiState.update { it.copy(playedUndo = undo) }
        }
    }

    fun onUndoMarkPlayed() {
        val undo = _uiState.value.playedUndo ?: return
        _uiState.update { it.copy(playedUndo = null) }
        viewModelScope.launch { undoMarkPlayed(undo, episodeRepository, repository) }
    }

    fun onPlayedMessageDismissed() {
        _uiState.update { it.copy(playedUndo = null) }
    }

    fun onPlay(item: QueueItemWithEpisode) {
        playbackRepository.play(item.episode, item.podcastTitle, item.podcastImageUrl)
    }

    /** Pauses/resumes the episode that is already loaded; starts any other one. */
    fun onPlayOrPause(item: QueueItemWithEpisode) {
        if (_uiState.value.currentlyPlayingEpisodeId == item.episode.id) playbackRepository.togglePlayPause() else onPlay(item)
    }

    companion object {
        fun factory(
            repository: QueueRepository,
            playbackRepository: PlaybackRepository,
            tagRepository: TagRepository,
            episodeRepository: EpisodeRepository,
            queueFilterRepository: QueueFilterRepository,
            downloadRepository: DownloadRepository
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return QueueViewModel(repository, playbackRepository, tagRepository, episodeRepository, queueFilterRepository, downloadRepository) as T
            }
        }
    }
}

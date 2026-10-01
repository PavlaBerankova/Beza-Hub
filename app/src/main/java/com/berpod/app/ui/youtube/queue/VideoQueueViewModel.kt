package com.berpod.app.ui.youtube.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berpod.app.data.local.QueueVideoItem
import com.berpod.app.data.local.RemovedVideoQueueEntry
import com.berpod.app.data.local.TagEntity
import com.berpod.app.data.repository.QueueFilterRepository
import com.berpod.app.data.repository.VideoQueueRepository
import com.berpod.app.data.repository.YoutubeRepository
import com.berpod.app.data.repository.filterVideoQueueOrder
import com.berpod.app.ui.common.resolveSelectedTagId
import com.berpod.app.ui.youtube.WatchedUndo
import com.berpod.app.ui.youtube.markWatched
import com.berpod.app.ui.youtube.undoMarkWatched
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

data class VideoQueueUiState(
    /** The whole queue, independent of the filter. */
    val items: List<QueueVideoItem> = emptyList(),
    /** Queue entries matching the selected category; what the list shows. */
    val visibleItems: List<QueueVideoItem> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    /** null = "Všechny". */
    val selectedTagId: Long? = null,
    /** The last swiped-away entry, while its "Zpět" snackbar can still restore it. */
    val removedEntry: RemovedVideoQueueEntry? = null,
    /** The last video swiped to "zhlédnuto", while its "Zpět" snackbar can still undo it. */
    val watchedUndo: WatchedUndo? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class VideoQueueViewModel(
    private val repository: VideoQueueRepository,
    private val youtubeRepository: YoutubeRepository,
    /** Remembered the same way as the podcast Fronta's filter, in its own SharedPreferences file. */
    private val queueFilterRepository: QueueFilterRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoQueueUiState())
    val uiState: StateFlow<VideoQueueUiState> = _uiState.asStateFlow()

    private val selectedTagId = queueFilterRepository.selectedTagId

    init {
        val taggedChannelIds = selectedTagId.flatMapLatest { tagId ->
            if (tagId == null) flowOf(null) else youtubeRepository.observeChannelIdsWithTag(tagId).map { it.toSet() }
        }
        combine(repository.observeQueue(), taggedChannelIds) { items, channelIds ->
            items to filterVideoQueueOrder(items, channelIds)
        }
            .onEach { (items, visible) -> _uiState.update { it.copy(items = items, visibleItems = visible) } }
            .launchIn(viewModelScope)
        youtubeRepository.observeTagsInUse()
            .onEach { tags ->
                val asTags = tags.map { TagEntity(it.id, it.name) }
                val resolved = resolveSelectedTagId(selectedTagId.value, asTags)
                queueFilterRepository.select(resolved)
                _uiState.update { it.copy(tags = asTags, selectedTagId = resolved) }
            }
            .launchIn(viewModelScope)
    }

    fun onSelectTag(tagId: Long?) {
        queueFilterRepository.select(tagId)
        _uiState.update { it.copy(selectedTagId = tagId) }
    }

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

    fun onMarkWatched(videoId: Long) {
        viewModelScope.launch {
            val undo = markWatched(videoId, youtubeRepository, repository)
            _uiState.update { it.copy(watchedUndo = undo) }
        }
    }

    fun onUndoMarkWatched() {
        val undo = _uiState.value.watchedUndo ?: return
        _uiState.update { it.copy(watchedUndo = null) }
        viewModelScope.launch { undoMarkWatched(undo, youtubeRepository, repository) }
    }

    fun onWatchedMessageDismissed() {
        _uiState.update { it.copy(watchedUndo = null) }
    }

    companion object {
        fun factory(
            repository: VideoQueueRepository,
            youtubeRepository: YoutubeRepository,
            queueFilterRepository: QueueFilterRepository
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return VideoQueueViewModel(repository, youtubeRepository, queueFilterRepository) as T
            }
        }
    }
}

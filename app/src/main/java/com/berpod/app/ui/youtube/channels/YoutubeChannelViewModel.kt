package com.berpod.app.ui.youtube.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berpod.app.data.local.VideoWithChannel
import com.berpod.app.data.repository.VideoQueueRepository
import kotlinx.coroutines.Job
import com.berpod.app.ui.common.TagAssignState
import com.berpod.app.data.local.TagEntity
import com.berpod.app.data.repository.YoutubeRepository
import com.berpod.app.ui.youtube.WatchedUndo
import com.berpod.app.ui.youtube.markWatched
import com.berpod.app.ui.youtube.undoMarkWatched
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

data class YoutubeChannelUiState(
    val channelTitle: String = "",
    val videos: List<VideoWithChannel> = emptyList(),
    val queuedVideoIds: Set<Long> = emptySet(),
    val snackbarMessage: String? = null,
    val watchedUndo: WatchedUndo? = null,
    /** The categories sheet of this channel, opened by the tag button in the top bar. */
    val categories: TagAssignState? = null
)

/** One channel's own videos (tapping a tile in Kanály); the simple list, no hero header. */
class YoutubeChannelViewModel(
    private val youtubeRepository: YoutubeRepository,
    private val videoQueueRepository: VideoQueueRepository,
    private val channelId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(YoutubeChannelUiState())
    val uiState: StateFlow<YoutubeChannelUiState> = _uiState.asStateFlow()

    init {
        combine(youtubeRepository.observeChannels(), youtubeRepository.observeVideos()) { channels, videos ->
            val title = channels.firstOrNull { it.id == channelId }?.title.orEmpty()
            title to videos.filter { it.video.feedId == channelId }
        }
            .onEach { (title, videos) -> _uiState.update { it.copy(channelTitle = title, videos = videos) } }
            .launchIn(viewModelScope)
        videoQueueRepository.observeQueuedVideoIds()
            .onEach { ids -> _uiState.update { it.copy(queuedVideoIds = ids) } }
            .launchIn(viewModelScope)
    }

    private var categoriesJob: Job? = null

    fun onOpenCategories() {
        categoriesJob?.cancel()
        _uiState.update { it.copy(categories = TagAssignState(channelId, it.channelTitle)) }
        categoriesJob = combine(youtubeRepository.observeTags(), youtubeRepository.observeTagIdsForChannel(channelId)) { tags, ids ->
            tags.map { TagEntity(it.id, it.name) } to ids.toSet()
        }
            .onEach { (tags, ids) -> _uiState.update { it.copy(categories = it.categories?.copy(tags = tags, checkedTagIds = ids)) } }
            .launchIn(viewModelScope)
    }

    fun onDismissCategories() {
        categoriesJob?.cancel()
        _uiState.update { it.copy(categories = null) }
    }

    fun onToggleCategory(tagId: Long, assigned: Boolean) {
        viewModelScope.launch { youtubeRepository.setAssigned(channelId, tagId, assigned) }
    }

    fun onCreateCategory(name: String) {
        viewModelScope.launch {
            youtubeRepository.createAndAssign(channelId, name)
                .onSuccess { _uiState.update { it.copy(categories = it.categories?.copy(errorMessage = null)) } }
                .onFailure { e -> _uiState.update { it.copy(categories = it.categories?.copy(errorMessage = e.message)) } }
        }
    }

    fun onDeleteCategory(tagId: Long) {
        viewModelScope.launch { youtubeRepository.deleteTag(tagId) }
    }

    /** Leaving the page for good: the new videos shown here have been seen. Outlives the view model's own scope. */
    override fun onCleared() {
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch { runCatching { youtubeRepository.markChannelSeen(channelId) } }
    }

    fun onAddToQueue(videoId: Long) {
        viewModelScope.launch {
            videoQueueRepository.addToQueue(videoId)
                .onSuccess { _uiState.update { it.copy(snackbarMessage = "Přidáno do fronty") } }
                .onFailure { error -> _uiState.update { it.copy(snackbarMessage = error.message) } }
        }
    }

    fun onMarkWatched(videoId: Long) {
        viewModelScope.launch {
            val undo = markWatched(videoId, youtubeRepository, videoQueueRepository)
            _uiState.update { it.copy(watchedUndo = undo) }
        }
    }

    fun onUndoMarkWatched() {
        val undo = _uiState.value.watchedUndo ?: return
        _uiState.update { it.copy(watchedUndo = null) }
        viewModelScope.launch { undoMarkWatched(undo, youtubeRepository, videoQueueRepository) }
    }

    fun onWatchedMessageDismissed() = _uiState.update { it.copy(watchedUndo = null) }

    fun onSnackbarMessageShown() = _uiState.update { it.copy(snackbarMessage = null) }

    companion object {
        fun factory(youtubeRepository: YoutubeRepository, videoQueueRepository: VideoQueueRepository, channelId: Long) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return YoutubeChannelViewModel(youtubeRepository, videoQueueRepository, channelId) as T
                }
            }
    }
}

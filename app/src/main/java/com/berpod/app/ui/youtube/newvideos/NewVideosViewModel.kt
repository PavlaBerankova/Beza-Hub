package com.berpod.app.ui.youtube.newvideos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berpod.app.data.local.TagEntity
import com.berpod.app.data.local.VideoWithChannel
import com.berpod.app.data.repository.VideoQueueRepository
import com.berpod.app.data.repository.YoutubeRepository
import com.berpod.app.ui.common.filterByPodcastIds
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

data class NewVideosUiState(
    /** Unwatched videos matching the selected category; what the list shows. */
    val videos: List<VideoWithChannel> = emptyList(),
    /** Whether any video exists at all, independent of the filter. */
    val hasAnyVideos: Boolean = false,
    val tags: List<TagEntity> = emptyList(),
    /** null = "Všechny". */
    val selectedTagId: Long? = null,
    val isRefreshing: Boolean = false,
    val snackbarMessage: String? = null,
    /** Set right after swiping a video to watched, while "Zpět" can undo it. */
    val watchedUndo: WatchedUndo? = null,
    /** Videos currently in the queue, so rows can hide the add-to-queue button. */
    val queuedVideoIds: Set<Long> = emptySet()
)

@OptIn(ExperimentalCoroutinesApi::class)
class NewVideosViewModel(
    private val youtubeRepository: YoutubeRepository,
    private val videoQueueRepository: VideoQueueRepository
) : ViewModel() {

    private val selectedTagId = MutableStateFlow<Long?>(null)

    private val _uiState = MutableStateFlow(NewVideosUiState())
    val uiState: StateFlow<NewVideosUiState> = _uiState.asStateFlow()

    init {
        val taggedChannelIds = selectedTagId.flatMapLatest { tagId ->
            if (tagId == null) flowOf(null) else youtubeRepository.observeChannelIdsWithTag(tagId).map { it.toSet() }
        }
        combine(youtubeRepository.observeVideos(), taggedChannelIds) { videos, channelIds ->
            val unwatched = videos.filter { it.video.watchedAtMs == null }
            unwatched to filterByPodcastIds(unwatched, channelIds) { it.video.feedId }
        }
            .onEach { (all, visible) -> _uiState.update { it.copy(videos = visible, hasAnyVideos = all.isNotEmpty()) } }
            .launchIn(viewModelScope)
        youtubeRepository.observeTagsInUse()
            .onEach { tags ->
                val asTags = tags.map { TagEntity(it.id, it.name) }
                selectedTagId.value = resolveSelectedTagId(selectedTagId.value, asTags)
                _uiState.update { it.copy(tags = asTags, selectedTagId = selectedTagId.value) }
            }
            .launchIn(viewModelScope)
        videoQueueRepository.observeQueuedVideoIds()
            .onEach { ids -> _uiState.update { it.copy(queuedVideoIds = ids) } }
            .launchIn(viewModelScope)
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val result = youtubeRepository.refreshOncePerSession()
            val message = result?.fold(
                { failures -> if (failures > 0) "Aktualizace dokončena, $failures kanálů se nepodařilo načíst" else null },
                { it.message ?: "Aktualizace selhala" }
            )
            _uiState.update { it.copy(isRefreshing = false, snackbarMessage = message) }
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

    fun onWatchedMessageDismissed() {
        _uiState.update { it.copy(watchedUndo = null) }
    }

    fun onSelectTag(tagId: Long?) {
        selectedTagId.value = tagId
        _uiState.update { it.copy(selectedTagId = tagId) }
    }

    fun onRefresh() {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            youtubeRepository.refreshAll()
                .onSuccess { failureCount ->
                    val message = if (failureCount > 0) "Aktualizace dokončena, $failureCount kanálů se nepodařilo načíst" else null
                    _uiState.update { it.copy(isRefreshing = false, snackbarMessage = message) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isRefreshing = false, snackbarMessage = error.message ?: "Aktualizace selhala") }
                }
        }
    }

    fun onAddToQueue(videoId: Long) {
        viewModelScope.launch {
            videoQueueRepository.addToQueue(videoId)
                .onSuccess { _uiState.update { it.copy(snackbarMessage = "Přidáno do fronty") } }
                .onFailure { error -> _uiState.update { it.copy(snackbarMessage = error.message) } }
        }
    }

    fun onSnackbarMessageShown() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    companion object {
        fun factory(youtubeRepository: YoutubeRepository, videoQueueRepository: VideoQueueRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return NewVideosViewModel(youtubeRepository, videoQueueRepository) as T
                }
            }
    }
}

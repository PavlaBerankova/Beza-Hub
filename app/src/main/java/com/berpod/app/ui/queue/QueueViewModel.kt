package com.berpod.app.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berpod.app.data.local.QueueItemWithEpisode
import com.berpod.app.data.local.TagEntity
import com.berpod.app.data.repository.PlaybackRepository
import com.berpod.app.data.repository.QueueRepository
import com.berpod.app.data.repository.TagRepository
import com.berpod.app.ui.common.resolveSelectedTagId
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
    val isPlaying: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class QueueViewModel(
    private val repository: QueueRepository,
    private val playbackRepository: PlaybackRepository,
    private val tagRepository: TagRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QueueUiState())
    val uiState: StateFlow<QueueUiState> = _uiState.asStateFlow()

    private val selectedTagId = MutableStateFlow<Long?>(null)

    init {
        val taggedPodcastIds = selectedTagId.flatMapLatest { tagId ->
            if (tagId == null) flowOf(null) else tagRepository.observePodcastIdsWithTag(tagId).map { it.toSet() }
        }
        combine(repository.observeQueue(), taggedPodcastIds) { items, podcastIds ->
            items to filterQueueByTag(items, podcastIds)
        }
            .onEach { (items, visible) -> _uiState.update { it.copy(items = items, visibleItems = visible) } }
            .launchIn(viewModelScope)
        tagRepository.observeTags()
            .onEach { tags ->
                selectedTagId.value = resolveSelectedTagId(selectedTagId.value, tags)
                _uiState.update { it.copy(tags = tags, selectedTagId = selectedTagId.value) }
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
        selectedTagId.value = tagId
        _uiState.update { it.copy(selectedTagId = tagId) }
    }

    fun onMoveEntry(movedId: Long, afterId: Long?, beforeId: Long?) {
        viewModelScope.launch { repository.moveEntry(movedId, afterId, beforeId) }
    }

    fun onRemove(queueEntryId: Long) {
        viewModelScope.launch { repository.removeFromQueue(queueEntryId) }
    }

    fun onPlay(item: QueueItemWithEpisode) {
        playbackRepository.play(item.episode, item.podcastTitle, item.podcastImageUrl)
    }

    companion object {
        fun factory(
            repository: QueueRepository,
            playbackRepository: PlaybackRepository,
            tagRepository: TagRepository
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return QueueViewModel(repository, playbackRepository, tagRepository) as T
            }
        }
    }
}

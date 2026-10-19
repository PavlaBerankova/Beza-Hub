package com.berpod.app.ui.youtube.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berpod.app.data.local.YoutubeChannelEntity
import com.berpod.app.data.remote.normalizeChannelUrl
import com.berpod.app.data.repository.VideoQueueRepository
import com.berpod.app.data.repository.YoutubeRepository
import com.berpod.app.data.local.TagEntity
import com.berpod.app.ui.common.AddFeedController
import com.berpod.app.ui.common.TagAssignState
import com.berpod.app.ui.common.filterByPodcastIds
import com.berpod.app.ui.common.resolveSelectedTagId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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

data class YoutubeChannelsUiState(
    /** Channels matching the selected category. */
    val channels: List<YoutubeChannelEntity> = emptyList(),
    /** Whether any channel exists at all, independent of the filter. */
    val hasAnyChannels: Boolean = false,
    /** New video count per channel id, for the grid's badge. */
    val unreadCounts: Map<Long, Int> = emptyMap(),
    val tags: List<TagEntity> = emptyList(),
    /** null = "Všechny". */
    val selectedTagId: Long? = null,
    val renamingTag: TagEntity? = null,
    val renameError: String? = null,
    val deletingTag: TagEntity? = null,
    /** The categories sheet for one channel, opened by long-pressing its tile. */
    val categories: TagAssignState? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class YoutubeChannelsViewModel(
    private val repository: YoutubeRepository,
    private val videoQueueRepository: VideoQueueRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(YoutubeChannelsUiState())
    val uiState: StateFlow<YoutubeChannelsUiState> = _uiState.asStateFlow()

    private val selectedTagId = MutableStateFlow<Long?>(null)
    private var categoriesJob: Job? = null

    val addChannel = AddFeedController(
        scope = viewModelScope,
        resolve = repository::resolve,
        add = { channel, tagIds -> repository.addChannel(channel, tagIds).map { } },
        observeTags = { repository.observeTags().map { tags -> tags.map { TagEntity(it.id, it.name) } } },
        createTagNamed = repository::createTag,
        onAdded = { },
        canLookup = { normalizeChannelUrl(it) != null }
    )

    init {
        repository.observeChannels()
            .onEach { channels -> _uiState.update { it.copy(hasAnyChannels = channels.isNotEmpty()) } }
            .launchIn(viewModelScope)
        val taggedChannelIds = selectedTagId.flatMapLatest { tagId ->
            if (tagId == null) flowOf(null) else repository.observeChannelIdsWithTag(tagId).map { it.toSet() }
        }
        combine(repository.observeChannels(), taggedChannelIds) { channels, ids ->
            filterByPodcastIds(channels, ids) { it.id }
        }
            .onEach { channels -> _uiState.update { it.copy(channels = channels) } }
            .launchIn(viewModelScope)
        // The badge counts what Nové shows for the channel; a queued video has been picked and no longer counts.
        combine(repository.observeNewVideos(), videoQueueRepository.observeQueuedVideoIds()) { videos, queued ->
            videos.filter { it.video.id !in queued }.groupingBy { it.video.feedId }.eachCount()
        }
            .onEach { counts -> _uiState.update { it.copy(unreadCounts = counts) } }
            .launchIn(viewModelScope)
        repository.observeTagsInUse()
            .onEach { tags ->
                val asTags = tags.map { TagEntity(it.id, it.name) }
                selectedTagId.value = resolveSelectedTagId(selectedTagId.value, asTags)
                _uiState.update { it.copy(tags = asTags, selectedTagId = selectedTagId.value) }
            }
            .launchIn(viewModelScope)
    }

    fun onSelectTag(tagId: Long?) {
        selectedTagId.value = tagId
        _uiState.update { it.copy(selectedTagId = tagId) }
    }

    fun onOpenCategories(channel: YoutubeChannelEntity) {
        categoriesJob?.cancel()
        _uiState.update { it.copy(categories = TagAssignState(channel.id, channel.title)) }
        categoriesJob = combine(repository.observeTags(), repository.observeTagIdsForChannel(channel.id)) { tags, ids ->
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
        val channelId = _uiState.value.categories?.podcastId ?: return
        viewModelScope.launch { repository.setAssigned(channelId, tagId, assigned) }
    }

    fun onCreateCategory(name: String) {
        val channelId = _uiState.value.categories?.podcastId ?: return
        viewModelScope.launch {
            repository.createAndAssign(channelId, name)
                .onSuccess { _uiState.update { it.copy(categories = it.categories?.copy(errorMessage = null)) } }
                .onFailure { e -> _uiState.update { it.copy(categories = it.categories?.copy(errorMessage = e.message)) } }
        }
    }

    fun onDeleteCategory(tagId: Long) {
        viewModelScope.launch { repository.deleteTag(tagId) }
    }

    fun onRequestRenameTag(tag: TagEntity) = _uiState.update { it.copy(renamingTag = tag, renameError = null) }

    fun onConfirmRenameTag(name: String) {
        val tag = _uiState.value.renamingTag ?: return
        viewModelScope.launch {
            repository.renameTag(tag.id, name)
                .onSuccess { _uiState.update { it.copy(renamingTag = null, renameError = null) } }
                .onFailure { e -> _uiState.update { it.copy(renameError = e.message) } }
        }
    }

    fun onDismissRenameTag() = _uiState.update { it.copy(renamingTag = null, renameError = null) }

    fun onRequestDeleteTag(tag: TagEntity) = _uiState.update { it.copy(deletingTag = tag) }

    fun onConfirmDeleteTag() {
        val tag = _uiState.value.deletingTag ?: return
        _uiState.update { it.copy(deletingTag = null) }
        viewModelScope.launch { repository.deleteTag(tag.id) }
    }

    fun onDismissDeleteTag() = _uiState.update { it.copy(deletingTag = null) }

    companion object {
        fun factory(repository: YoutubeRepository, videoQueueRepository: VideoQueueRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = YoutubeChannelsViewModel(repository, videoQueueRepository) as T
        }
    }
}

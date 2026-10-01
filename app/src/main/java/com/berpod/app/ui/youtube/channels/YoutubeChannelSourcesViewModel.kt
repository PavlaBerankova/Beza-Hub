package com.berpod.app.ui.youtube.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berpod.app.data.local.TagEntity
import com.berpod.app.data.local.YoutubeChannelEntity
import com.berpod.app.data.local.YoutubeChannelWithCount
import com.berpod.app.data.repository.YoutubeRepository
import com.berpod.app.ui.common.TagAssignState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class YoutubeChannelSourcesUiState(
    val channels: List<YoutubeChannelWithCount> = emptyList(),
    val deleting: YoutubeChannelEntity? = null,
    /** The categories dialog for one channel, reusing the shared tag picker. */
    val categories: TagAssignState? = null
)

class YoutubeChannelSourcesViewModel(private val repository: YoutubeRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(YoutubeChannelSourcesUiState())
    val uiState: StateFlow<YoutubeChannelSourcesUiState> = _uiState.asStateFlow()

    private var categoriesJob: Job? = null

    init {
        repository.observeChannelsWithCount()
            .onEach { channels -> _uiState.update { it.copy(channels = channels) } }
            .launchIn(viewModelScope)
    }

    fun onRequestDelete(channel: YoutubeChannelEntity) = _uiState.update { it.copy(deleting = channel) }

    fun onConfirmDelete() {
        val channel = _uiState.value.deleting ?: return
        _uiState.update { it.copy(deleting = null) }
        viewModelScope.launch { repository.deleteChannel(channel.id) }
    }

    fun onDismissDelete() = _uiState.update { it.copy(deleting = null) }

    fun onOpenCategories(channel: YoutubeChannelEntity) {
        categoriesJob?.cancel()
        val site = (channel.channelUrl ?: channel.rssUrl).removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
        _uiState.update { it.copy(categories = TagAssignState(channel.id, channel.title, subtitle = site)) }
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

    companion object {
        fun factory(repository: YoutubeRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = YoutubeChannelSourcesViewModel(repository) as T
        }
    }
}

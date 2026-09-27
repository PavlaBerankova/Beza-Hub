package com.berpod.app.ui.podcastlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berpod.app.data.local.PodcastFeedEntity
import com.berpod.app.data.local.TagEntity
import com.berpod.app.data.repository.EpisodeRepository
import com.berpod.app.data.repository.PodcastRepository
import com.berpod.app.data.repository.TagRepository
import com.berpod.app.ui.common.TagAssignmentController
import com.berpod.app.ui.common.resolveSelectedTagId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PodcastListUiState(
    /** Feeds matching the selected filter. */
    val feeds: List<PodcastFeedEntity> = emptyList(),
    /** Whether any podcast exists at all, independent of the filter. */
    val hasAnyPodcasts: Boolean = false,
    /** Number of subscribed podcasts, independent of the filter. */
    val podcastCount: Int = 0,
    val tags: List<TagEntity> = emptyList(),
    /** null = "Všechny". */
    val selectedTagId: Long? = null,
    val renamingTag: TagEntity? = null,
    val renameError: String? = null,
    val deletingTag: TagEntity? = null,
    val isAddDialogOpen: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class PodcastListViewModel(
    private val repository: PodcastRepository,
    private val episodeRepository: EpisodeRepository,
    private val tagRepository: TagRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PodcastListUiState())
    val uiState: StateFlow<PodcastListUiState> = _uiState.asStateFlow()

    val tagAssignment = TagAssignmentController(tagRepository, viewModelScope)

    private val selectedTagId = MutableStateFlow<Long?>(null)

    init {
        repository.observeFeeds()
            .onEach { feeds -> _uiState.update { it.copy(hasAnyPodcasts = feeds.isNotEmpty(), podcastCount = feeds.size) } }
            .launchIn(viewModelScope)
        selectedTagId
            .flatMapLatest { tagId -> tagRepository.observeFeeds(tagId) }
            .onEach { feeds -> _uiState.update { it.copy(feeds = feeds) } }
            .launchIn(viewModelScope)
        tagRepository.observeTags()
            .onEach { tags ->
                selectedTagId.value = resolveSelectedTagId(selectedTagId.value, tags)
                _uiState.update { it.copy(tags = tags, selectedTagId = selectedTagId.value) }
            }
            .launchIn(viewModelScope)
    }

    fun onSelectTag(tagId: Long?) {
        selectedTagId.value = tagId
        _uiState.update { it.copy(selectedTagId = tagId) }
    }

    fun onOpenTagDialog(feed: PodcastFeedEntity) = tagAssignment.open(feed.id, feed.title)

    fun onRequestRenameTag(tag: TagEntity) {
        _uiState.update { it.copy(renamingTag = tag, renameError = null) }
    }

    fun onDismissRenameTag() {
        _uiState.update { it.copy(renamingTag = null, renameError = null) }
    }

    fun onConfirmRenameTag(name: String) {
        val tag = _uiState.value.renamingTag ?: return
        viewModelScope.launch {
            tagRepository.rename(tag.id, name)
                .onSuccess { _uiState.update { it.copy(renamingTag = null, renameError = null) } }
                .onFailure { error -> _uiState.update { it.copy(renameError = error.message) } }
        }
    }

    fun onRequestDeleteTag(tag: TagEntity) {
        _uiState.update { it.copy(deletingTag = tag) }
    }

    fun onDismissDeleteTag() {
        _uiState.update { it.copy(deletingTag = null) }
    }

    fun onConfirmDeleteTag() {
        val tag = _uiState.value.deletingTag ?: return
        _uiState.update { it.copy(deletingTag = null) }
        // The tags observer resets the filter to "Všechny" if this was the selected tag.
        viewModelScope.launch { tagRepository.delete(tag.id) }
    }

    fun onFabClick() {
        _uiState.update { it.copy(isAddDialogOpen = true, errorMessage = null) }
    }

    fun onDismissAddDialog() {
        _uiState.update { it.copy(isAddDialogOpen = false, errorMessage = null) }
    }

    fun onAddFeed(url: String) {
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            repository.addFeed(url)
                .onSuccess { feedId ->
                    _uiState.update { it.copy(isSubmitting = false, isAddDialogOpen = false) }
                    // Best-effort: a slow/broken episode fetch shouldn't block the "feed added" success state.
                    episodeRepository.syncEpisodesForFeedId(feedId)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isSubmitting = false, errorMessage = error.message ?: "Nepodařilo se přidat podcast")
                    }
                }
        }
    }

    companion object {
        fun factory(
            repository: PodcastRepository,
            episodeRepository: EpisodeRepository,
            tagRepository: TagRepository
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PodcastListViewModel(repository, episodeRepository, tagRepository) as T
            }
        }
    }
}

package com.berpod.app.ui.podcastlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berpod.app.data.local.PodcastFeedEntity
import com.berpod.app.data.local.TagEntity
import com.berpod.app.data.repository.EpisodeRepository
import com.berpod.app.data.repository.PodcastRepository
import com.berpod.app.data.repository.QueueRepository
import com.berpod.app.ui.newepisodes.newEpisodeCounts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import com.berpod.app.data.repository.TagRepository
import com.berpod.app.ui.common.AddFeedController
import com.berpod.app.ui.common.TagAssignmentController
import com.berpod.app.ui.common.TagEditorController
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
    val tags: List<TagEntity> = emptyList(),
    /** null = "Všechny". */
    val selectedTagId: Long? = null,
    val renamingTag: TagEntity? = null,
    val renameError: String? = null,
    val deletingTag: TagEntity? = null,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    /** New episodes per podcast id, as Nové counts them; podcasts without any are missing. */
    val newCounts: Map<Long, Int> = emptyMap(),
    /** All podcasts are being refreshed, by the button or when the app opened. */
    val isRefreshing: Boolean = false,
    /** Why the last button-triggered refresh was not clean; shown once. */
    val refreshMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class PodcastListViewModel(
    private val repository: PodcastRepository,
    private val episodeRepository: EpisodeRepository,
    private val tagRepository: TagRepository,
    private val queueRepository: QueueRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PodcastListUiState())
    val uiState: StateFlow<PodcastListUiState> = _uiState.asStateFlow()

    val tagAssignment = TagAssignmentController(tagRepository, viewModelScope)

    /** The editor of all štítky (add, rename, delete, reorder), opened from the title row's menu. */
    val tagEditor = TagEditorController(
        scope = viewModelScope,
        observeTags = tagRepository::observeTags,
        create = tagRepository::createTag,
        rename = tagRepository::rename,
        delete = tagRepository::delete,
        reorder = tagRepository::reorder
    )

    val addPodcast = AddFeedController(
        scope = viewModelScope,
        resolve = repository::resolve,
        add = { meta, tagIds ->
            repository.addFeed(meta).map { feedId ->
                tagIds.forEach { tagRepository.setAssigned(feedId, it, assigned = true) }
                // Best-effort: a slow episode fetch shouldn't hold up the added podcast.
                viewModelScope.launch { episodeRepository.syncEpisodesForFeedId(feedId) }
            }
        },
        observeTags = tagRepository::observeTags,
        createTagNamed = tagRepository::createTag,
        onAdded = { }
    )

    private val selectedTagId = MutableStateFlow<Long?>(null)

    init {
        repository.observeFeeds()
            .onEach { feeds -> _uiState.update { it.copy(hasAnyPodcasts = feeds.isNotEmpty()) } }
            .launchIn(viewModelScope)
        combine(episodeRepository.observeNewEpisodes(), queueRepository.observeQueuedEpisodeIds()) { episodes, queued ->
            newEpisodeCounts(episodes, queued.toSet())
        }
            .flowOn(Dispatchers.Default)
            .onEach { counts -> _uiState.update { it.copy(newCounts = counts) } }
            .launchIn(viewModelScope)
        selectedTagId
            .flatMapLatest { tagId -> tagRepository.observeFeeds(tagId) }
            .onEach { feeds -> _uiState.update { it.copy(feeds = feeds) } }
            .launchIn(viewModelScope)
        tagRepository.observeTagsInUse()
            .onEach { tags ->
                selectedTagId.value = resolveSelectedTagId(selectedTagId.value, tags)
                _uiState.update { it.copy(tags = tags, selectedTagId = selectedTagId.value) }
            }
            .launchIn(viewModelScope)
        // Opening the app brings every podcast up to date, so the badges with new episodes are right from the start.
        refresh(announce = false)
    }

    /** The button in the title row. */
    fun onRefresh() = refresh(announce = true)

    fun onRefreshMessageShown() = _uiState.update { it.copy(refreshMessage = null) }

    /** [announce]: tell the user when some podcasts could not be loaded (the automatic run stays quiet). */
    private fun refresh(announce: Boolean) {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val result = episodeRepository.refreshAllFeeds()
            val message = if (!announce) null else result.fold(
                onSuccess = { failed -> if (failed > 0) "Aktualizace dokončena, $failed zdrojů se nepodařilo načíst" else null },
                onFailure = { error -> error.message ?: "Aktualizace selhala" }
            )
            _uiState.update { it.copy(isRefreshing = false, refreshMessage = message) }
        }
    }

    fun onSelectTag(tagId: Long?) {
        selectedTagId.value = tagId
        _uiState.update { it.copy(selectedTagId = tagId) }
    }

    fun onOpenTagDialog(feed: PodcastFeedEntity) =
        tagAssignment.open(feed.id, feed.title, feed.imageUrl, feed.author)

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

    companion object {
        fun factory(
            repository: PodcastRepository,
            episodeRepository: EpisodeRepository,
            tagRepository: TagRepository,
            queueRepository: QueueRepository
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PodcastListViewModel(repository, episodeRepository, tagRepository, queueRepository) as T
            }
        }
    }
}

package com.berpod.app.ui.articles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berpod.app.data.local.ArticleFeedEntity
import com.berpod.app.data.local.ArticleFeedWithCount
import com.berpod.app.data.local.TagEntity
import com.berpod.app.data.repository.ArticleRepository
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

data class ArticleSourcesUiState(
    val feeds: List<ArticleFeedWithCount> = emptyList(),
    val deleting: ArticleFeedEntity? = null,
    /** The categories dialog for one source, reusing the podcast tag dialog. */
    val categories: TagAssignState? = null
)

class ArticleSourcesViewModel(private val repository: ArticleRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ArticleSourcesUiState())
    val uiState: StateFlow<ArticleSourcesUiState> = _uiState.asStateFlow()

    private var categoriesJob: Job? = null

    init {
        repository.observeFeedsWithCount()
            .onEach { feeds -> _uiState.update { it.copy(feeds = feeds) } }
            .launchIn(viewModelScope)
    }

    fun onRequestDelete(feed: ArticleFeedEntity) = _uiState.update { it.copy(deleting = feed) }

    fun onConfirmDelete() {
        val feed = _uiState.value.deleting ?: return
        _uiState.update { it.copy(deleting = null) }
        viewModelScope.launch { repository.deleteFeed(feed.id) }
    }

    fun onDismissDelete() = _uiState.update { it.copy(deleting = null) }

    fun onOpenCategories(feed: ArticleFeedEntity) {
        categoriesJob?.cancel()
        val site = (feed.siteUrl ?: feed.rssUrl).removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
        _uiState.update { it.copy(categories = TagAssignState(feed.id, feed.title, subtitle = site)) }
        categoriesJob = combine(repository.observeTags(), repository.observeTagIdsForFeed(feed.id)) { tags, ids ->
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
        val feedId = _uiState.value.categories?.podcastId ?: return
        viewModelScope.launch { repository.setAssigned(feedId, tagId, assigned) }
    }

    fun onCreateCategory(name: String) {
        val feedId = _uiState.value.categories?.podcastId ?: return
        viewModelScope.launch {
            repository.createAndAssign(feedId, name)
                .onSuccess { _uiState.update { it.copy(categories = it.categories?.copy(errorMessage = null)) } }
                .onFailure { e -> _uiState.update { it.copy(categories = it.categories?.copy(errorMessage = e.message)) } }
        }
    }

    fun onDeleteCategory(tagId: Long) {
        viewModelScope.launch { repository.deleteTag(tagId) }
    }

    companion object {
        fun factory(repository: ArticleRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ArticleSourcesViewModel(repository) as T
        }
    }
}

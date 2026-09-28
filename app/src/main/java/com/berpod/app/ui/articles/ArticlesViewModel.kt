package com.berpod.app.ui.articles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berpod.app.data.local.ArticleWithFeed
import com.berpod.app.data.local.TagEntity
import com.berpod.app.data.repository.ArticleRepository
import com.berpod.app.ui.common.AddFeedController
import com.berpod.app.ui.common.filterByPodcastIds
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

data class ArticlesUiState(
    /** Articles in the selected category, newest first. */
    val articles: List<ArticleWithFeed> = emptyList(),
    val unreadCount: Int = 0,
    val sourceCount: Int = 0,
    val hasAnySource: Boolean = false,
    /** Categories some source has, as tags so the shared chip row can show them. */
    val tags: List<TagEntity> = emptyList(),
    val selectedTagId: Long? = null,
    val isRefreshing: Boolean = false,
    val renamingTag: TagEntity? = null,
    val renameError: String? = null,
    val deletingTag: TagEntity? = null,
    val snackbarMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ArticlesViewModel(private val repository: ArticleRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ArticlesUiState())
    val uiState: StateFlow<ArticlesUiState> = _uiState.asStateFlow()

    private val selectedTagId = MutableStateFlow<Long?>(null)

    val addSource = AddFeedController(
        scope = viewModelScope,
        resolve = repository::resolve,
        add = { feed, tagIds -> repository.addFeed(feed, tagIds).map { } },
        observeTags = { repository.observeTags().map { tags -> tags.map { TagEntity(it.id, it.name) } } },
        createTagNamed = repository::createTag,
        onAdded = { _uiState.update { it.copy(snackbarMessage = "Zdroj přidán") } }
    )

    init {
        val taggedFeedIds = selectedTagId.flatMapLatest { tagId ->
            if (tagId == null) flowOf(null) else repository.observeFeedIdsWithTag(tagId).map { it.toSet() }
        }
        combine(repository.observeArticles(), taggedFeedIds) { articles, feedIds ->
            filterByPodcastIds(articles, feedIds) { it.article.feedId }
        }
            .onEach { visible ->
                _uiState.update { state ->
                    state.copy(articles = visible, unreadCount = visible.count { it.article.readAtMs == null })
                }
            }
            .launchIn(viewModelScope)
        repository.observeFeeds()
            .onEach { feeds -> _uiState.update { it.copy(sourceCount = feeds.size, hasAnySource = feeds.isNotEmpty()) } }
            .launchIn(viewModelScope)
        repository.observeTagsInUse()
            .onEach { tags ->
                val asTags = tags.map { TagEntity(it.id, it.name) }
                selectedTagId.value = resolveSelectedTagId(selectedTagId.value, asTags)
                _uiState.update { it.copy(tags = asTags, selectedTagId = selectedTagId.value) }
            }
            .launchIn(viewModelScope)
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val result = repository.refreshOncePerSession()
            _uiState.update { it.copy(isRefreshing = false, snackbarMessage = result?.let(::refreshMessage)) }
        }
    }

    fun onRefresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val result = repository.refreshAll()
            _uiState.update { it.copy(isRefreshing = false, snackbarMessage = refreshMessage(result)) }
        }
    }

    private fun refreshMessage(result: Result<Int>): String? = result.fold(
        { failures -> if (failures > 0) "Aktualizace dokončena, $failures zdrojů se nepodařilo načíst" else null },
        { it.message ?: "Aktualizace selhala" }
    )

    fun onSelectTag(tagId: Long?) {
        selectedTagId.value = tagId
        _uiState.update { it.copy(selectedTagId = tagId) }
    }

    fun onToggleRead(item: ArticleWithFeed) {
        viewModelScope.launch { repository.setRead(item.article.id, read = item.article.readAtMs == null) }
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

    fun onSnackbarMessageShown() = _uiState.update { it.copy(snackbarMessage = null) }

    companion object {
        fun factory(repository: ArticleRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ArticlesViewModel(repository) as T
        }
    }
}

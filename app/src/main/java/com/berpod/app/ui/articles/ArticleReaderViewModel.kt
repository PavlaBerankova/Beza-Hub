package com.berpod.app.ui.articles

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berpod.app.data.local.ArticleWithFeed
import com.berpod.app.data.repository.ArticleRepository
import com.berpod.app.ui.common.filterByPodcastIds
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

/** Reader text sizes the Aa button cycles through, in sp. */
val READER_FONT_SIZES = listOf(16, 18, 20, 22)

fun nextFontSize(current: Int): Int = READER_FONT_SIZES[(READER_FONT_SIZES.indexOf(current) + 1).mod(READER_FONT_SIZES.size)]

data class ArticleReaderUiState(
    val isLoaded: Boolean = false,
    val item: ArticleWithFeed? = null,
    val categoryNames: List<String> = emptyList(),
    val fontSize: Int = 18,
    /** The next unread article in the list the reader came from; null when none is left. */
    val nextUnreadId: Long? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ArticleReaderViewModel(
    private val repository: ArticleRepository,
    private val prefs: SharedPreferences,
    private val articleId: Long,
    /** The category the list was filtered by, so "Další nepřečtený" stays in it. */
    private val filterTagId: Long?
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArticleReaderUiState(fontSize = prefs.getInt(KEY_FONT_SIZE, 18)))
    val uiState: StateFlow<ArticleReaderUiState> = _uiState.asStateFlow()

    private var markedRead = false

    init {
        repository.observeArticle(articleId)
            .onEach { item ->
                _uiState.update { it.copy(isLoaded = true, item = item) }
                // Opening it reads it — once, so "mark unread" elsewhere isn't undone while it's open.
                if (item != null && !markedRead) {
                    markedRead = true
                    if (item.article.readAtMs == null) repository.setRead(articleId, true)
                }
            }
            .launchIn(viewModelScope)
        repository.observeArticle(articleId)
            .flatMapLatest { item ->
                if (item == null) flowOf(emptyList())
                else combine(repository.observeTags(), repository.observeTagIdsForFeed(item.article.feedId)) { tags, ids ->
                    tags.filter { it.id in ids }.map { it.name }
                }
            }
            .onEach { names -> _uiState.update { it.copy(categoryNames = names) } }
            .launchIn(viewModelScope)
        val feedIds = if (filterTagId == null) flowOf(null) else repository.observeFeedIdsWithTag(filterTagId).map { it.toSet() }
        combine(repository.observeArticles(), feedIds) { articles, ids ->
            val visible = filterByPodcastIds(articles, ids) { it.article.feedId }
            nextUnreadId(
                orderedIds = visible.map { it.article.id },
                unreadIds = visible.filter { it.article.readAtMs == null }.map { it.article.id }.toSet(),
                currentId = articleId
            )
        }
            .onEach { next -> _uiState.update { it.copy(nextUnreadId = next) } }
            .launchIn(viewModelScope)
    }

    fun onCycleFontSize() {
        val size = nextFontSize(_uiState.value.fontSize)
        prefs.edit().putInt(KEY_FONT_SIZE, size).apply()
        _uiState.update { it.copy(fontSize = size) }
    }

    companion object {
        private const val KEY_FONT_SIZE = "fontSize"

        fun factory(repository: ArticleRepository, prefs: SharedPreferences, articleId: Long, filterTagId: Long?) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ArticleReaderViewModel(repository, prefs, articleId, filterTagId) as T
            }
    }
}

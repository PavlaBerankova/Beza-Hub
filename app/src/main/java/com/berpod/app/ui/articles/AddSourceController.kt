package com.berpod.app.ui.articles

import com.berpod.app.data.local.TagEntity
import com.berpod.app.data.remote.ParsedArticleFeed
import com.berpod.app.data.remote.normalizeSourceUrl
import com.berpod.app.data.repository.ArticleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SourceLookup {
    data object Idle : SourceLookup
    data object Searching : SourceLookup
    data class Found(val feed: ParsedArticleFeed) : SourceLookup
    data class NotFound(val message: String) : SourceLookup
}

data class AddSourceState(
    val url: String = "",
    val lookup: SourceLookup = SourceLookup.Idle,
    val tags: List<TagEntity> = emptyList(),
    val selectedTagIds: Set<Long> = emptySet(),
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

/** The "Přidat zdroj" sheet: looks the address up while typing, picks categories, adds the source. */
class AddSourceController(
    private val repository: ArticleRepository,
    private val scope: CoroutineScope,
    private val onAdded: () -> Unit
) {
    private val _state = MutableStateFlow<AddSourceState?>(null)
    /** null while the sheet is closed. */
    val state: StateFlow<AddSourceState?> = _state.asStateFlow()

    private var lookupJob: Job? = null
    private var tagsJob: Job? = null

    fun open() {
        _state.value = AddSourceState()
        tagsJob?.cancel()
        tagsJob = repository.observeTags()
            .onEach { tags -> _state.update { it?.copy(tags = tags.map { t -> TagEntity(t.id, t.name) }) } }
            .launchIn(scope)
    }

    fun dismiss() {
        lookupJob?.cancel()
        tagsJob?.cancel()
        _state.value = null
    }

    /** Typing waits a moment before looking up; a pasted address is looked up straight away. */
    fun onUrlChange(url: String, immediate: Boolean = false) {
        _state.update { it?.copy(url = url, errorMessage = null) }
        lookupJob?.cancel()
        if (normalizeSourceUrl(url) == null) {
            _state.update { it?.copy(lookup = SourceLookup.Idle) }
            return
        }
        lookupJob = scope.launch {
            if (!immediate) delay(700)
            _state.update { it?.copy(lookup = SourceLookup.Searching) }
            val result = repository.resolve(url)
            _state.update { state ->
                state?.copy(
                    lookup = result.fold(
                        { SourceLookup.Found(it) },
                        { SourceLookup.NotFound(it.message ?: "Zdroj se nepodařilo načíst") }
                    )
                )
            }
        }
    }

    fun toggleTag(tagId: Long) {
        _state.update { s -> s?.copy(selectedTagIds = if (tagId in s.selectedTagIds) s.selectedTagIds - tagId else s.selectedTagIds + tagId) }
    }

    fun createTag(name: String) {
        scope.launch {
            repository.createTag(name)
                .onSuccess { id -> _state.update { it?.copy(selectedTagIds = it.selectedTagIds + id, errorMessage = null) } }
                .onFailure { e -> _state.update { it?.copy(errorMessage = e.message) } }
        }
    }

    fun submit() {
        val state = _state.value ?: return
        val found = state.lookup as? SourceLookup.Found ?: return
        if (state.isSubmitting) return
        _state.update { it?.copy(isSubmitting = true, errorMessage = null) }
        scope.launch {
            repository.addFeed(found.feed, state.selectedTagIds)
                .onSuccess {
                    dismiss()
                    onAdded()
                }
                .onFailure { e -> _state.update { it?.copy(isSubmitting = false, errorMessage = e.message) } }
        }
    }
}

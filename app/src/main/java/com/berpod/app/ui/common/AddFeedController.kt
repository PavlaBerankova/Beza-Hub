package com.berpod.app.ui.common

import com.berpod.app.data.local.TagEntity
import com.berpod.app.data.remote.normalizeSourceUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** A failed lookup in words the reader can act on; our own messages (already Czech) are kept. */
fun feedLookupMessage(error: Throwable): String = when (error) {
    is UnknownHostException -> "Adresa neexistuje nebo není dostupná"
    is SocketTimeoutException -> "Server neodpovídá"
    is IOException -> "Nepodařilo se připojit k serveru"
    else -> error.message?.takeIf { it.isNotBlank() } ?: "Nepodařilo se načíst"
}

/** What the address typed into the sheet has turned into so far. */
sealed interface FeedLookup<out T> {
    data object Idle : FeedLookup<Nothing>
    data object Searching : FeedLookup<Nothing>
    data class Found<T>(val feed: T) : FeedLookup<T>
    data class NotFound(val message: String) : FeedLookup<Nothing>
}

data class AddFeedState<T>(
    val url: String = "",
    val lookup: FeedLookup<T> = FeedLookup.Idle,
    val tags: List<TagEntity> = emptyList(),
    val selectedTagIds: Set<Long> = emptySet(),
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

/**
 * The "add a feed" sheet behind both Podcasty and Články: it looks the typed address up while the user
 * types, collects tags, then hands the found feed to [add]. State is null while the sheet is closed.
 */
class AddFeedController<T>(
    private val scope: CoroutineScope,
    private val resolve: suspend (url: String) -> Result<T>,
    private val add: suspend (feed: T, tagIds: Set<Long>) -> Result<Unit>,
    private val observeTags: () -> Flow<List<TagEntity>>,
    private val createTagNamed: suspend (name: String) -> Result<Long>,
    private val onAdded: () -> Unit
) {
    private val _state = MutableStateFlow<AddFeedState<T>?>(null)
    val state: StateFlow<AddFeedState<T>?> = _state.asStateFlow()

    private var lookupJob: Job? = null
    private var tagsJob: Job? = null

    fun open() {
        _state.value = AddFeedState()
        tagsJob?.cancel()
        tagsJob = observeTags()
            .onEach { tags -> _state.update { it?.copy(tags = tags) } }
            .launchIn(scope)
    }

    fun dismiss() {
        lookupJob?.cancel()
        tagsJob?.cancel()
        _state.value = null
    }

    /** Typing waits a moment before looking the address up; a pasted one is looked up straight away. */
    fun onUrlChange(url: String, immediate: Boolean = false) {
        _state.update { it?.copy(url = url, errorMessage = null) }
        lookupJob?.cancel()
        if (normalizeSourceUrl(url) == null) {
            _state.update { it?.copy(lookup = FeedLookup.Idle) }
            return
        }
        lookupJob = scope.launch {
            if (!immediate) delay(700)
            _state.update { it?.copy(lookup = FeedLookup.Searching) }
            val result = resolve(url)
            _state.update { state ->
                state?.copy(
                    lookup = result.fold(
                        { FeedLookup.Found(it) },
                        { FeedLookup.NotFound(feedLookupMessage(it)) }
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
            createTagNamed(name)
                .onSuccess { id -> _state.update { it?.copy(selectedTagIds = it.selectedTagIds + id, errorMessage = null) } }
                .onFailure { e -> _state.update { it?.copy(errorMessage = e.message) } }
        }
    }

    fun submit() {
        val state = _state.value ?: return
        val found = state.lookup as? FeedLookup.Found ?: return
        if (state.isSubmitting) return
        _state.update { it?.copy(isSubmitting = true, errorMessage = null) }
        scope.launch {
            add(found.feed, state.selectedTagIds)
                .onSuccess {
                    dismiss()
                    onAdded()
                }
                .onFailure { e -> _state.update { it?.copy(isSubmitting = false, errorMessage = e.message) } }
        }
    }
}

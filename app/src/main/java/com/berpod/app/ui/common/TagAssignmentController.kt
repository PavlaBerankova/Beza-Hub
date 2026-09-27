package com.berpod.app.ui.common

import com.berpod.app.data.local.TagEntity
import com.berpod.app.data.repository.TagRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TagAssignState(
    val podcastId: Long,
    val podcastTitle: String,
    val tags: List<TagEntity> = emptyList(),
    val checkedTagIds: Set<Long> = emptySet(),
    val errorMessage: String? = null
)

/**
 * State and actions for the "assign tags to a podcast" dialog, shared by every screen that offers it.
 * The owning view model passes its viewModelScope; state is null while the dialog is closed.
 */
class TagAssignmentController(
    private val tagRepository: TagRepository,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow<TagAssignState?>(null)
    val state: StateFlow<TagAssignState?> = _state.asStateFlow()

    private var observeJob: Job? = null

    fun open(podcastId: Long, podcastTitle: String) {
        observeJob?.cancel()
        _state.value = TagAssignState(podcastId, podcastTitle)
        observeJob = combine(
            tagRepository.observeTags(),
            tagRepository.observeTagIdsForPodcast(podcastId)
        ) { tags, checkedIds -> tags to checkedIds.toSet() }
            .onEach { (tags, checkedIds) ->
                _state.update { it?.copy(tags = tags, checkedTagIds = checkedIds) }
            }
            .launchIn(scope)
    }

    fun dismiss() {
        observeJob?.cancel()
        observeJob = null
        _state.value = null
    }

    fun setAssigned(tagId: Long, assigned: Boolean) {
        val podcastId = _state.value?.podcastId ?: return
        scope.launch { tagRepository.setAssigned(podcastId, tagId, assigned) }
    }

    fun createAndAssign(name: String) {
        val podcastId = _state.value?.podcastId ?: return
        scope.launch {
            tagRepository.createAndAssign(podcastId, name)
                .onSuccess { _state.update { it?.copy(errorMessage = null) } }
                .onFailure { error -> _state.update { it?.copy(errorMessage = error.message) } }
        }
    }

    /** Deletes the tag everywhere (all podcasts lose it); the open dialog updates from the tag flow. */
    fun deleteTag(tagId: Long) {
        scope.launch { tagRepository.delete(tagId) }
    }
}

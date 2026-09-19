package com.berpod.app.ui.podcastlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berpod.app.data.local.PodcastFeedEntity
import com.berpod.app.data.repository.EpisodeRepository
import com.berpod.app.data.repository.PodcastRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PodcastListUiState(
    val feeds: List<PodcastFeedEntity> = emptyList(),
    val isAddDialogOpen: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

class PodcastListViewModel(
    private val repository: PodcastRepository,
    private val episodeRepository: EpisodeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PodcastListUiState())
    val uiState: StateFlow<PodcastListUiState> = _uiState.asStateFlow()

    init {
        repository.observeFeeds()
            .onEach { feeds -> _uiState.update { it.copy(feeds = feeds) } }
            .launchIn(viewModelScope)
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
        fun factory(repository: PodcastRepository, episodeRepository: EpisodeRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PodcastListViewModel(repository, episodeRepository) as T
                }
            }
    }
}

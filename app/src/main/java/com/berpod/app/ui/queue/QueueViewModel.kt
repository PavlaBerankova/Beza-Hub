package com.berpod.app.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berpod.app.data.local.QueueItemWithEpisode
import com.berpod.app.data.repository.PlaybackRepository
import com.berpod.app.data.repository.QueueRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QueueUiState(
    val items: List<QueueItemWithEpisode> = emptyList(),
    val currentlyPlayingEpisodeId: Long? = null,
    val isPlaying: Boolean = false
)

class QueueViewModel(
    private val repository: QueueRepository,
    private val playbackRepository: PlaybackRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QueueUiState())
    val uiState: StateFlow<QueueUiState> = _uiState.asStateFlow()

    init {
        repository.observeQueue()
            .onEach { items -> _uiState.update { it.copy(items = items) } }
            .launchIn(viewModelScope)
        playbackRepository.uiState
            .onEach { playback ->
                _uiState.update {
                    it.copy(currentlyPlayingEpisodeId = playback.currentEpisodeId, isPlaying = playback.isPlaying)
                }
            }
            .launchIn(viewModelScope)
    }

    fun onMoveUp(index: Int) {
        viewModelScope.launch { repository.moveItem(_uiState.value.items, index, index - 1) }
    }

    fun onMoveDown(index: Int) {
        viewModelScope.launch { repository.moveItem(_uiState.value.items, index, index + 1) }
    }

    fun onRemove(queueEntryId: Long) {
        viewModelScope.launch { repository.removeFromQueue(queueEntryId) }
    }

    fun onPlay(item: QueueItemWithEpisode) {
        playbackRepository.play(item.episode, item.podcastTitle, item.podcastImageUrl)
    }

    companion object {
        fun factory(repository: QueueRepository, playbackRepository: PlaybackRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return QueueViewModel(repository, playbackRepository) as T
                }
            }
    }
}

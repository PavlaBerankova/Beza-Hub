package com.berpod.app.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berpod.app.data.local.QueueItemWithEpisode
import com.berpod.app.data.repository.QueueRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QueueUiState(
    val items: List<QueueItemWithEpisode> = emptyList()
)

class QueueViewModel(private val repository: QueueRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(QueueUiState())
    val uiState: StateFlow<QueueUiState> = _uiState.asStateFlow()

    init {
        repository.observeQueue()
            .onEach { items -> _uiState.update { it.copy(items = items) } }
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

    companion object {
        fun factory(repository: QueueRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return QueueViewModel(repository) as T
            }
        }
    }
}

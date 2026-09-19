package com.berpod.app.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berpod.app.data.local.PlaylistEntity
import com.berpod.app.data.repository.PlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaylistsUiState(
    val playlists: List<PlaylistEntity> = emptyList(),
    val isCreateDialogOpen: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

class PlaylistsViewModel(private val repository: PlaylistRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistsUiState())
    val uiState: StateFlow<PlaylistsUiState> = _uiState.asStateFlow()

    init {
        repository.observePlaylists()
            .onEach { playlists -> _uiState.update { it.copy(playlists = playlists) } }
            .launchIn(viewModelScope)
    }

    fun onFabClick() {
        _uiState.update { it.copy(isCreateDialogOpen = true, errorMessage = null) }
    }

    fun onDismissDialog() {
        _uiState.update { it.copy(isCreateDialogOpen = false, errorMessage = null) }
    }

    fun onCreatePlaylist(name: String) {
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            repository.createPlaylist(name)
                .onSuccess { _uiState.update { it.copy(isSubmitting = false, isCreateDialogOpen = false) } }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isSubmitting = false, errorMessage = error.message ?: "Nepodařilo se vytvořit playlist")
                    }
                }
        }
    }

    companion object {
        fun factory(repository: PlaylistRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PlaylistsViewModel(repository) as T
            }
        }
    }
}

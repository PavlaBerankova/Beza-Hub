package com.berpod.app.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berpod.app.data.local.PlaylistEpisodeWithEpisode
import com.berpod.app.data.repository.PlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaylistDetailUiState(
    val playlistName: String = "",
    val episodes: List<PlaylistEpisodeWithEpisode> = emptyList()
)

class PlaylistDetailViewModel(
    private val repository: PlaylistRepository,
    private val playlistId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistDetailUiState())
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val name = repository.getPlaylistName(playlistId).orEmpty()
            _uiState.update { it.copy(playlistName = name) }
        }
        repository.observePlaylistEpisodes(playlistId)
            .onEach { episodes -> _uiState.update { it.copy(episodes = episodes) } }
            .launchIn(viewModelScope)
    }

    fun onMoveUp(index: Int) {
        viewModelScope.launch { repository.moveItem(_uiState.value.episodes, index, index - 1) }
    }

    fun onMoveDown(index: Int) {
        viewModelScope.launch { repository.moveItem(_uiState.value.episodes, index, index + 1) }
    }

    fun onRemove(episodeId: Long) {
        viewModelScope.launch { repository.removeEpisode(playlistId, episodeId) }
    }

    companion object {
        fun factory(repository: PlaylistRepository, playlistId: Long) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PlaylistDetailViewModel(repository, playlistId) as T
            }
        }
    }
}

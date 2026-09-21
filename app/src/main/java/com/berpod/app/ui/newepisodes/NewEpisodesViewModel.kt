package com.berpod.app.ui.newepisodes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berpod.app.data.local.EpisodeWithFeed
import com.berpod.app.data.local.PlaylistEntity
import com.berpod.app.data.repository.EpisodeRepository
import com.berpod.app.data.repository.PlaybackRepository
import com.berpod.app.data.repository.PlaylistRepository
import com.berpod.app.data.repository.QueueRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// TODO: read/unread tracking is out of scope for this iteration — "new" just means
// "all known episodes, newest published first".
data class NewEpisodesUiState(
    val episodes: List<EpisodeWithFeed> = emptyList(),
    val isRefreshing: Boolean = false,
    val snackbarMessage: String? = null,
    val playlists: List<PlaylistEntity> = emptyList(),
    val playlistPickerEpisodeId: Long? = null,
    val isCreatingPlaylist: Boolean = false,
    val playlistPickerError: String? = null,
    val currentlyPlayingEpisodeId: Long? = null,
    val isPlaying: Boolean = false
)

class NewEpisodesViewModel(
    private val episodeRepository: EpisodeRepository,
    private val queueRepository: QueueRepository,
    private val playlistRepository: PlaylistRepository,
    private val playbackRepository: PlaybackRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewEpisodesUiState())
    val uiState: StateFlow<NewEpisodesUiState> = _uiState.asStateFlow()

    init {
        episodeRepository.observeNewEpisodes()
            .onEach { episodes -> _uiState.update { it.copy(episodes = episodes) } }
            .launchIn(viewModelScope)
        playlistRepository.observePlaylists()
            .onEach { playlists -> _uiState.update { it.copy(playlists = playlists) } }
            .launchIn(viewModelScope)
        playbackRepository.uiState
            .onEach { playback ->
                _uiState.update {
                    it.copy(currentlyPlayingEpisodeId = playback.currentEpisodeId, isPlaying = playback.isPlaying)
                }
            }
            .launchIn(viewModelScope)
    }

    fun onPlay(item: EpisodeWithFeed) {
        playbackRepository.play(item.episode, item.podcastTitle, item.podcastImageUrl)
    }

    fun onRefresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            episodeRepository.refreshAllFeeds()
                .onSuccess { failureCount ->
                    val message = if (failureCount > 0) {
                        "Aktualizace dokončena, $failureCount zdrojů se nepodařilo načíst"
                    } else {
                        null
                    }
                    _uiState.update { it.copy(isRefreshing = false, snackbarMessage = message) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isRefreshing = false, snackbarMessage = error.message ?: "Aktualizace selhala")
                    }
                }
        }
    }

    fun onAddToQueue(episodeId: Long) {
        viewModelScope.launch {
            queueRepository.addToQueue(episodeId)
                .onSuccess { _uiState.update { it.copy(snackbarMessage = "Přidáno do fronty") } }
                .onFailure { error -> _uiState.update { it.copy(snackbarMessage = error.message) } }
        }
    }

    fun onSnackbarMessageShown() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun onOpenPlaylistPicker(episodeId: Long) {
        _uiState.update { it.copy(playlistPickerEpisodeId = episodeId, playlistPickerError = null) }
    }

    fun onDismissPlaylistPicker() {
        _uiState.update { it.copy(playlistPickerEpisodeId = null, playlistPickerError = null) }
    }

    fun onAddToExistingPlaylist(playlistId: Long) {
        val episodeId = _uiState.value.playlistPickerEpisodeId ?: return
        viewModelScope.launch {
            playlistRepository.addEpisode(playlistId, episodeId)
                .onSuccess {
                    _uiState.update {
                        it.copy(playlistPickerEpisodeId = null, playlistPickerError = null, snackbarMessage = "Přidáno do playlistu")
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(playlistPickerError = error.message) } }
        }
    }

    fun onCreatePlaylistAndAdd(name: String) {
        val episodeId = _uiState.value.playlistPickerEpisodeId ?: return
        _uiState.update { it.copy(isCreatingPlaylist = true, playlistPickerError = null) }
        viewModelScope.launch {
            playlistRepository.createPlaylist(name)
                .onSuccess { playlistId ->
                    playlistRepository.addEpisode(playlistId, episodeId)
                    _uiState.update {
                        it.copy(
                            isCreatingPlaylist = false,
                            playlistPickerEpisodeId = null,
                            playlistPickerError = null,
                            snackbarMessage = "Přidáno do playlistu"
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isCreatingPlaylist = false, playlistPickerError = error.message) }
                }
        }
    }

    companion object {
        fun factory(
            episodeRepository: EpisodeRepository,
            queueRepository: QueueRepository,
            playlistRepository: PlaylistRepository,
            playbackRepository: PlaybackRepository
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return NewEpisodesViewModel(episodeRepository, queueRepository, playlistRepository, playbackRepository) as T
            }
        }
    }
}

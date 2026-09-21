package com.berpod.app.ui.podcastlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.berpod.app.data.local.EpisodeEntity
import com.berpod.app.data.local.PlaylistEntity
import com.berpod.app.data.repository.EpisodeRepository
import com.berpod.app.data.repository.PlaybackRepository
import com.berpod.app.data.repository.PlaylistRepository
import com.berpod.app.data.repository.PodcastRepository
import com.berpod.app.data.repository.QueueRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PodcastEpisodesUiState(
    val feedTitle: String = "",
    val feedImageUrl: String? = null,
    val episodes: List<EpisodeEntity> = emptyList(),
    val isRefreshing: Boolean = false,
    val snackbarMessage: String? = null,
    val playlists: List<PlaylistEntity> = emptyList(),
    val playlistPickerEpisodeId: Long? = null,
    val isCreatingPlaylist: Boolean = false,
    val playlistPickerError: String? = null,
    val currentlyPlayingEpisodeId: Long? = null,
    val isPlaying: Boolean = false
)

class PodcastEpisodesViewModel(
    private val podcastRepository: PodcastRepository,
    private val episodeRepository: EpisodeRepository,
    private val queueRepository: QueueRepository,
    private val playlistRepository: PlaylistRepository,
    private val playbackRepository: PlaybackRepository,
    private val feedId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(PodcastEpisodesUiState())
    val uiState: StateFlow<PodcastEpisodesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val feed = podcastRepository.getFeedById(feedId)
            _uiState.update { it.copy(feedTitle = feed?.title.orEmpty(), feedImageUrl = feed?.imageUrl) }
        }
        episodeRepository.observeEpisodesForFeed(feedId)
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

    fun onPlay(episode: EpisodeEntity) {
        val state = _uiState.value
        playbackRepository.play(episode, state.feedTitle, state.feedImageUrl)
    }

    fun onRefresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            episodeRepository.syncEpisodesForFeedId(feedId)
                .onSuccess { _uiState.update { it.copy(isRefreshing = false) } }
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
            podcastRepository: PodcastRepository,
            episodeRepository: EpisodeRepository,
            queueRepository: QueueRepository,
            playlistRepository: PlaylistRepository,
            playbackRepository: PlaybackRepository,
            feedId: Long
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PodcastEpisodesViewModel(
                    podcastRepository,
                    episodeRepository,
                    queueRepository,
                    playlistRepository,
                    playbackRepository,
                    feedId
                ) as T
            }
        }
    }
}

package com.berpod.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.berpod.app.data.repository.PlaybackRepository
import com.berpod.app.data.repository.PlaybackUiState
import kotlinx.coroutines.flow.StateFlow

class PlayerViewModel(private val playbackRepository: PlaybackRepository) : ViewModel() {

    val uiState: StateFlow<PlaybackUiState> = playbackRepository.uiState

    fun togglePlayPause() = playbackRepository.togglePlayPause()

    fun seekTo(positionMs: Long) = playbackRepository.seekTo(positionMs)

    companion object {
        fun factory(playbackRepository: PlaybackRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PlayerViewModel(playbackRepository) as T
            }
        }
    }
}

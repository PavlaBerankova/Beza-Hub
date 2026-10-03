package com.berpod.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.berpod.app.data.repository.PlaybackRepository
import com.berpod.app.data.repository.PlaybackUiState
import kotlinx.coroutines.flow.StateFlow

class PlayerViewModel(private val playbackRepository: PlaybackRepository) : ViewModel() {

    val uiState: StateFlow<PlaybackUiState> = playbackRepository.uiState

    suspend fun currentFeedId(): Long? = playbackRepository.currentFeedId()

    fun togglePlayPause() = playbackRepository.togglePlayPause()

    fun seekTo(positionMs: Long) = playbackRepository.seekTo(positionMs)

    fun skipBack() = playbackRepository.skipBy(-15_000)

    fun skipForward() = playbackRepository.skipBy(30_000)

    fun playNext() = playbackRepository.playNext()

    fun playPrevious() = playbackRepository.playPrevious()

    fun cycleSpeed() = playbackRepository.cycleSpeed()

    fun setSleepTimer(minutes: Int) = playbackRepository.setSleepTimer(minutes)

    fun setSleepAtEndOfEpisode() = playbackRepository.setSleepAtEndOfEpisode()

    fun cancelSleepTimer() = playbackRepository.cancelSleepTimer()

    companion object {
        fun factory(playbackRepository: PlaybackRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PlayerViewModel(playbackRepository) as T
            }
        }
    }
}

package com.berpod.app.ui.common

import com.berpod.app.data.local.RemovedQueueEntry
import com.berpod.app.data.repository.EpisodeRepository
import com.berpod.app.data.repository.QueueRepository

/** What "Zpět" needs after marking an episode played: the episode and its removed queue entry, if any. */
data class PlayedUndo(val episodeId: Long, val removedFromQueue: RemovedQueueEntry?)

/** Marks played (and takes it out of the queue, like finishing it does); returns what undo needs. */
suspend fun markPlayed(episodeId: Long, episodes: EpisodeRepository, queue: QueueRepository): PlayedUndo {
    episodes.setPlayed(episodeId, played = true)
    return PlayedUndo(episodeId, queue.removeEpisode(episodeId))
}

suspend fun undoMarkPlayed(undo: PlayedUndo, episodes: EpisodeRepository, queue: QueueRepository) {
    episodes.setPlayed(undo.episodeId, played = false)
    undo.removedFromQueue?.let { queue.restore(it) }
}

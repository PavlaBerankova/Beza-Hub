package com.berpod.app.ui.youtube

import com.berpod.app.data.local.RemovedVideoQueueEntry
import com.berpod.app.data.repository.VideoQueueRepository
import com.berpod.app.data.repository.YoutubeRepository

/** What "Zpět" needs after marking a video watched: the video and its removed queue entry, if any. */
data class WatchedUndo(val videoId: Long, val removedFromQueue: RemovedVideoQueueEntry?)

/** Marks watched (and takes it out of the queue, like finishing it does); returns what undo needs. */
suspend fun markWatched(videoId: Long, videos: YoutubeRepository, queue: VideoQueueRepository): WatchedUndo {
    videos.setWatched(videoId, watched = true)
    return WatchedUndo(videoId, queue.removeVideo(videoId))
}

suspend fun undoMarkWatched(undo: WatchedUndo, videos: YoutubeRepository, queue: VideoQueueRepository) {
    videos.setWatched(undo.videoId, watched = false)
    undo.removedFromQueue?.let { queue.restore(it) }
}

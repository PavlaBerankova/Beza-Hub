package com.berpod.app.data.repository

import com.berpod.app.data.local.QueueVideoItem
import com.berpod.app.data.local.RemovedVideoQueueEntry
import com.berpod.app.data.local.VideoQueueDao
import com.berpod.app.data.local.VideoQueueEntryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class VideoQueueRepository(private val videoQueueDao: VideoQueueDao) {

    fun observeQueue(): Flow<List<QueueVideoItem>> = videoQueueDao.observeQueue()

    suspend fun addToQueue(videoId: Long): Result<Unit> = runCatching {
        require(!videoQueueDao.isQueued(videoId)) { "Video už je ve frontě" }
        val nextPosition = videoQueueDao.getMaxPosition() + 1
        videoQueueDao.insert(VideoQueueEntryEntity(videoId = videoId, position = nextPosition, dateAdded = System.currentTimeMillis()))
    }

    fun observeQueuedVideoIds(): Flow<Set<Long>> = videoQueueDao.observeQueuedVideoIds().map { it.toSet() }

    suspend fun removeFromQueue(queueEntryId: Long): RemovedVideoQueueEntry? = videoQueueDao.removeEntry(queueEntryId)

    suspend fun restore(removed: RemovedVideoQueueEntry) = videoQueueDao.restoreEntry(removed)

    suspend fun moveEntry(movedId: Long, afterId: Long?, beforeId: Long?) =
        videoQueueDao.moveEntry(movedId, afterId, beforeId)

    suspend fun queueAtFront(videoId: Long) = videoQueueDao.queueAtFront(videoId)

    suspend fun removeVideo(videoId: Long): RemovedVideoQueueEntry? = videoQueueDao.removeVideo(videoId)
}

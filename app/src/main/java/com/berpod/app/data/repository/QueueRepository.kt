package com.berpod.app.data.repository

import com.berpod.app.data.local.QueueDao
import com.berpod.app.data.local.QueueEntryEntity
import com.berpod.app.data.local.QueueItemWithEpisode
import com.berpod.app.data.local.RemovedQueueEntry
import kotlinx.coroutines.flow.Flow

class QueueRepository(private val queueDao: QueueDao) {

    fun observeQueue(): Flow<List<QueueItemWithEpisode>> = queueDao.observeQueue()

    suspend fun addToQueue(episodeId: Long): Result<Unit> = runCatching {
        require(!queueDao.isQueued(episodeId)) { "Epizoda už je ve frontě" }
        val nextPosition = queueDao.getMaxPosition() + 1
        queueDao.insert(QueueEntryEntity(episodeId = episodeId, position = nextPosition, dateAdded = System.currentTimeMillis()))
    }

    suspend fun removeFromQueue(queueEntryId: Long): RemovedQueueEntry? = queueDao.removeEntry(queueEntryId)

    suspend fun restore(removed: RemovedQueueEntry) = queueDao.restoreEntry(removed)

    suspend fun moveEntry(movedId: Long, afterId: Long?, beforeId: Long?) =
        queueDao.moveEntry(movedId, afterId, beforeId)
}

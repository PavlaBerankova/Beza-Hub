package com.berpod.app.data.repository

import com.berpod.app.data.local.QueueDao
import com.berpod.app.data.local.QueueEntryEntity
import com.berpod.app.data.local.QueueItemWithEpisode
import com.berpod.app.data.local.RemovedQueueEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class QueueRepository(private val queueDao: QueueDao) {

    fun observeQueue(): Flow<List<QueueItemWithEpisode>> = queueDao.observeQueue()

    suspend fun addToQueue(episodeId: Long): Result<Unit> = runCatching {
        require(!queueDao.isQueued(episodeId)) { "Epizoda už je ve frontě" }
        val nextPosition = queueDao.getMaxPosition() + 1
        queueDao.insert(QueueEntryEntity(episodeId = episodeId, position = nextPosition, dateAdded = System.currentTimeMillis()))
    }

    fun observeQueuedEpisodeIds(): Flow<Set<Long>> = queueDao.observeQueuedEpisodeIds().map { it.toSet() }

    suspend fun removeFromQueue(queueEntryId: Long): RemovedQueueEntry? = queueDao.removeEntry(queueEntryId)

    suspend fun restore(removed: RemovedQueueEntry) = queueDao.restoreEntry(removed)

    suspend fun moveEntry(movedId: Long, afterId: Long?, beforeId: Long?) =
        queueDao.moveEntry(movedId, afterId, beforeId)

    suspend fun orderedEntryIds(): List<Long> = queueDao.getOrderedIds()

    suspend fun applyOrder(entryIds: List<Long>) = queueDao.applyOrder(entryIds)

    suspend fun queueAtFront(episodeId: Long) = queueDao.queueAtFront(episodeId)

    suspend fun removeEpisode(episodeId: Long): RemovedQueueEntry? = queueDao.removeEpisode(episodeId)
}

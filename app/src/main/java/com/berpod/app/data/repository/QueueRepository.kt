package com.berpod.app.data.repository

import com.berpod.app.data.local.QueueDao
import com.berpod.app.data.local.QueueEntryEntity
import com.berpod.app.data.local.QueueItemWithEpisode
import kotlinx.coroutines.flow.Flow

class QueueRepository(private val queueDao: QueueDao) {

    fun observeQueue(): Flow<List<QueueItemWithEpisode>> = queueDao.observeQueue()

    suspend fun addToQueue(episodeId: Long): Result<Unit> = runCatching {
        require(!queueDao.isQueued(episodeId)) { "Epizoda už je ve frontě" }
        val nextPosition = queueDao.getMaxPosition() + 1
        queueDao.insert(QueueEntryEntity(episodeId = episodeId, position = nextPosition, dateAdded = System.currentTimeMillis()))
    }

    suspend fun removeFromQueue(queueEntryId: Long) = queueDao.deleteById(queueEntryId)

    suspend fun moveItem(currentOrder: List<QueueItemWithEpisode>, fromIndex: Int, toIndex: Int) {
        if (fromIndex !in currentOrder.indices || toIndex !in currentOrder.indices) return
        val a = currentOrder[fromIndex]
        val b = currentOrder[toIndex]
        queueDao.swapEntries(a.queueEntryId, b.queueEntryId)
    }
}

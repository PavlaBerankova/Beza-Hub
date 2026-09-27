package com.berpod.app.data.repository

import com.berpod.app.data.local.QueueDao
import com.berpod.app.data.local.QueueEntryEntity
import com.berpod.app.data.local.QueueItemWithEpisode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Stores positions per queue entry id; inherits the real moveEntry transaction logic. */
private class RecordingQueueDao(initial: Map<Long, Int>) : QueueDao {
    val positions = initial.toMutableMap()

    override fun observeQueue(): Flow<List<QueueItemWithEpisode>> = emptyFlow()
    override suspend fun getMaxPosition(): Int = positions.values.maxOrNull() ?: -1
    override suspend fun isQueued(episodeId: Long): Boolean = false
    override suspend fun insert(entry: QueueEntryEntity): Long = entry.id
    override suspend fun deleteById(queueEntryId: Long) {
        positions.remove(queueEntryId)
    }
    override suspend fun updatePosition(queueEntryId: Long, position: Int) {
        positions[queueEntryId] = position
    }
    override suspend fun getOrderedIds(): List<Long> = positions.entries.sortedBy { it.value }.map { it.key }

    fun order(): List<Long> = positions.entries.sortedBy { it.value }.map { it.key }
}

class QueueRepositoryTest {

    @Test
    fun `moveEntry stores the new order as positions 0 to n-1`() = runTest {
        val dao = RecordingQueueDao(mapOf(10L to 0, 11L to 1, 12L to 2))
        QueueRepository(dao).moveEntry(movedId = 10, afterId = 12, beforeId = null)

        assertEquals(listOf(11L, 12L, 10L), dao.order())
        assertEquals(setOf(0, 1, 2), dao.positions.values.toSet())
    }

    @Test
    fun `moveEntry with stale anchors keeps positions 0 to n-1`() = runTest {
        // Two quick moves computed from the same (stale) list A,B,C.
        val dao = RecordingQueueDao(mapOf(20L to 0, 21L to 1, 22L to 2))
        val repository = QueueRepository(dao)

        repository.moveEntry(movedId = 22, afterId = 20, beforeId = 21) // C up
        repository.moveEntry(movedId = 21, afterId = null, beforeId = 20) // stale "B up"

        assertEquals(listOf(21L, 20L, 22L), dao.order())
        assertEquals(setOf(0, 1, 2), dao.positions.values.toSet())
    }

    @Test
    fun `moveEntry for a removed entry changes nothing`() = runTest {
        val dao = RecordingQueueDao(mapOf(10L to 0, 11L to 1))
        QueueRepository(dao).moveEntry(movedId = 99, afterId = 10, beforeId = 11)

        assertEquals(mapOf(10L to 0, 11L to 1), dao.positions)
    }
}

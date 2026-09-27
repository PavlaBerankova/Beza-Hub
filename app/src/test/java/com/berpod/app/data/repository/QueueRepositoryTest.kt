package com.berpod.app.data.repository

import com.berpod.app.data.local.QueueDao
import com.berpod.app.data.local.QueueEntryEntity
import com.berpod.app.data.local.QueueItemWithEpisode
import com.berpod.app.testutil.queueItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Records stored positions per queue entry id; only what moveItem touches is implemented. */
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
}

class QueueRepositoryTest {

    // Full queue: 10@0 (feed 1), 11@1 (feed 2), 12@2 (feed 1). Filter shows feed 1 only.
    private val dao = RecordingQueueDao(mapOf(10L to 0, 11L to 1, 12L to 2))
    private val repository = QueueRepository(dao)
    private val visible = listOf(
        queueItem(entryId = 10, position = 0, feedId = 1),
        queueItem(entryId = 12, position = 2, feedId = 1)
    )

    @Test
    fun `moveItem swaps only the two given visible entries`() = runTest {
        repository.moveItem(visible, fromIndex = 1, toIndex = 0)

        assertEquals(mapOf(10L to 2, 11L to 1, 12L to 0), dao.positions)
    }

    @Test
    fun `moveItem ignores out-of-range indices`() = runTest {
        repository.moveItem(visible, fromIndex = 1, toIndex = 2)
        repository.moveItem(visible, fromIndex = 0, toIndex = -1)

        assertEquals(mapOf(10L to 0, 11L to 1, 12L to 2), dao.positions)
    }
}

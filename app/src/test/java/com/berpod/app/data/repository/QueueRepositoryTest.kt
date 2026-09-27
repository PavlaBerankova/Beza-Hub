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
    override suspend fun getPosition(queueEntryId: Long): Int? = positions[queueEntryId]
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

    @Test
    fun `second move with a stale list keeps positions unique`() = runTest {
        // Full queue A=20@0, B=21@1, C=22@2; the UI list is not refreshed between two quick taps.
        val dao = RecordingQueueDao(mapOf(20L to 0, 21L to 1, 22L to 2))
        val repository = QueueRepository(dao)
        val stale = listOf(
            queueItem(entryId = 20, position = 0, feedId = 1),
            queueItem(entryId = 21, position = 1, feedId = 1),
            queueItem(entryId = 22, position = 2, feedId = 1)
        )

        repository.moveItem(stale, fromIndex = 2, toIndex = 1) // C up: A@0, C@1, B@2
        repository.moveItem(stale, fromIndex = 1, toIndex = 0) // stale "B up"

        assertEquals(setOf(0, 1, 2), dao.positions.values.toSet())
    }
}

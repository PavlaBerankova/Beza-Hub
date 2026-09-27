package com.berpod.app.data.repository

import com.berpod.app.data.local.QueueDao
import com.berpod.app.data.local.QueueEntryEntity
import com.berpod.app.data.local.QueueItemWithEpisode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** In-memory queue: positions and episode per entry id; inherits the real transaction default methods. */
private class RecordingQueueDao(initial: Map<Long, Int>) : QueueDao {
    val positions = initial.toMutableMap()
    private val episodeIds = initial.keys.associateWith { it + 1000 }.toMutableMap()
    private val datesAdded = mutableMapOf<Long, Long>()

    override fun observeQueue(): Flow<List<QueueItemWithEpisode>> = emptyFlow()
    override suspend fun getMaxPosition(): Int = positions.values.maxOrNull() ?: -1
    override suspend fun isQueued(episodeId: Long): Boolean = episodeId in episodeIds.values
    override suspend fun insert(entry: QueueEntryEntity): Long {
        val id = (positions.keys.maxOrNull() ?: 0L) + 100
        positions[id] = entry.position
        episodeIds[id] = entry.episodeId
        datesAdded[id] = entry.dateAdded
        return id
    }
    override suspend fun deleteById(queueEntryId: Long) {
        positions.remove(queueEntryId)
        episodeIds.remove(queueEntryId)
    }
    override suspend fun updatePosition(queueEntryId: Long, position: Int) {
        positions[queueEntryId] = position
    }
    override suspend fun getOrderedIds(): List<Long> = positions.entries.sortedBy { it.value }.map { it.key }
    override suspend fun getById(queueEntryId: Long): QueueEntryEntity? =
        positions[queueEntryId]?.let { pos ->
            QueueEntryEntity(queueEntryId, episodeIds.getValue(queueEntryId), pos, datesAdded[queueEntryId] ?: 0L)
        }

    override suspend fun findIdByEpisode(episodeId: Long): Long? = episodeIds.entries.firstOrNull { it.value == episodeId }?.key

    fun order(): List<Long> = positions.entries.sortedBy { it.value }.map { it.key }
    fun episodeOrder(): List<Long> = order().map { episodeIds.getValue(it) }
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

    // Entries 1..4 hold episodes 1001..1004.
    private fun queueOf(vararg ids: Long) = RecordingQueueDao(ids.withIndex().associate { (i, id) -> id to i })

    @Test
    fun `removed entry can be restored to its place`() = runTest {
        val dao = queueOf(1, 2, 3)
        val repository = QueueRepository(dao)

        val removed = repository.removeFromQueue(2)!!
        assertEquals(listOf(1001L, 1003L), dao.episodeOrder())

        repository.restore(removed)
        assertEquals(listOf(1001L, 1002L, 1003L), dao.episodeOrder())
    }

    @Test
    fun `restore keeps the entry after its previous neighbour when the queue was reordered`() = runTest {
        val dao = queueOf(1, 2, 3, 4)
        val repository = QueueRepository(dao)

        val removed = repository.removeFromQueue(2)!! // neighbours 1 and 3
        repository.moveEntry(movedId = 3, afterId = null, beforeId = 1) // 3,1,4
        repository.restore(removed)

        assertEquals(listOf(1003L, 1001L, 1002L, 1004L), dao.episodeOrder())
        assertEquals(setOf(0, 1, 2, 3), dao.positions.values.toSet())
    }

    @Test
    fun `restore goes to the end when both neighbours are gone`() = runTest {
        val dao = queueOf(1, 2, 3, 4)
        val repository = QueueRepository(dao)

        val removed = repository.removeFromQueue(2)!!
        repository.removeFromQueue(1)
        repository.removeFromQueue(3)
        repository.restore(removed)

        assertEquals(listOf(1004L, 1002L), dao.episodeOrder())
    }

    @Test
    fun `restore does nothing when the episode is already queued again`() = runTest {
        val dao = queueOf(1, 2)
        val repository = QueueRepository(dao)

        val removed = repository.removeFromQueue(2)!!
        repository.restore(removed)
        repository.restore(removed)

        assertEquals(listOf(1001L, 1002L), dao.episodeOrder())
    }

    @Test
    fun `removing an unknown entry returns null`() = runTest {
        assertEquals(null, QueueRepository(queueOf(1)).removeFromQueue(99))
    }

    @Test
    fun `queueAtFront puts a new episode first`() = runTest {
        val dao = queueOf(1, 2)
        QueueRepository(dao).queueAtFront(episodeId = 5005)
        assertEquals(listOf(5005L, 1001L, 1002L), dao.episodeOrder())
    }

    @Test
    fun `queueAtFront leaves an already queued episode where it is`() = runTest {
        val dao = queueOf(1, 2)
        QueueRepository(dao).queueAtFront(episodeId = 1002)
        assertEquals(listOf(1001L, 1002L), dao.episodeOrder())
    }

    @Test
    fun `queueAtFront on an empty queue`() = runTest {
        val dao = RecordingQueueDao(emptyMap())
        QueueRepository(dao).queueAtFront(episodeId = 7)
        assertEquals(listOf(7L), dao.episodeOrder())
    }

    @Test
    fun `removing by episode can be undone`() = runTest {
        val dao = queueOf(1, 2, 3)
        val repository = QueueRepository(dao)

        val removed = repository.removeEpisode(1002)!!
        assertEquals(listOf(1001L, 1003L), dao.episodeOrder())
        repository.restore(removed)
        assertEquals(listOf(1001L, 1002L, 1003L), dao.episodeOrder())
        assertEquals(null, repository.removeEpisode(9999))
    }
}

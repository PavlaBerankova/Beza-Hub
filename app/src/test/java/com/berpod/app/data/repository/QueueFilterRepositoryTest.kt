package com.berpod.app.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class InMemoryStorage(private var stored: Long? = null) : QueueFilterStorage {
    var writes = 0
    override fun read(): Long? = stored
    override fun write(tagId: Long?) {
        writes++
        stored = tagId
    }
}

class QueueFilterRepositoryTest {

    @Test
    fun `the selected tag is remembered for the next run`() = runTest {
        val storage = InMemoryStorage()
        QueueFilterRepository(storage).select(7)

        assertEquals(7L, QueueFilterRepository(storage).selectedTagId.value)
    }

    @Test
    fun `choosing Všechny clears the stored tag`() = runTest {
        val storage = InMemoryStorage(stored = 7)
        val repository = QueueFilterRepository(storage)

        repository.select(null)

        assertEquals(null, repository.selectedTagId.value)
        assertEquals(null, QueueFilterRepository(storage).selectedTagId.value)
    }

    @Test
    fun `re-selecting the same tag writes nothing`() = runTest {
        val storage = InMemoryStorage(stored = 7)
        val repository = QueueFilterRepository(storage)

        repository.select(7)

        assertEquals(0, storage.writes)
    }
}

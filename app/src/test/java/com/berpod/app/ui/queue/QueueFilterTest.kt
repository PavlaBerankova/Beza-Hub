package com.berpod.app.ui.queue

import com.berpod.app.testutil.queueItem
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueFilterTest {

    private val items = listOf(
        queueItem(entryId = 10, position = 0, feedId = 1),
        queueItem(entryId = 11, position = 1, feedId = 2),
        queueItem(entryId = 12, position = 2, feedId = 1),
        queueItem(entryId = 13, position = 3, feedId = 3)
    )

    @Test
    fun `null filter returns the whole queue`() {
        assertEquals(items, filterQueueByTag(items, null))
    }

    @Test
    fun `keeps only items from tagged podcasts in queue order`() {
        assertEquals(listOf(10L, 12L, 13L), filterQueueByTag(items, setOf(1L, 3L)).map { it.queueEntryId })
    }

    @Test
    fun `empty set returns nothing`() {
        assertEquals(emptyList<Any>(), filterQueueByTag(items, emptySet()))
    }
}

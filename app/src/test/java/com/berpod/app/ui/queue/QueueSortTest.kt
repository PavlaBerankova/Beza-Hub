package com.berpod.app.ui.queue

import com.berpod.app.data.local.QueueItemWithEpisode
import com.berpod.app.testutil.episode
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueSortTest {
    private fun item(entryId: Long, published: Long?) = QueueItemWithEpisode(
        episode = episode(id = entryId, feedId = 1).copy(pubDateMillis = published),
        podcastTitle = "P",
        podcastImageUrl = null,
        queueEntryId = entryId,
        queuePosition = 0
    )

    private val items = listOf(item(1, 200), item(2, 300), item(3, null), item(4, 100))

    @Test
    fun `entries sort by publish date with undated ones last`() {
        assertEquals(listOf(2L, 1L, 4L, 3L), sortedQueueEntryIds(items, oldestFirst = false))
        assertEquals(listOf(4L, 1L, 2L, 3L), sortedQueueEntryIds(items, oldestFirst = true))
    }

    @Test
    fun `the current order is recognised or called custom`() {
        assertEquals(QueueOrder.Custom, queueOrderOf(items))
        val newest = sortedQueueEntryIds(items, false).map { id -> items.first { it.queueEntryId == id } }
        val oldest = sortedQueueEntryIds(items, true).map { id -> items.first { it.queueEntryId == id } }
        assertEquals(QueueOrder.NewestFirst, queueOrderOf(newest))
        assertEquals(QueueOrder.OldestFirst, queueOrderOf(oldest))
    }
}

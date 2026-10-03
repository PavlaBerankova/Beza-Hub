package com.berpod.app.ui.queue

import com.berpod.app.testutil.queueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueSummaryTest {

    private fun item(id: Long, durationSeconds: Int?, positionMs: Long = 0) = queueItem(id, id.toInt(), feedId = 1).let {
        it.copy(episode = it.episode.copy(durationSeconds = durationSeconds, lastPositionMs = positionMs))
    }

    @Test
    fun `czech plural of episodes`() {
        assertEquals("1 epizoda", episodeCountText(1))
        assertEquals("3 epizody", episodeCountText(3))
        assertEquals("5 epizod", episodeCountText(5))
    }

    @Test
    fun `summary adds up remaining time`() {
        val items = listOf(item(1, 1500, positionMs = 60_000), item(2, 900), item(3, 1320))
        // 24 + 15 + 22 min = 61 min
        assertEquals("3 epizody · 1 h 1 min", queueSummary(items))
    }

    @Test
    fun `summary without known durations shows only the count`() {
        assertEquals("1 epizoda", queueSummary(listOf(item(1, null))))
    }

    @Test
    fun `empty queue has no summary`() {
        assertNull(queueSummary(emptyList()))
    }
}

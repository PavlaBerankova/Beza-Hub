package com.berpod.app.ui.newepisodes

import com.berpod.app.data.local.EpisodeWithFeed
import com.berpod.app.testutil.episode
import org.junit.Assert.assertEquals
import org.junit.Test

class NewCountsTest {

    private fun item(id: Long, feedId: Long) = EpisodeWithFeed(episode(id, feedId), "Podcast $feedId", null)

    @Test
    fun `counts new episodes per podcast`() {
        val counts = newEpisodeCounts(listOf(item(1, 1), item(2, 1), item(3, 2)), emptySet())
        assertEquals(mapOf(1L to 2, 2L to 1), counts)
    }

    @Test
    fun `queued episodes are no longer new`() {
        val counts = newEpisodeCounts(listOf(item(1, 1), item(2, 1), item(3, 2)), setOf(2L, 3L))
        assertEquals(mapOf(1L to 1), counts)
    }

    @Test
    fun `no new episodes gives no counts`() {
        assertEquals(emptyMap<Long, Int>(), newEpisodeCounts(emptyList(), emptySet()))
    }
}

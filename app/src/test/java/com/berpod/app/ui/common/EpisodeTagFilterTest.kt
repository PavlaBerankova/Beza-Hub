package com.berpod.app.ui.common

import com.berpod.app.data.local.EpisodeWithFeed
import com.berpod.app.testutil.episode
import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeTagFilterTest {

    private fun ewf(id: Long, feedId: Long) = EpisodeWithFeed(episode(id, feedId), "Podcast $feedId", null)
    private val items = listOf(ewf(1, 10), ewf(2, 20), ewf(3, 10))

    @Test
    fun `null filter keeps everything`() {
        assertEquals(items, filterByPodcastIds(items, null) { it.episode.feedId })
    }

    @Test
    fun `keeps only episodes of tagged podcasts in order`() {
        assertEquals(listOf(1L, 3L), filterByPodcastIds(items, setOf(10L)) { it.episode.feedId }.map { it.episode.id })
        assertEquals(emptyList<EpisodeWithFeed>(), filterByPodcastIds(items, emptySet()) { it.episode.feedId })
    }
}

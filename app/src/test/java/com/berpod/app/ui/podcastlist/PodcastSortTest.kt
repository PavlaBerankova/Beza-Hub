package com.berpod.app.ui.podcastlist

import com.berpod.app.data.local.PodcastFeedEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class PodcastSortTest {

    private fun feed(id: Long, title: String, dateAdded: Long) = PodcastFeedEntity(
        id = id, rssUrl = "https://example.com/$id", title = title, imageUrl = null,
        description = null, dateAdded = dateAdded, lastFetchedAt = null
    )

    private val feeds = listOf(feed(1, "Čapek", 100), feed(2, "Zrcadlo", 300), feed(3, "Cesta", 200), feed(4, "adam", 50))

    @Test
    fun `date added puts the most recently added first`() {
        assertEquals(listOf(2L, 3L, 1L, 4L), sortedPodcasts(feeds, PodcastSort.DateAdded).map { it.id })
    }

    @Test
    fun `alphabetical follows Czech order and ignores case`() {
        // Czech: c < č < ... < z, so Cesta comes before Čapek.
        assertEquals(listOf(4L, 3L, 1L, 2L), sortedPodcasts(feeds, PodcastSort.Alphabetical).map { it.id })
    }
}

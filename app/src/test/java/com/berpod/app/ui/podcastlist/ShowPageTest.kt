package com.berpod.app.ui.podcastlist

import com.berpod.app.data.local.TagEntity
import com.berpod.app.testutil.episode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowPageTest {

    private fun ep(id: Long, positionMs: Long = 0, durationSeconds: Int? = 600) =
        episode(id = id, feedId = 1).copy(lastPositionMs = positionMs, durationSeconds = durationSeconds)

    @Test
    fun `header plays the newest episode when nothing is in progress`() {
        val result = pickHeaderEpisode(listOf(ep(3), ep(2), ep(1)))
        assertEquals(3L, result?.episode?.id)
        assertFalse(result!!.isResume)
    }

    @Test
    fun `header resumes the newest episode that is in progress`() {
        val result = pickHeaderEpisode(listOf(ep(3), ep(2, positionMs = 60_000), ep(1, positionMs = 30_000)))
        assertEquals(2L, result?.episode?.id)
        assertTrue(result!!.isResume)
    }

    @Test
    fun `finished episodes are not resumed`() {
        val result = pickHeaderEpisode(listOf(ep(3), ep(2, positionMs = 595_000)))
        assertEquals(3L, result?.episode?.id)
        assertFalse(result!!.isResume)
    }

    @Test
    fun `no episodes gives no header episode`() {
        assertNull(pickHeaderEpisode(emptyList()))
    }

    @Test
    fun `description snippet strips html and collapses whitespace`() {
        assertEquals(
            "Díl o AI & o tom, co přijde.",
            descriptionSnippet("<p>Díl o <b>AI</b> &amp; o tom,\n\n co&nbsp;přijde.</p>")
        )
        assertNull(descriptionSnippet("<p> </p>"))
        assertNull(descriptionSnippet(null))
    }

    @Test
    fun `remaining time shows what is left of an episode in progress`() {
        assertEquals("zbývá 5 min", remainingText(ep(1, positionMs = 300_000, durationSeconds = 600)))
        assertEquals("10 min", remainingText(ep(1)))
        assertNull(remainingText(ep(1, durationSeconds = null)))
    }

    @Test
    fun `tag names of the podcast in the tags' order, none when untagged`() {
        val tags = listOf(TagEntity(1, "Health"), TagEntity(2, "IT Tech"), TagEntity(3, "News"))
        assertEquals(listOf("Health", "News"), tagNamesFor(tags, assignedIds = setOf(3L, 1L)))
        assertEquals(emptyList<String>(), tagNamesFor(tags, assignedIds = emptySet()))
    }

    @Test
    fun `episode status is played, in progress or unplayed`() {
        assertEquals(EpisodeStatus.Unplayed, episodeStatus(ep(1)))
        assertEquals(EpisodeStatus.InProgress, episodeStatus(ep(1, positionMs = 60_000)))
        assertEquals(EpisodeStatus.Played, episodeStatus(ep(1).copy(playedAtMs = 5L)))
        // A played episode stays played even if a stale position is stored.
        assertEquals(EpisodeStatus.Played, episodeStatus(ep(1, positionMs = 60_000).copy(playedAtMs = 5L)))
    }

    @Test
    fun `header skips played episodes when picking the latest`() {
        val result = pickHeaderEpisode(listOf(ep(3).copy(playedAtMs = 1L), ep(2), ep(1)))
        assertEquals(2L, result?.episode?.id)
    }
}

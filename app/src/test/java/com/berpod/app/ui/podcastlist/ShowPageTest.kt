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

    @Test
    fun `header colour prefers the vibrant swatch and falls back down the list`() {
        assertEquals(1, pickHeaderColor(vibrant = 1, darkVibrant = 2, muted = 3, dominant = 4))
        assertEquals(2, pickHeaderColor(vibrant = null, darkVibrant = 2, muted = 3, dominant = 4))
        assertEquals(3, pickHeaderColor(vibrant = null, darkVibrant = null, muted = 3, dominant = 4))
        assertEquals(4, pickHeaderColor(vibrant = null, darkVibrant = null, muted = null, dominant = 4))
        assertNull(pickHeaderColor(null, null, null, null))
    }

    private val zone = java.time.ZoneId.of("Europe/Prague")
    private fun day(year: Int, month: Int, dayOfMonth: Int): Long =
        java.time.ZonedDateTime.of(year, month, dayOfMonth, 12, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `a weekly podcast on the same day is named after that day`() {
        val fridays = listOf(day(2026, 9, 25), day(2026, 9, 18), day(2026, 9, 11), day(2026, 9, 4), day(2026, 8, 28))
        assertEquals("každý pátek", publishingFrequency(fridays, zone))
    }

    @Test
    fun `a weekly podcast on varying days is just weekly`() {
        val dates = listOf(day(2026, 9, 25), day(2026, 9, 17), day(2026, 9, 12), day(2026, 9, 2), day(2026, 8, 29))
        assertEquals("jednou týdně", publishingFrequency(dates, zone))
    }

    @Test
    fun `other rhythms are told by the typical gap`() {
        assertEquals("denně", publishingFrequency((0 until 8).map { day(2026, 9, 25 - it) }, zone))
        // 25.9.2026 is a Friday; 21.9. a Monday: five weekdays in a row, then the weekend off.
        assertEquals("ve všední dny", publishingFrequency(listOf(25, 24, 23, 22, 21, 18, 17, 16, 15, 14).map { day(2026, 9, it) }, zone))
        assertEquals("několikrát týdně", publishingFrequency(listOf(day(2026, 9, 25), day(2026, 9, 22), day(2026, 9, 19), day(2026, 9, 16)), zone))
        assertEquals("jednou za 2 týdny", publishingFrequency(listOf(day(2026, 9, 25), day(2026, 9, 11), day(2026, 8, 28), day(2026, 8, 14)), zone))
        assertEquals("jednou měsíčně", publishingFrequency(listOf(day(2026, 9, 25), day(2026, 8, 25), day(2026, 7, 25), day(2026, 6, 25)), zone))
        assertEquals("nepravidelně", publishingFrequency(listOf(day(2026, 9, 25), day(2026, 6, 1), day(2026, 1, 10)), zone))
    }

    @Test
    fun `too few dated episodes tell nothing`() {
        assertNull(publishingFrequency(listOf(day(2026, 9, 25), day(2026, 9, 18)), zone))
        assertNull(publishingFrequency(listOf(day(2026, 9, 25), null, null, day(2026, 9, 11)), zone))
    }
}

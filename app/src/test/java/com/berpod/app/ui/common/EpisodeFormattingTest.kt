package com.berpod.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodeFormattingTest {

    @Test
    fun `formatDuration returns null for null or non-positive input`() {
        assertNull(formatDuration(null))
        assertNull(formatDuration(0))
        assertNull(formatDuration(-5))
    }

    @Test
    fun `formatDuration renders sub-minute durations`() {
        assertEquals("<1 min", formatDuration(30))
    }

    @Test
    fun `formatDuration renders minutes only`() {
        assertEquals("1 min", formatDuration(60))
        assertEquals("42 min", formatDuration(2520))
        assertEquals("58 min", formatDuration(3480))
    }

    @Test
    fun `formatDuration renders hours and minutes`() {
        assertEquals("1 h 0 min", formatDuration(3600))
        assertEquals("1 h 7 min", formatDuration(4020))
    }

    @Test
    fun `episodeProgressFraction is null when nothing has been played`() {
        assertNull(episodeProgressFraction(lastPositionMs = 0L, durationSeconds = 100))
    }

    @Test
    fun `episodeProgressFraction is null when duration is unknown or zero`() {
        assertNull(episodeProgressFraction(lastPositionMs = 5_000L, durationSeconds = null))
        assertNull(episodeProgressFraction(lastPositionMs = 5_000L, durationSeconds = 0))
    }

    @Test
    fun `episodeProgressFraction computes the played fraction`() {
        assertEquals(0.5f, episodeProgressFraction(lastPositionMs = 30_000L, durationSeconds = 60)!!, 0.001f)
    }

    @Test
    fun `episodeProgressFraction is coerced to at most 1`() {
        assertEquals(1f, episodeProgressFraction(lastPositionMs = 999_999L, durationSeconds = 60)!!, 0.001f)
    }
}

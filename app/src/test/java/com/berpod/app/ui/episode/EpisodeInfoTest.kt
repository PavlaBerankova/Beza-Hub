package com.berpod.app.ui.episode

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodeInfoTest {
    private val zone = ZoneId.of("Europe/Prague")
    private fun at(year: Int, month: Int, day: Int, hour: Int = 12) =
        ZonedDateTime.of(year, month, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    private val now = at(2026, 10, 3, 10)

    @Test
    fun `age reads in words`() {
        assertEquals("dnes", episodeAgeText(at(2026, 10, 3, 6), now, zone))
        assertEquals("včera", episodeAgeText(at(2026, 10, 2, 23), now, zone))
        assertEquals("před 3 dny", episodeAgeText(at(2026, 9, 30), now, zone))
        assertEquals("před týdnem", episodeAgeText(at(2026, 9, 24), now, zone))
        assertEquals("před 3 týdny", episodeAgeText(at(2026, 9, 10), now, zone))
        assertEquals("před měsícem", episodeAgeText(at(2026, 8, 25), now, zone))
        assertEquals("před 5 měsíci", episodeAgeText(at(2026, 5, 1), now, zone))
        assertEquals("před rokem", episodeAgeText(at(2025, 6, 1), now, zone))
        assertEquals("před 3 lety", episodeAgeText(at(2023, 6, 1), now, zone))
    }

    @Test
    fun `age is unknown without a date or for the future`() {
        assertNull(episodeAgeText(null, now, zone))
        assertNull(episodeAgeText(at(2026, 10, 9), now, zone))
    }

    @Test
    fun `file size is shown in decimal units`() {
        assertEquals("41,6 MB", formatFileSize(41_600_000))
        assertEquals("812 kB", formatFileSize(812_345))
        assertEquals("1,2 GB", formatFileSize(1_234_000_000))
        assertNull(formatFileSize(null))
        assertNull(formatFileSize(0))
    }
}

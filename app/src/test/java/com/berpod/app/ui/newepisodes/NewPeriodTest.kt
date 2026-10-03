package com.berpod.app.ui.newepisodes

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class NewPeriodTest {
    private val zone = ZoneId.of("Europe/Prague")
    private fun at(month: Int, day: Int, hour: Int = 12): Long =
        ZonedDateTime.of(2026, month, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    // Now is Saturday 3 October 2026, 10:30.
    private val now = ZonedDateTime.of(2026, 10, 3, 10, 30, 0, 0, zone).toInstant().toEpochMilli()
    private val published = listOf(
        "this morning" to at(10, 3, 6),
        "yesterday" to at(10, 2),
        "five days ago" to at(9, 28),
        "ten days ago" to at(9, 23),
        "three weeks ago" to at(9, 12),
        "two months ago" to at(8, 1)
    )

    private fun shown(period: NewPeriod) = filterByPeriod(published, period, now, zone) { it.second }.map { it.first }

    @Test
    fun `today means since midnight`() = assertEquals(listOf("this morning"), shown(NewPeriod.Today))

    @Test
    fun `week and two weeks count back from now`() {
        assertEquals(listOf("this morning", "yesterday", "five days ago"), shown(NewPeriod.Week))
        assertEquals(listOf("this morning", "yesterday", "five days ago", "ten days ago"), shown(NewPeriod.TwoWeeks))
    }

    @Test
    fun `month reaches thirty days back`() =
        assertEquals(listOf("this morning", "yesterday", "five days ago", "ten days ago", "three weeks ago"), shown(NewPeriod.Month))

    @Test
    fun `all keeps everything and a limit drops undated episodes`() {
        assertEquals(published.size, shown(NewPeriod.All).size)
        assertEquals(emptyList<Long?>(), filterByPeriod(listOf<Long?>(null), NewPeriod.Month, now, zone) { it })
        assertEquals(1, filterByPeriod(listOf<Long?>(null), NewPeriod.All, now, zone) { it }.size)
    }

    @Test
    fun `sorting reverses newest first and keeps undated episodes last`() {
        val newestFirst = listOf(30L, 20L, 10L, null)
        assertEquals(newestFirst, sortByPublished(newestFirst, oldestFirst = false) { it })
        assertEquals(listOf(10L, 20L, 30L, null), sortByPublished(newestFirst, oldestFirst = true) { it })
    }
}

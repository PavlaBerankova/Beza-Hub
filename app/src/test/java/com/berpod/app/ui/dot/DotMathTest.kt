package com.berpod.app.ui.dot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.hypot

class DotMathTest {

    private val prague = ZoneId.of("Europe/Prague")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, prague).toInstant().toEpochMilli()

    @Test
    fun `progress cells split into played, one current and rest`() {
        val cells = dotProgressCells(0.3f, 10)
        assertEquals(3, cells.count { it == CellRole.Played })
        assertEquals(CellRole.Current, cells[3])
        assertEquals(6, cells.count { it == CellRole.Rest })
    }

    @Test
    fun `progress cells clamp and handle missing progress`() {
        assertEquals(List(5) { CellRole.Played }, dotProgressCells(1.4f, 5))
        assertEquals(List(5) { CellRole.Rest }, dotProgressCells(null, 5))
        assertEquals(CellRole.Current, dotProgressCells(0f, 5).first())
    }

    @Test
    fun `ring dots sit on the circle starting at the top`() {
        val dots = ringDots(count = 56, center = 140f, radius = 132f, fraction = 0.25f)
        assertEquals(56, dots.size)
        assertTrue(dots.all { abs(hypot(it.x - 140f, it.y - 140f) - 132f) < 0.01f })
        assertEquals(140f, dots[0].x, 0.01f)
        assertEquals(8f, dots[0].y, 0.01f)
        assertEquals(14, dots.count { it.role == CellRole.Played })
    }

    @Test
    fun `wave heights are deterministic and in range`() {
        val a = waveHeights(seed = 42)
        assertEquals(a, waveHeights(seed = 42))
        assertEquals(36, a.size)
        assertTrue(a.all { it in 2..9 })
        assertTrue(a != waveHeights(seed = 43))
    }

    @Test
    fun `wave seek fraction clamps to the wave`() {
        assertEquals(0.5f, waveSeekFraction(50f, 100f), 0.001f)
        assertEquals(0f, waveSeekFraction(-10f, 100f), 0.001f)
        assertEquals(1f, waveSeekFraction(130f, 100f), 0.001f)
        assertEquals(0f, waveSeekFraction(10f, 0f), 0.001f)
    }

    @Test
    fun `dates format the Dot Matrix way`() {
        assertEquals("14.09.2026", formatDotDate(at(2026, 9, 14), prague))
        assertEquals("27.09", formatDayMonth(at(2026, 9, 27), prague))
        assertEquals("2026", formatYear(at(2026, 9, 27), prague))
        assertEquals(null, formatDotDate(null, prague))
    }

    @Test
    fun `today uses the device zone`() {
        val now = at(2026, 9, 27, h = 0) + 30 * 60_000 // 00:30 local
        assertTrue(isPublishedToday(at(2026, 9, 27, h = 0) + 5 * 60_000, now, prague))
        assertFalse(isPublishedToday(at(2026, 9, 26, h = 23), now, prague))
        assertFalse(isPublishedToday(null, now, prague))
    }

    @Test
    fun `recent means within seven days`() {
        val now = at(2026, 9, 27)
        assertTrue(isRecentEpisode(at(2026, 9, 21), now))
        assertFalse(isRecentEpisode(at(2026, 9, 19), now))
        assertFalse(isRecentEpisode(null, now))
    }

    @Test
    fun `czech uppercase`() {
        assertEquals("VINOHRADSKÁ 12 · ZBÝVÁ", "Vinohradská 12 · zbývá".upperCs())
    }

    @Test
    fun `czech plural of subscriptions`() {
        assertEquals("1 ODBĚR", subscriptionCountText(1))
        assertEquals("3 ODBĚRY", subscriptionCountText(3))
        assertEquals("5 ODBĚRŮ", subscriptionCountText(5))
        assertEquals("0 ODBĚRŮ", subscriptionCountText(0))
    }
}

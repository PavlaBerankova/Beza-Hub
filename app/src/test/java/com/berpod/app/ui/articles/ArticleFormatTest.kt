package com.berpod.app.ui.articles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ArticleFormatTest {

    private val prague = ZoneId.of("Europe/Prague")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, prague).toInstant().toEpochMilli()

    @Test
    fun `relative time goes from minutes to hours, yesterday and dates`() {
        val now = at(2026, 9, 28, 12)
        assertEquals("PRÁVĚ TEĎ", relativeTimeLabel(now - 20_000, now, prague))
        assertEquals("PŘED 1 MIN", relativeTimeLabel(at(2026, 9, 28, 11, 59), now, prague))
        assertEquals("PŘED 25 MIN", relativeTimeLabel(at(2026, 9, 28, 11, 35), now, prague))
        assertEquals("PŘED 3 H", relativeTimeLabel(at(2026, 9, 28, 9), now, prague))
        assertEquals("VČERA", relativeTimeLabel(at(2026, 9, 27, 8), now, prague))
        assertEquals("25.09.", relativeTimeLabel(at(2026, 9, 25, 8), now, prague))
        assertEquals("25.09.2025", relativeTimeLabel(at(2025, 9, 25, 8), now, prague))
        assertNull(relativeTimeLabel(null, now, prague))
    }

    @Test
    fun `yesterday means the calendar day, even under 24 hours ago`() {
        assertEquals("VČERA", relativeTimeLabel(at(2026, 9, 27, 23), at(2026, 9, 28, 6), prague))
    }

    @Test
    fun `reading time counts words at 200 a minute, at least one`() {
        val words = List(900) { "slovo" }.joinToString(" ")
        assertEquals(5, readingMinutes("<p>$words</p>"))
        assertEquals(1, readingMinutes("<p>Krátké.</p>"))
        assertNull(readingMinutes(null))
    }

    @Test
    fun `initials take the first letters of two words`() {
        assertEquals("TK", initials("Týdeník Kód"))
        assertEquals("LU", initials("lupa.cz"))
        assertEquals("ZV", initials("Zprávy z vesmíru"))
        assertEquals("?", initials("  "))
    }

    @Test
    fun `czech plural forms`() {
        assertEquals("1 ZDROJ", czechCount(1, "zdroj", "zdroje", "zdrojů"))
        assertEquals("3 ZDROJE", czechCount(3, "zdroj", "zdroje", "zdrojů"))
        assertEquals("6 ZDROJŮ", czechCount(6, "zdroj", "zdroje", "zdrojů"))
        assertEquals("0 ZDROJŮ", czechCount(0, "zdroj", "zdroje", "zdrojů"))
    }

    @Test
    fun `next unread is the next unread one below, then from the top`() {
        val order = listOf(1L, 2L, 3L, 4L, 5L)
        val unread = setOf(2L, 4L)
        assertEquals(4L, nextUnreadId(order, unread, currentId = 3))
        assertEquals(2L, nextUnreadId(order, unread, currentId = 4))
        assertEquals(null, nextUnreadId(order, setOf(3L), currentId = 3))
        assertEquals(2L, nextUnreadId(order, unread, currentId = 99))
    }

    @Test
    fun `font size cycles through the reader sizes, unknown starts over`() {
        assertEquals(20, nextFontSize(18))
        assertEquals(16, nextFontSize(22))
        assertEquals(16, nextFontSize(17))
    }

    @Test
    fun `unread articles come first, each group keeping the newest-first order`() {
        val items = listOf("a" to true, "b" to false, "c" to true, "d" to false)
        val (unread, read) = unreadFirst(items) { it.second }
        assertEquals(listOf("a", "c"), unread.map { it.first })
        assertEquals(listOf("b", "d"), read.map { it.first })
    }
}

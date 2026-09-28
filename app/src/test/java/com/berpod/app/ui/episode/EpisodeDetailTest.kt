package com.berpod.app.ui.episode

import com.berpod.app.data.local.EpisodeEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeDetailTest {

    private fun episode(lastPositionMs: Long = 0, playedAtMs: Long? = null) = EpisodeEntity(
        id = 1, feedId = 1, guid = "g", title = "T", audioUrl = "u", pubDateMillis = null,
        durationSeconds = 3600, description = null, imageUrl = null, dateFetched = 0,
        lastPositionMs = lastPositionMs, playedAtMs = playedAtMs
    )

    @Test
    fun `chapters come from lines starting with a time, html or plain text`() {
        val html = "<p>Intro text</p><p>0:00 Úvod<br>4:12 – Hodinky<br>1:08:30 Závěr &amp; otázky</p>"
        assertEquals(
            listOf(Chapter(0, "Úvod"), Chapter(252_000, "Hodinky"), Chapter(4_110_000, "Závěr & otázky")),
            parseChapters(html)
        )
        assertEquals(
            listOf(Chapter(65_000, "První"), Chapter(600_000, "Druhá")),
            parseChapters("Popis\n(01:05) První\n[10:00] - Druhá")
        )
    }

    @Test
    fun `chapters glued into one line are split at each time`() {
        val html = "<p>Intro.</p><p><br></p><p>00:00 Úvod – o čem bude díl00:59 Co je vibe coding?02:21 Škála: A → B10:38 Oprava</p>"
        assertEquals(
            listOf(Chapter(0, "Úvod – o čem bude díl"), Chapter(59_000, "Co je vibe coding?"), Chapter(141_000, "Škála: A → B"), Chapter(638_000, "Oprava")),
            parseChapters(html)
        )
    }

    @Test
    fun `glued chapter times get a line break for display, other times stay`() {
        assertEquals(
            "<p>00:00 Úvod<br>00:59 Co je?<br>02:21 Škála</p>",
            unglueChapterTimes("<p>00:00 Úvod00:59 Co je?02:21 Škála</p>")
        )
        assertEquals("Spoluautor podcastu 10:35. Ve 06:00 hotovo", unglueChapterTimes("Spoluautor podcastu 10:35. Ve 06:00 hotovo"))
    }

    @Test
    fun `no chapters from a single time, times inside sentences or out of order`() {
        assertEquals(emptyList<Chapter>(), parseChapters("4:12 Jen jedna"))
        assertEquals(emptyList<Chapter>(), parseChapters("Spoluautor podcastu 10:35. Aktualizováno 25.9. 06:00"))
        assertEquals(emptyList<Chapter>(), parseChapters("10:00 Druhá\n5:00 První"))
        assertEquals(emptyList<Chapter>(), parseChapters(null))
    }

    @Test
    fun `current chapter is the last one started`() {
        val chapters = listOf(Chapter(0, "a"), Chapter(60_000, "b"), Chapter(120_000, "c"))
        assertEquals(0, currentChapterIndex(chapters, 59_999))
        assertEquals(1, currentChapterIndex(chapters, 60_000))
        assertEquals(2, currentChapterIndex(chapters, 500_000))
        assertEquals(-1, currentChapterIndex(listOf(Chapter(30_000, "late")), 10_000))
    }

    @Test
    fun `primary action follows the episode state`() {
        assertEquals(PrimaryAction.Play, primaryAction(episode(), isCurrent = false, isPlaying = false))
        assertEquals(PrimaryAction.Resume, primaryAction(episode(lastPositionMs = 5_000), isCurrent = false, isPlaying = false))
        assertEquals(PrimaryAction.PlayAgain, primaryAction(episode(playedAtMs = 1), isCurrent = false, isPlaying = false))
        assertEquals(PrimaryAction.Pause, primaryAction(episode(), isCurrent = true, isPlaying = true))
        assertEquals(PrimaryAction.Resume, primaryAction(episode(), isCurrent = true, isPlaying = false))
    }
}

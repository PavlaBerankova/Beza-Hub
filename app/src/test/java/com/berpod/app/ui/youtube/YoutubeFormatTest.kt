package com.berpod.app.ui.youtube

import com.berpod.app.data.local.VideoWithChannel
import com.berpod.app.data.local.YoutubeVideoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class YoutubeFormatTest {

    private val prague = ZoneId.of("Europe/Prague")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, prague).toInstant().toEpochMilli()

    private fun video(id: Long, pubDateMillis: Long?, durationMs: Long? = null, lastPositionMs: Long = 0) = VideoWithChannel(
        video = YoutubeVideoEntity(
            id = id, feedId = 1, guid = "g$id", videoId = "yt$id", title = "T$id",
            videoUrl = "https://youtube.com/watch?v=yt$id", thumbnailUrl = null, description = null,
            pubDateMillis = pubDateMillis, dateFetched = 0, durationMs = durationMs, lastPositionMs = lastPositionMs
        ),
        channelTitle = "C", channelThumbnailUrl = null
    )

    @Test
    fun `videos group under DNES, VČERA, then a date label, keeping their own order inside each day`() {
        val now = at(2026, 9, 28, 12)
        val videos = listOf(
            video(1, at(2026, 9, 28, 9)),
            video(2, at(2026, 9, 28, 8)),
            video(3, at(2026, 9, 27, 20)),
            video(4, at(2026, 9, 25, 10))
        )

        val groups = groupByDay(videos, now, prague)

        assertEquals(listOf("DNES", "VČERA", "25.09."), groups.map { it.first })
        assertEquals(listOf(1L, 2L), groups[0].second.map { it.video.id })
        assertEquals(listOf(3L), groups[1].second.map { it.video.id })
        assertEquals(listOf(4L), groups[2].second.map { it.video.id })
    }

    @Test
    fun `a date in another year gets the year in its label`() {
        val now = at(2026, 9, 28, 12)
        val groups = groupByDay(listOf(video(1, at(2025, 9, 25, 10))), now, prague)
        assertEquals(listOf("25.09.2025"), groups.map { it.first })
    }

    @Test
    fun `videos without a known date still group, without crashing`() {
        val now = at(2026, 9, 28, 12)
        val groups = groupByDay(listOf(video(1, null)), now, prague)
        assertEquals(1, groups.size)
        assertEquals(listOf(1L), groups.single().second.map { it.video.id })
    }

    @Test
    fun `remaining text mirrors the episode one, null until duration is known`() {
        assertNull(remainingVideoText(video(1, null).video))
        assertEquals("24 min", remainingVideoText(video(1, null, durationMs = 24 * 60_000L).video))
        assertEquals("zbývá 14 min", remainingVideoText(video(1, null, durationMs = 24 * 60_000L, lastPositionMs = 10 * 60_000L).video))
    }
}

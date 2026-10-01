package com.berpod.app.ui.youtube.queue

import com.berpod.app.data.local.QueueVideoItem
import com.berpod.app.data.local.YoutubeVideoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoQueueSummaryTest {

    private fun item(id: Long, durationMs: Long?, positionMs: Long = 0) = QueueVideoItem(
        video = YoutubeVideoEntity(
            id = id, feedId = 1, guid = "g$id", videoId = "yt$id", title = "T$id",
            videoUrl = "https://youtube.com/watch?v=yt$id", thumbnailUrl = null, description = null,
            pubDateMillis = null, dateFetched = 0, durationMs = durationMs, lastPositionMs = positionMs
        ),
        channelTitle = "C", channelThumbnailUrl = null, queueEntryId = id, queuePosition = 0
    )

    @Test
    fun `czech plural of videos`() {
        assertEquals("1 video", videoCountText(1))
        assertEquals("3 videa", videoCountText(3))
        assertEquals("5 videí", videoCountText(5))
    }

    @Test
    fun `total adds up remaining time across the queue`() {
        val items = listOf(item(1, 1_500_000, positionMs = 60_000), item(2, 900_000), item(3, 1_320_000))
        // 24 + 15 + 22 min = 61 min
        assertEquals("1 h 1 min", videoQueueTotalText(items))
    }

    @Test
    fun `total is null when no duration is known yet`() {
        assertNull(videoQueueTotalText(listOf(item(1, null))))
    }

    @Test
    fun `total is null for an empty queue`() {
        assertNull(videoQueueTotalText(emptyList()))
    }
}

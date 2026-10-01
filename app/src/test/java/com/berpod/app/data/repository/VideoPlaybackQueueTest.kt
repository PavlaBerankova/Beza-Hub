package com.berpod.app.data.repository

import com.berpod.app.data.local.QueueVideoItem
import com.berpod.app.data.local.YoutubeVideoEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPlaybackQueueTest {

    private fun queueItem(videoId: Long, feedId: Long) = QueueVideoItem(
        video = YoutubeVideoEntity(
            id = videoId, feedId = feedId, guid = "g$videoId", videoId = "yt$videoId", title = "T",
            videoUrl = "https://youtube.com/watch?v=yt$videoId", thumbnailUrl = null, description = null,
            pubDateMillis = null, dateFetched = 0
        ),
        channelTitle = "C$feedId", channelThumbnailUrl = null, queueEntryId = videoId, queuePosition = 0
    )

    @Test
    fun `filtering the video queue order keeps only the given channels, or everything when null`() {
        val queue = listOf(queueItem(1, feedId = 10), queueItem(2, feedId = 20), queueItem(3, feedId = 10))

        assertEquals(listOf(1L, 3L), filterVideoQueueOrder(queue, setOf(10L)).map { it.video.id })
        assertEquals(listOf(1L, 2L, 3L), filterVideoQueueOrder(queue, null).map { it.video.id })
        assertEquals(emptyList<Long>(), filterVideoQueueOrder(queue, setOf(99L)).map { it.video.id })
    }
}

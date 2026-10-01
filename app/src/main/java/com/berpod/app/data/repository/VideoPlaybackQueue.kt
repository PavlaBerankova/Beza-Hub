package com.berpod.app.data.repository

import com.berpod.app.data.local.QueueVideoItem

/** Queue entries whose channel is in [channelIds], in queue order; null means no filter (everything). */
fun filterVideoQueueOrder(queue: List<QueueVideoItem>, channelIds: Set<Long>?): List<QueueVideoItem> =
    if (channelIds == null) queue else queue.filter { it.video.feedId in channelIds }

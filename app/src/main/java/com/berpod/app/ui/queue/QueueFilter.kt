package com.berpod.app.ui.queue

import com.berpod.app.data.local.QueueItemWithEpisode

/** Queue entries whose podcast is in [podcastIds], in queue order; null means no filter. */
fun filterQueueByTag(items: List<QueueItemWithEpisode>, podcastIds: Set<Long>?): List<QueueItemWithEpisode> =
    if (podcastIds == null) items else items.filter { it.episode.feedId in podcastIds }

package com.berpod.app.ui.queue

import com.berpod.app.data.local.QueueItemWithEpisode
import com.berpod.app.ui.common.filterByPodcastIds

/** Queue entries whose podcast is in [podcastIds], in queue order; null means no filter. */
fun filterQueueByTag(items: List<QueueItemWithEpisode>, podcastIds: Set<Long>?): List<QueueItemWithEpisode> =
    filterByPodcastIds(items, podcastIds) { it.episode.feedId }

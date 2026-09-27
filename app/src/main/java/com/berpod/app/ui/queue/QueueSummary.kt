package com.berpod.app.ui.queue

import com.berpod.app.data.local.QueueItemWithEpisode
import com.berpod.app.ui.common.formatDuration
import com.berpod.app.ui.podcastlist.remainingSeconds

/** "1 epizoda", "3 epizody", "5 epizod". */
fun episodeCountText(count: Int): String = when {
    count == 1 -> "1 epizoda"
    count in 2..4 -> "$count epizody"
    else -> "$count epizod"
}

/** "3 epizody · celkem 1 h 1 min" for the listed entries; null when there are none. */
fun queueSummary(items: List<QueueItemWithEpisode>): String? {
    if (items.isEmpty()) return null
    val total = items.sumOf { remainingSeconds(it.episode) ?: 0 }
    val totalText = formatDuration(total)
    return episodeCountText(items.size) + (totalText?.let { " · celkem $it" } ?: "")
}

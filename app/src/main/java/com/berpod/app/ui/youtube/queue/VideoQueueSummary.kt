package com.berpod.app.ui.youtube.queue

import com.berpod.app.data.local.QueueVideoItem
import com.berpod.app.ui.common.formatDuration
import com.berpod.app.ui.youtube.videoRemainingSeconds

/** "1 video", "3 videa", "5 videí". */
fun videoCountText(count: Int): String = when {
    count == 1 -> "1 video"
    count in 2..4 -> "$count videa"
    else -> "$count videí"
}

/** Total time left across [items], e.g. "1 h 1 min"; null when no duration is known yet. */
fun videoQueueTotalText(items: List<QueueVideoItem>): String? {
    val total = items.sumOf { videoRemainingSeconds(it.video) ?: 0 }
    return if (total > 0) formatDuration(total) else null
}

package com.berpod.app.ui.queue

import com.berpod.app.data.local.QueueItemWithEpisode

/** How the queue is ordered by publish date. [Custom] is any hand-made order. */
enum class QueueOrder { NewestFirst, OldestFirst, Custom }

/** The queue entry ids sorted by publish date, newest or oldest first; undated episodes stay at the end. */
fun sortedQueueEntryIds(items: List<QueueItemWithEpisode>, oldestFirst: Boolean): List<Long> {
    val (dated, undated) = items.partition { it.episode.pubDateMillis != null }
    val sorted = if (oldestFirst) dated.sortedBy { it.episode.pubDateMillis } else dated.sortedByDescending { it.episode.pubDateMillis }
    return (sorted + undated).map { it.queueEntryId }
}

/** Which of the two date orders the queue currently is in, or [QueueOrder.Custom]; a queue of 0-1 episodes reads as newest first. */
fun queueOrderOf(items: List<QueueItemWithEpisode>): QueueOrder {
    val ids = items.map { it.queueEntryId }
    return when (ids) {
        sortedQueueEntryIds(items, oldestFirst = false) -> QueueOrder.NewestFirst
        sortedQueueEntryIds(items, oldestFirst = true) -> QueueOrder.OldestFirst
        else -> QueueOrder.Custom
    }
}

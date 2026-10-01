package com.berpod.app.data.repository

import com.berpod.app.data.local.QueueItemWithEpisode

/** Queue entry (episode id) after [currentEpisodeId]; the first entry if the current one isn't queued. */
fun nextInQueue(order: List<Long>, currentEpisodeId: Long?): Long? {
    val index = order.indexOf(currentEpisodeId)
    return if (index < 0) order.firstOrNull { it != currentEpisodeId } else order.getOrNull(index + 1)
}

/** Queue entry (episode id) before [currentEpisodeId], or null. */
fun previousInQueue(order: List<Long>, currentEpisodeId: Long?): Long? {
    val index = order.indexOf(currentEpisodeId)
    return if (index <= 0) null else order[index - 1]
}

enum class PreviousAction { Restart, PlayPrevious }

/** First press restarts the episode; a second press within [windowMs] goes to the previous entry. */
fun previousAction(lastPressAtMs: Long?, nowMs: Long, windowMs: Long = 3_000): PreviousAction =
    if (lastPressAtMs != null && nowMs - lastPressAtMs in 0..windowMs) PreviousAction.PlayPrevious else PreviousAction.Restart

val SPEEDS = listOf(0.8f, 1.0f, 1.2f, 1.5f, 2.0f)

/** The speed after [current] in [SPEEDS]; an unknown value goes to 1.0. */
fun nextSpeed(current: Float): Float {
    val index = SPEEDS.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }
    return if (index < 0) 1.0f else SPEEDS[(index + 1) % SPEEDS.size]
}

sealed interface SleepTimer {
    data class At(val endsAtMs: Long) : SleepTimer
    data object EndOfEpisode : SleepTimer
}

fun sleepRemainingMs(timer: SleepTimer.At, nowMs: Long): Long = (timer.endsAtMs - nowMs).coerceAtLeast(0L)

/** Volume during the fade-out: 1 at the start, 0 after [fadeMs]. */
fun fadeVolume(elapsedMs: Long, fadeMs: Long = 5_000): Float = (1f - elapsedMs.toFloat() / fadeMs).coerceIn(0f, 1f)

/** Position after skipping [deltaMs], kept within the episode (duration ≤ 0 = unknown, only 0 is enforced). */
fun skipTarget(positionMs: Long, deltaMs: Long, durationMs: Long): Long {
    val target = (positionMs + deltaMs).coerceAtLeast(0L)
    return if (durationMs > 0) target.coerceAtMost(durationMs) else target
}

/** Queue entries whose podcast is in [podcastIds], in queue order; null means no filter (everything). */
fun filterQueueOrder(queue: List<QueueItemWithEpisode>, podcastIds: Set<Long>?): List<QueueItemWithEpisode> =
    if (podcastIds == null) queue else queue.filter { it.episode.feedId in podcastIds }

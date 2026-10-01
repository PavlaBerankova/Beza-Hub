package com.berpod.app.ui.youtube

import com.berpod.app.data.local.VideoWithChannel
import com.berpod.app.data.local.YoutubeVideoEntity
import com.berpod.app.ui.common.formatDuration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dayMonth = DateTimeFormatter.ofPattern("dd.MM.")
private val fullDate = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** "DNES"/"VČERA"/"25.09." (this year) or "25.09.2025" (another year) for a calendar day's group header. */
private fun dayLabel(millis: Long, nowMillis: Long, zone: ZoneId): String {
    val then = Instant.ofEpochMilli(millis).atZone(zone)
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
    return when {
        then.toLocalDate() == now.toLocalDate() -> "DNES"
        then.toLocalDate() == now.toLocalDate().minusDays(1) -> "VČERA"
        then.year == now.year -> then.format(dayMonth)
        else -> then.format(fullDate)
    }
}

/**
 * Groups [videos] (already newest-first) by calendar day, each group keeping its videos' own order.
 * A video with no known date groups as if published at the epoch, rather than crashing.
 */
fun groupByDay(videos: List<VideoWithChannel>, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): List<Pair<String, List<VideoWithChannel>>> {
    val groups = LinkedHashMap<String, MutableList<VideoWithChannel>>()
    videos.forEach { video ->
        val label = dayLabel(video.video.pubDateMillis ?: 0L, nowMillis, zone)
        groups.getOrPut(label) { mutableListOf() } += video
    }
    return groups.map { (label, items) -> label to items }
}

/** Seconds left to watch: the rest of a video in progress, otherwise its full duration; null when unknown. */
fun videoRemainingSeconds(video: YoutubeVideoEntity): Int? {
    val durationMs = video.durationMs ?: return null
    val remainingMs = if (video.lastPositionMs > 0) durationMs - video.lastPositionMs else durationMs
    return (remainingMs / 1000).toInt()
}

/** "zbývá N min" for a video in progress, otherwise its full duration; null until the duration is known. */
fun remainingVideoText(video: YoutubeVideoEntity): String? {
    val seconds = videoRemainingSeconds(video) ?: return null
    val text = formatDuration(seconds) ?: return null
    return if (video.lastPositionMs > 0) "zbývá $text" else text
}

package com.berpod.app.ui.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val pubDateFormatter = DateTimeFormatter.ofPattern("d. M. yyyy")

fun formatPubDate(millis: Long?): String? {
    if (millis == null) return null
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(pubDateFormatter)
}

fun formatPlaybackTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}

fun formatDuration(seconds: Int?): String? {
    if (seconds == null || seconds <= 0) return null
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "$hours h $minutes min"
        totalMinutes > 0 -> "$minutes min"
        else -> "<1 min"
    }
}

fun episodeProgressFraction(lastPositionMs: Long, durationSeconds: Int?): Float? {
    if (lastPositionMs <= 0L) return null
    if (durationSeconds == null || durationSeconds <= 0) return null
    val durationMs = durationSeconds.toLong() * 1000L
    return (lastPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

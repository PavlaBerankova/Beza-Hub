package com.berpod.app.ui.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val pubDateFormatter = DateTimeFormatter.ofPattern("d. M. yyyy")

fun formatPubDate(millis: Long?): String? {
    if (millis == null) return null
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(pubDateFormatter)
}

fun formatDuration(seconds: Int?): String? {
    if (seconds == null || seconds <= 0) return null
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}

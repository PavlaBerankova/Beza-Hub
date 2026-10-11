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

private val droppedBlocks = Regex("(?is)<(script|style)\\b.*?</\\1>")
private val lineBreakingTags = Regex("(?i)</?(p|div|br|li|ul|ol|h[1-6]|tr|blockquote)\\b[^>]*>")
private val anyTag = Regex("(?s)<[^>]*>")
private val numericEntity = Regex("&#(x[0-9a-fA-F]+|[0-9]+);")
private val whitespaceRun = Regex("[\\s\\u00a0\\u200b\\u200c\\u200d\\ufeff]+")

private val namedEntities = mapOf(
    "&nbsp;" to " ", "&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&apos;" to "'",
    "&hellip;" to "…", "&ndash;" to "–", "&mdash;" to "—", "&lsquo;" to "‘", "&rsquo;" to "’",
    "&ldquo;" to "“", "&rdquo;" to "”"
)

/**
 * An episode description (usually HTML) as one line of plain text for the list: tags removed, entities decoded,
 * every run of white space (line breaks included) collapsed to a single space. Empty when there is nothing to show.
 */
fun plainDescription(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    var text = raw.replace(droppedBlocks, " ").replace(lineBreakingTags, " ").replace(anyTag, "")
    namedEntities.forEach { (entity, plain) -> text = text.replace(entity, plain) }
    text = numericEntity.replace(text) { match ->
        val code = match.groupValues[1].let { if (it.startsWith("x", ignoreCase = true)) it.drop(1).toIntOrNull(16) else it.toIntOrNull() }
        code?.takeIf { it in 1..0x10FFFF }?.let { String(Character.toChars(it)) } ?: ""
    }
    return text.replace(whitespaceRun, " ").trim()
}

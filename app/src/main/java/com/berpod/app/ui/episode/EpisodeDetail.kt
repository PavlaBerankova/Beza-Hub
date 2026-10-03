package com.berpod.app.ui.episode

import com.berpod.app.data.local.EpisodeEntity

/** A chapter found in the episode description: where it starts and its name. */
data class Chapter(val startMs: Long, val title: String)

private val lineBreak = Regex("(?i)<br\\s*/?>|</p>|<p[^>]*>|</li>|<li[^>]*>|</div>|<div[^>]*>")
private val htmlTag = Regex("<[^>]+>")
private val numericEntity = Regex("&#(\\d+);")
private val namedEntities = listOf("&nbsp;" to " ", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'", "&amp;" to "&")

// "4:12 Téma", "1:08:30 – Závěr", "(01:05) …", "[10:00] - …": a time at the very start of a line, then the name.
private val chapterLine = Regex("^[\\[(]?((?:\\d{1,2}:)?\\d{1,2}:\\d{2})[\\])]?\\s*[-–—:|.]?\\s*(\\S.*)$")

// A time stuck to the text before it ("…díl00:59 Co je…"): some feeds lose the line breaks between chapters.
private val gluedTime = Regex("(?<=[^\\s>\\d:(\\[])((?:\\d{1,2}:)?\\d{2}:\\d{2})(?=\\s)")

/** Puts a line break before each glued chapter time, so the chapters read as lines again. */
fun unglueChapterTimes(html: String): String = gluedTime.replace(html) { "<br>" + it.groupValues[1] }

/**
 * Chapters written as lines starting with a time. Needs at least two, in increasing order — otherwise stray
 * times in the text (a "10:35" in a sentence) would pose as chapters.
 */
fun parseChapters(description: String?): List<Chapter> {
    if (description == null) return emptyList()
    var text = unglueChapterTimes(description).replace(lineBreak, "\n").replace(htmlTag, "")
    text = numericEntity.replace(text) { it.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: it.value }
    namedEntities.forEach { (entity, char) -> text = text.replace(entity, char) }
    val chapters = text.lines().mapNotNull { line ->
        val match = chapterLine.find(line.trim()) ?: return@mapNotNull null
        val start = parseTimeMs(match.groupValues[1]) ?: return@mapNotNull null
        Chapter(start, match.groupValues[2].trim())
    }
    if (chapters.size < 2 || chapters.zipWithNext().any { (a, b) -> b.startMs <= a.startMs }) return emptyList()
    return chapters
}

private fun parseTimeMs(time: String): Long? {
    val parts = time.split(":").map { it.toLongOrNull() ?: return null }
    if (parts.drop(1).any { it >= 60 }) return null
    return parts.fold(0L) { acc, part -> acc * 60 + part } * 1000
}

/** The chapter playing at [positionMs] (the last one already started), or -1 before the first. */
fun currentChapterIndex(chapters: List<Chapter>, positionMs: Long): Int = chapters.indexOfLast { it.startMs <= positionMs }

enum class PrimaryAction { Play, Resume, Pause, PlayAgain }

/** What the big button does: pause the playing episode, resume a started or loaded one, replay a played one. */
fun primaryAction(episode: EpisodeEntity, isCurrent: Boolean, isPlaying: Boolean): PrimaryAction = when {
    isCurrent && isPlaying -> PrimaryAction.Pause
    isCurrent -> PrimaryAction.Resume
    episode.playedAtMs != null -> PrimaryAction.PlayAgain
    episode.lastPositionMs > 0 -> PrimaryAction.Resume
    else -> PrimaryAction.Play
}

/** "dnes", "včera", "před 3 dny", "před týdnem", "před 3 týdny", "před měsícem", "před 5 měsíci", "před rokem"… */
fun episodeAgeText(publishedMillis: Long?, nowMillis: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String? {
    val published = publishedMillis ?: return null
    val today = java.time.LocalDate.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
    val day = java.time.LocalDate.ofInstant(java.time.Instant.ofEpochMilli(published), zone)
    val days = java.time.temporal.ChronoUnit.DAYS.between(day, today).toInt()
    return when {
        days < 0 -> null
        days == 0 -> "dnes"
        days == 1 -> "včera"
        days < 7 -> "před ${czechUnit(days, "dnem", "dny", "dny")}".replace("před 2 dny", "před 2 dny")
        days < 14 -> "před týdnem"
        days < 30 -> "před ${days / 7} týdny"
        days < 60 -> "před měsícem"
        days < 365 -> "před ${days / 30} ${if (days / 30 in 2..4) "měsíci" else "měsíci"}"
        days < 730 -> "před rokem"
        else -> "před ${days / 365} ${if (days / 365 in 2..4) "lety" else "lety"}"
    }
}

private fun czechUnit(count: Int, one: String, few: String, many: String): String =
    "$count ${when (count) { 1 -> one; in 2..4 -> few; else -> many }}"

/** "41,6 MB", "812 kB", "1,2 GB" (decimal units, comma as the decimal mark); null when the size is unknown. */
fun formatFileSize(bytes: Long?): String? {
    if (bytes == null || bytes <= 0) return null
    val locale = java.util.Locale.forLanguageTag("cs")
    return when {
        bytes >= 1_000_000_000L -> String.format(locale, "%.1f GB", bytes / 1e9)
        bytes >= 1_000_000L -> String.format(locale, "%.1f MB", bytes / 1e6)
        else -> String.format(locale, "%d kB", (bytes / 1000).coerceAtLeast(1))
    }
}

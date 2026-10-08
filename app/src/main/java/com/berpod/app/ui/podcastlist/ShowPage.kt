package com.berpod.app.ui.podcastlist

import com.berpod.app.data.local.EpisodeEntity
import com.berpod.app.data.local.TagEntity
import com.berpod.app.ui.common.episodeProgressFraction
import com.berpod.app.ui.common.formatDuration
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/** Below this share played, an episode counts as "in progress"; at or above it, as finished. */
private const val FINISHED_FRACTION = 0.98f

enum class EpisodeStatus { Unplayed, InProgress, Played }

/** Played wins over a stored position; otherwise started-but-unfinished counts as in progress. */
fun episodeStatus(episode: EpisodeEntity): EpisodeStatus = when {
    episode.playedAtMs != null -> EpisodeStatus.Played
    episode.isInProgress() -> EpisodeStatus.InProgress
    else -> EpisodeStatus.Unplayed
}

/** The episode behind the header's play button and whether it's a resume ("Pokračovat"). */
data class HeaderEpisode(val episode: EpisodeEntity, val isResume: Boolean)

/**
 * The newest episode that is started but not finished; otherwise the newest episode.
 * [episodes] is expected newest first, as the detail screen lists them.
 */
fun pickHeaderEpisode(episodes: List<EpisodeEntity>): HeaderEpisode? {
    val unplayed = episodes.filter { it.playedAtMs == null }
    val inProgress = unplayed.firstOrNull { it.isInProgress() }
    if (inProgress != null) return HeaderEpisode(inProgress, isResume = true)
    return (unplayed.firstOrNull() ?: episodes.firstOrNull())?.let { HeaderEpisode(it, isResume = false) }
}

private fun EpisodeEntity.isInProgress(): Boolean {
    val fraction = episodeProgressFraction(lastPositionMs, durationSeconds) ?: return false
    return fraction > 0f && fraction < FINISHED_FRACTION
}

private val htmlTag = Regex("<[^>]+>")
private val whitespace = Regex("\\s+")
private val entities = mapOf("&amp;" to "&", "&nbsp;" to " ", "&quot;" to "\"", "&#39;" to "'", "&lt;" to "<", "&gt;" to ">")

/** Plain-text episode description for the two-line preview, or null if there's nothing to show. */
fun descriptionSnippet(raw: String?): String? {
    if (raw == null) return null
    var text = raw.replace(htmlTag, " ")
    entities.forEach { (entity, char) -> text = text.replace(entity, char) }
    return text.replace(whitespace, " ").trim().takeIf { it.isNotEmpty() }
}

/** Seconds left to listen: the rest of an episode in progress, otherwise its full duration; null if unknown. */
fun remainingSeconds(episode: EpisodeEntity): Int? {
    val duration = episode.durationSeconds ?: return null
    return if (episode.isInProgress()) duration - (episode.lastPositionMs / 1000).toInt() else duration
}

/** "zbývá 5 min" for an episode in progress, otherwise its full duration; null when the duration is unknown. */
fun remainingText(episode: EpisodeEntity): String? {
    val seconds = remainingSeconds(episode) ?: return null
    val text = formatDuration(seconds) ?: return null
    return if (episode.isInProgress()) "zbývá $text" else text
}

/** Names of the podcast's tags, in [tags]' (alphabetical) order; empty when it has none. */
fun tagNamesFor(tags: List<TagEntity>, assignedIds: Set<Long>): List<String> =
    tags.filter { it.id in assignedIds }.map { it.name }

private const val DAY_MS = 24L * 60 * 60 * 1000
private const val RECENT_EPISODES = 12

private val everyWeekday = mapOf(
    DayOfWeek.MONDAY to "každé pondělí",
    DayOfWeek.TUESDAY to "každé úterý",
    DayOfWeek.WEDNESDAY to "každou středu",
    DayOfWeek.THURSDAY to "každý čtvrtek",
    DayOfWeek.FRIDAY to "každý pátek",
    DayOfWeek.SATURDAY to "každou sobotu",
    DayOfWeek.SUNDAY to "každou neděli"
)

/**
 * How often a podcast comes out, from the dates of its latest episodes ("každý pátek", "jednou za 2 týdny"…);
 * null when there are too few dated episodes to tell. [pubDates] may come in any order.
 */
fun publishingFrequency(pubDates: List<Long?>, zone: ZoneId = ZoneId.systemDefault()): String? {
    val dates = pubDates.filterNotNull().sortedDescending().take(RECENT_EPISODES)
    if (dates.size < 3) return null
    val gapsInDays = dates.zipWithNext { newer, older -> (newer - older).toDouble() / DAY_MS }.sorted()
    val median = gapsInDays[gapsInDays.size / 2]
    return when {
        median < 1.5 -> {
            val atWeekend = dates.count { Instant.ofEpochMilli(it).atZone(zone).dayOfWeek >= DayOfWeek.SATURDAY }
            if (atWeekend * 10 <= dates.size) "ve všední dny" else "denně"
        }
        median < 4.5 -> "několikrát týdně"
        median < 9.5 -> {
            val weekdays = dates.map { Instant.ofEpochMilli(it).atZone(zone).dayOfWeek }
            val (day, count) = weekdays.groupingBy { it }.eachCount().maxByOrNull { it.value }!!
            if (count * 10 >= weekdays.size * 7) everyWeekday.getValue(day) else "jednou týdně"
        }
        median < 18 -> "jednou za 2 týdny"
        median < 40 -> "jednou měsíčně"
        else -> "nepravidelně"
    }
}

enum class EpisodeSort { NewestFirst, OldestFirst }
enum class EpisodeFilter { All, Unplayed, Played }

/** The filter button's order: all, unplayed, played, then round again. */
fun nextEpisodeFilter(filter: EpisodeFilter): EpisodeFilter = EpisodeFilter.entries[(filter.ordinal + 1) % EpisodeFilter.entries.size]

fun nextEpisodeSort(sort: EpisodeSort): EpisodeSort = EpisodeSort.entries[(sort.ordinal + 1) % EpisodeSort.entries.size]

/**
 * The episodes as the podcast page lists them. [episodes] comes newest first; undated ones always stay at the end,
 * whichever way the list is sorted.
 */
fun applyEpisodeView(episodes: List<EpisodeEntity>, sort: EpisodeSort, filter: EpisodeFilter): List<EpisodeEntity> {
    val shown = when (filter) {
        EpisodeFilter.All -> episodes
        EpisodeFilter.Unplayed -> episodes.filter { it.playedAtMs == null }
        EpisodeFilter.Played -> episodes.filter { it.playedAtMs != null }
    }
    if (sort == EpisodeSort.NewestFirst) return shown
    val (dated, undated) = shown.partition { it.pubDateMillis != null }
    return dated.sortedBy { it.pubDateMillis } + undated
}

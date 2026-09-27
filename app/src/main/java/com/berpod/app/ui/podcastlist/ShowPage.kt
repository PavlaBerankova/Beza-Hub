package com.berpod.app.ui.podcastlist

import com.berpod.app.data.local.EpisodeEntity
import com.berpod.app.data.local.TagEntity
import com.berpod.app.ui.common.episodeProgressFraction
import com.berpod.app.ui.common.formatDuration

/** Below this share played, an episode counts as "in progress"; at or above it, as finished. */
private const val FINISHED_FRACTION = 0.98f

/** The episode behind the header's play button and whether it's a resume ("Pokračovat"). */
data class HeaderEpisode(val episode: EpisodeEntity, val isResume: Boolean)

/**
 * The newest episode that is started but not finished; otherwise the newest episode.
 * [episodes] is expected newest first, as the detail screen lists them.
 */
fun pickHeaderEpisode(episodes: List<EpisodeEntity>): HeaderEpisode? {
    val inProgress = episodes.firstOrNull { it.isInProgress() }
    if (inProgress != null) return HeaderEpisode(inProgress, isResume = true)
    return episodes.firstOrNull()?.let { HeaderEpisode(it, isResume = false) }
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

/** "zbývá 5 min" for an episode in progress, otherwise its full duration; null when the duration is unknown. */
fun remainingText(episode: EpisodeEntity): String? {
    val duration = episode.durationSeconds ?: return null
    if (!episode.isInProgress()) return formatDuration(duration)
    val remainingSeconds = duration - (episode.lastPositionMs / 1000).toInt()
    return formatDuration(remainingSeconds)?.let { "zbývá $it" }
}

/** Whether [argb] is light enough to need dark text and icons on top of it. */
fun isLightColor(argb: Int): Boolean {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0 > 0.6
}

/**
 * The header colour from the cover's palette swatches (ARGB): the vivid one Apple-style headers use, falling
 * back to darker, muted and finally the dominant colour; null if the palette found none.
 */
fun pickHeaderColor(vibrant: Int?, darkVibrant: Int?, muted: Int?, dominant: Int?): Int? =
    vibrant ?: darkVibrant ?: muted ?: dominant

/** Names of the podcast's tags, in [tags]' (alphabetical) order; empty when it has none. */
fun tagNamesFor(tags: List<TagEntity>, assignedIds: Set<Long>): List<String> =
    tags.filter { it.id in assignedIds }.map { it.name }

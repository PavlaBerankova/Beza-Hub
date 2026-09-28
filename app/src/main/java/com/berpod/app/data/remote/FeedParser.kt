package com.berpod.app.data.remote

import com.prof18.rssparser.RssParser
import com.prof18.rssparser.model.RssItem
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class ParsedFeedMeta(
    val title: String,
    val imageUrl: String?,
    val description: String?,
    val episodes: List<ParsedEpisode>,
    val author: String? = null
)

data class ParsedEpisode(
    val guid: String,
    val title: String,
    val audioUrl: String,
    val pubDateMillis: Long?,
    val durationSeconds: Int?,
    val description: String?,
    val imageUrl: String?
)

/** The first non-blank of the iTunes author and the iTunes owner name, trimmed; null if neither. */
fun pickAuthor(itunesAuthor: String?, ownerName: String?): String? =
    listOf(itunesAuthor, ownerName).firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }

/** RSS (RFC 1123) or Atom (ISO 8601) date to epoch millis; null when it can't be read. */
fun parseFeedDateMillis(raw: String): Long? {
    val trimmed = raw.trim()
    val formatters = listOf(
        DateTimeFormatter.RFC_1123_DATE_TIME,
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        DateTimeFormatter.ISO_ZONED_DATE_TIME
    )
    for (formatter in formatters) {
        try {
            return ZonedDateTime.parse(trimmed, formatter).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            // try next formatter
        }
    }
    return null
}

interface FeedFetcher {
    suspend fun fetchAndParse(url: String): ParsedFeedMeta
}

class FeedParser(private val rssParser: RssParser = RssParser()) : FeedFetcher {

    override suspend fun fetchAndParse(url: String): ParsedFeedMeta {
        val channel = rssParser.getRssChannel(url)
        val title = channel.title?.takeIf { it.isNotBlank() } ?: url
        val imageUrl = channel.image?.url ?: channel.itunesChannelData?.image
        val description = channel.description?.takeIf { it.isNotBlank() }
            ?: channel.itunesChannelData?.summary
        val episodes = channel.items.mapNotNull(::toParsedEpisode)
        val author = pickAuthor(channel.itunesChannelData?.author, channel.itunesChannelData?.owner?.name)
        return ParsedFeedMeta(title = title, imageUrl = imageUrl, description = description, episodes = episodes, author = author)
    }

    private fun toParsedEpisode(item: RssItem): ParsedEpisode? {
        val title = item.title?.takeIf { it.isNotBlank() } ?: return null
        val audioUrl = (item.audio ?: item.rawEnclosure?.url)?.takeIf { it.isNotBlank() } ?: return null
        val guid = item.guid?.takeIf { it.isNotBlank() }
            ?: item.link?.takeIf { it.isNotBlank() }
            ?: "fallback:${(title + item.pubDate.orEmpty()).hashCode()}"
        return ParsedEpisode(
            guid = guid,
            title = title,
            audioUrl = audioUrl,
            pubDateMillis = item.pubDate?.let(::parseFeedDateMillis),
            durationSeconds = item.itunesItemData?.duration?.let(::parseItunesDuration),
            description = item.description?.takeIf { it.isNotBlank() } ?: item.itunesItemData?.summary,
            imageUrl = item.image ?: item.itunesItemData?.image
        )
    }

    private fun parseItunesDuration(raw: String): Int? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (':' !in trimmed) return trimmed.toIntOrNull()
        val parts = trimmed.split(":")
        if (parts.size !in 2..3) return null
        val nums = parts.map { it.toIntOrNull() ?: return null }
        return when (nums.size) {
            2 -> nums[0] * 60 + nums[1]
            3 -> nums[0] * 3600 + nums[1] * 60 + nums[2]
            else -> null
        }
    }
}

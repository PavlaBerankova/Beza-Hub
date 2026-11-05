package com.berpod.app.data.remote

import com.prof18.rssparser.RssParser
import com.prof18.rssparser.model.RssItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class ParsedFeedMeta(
    /** The feed's own address: what the user typed may have been a web page pointing at it. */
    val feedUrl: String,
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
    val imageUrl: String?,
    val episodeNumber: Int? = null,
    val fileSizeBytes: Long? = null
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

/** The result of asking for a feed that may not have changed. */
sealed interface FeedFetchResult {
    data class Changed(val meta: ParsedFeedMeta, val validators: FeedValidators) : FeedFetchResult
    data object NotModified : FeedFetchResult
}

interface FeedFetcher {
    suspend fun fetchAndParse(url: String): ParsedFeedMeta

    /** Fetches the feed unless the server says it is unchanged since [known]. Fetchers without validators always fetch. */
    suspend fun fetchIfChanged(url: String, known: FeedValidators?): FeedFetchResult =
        FeedFetchResult.Changed(fetchAndParse(url), FeedValidators())
}

class FeedParser(
    private val rssParser: RssParser = RssParser(),
    private val documentLoader: FeedDocumentLoader = FeedDocumentLoader()
) : FeedFetcher {

    override suspend fun fetchAndParse(url: String): ParsedFeedMeta {
        val document = documentLoader.resolve(url)
        // Parsing a feed of thousands of episodes is real work: keep it off the main thread.
        return withContext(Dispatchers.Default) { toParsedMeta(document) }
    }

    override suspend fun fetchIfChanged(url: String, known: FeedValidators?): FeedFetchResult =
        when (val load = documentLoader.resolveIfChanged(url, known)) {
            is FeedLoad.NotModified -> FeedFetchResult.NotModified
            is FeedLoad.Fresh -> FeedFetchResult.Changed(
                withContext(Dispatchers.Default) { toParsedMeta(load.document) },
                load.document.validators
            )
        }

    private suspend fun toParsedMeta(document: FeedDocument): ParsedFeedMeta {
        val channel = rssParser.parse(document.text)
        val title = channel.title?.takeIf { it.isNotBlank() } ?: document.url
        val imageUrl = webUrlOrNull(channel.image?.url ?: channel.itunesChannelData?.image)
        val description = channel.description?.takeIf { it.isNotBlank() }
            ?: channel.itunesChannelData?.summary
        val episodes = channel.items.mapNotNull(::toParsedEpisode)
        val author = pickAuthor(channel.itunesChannelData?.author, channel.itunesChannelData?.owner?.name)
        return ParsedFeedMeta(feedUrl = document.url, title = title, imageUrl = imageUrl, description = description, episodes = episodes, author = author)
    }

    private fun toParsedEpisode(item: RssItem): ParsedEpisode? {
        val title = item.title?.takeIf { it.isNotBlank() } ?: return null
        val audioUrl = webUrlOrNull(item.audio ?: item.rawEnclosure?.url) ?: return null
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
            imageUrl = webUrlOrNull(item.image ?: item.itunesItemData?.image),
            episodeNumber = item.itunesItemData?.episode?.trim()?.toIntOrNull(),
            fileSizeBytes = item.rawEnclosure?.length?.takeIf { it > 0 }
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

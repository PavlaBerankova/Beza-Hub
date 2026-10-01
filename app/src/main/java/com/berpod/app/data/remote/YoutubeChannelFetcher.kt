package com.berpod.app.data.remote

import com.prof18.rssparser.RssParser
import com.prof18.rssparser.model.RssChannel
import com.prof18.rssparser.model.RssItem
import java.net.URI

/** Like [normalizeSourceUrl], but a bare "@handle" is turned into a youtube.com channel address first. */
fun normalizeChannelUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.startsWith("@") && trimmed.length > 1 && !trimmed.contains(' ')) {
        return normalizeSourceUrl("https://www.youtube.com/$trimmed")
    }
    return normalizeSourceUrl(trimmed)
}

data class ParsedYoutubeVideo(
    val guid: String,
    val title: String,
    val videoId: String,
    val videoUrl: String,
    val thumbnailUrl: String?,
    val description: String?,
    val pubDateMillis: Long?
)

/** A channel found at an address: the feed's own URL (not the page's), its id, name and videos. */
data class ParsedYoutubeChannel(
    val feedUrl: String,
    val channelId: String?,
    val title: String,
    val channelUrl: String?,
    val thumbnailUrl: String?,
    val videos: List<ParsedYoutubeVideo>
)

interface YoutubeChannelFetcher {
    /** Reads [url] as a feed, or as a channel page whose <link rel="alternate"> points to one. */
    suspend fun resolve(url: String): ParsedYoutubeChannel
}

class YoutubeChannelLoader(
    private val rssParser: RssParser = RssParser(),
    private val documentLoader: FeedDocumentLoader = FeedDocumentLoader()
) : YoutubeChannelFetcher {

    override suspend fun resolve(url: String): ParsedYoutubeChannel {
        val document = documentLoader.resolve(url)
        return toChannel(document.url, rssParser.parse(document.text))
    }

    private fun toChannel(feedUrl: String, channel: RssChannel): ParsedYoutubeChannel {
        val channelId = channel.youtubeChannelData?.channelId
        val videos = channel.items.mapNotNull(::toVideo)
        require(videos.isNotEmpty()) { "Na této adrese není YouTube kanál" }
        return ParsedYoutubeChannel(
            feedUrl = feedUrl,
            channelId = channelId,
            title = channel.title?.trim()?.takeIf { it.isNotEmpty() } ?: hostOf(feedUrl),
            channelUrl = channelId?.let { "https://www.youtube.com/channel/$it" },
            thumbnailUrl = channel.image?.url,
            videos = videos
        )
    }

    private fun toVideo(item: RssItem): ParsedYoutubeVideo? {
        val data = item.youtubeItemData ?: return null
        val videoId = data.videoId?.takeIf { it.isNotBlank() } ?: return null
        val title = (data.title ?: item.title)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return ParsedYoutubeVideo(
            guid = item.guid?.takeIf { it.isNotBlank() } ?: videoId,
            title = title,
            videoId = videoId,
            videoUrl = data.videoUrl?.takeIf { it.isNotBlank() } ?: "https://www.youtube.com/watch?v=$videoId",
            thumbnailUrl = data.thumbnailUrl,
            description = data.description?.takeIf { it.isNotBlank() },
            pubDateMillis = item.pubDate?.let(::parseFeedDateMillis)
        )
    }

    private fun hostOf(url: String): String = runCatching { URI(url).host?.removePrefix("www.") }.getOrNull() ?: url
}

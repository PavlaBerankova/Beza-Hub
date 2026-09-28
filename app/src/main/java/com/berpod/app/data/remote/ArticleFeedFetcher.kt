package com.berpod.app.data.remote

import com.prof18.rssparser.RssParser
import com.prof18.rssparser.model.RssChannel
import com.prof18.rssparser.model.RssItem
import java.net.URI

data class ParsedArticle(
    val guid: String,
    val title: String,
    val link: String?,
    val author: String?,
    val pubDateMillis: Long?,
    val summary: String?,
    val content: String?
)

/** A source found at an address: the feed's own URL (not the page's), its name, site and articles. */
data class ParsedArticleFeed(
    val feedUrl: String,
    val title: String,
    val siteUrl: String?,
    val imageUrl: String?,
    val articles: List<ParsedArticle>
)

interface ArticleFeedFetcher {
    /** Reads [url] as a feed, or as a web page whose <link rel="alternate"> points to one. */
    suspend fun resolve(url: String): ParsedArticleFeed
}

class ArticleFeedLoader(
    private val rssParser: RssParser = RssParser(),
    private val documentLoader: FeedDocumentLoader = FeedDocumentLoader()
) : ArticleFeedFetcher {

    override suspend fun resolve(url: String): ParsedArticleFeed {
        val document = documentLoader.resolve(url)
        return toFeed(document.url, rssParser.parse(document.text))
    }

    private fun toFeed(feedUrl: String, channel: RssChannel): ParsedArticleFeed {
        val siteUrl = channel.link?.takeIf { it.isNotBlank() }
        return ParsedArticleFeed(
            feedUrl = feedUrl,
            title = channel.title?.trim()?.takeIf { it.isNotEmpty() } ?: siteUrl?.let(::hostOf) ?: hostOf(feedUrl),
            siteUrl = siteUrl,
            imageUrl = channel.image?.url,
            articles = channel.items.mapNotNull(::toArticle)
        )
    }

    private fun toArticle(item: RssItem): ParsedArticle? {
        val title = item.title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val link = item.link?.trim()?.takeIf { it.isNotEmpty() }
        return ParsedArticle(
            guid = item.guid?.takeIf { it.isNotBlank() } ?: link ?: "fallback:${(title + item.pubDate.orEmpty()).hashCode()}",
            title = title,
            link = link,
            author = item.author?.trim()?.takeIf { it.isNotEmpty() },
            pubDateMillis = item.pubDate?.let(::parseFeedDateMillis),
            summary = item.description?.takeIf { it.isNotBlank() },
            content = item.content?.takeIf { it.isNotBlank() }
        )
    }

    private fun hostOf(url: String): String = runCatching { URI(url).host?.removePrefix("www.") }.getOrNull() ?: url
}

package com.berpod.app.data.remote

import com.prof18.rssparser.RssParser
import com.prof18.rssparser.model.RssChannel
import com.prof18.rssparser.model.RssItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.Charset

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

class ArticleFeedLoader(private val rssParser: RssParser = RssParser()) : ArticleFeedFetcher {

    override suspend fun resolve(url: String): ParsedArticleFeed {
        val page = download(url)
        if (looksLikeFeed(page.text)) return toFeed(page.finalUrl, rssParser.parse(page.text))
        val feedUrl = findFeedLinks(page.text, page.finalUrl).firstOrNull()
            ?: throw IllegalArgumentException("Na této adrese není RSS zdroj")
        val feed = download(feedUrl)
        require(looksLikeFeed(feed.text)) { "Na této adrese není RSS zdroj" }
        return toFeed(feed.finalUrl, rssParser.parse(feed.text))
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

    private class Page(val finalUrl: String, val text: String)

    /** GET with redirects followed across http/https (HttpURLConnection alone won't switch protocols). */
    private suspend fun download(url: String): Page = withContext(Dispatchers.IO) {
        var current = url
        repeat(5) {
            val connection = (URI(current).toURL().openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", "BEZA Hub (Android RSS reader)")
                setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, text/html;q=0.9, */*;q=0.8")
            }
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location") ?: throw IllegalStateException("Přesměrování bez adresy")
                    current = URI(current).resolve(location).toString()
                    return@repeat
                }
                require(code in 200..299) { "Server odpověděl chybou $code" }
                val bytes = connection.inputStream.use { it.readBytes() }
                return@withContext Page(current, decode(bytes, connection.contentType))
            } finally {
                connection.disconnect()
            }
        }
        throw IllegalStateException("Příliš mnoho přesměrování")
    }

    private fun decode(bytes: ByteArray, contentType: String?): String {
        val declared = contentType?.let { Regex("(?i)charset=\"?([\\w-]+)").find(it)?.groupValues?.get(1) }
            ?: Regex("(?i)encoding=[\"']([\\w-]+)").find(String(bytes, 0, minOf(bytes.size, 200), Charsets.ISO_8859_1))?.groupValues?.get(1)
        val charset = declared?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
        return String(bytes, charset)
    }

    private fun hostOf(url: String): String = runCatching { URI(url).host?.removePrefix("www.") }.getOrNull() ?: url
}

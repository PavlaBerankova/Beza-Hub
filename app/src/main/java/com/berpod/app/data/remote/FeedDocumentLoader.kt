package com.berpod.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.Charset

/** A downloaded feed: the address it really came from (after redirects) and its XML. */
data class FeedDocument(val url: String, val text: String)

/**
 * Turns an address the user typed into a feed document: an address that already serves a feed is used
 * as it is, a web page is searched for the RSS/Atom link it advertises.
 */
class FeedDocumentLoader {

    suspend fun resolve(url: String): FeedDocument {
        val page = download(url)
        if (looksLikeFeed(page.text)) return page
        val feedUrl = findFeedLinks(page.text, page.url).firstOrNull()
            ?: throw IllegalArgumentException("Na této adrese není RSS zdroj")
        val feed = download(feedUrl)
        require(looksLikeFeed(feed.text)) { "Na této adrese není RSS zdroj" }
        return feed
    }

    /** GET with redirects followed across http/https (HttpURLConnection alone won't switch protocols). */
    private suspend fun download(url: String): FeedDocument = withContext(Dispatchers.IO) {
        var current = url
        repeat(5) {
            val connection = (URI(current).toURL().openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", HTTP_USER_AGENT)
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
                return@withContext FeedDocument(current, decode(bytes, connection.contentType))
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

}

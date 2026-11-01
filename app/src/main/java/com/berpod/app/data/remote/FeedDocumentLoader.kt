package com.berpod.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.Charset

/** The server sent more than the app is willing to read as a feed. */
class ResponseTooLargeException : IOException("Odpověď je příliš velká")

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
        require(isWebUrl(url)) { "Adresa musí začínat http:// nebo https://" }
        var current = url.trim()
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
                    current = redirectTarget(current, location)
                    return@repeat
                }
                require(code in 200..299) { "Server odpověděl chybou $code" }
                val bytes = connection.inputStream.use { readLimited(it, MAX_FEED_BYTES) }
                return@withContext FeedDocument(current, decode(bytes, connection.contentType))
            } finally {
                connection.disconnect()
            }
        }
        throw IllegalStateException("Příliš mnoho přesměrování")
    }

    /** Reads the whole stream, but gives up beyond [limit] bytes so a hostile server can't fill the memory. */
    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (out.size() + read > limit) throw ResponseTooLargeException()
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun decode(bytes: ByteArray, contentType: String?): String {
        val declared = contentType?.let { Regex("(?i)charset=\"?([\\w-]+)").find(it)?.groupValues?.get(1) }
            ?: Regex("(?i)encoding=[\"']([\\w-]+)").find(String(bytes, 0, minOf(bytes.size, 200), Charsets.ISO_8859_1))?.groupValues?.get(1)
        val charset = declared?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
        return String(bytes, charset)
    }

    private companion object {
        const val MAX_FEED_BYTES = 20 * 1024 * 1024
    }
}

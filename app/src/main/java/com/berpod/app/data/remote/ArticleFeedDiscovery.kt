package com.berpod.app.data.remote

import java.net.URI

/** Trimmed http(s) address; "example.cz" gets https://. Null when blank or not an address. */
fun normalizeSourceUrl(raw: String): String? {
    val trimmed = raw.trim().takeIf { it.isNotEmpty() } ?: return null
    val url = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    val host = runCatching { URI(url).host }.getOrNull()
    return url.takeIf { host != null && '.' in host && ' ' !in url }
}

private val linkTag = Regex("(?is)<link\\b[^>]*>")
private fun attr(tag: String, name: String): String? =
    Regex("(?is)\\b$name\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))").find(tag)?.let { m ->
        m.groupValues.drop(2).firstOrNull { it.isNotEmpty() }
    }

/** RSS/Atom addresses a web page advertises with <link rel="alternate">, resolved against [pageUrl]. */
fun findFeedLinks(html: String, pageUrl: String): List<String> =
    linkTag.findAll(html).mapNotNull { match ->
        val tag = match.value
        val rel = attr(tag, "rel")?.lowercase() ?: return@mapNotNull null
        val type = attr(tag, "type")?.lowercase() ?: return@mapNotNull null
        if ("alternate" !in rel.split(Regex("\\s+"))) return@mapNotNull null
        if (type != "application/rss+xml" && type != "application/atom+xml") return@mapNotNull null
        val href = attr(tag, "href") ?: return@mapNotNull null
        runCatching { URI(pageUrl).resolve(href.trim()).toString() }.getOrNull()
    }.distinct().toList()

private val feedRoot = Regex("(?is)^\\s*(<\\?xml[^>]*\\?>\\s*)?(<!--.*?-->\\s*)*<(rss|feed|rdf:RDF)\\b")

/** Whether a downloaded document is itself a feed (rather than a web page). */
fun looksLikeFeed(document: String): Boolean = feedRoot.containsMatchIn(document.take(4096))

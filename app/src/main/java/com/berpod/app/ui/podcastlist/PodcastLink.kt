package com.berpod.app.ui.podcastlist

import java.net.URI

/** The address as it should be saved, or null when it is not a web address (http/https with a host that has a dot). */
fun normalizeLinkUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
    val withScheme = if (trimmed.startsWith("www.", ignoreCase = true)) "https://$trimmed" else trimmed
    val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    val host = uri.host ?: return null
    return if ('.' in host) withScheme else null
}

/** What the menu calls the link: its title, or the site's domain (without "www.") when it has none. */
fun linkLabel(title: String?, url: String): String {
    title?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val host = runCatching { URI(url).host }.getOrNull()
    return host?.removePrefix("www.") ?: url
}

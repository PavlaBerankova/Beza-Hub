package com.berpod.app.data.remote

import java.net.URI

/**
 * Feeds and web pages are untrusted: an address coming from them may be `intent:`, `tel:`, `file:`, `content:`
 * and so on. Only plain web addresses may be opened, played, fetched or shown as images.
 */
fun isWebUrl(url: String?): Boolean {
    val trimmed = url?.trim()?.takeIf { it.isNotEmpty() } ?: return false
    if (trimmed.any { it.isWhitespace() || it.isISOControl() }) return false
    val uri = runCatching { URI(trimmed) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase()
    return (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
}

/** The trimmed address when it is a web address, otherwise null. */
fun webUrlOrNull(url: String?): String? = url?.trim()?.takeIf { isWebUrl(it) }

/**
 * Where a redirect to [location] from [current] leads. Throws when it would leave http/https, or step down
 * from https to http (the feed would then travel unprotected).
 */
fun redirectTarget(current: String, location: String): String {
    val target = URI(current).resolve(location.trim()).toString()
    require(isWebUrl(target)) { "Přesměrování na nepodporovanou adresu" }
    val downgrade = current.startsWith("https://", ignoreCase = true) && !target.startsWith("https://", ignoreCase = true)
    require(!downgrade) { "Přesměrování z https na nezabezpečený http" }
    return target
}

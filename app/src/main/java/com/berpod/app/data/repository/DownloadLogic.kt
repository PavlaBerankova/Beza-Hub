package com.berpod.app.data.repository

/** What to do with the server's answer to a (possibly resumed) download request. */
sealed interface ResponseAction {
    /** 206: the bytes continue the partial file. */
    data object Append : ResponseAction
    /** 200: the whole file comes (the server ignored the range, or there was none). */
    data object StartOver : ResponseAction
    /** 416 and the like: the partial file is unusable; drop it and ask again from the start. */
    data object DiscardAndRetry : ResponseAction
    /** A hiccup on the server side: try again later. */
    data object Retry : ResponseAction
    /** The file is not there to be had (404, 403, ...). */
    data object Fail : ResponseAction
}

fun classifyResponse(code: Int): ResponseAction = when {
    code == 206 -> ResponseAction.Append
    code in 200..299 -> ResponseAction.StartOver
    code == 416 -> ResponseAction.DiscardAndRetry
    code == 408 || code == 429 || code in 500..599 -> ResponseAction.Retry
    else -> ResponseAction.Fail
}

/** The `Range` header that continues after [existingBytes], or null to ask for the whole file. */
fun rangeHeader(existingBytes: Long): String? = if (existingBytes > 0) "bytes=$existingBytes-" else null

/** The full size from a `Content-Range: bytes 100-999/1000` value; null when unknown ("*") or malformed. */
fun totalFromContentRange(header: String?): Long? =
    header?.substringAfterLast('/')?.trim()?.toLongOrNull()

/** The name the episode's file gets: its id plus the audio's own extension (mp3 when the address has none). */
fun downloadFileName(episodeId: Long, audioUrl: String): String {
    val path = audioUrl.substringBefore('?').substringBefore('#')
    val ext = path.substringAfterLast('/').substringAfterLast('.', "").lowercase()
    val safe = ext.takeIf { it.length in 2..4 && it.all { ch -> ch.isLetterOrDigit() } } ?: "mp3"
    return "$episodeId.$safe"
}

/** Whole percent done, or null while the total size is unknown. */
fun progressPercent(downloadedBytes: Long, totalBytes: Long?): Int? {
    if (totalBytes == null || totalBytes <= 0) return null
    return (downloadedBytes * 100 / totalBytes).toInt().coerceIn(0, 100)
}

package com.berpod.app.ui.common

import androidx.compose.ui.graphics.Color
import com.berpod.app.data.remote.isWebUrl
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration

// Images, scripts and styles have no place in plain reading text (images would show as a placeholder glyph).
private val dropped = Regex("(?is)<(script|style)\\b.*?</\\1>|<img\\b[^>]*>|<figure\\b.*?</figure>|<iframe\\b.*?</iframe>")

private val anchorTag = Regex("(?is)<a\\b[^>]*>")
private val hrefAttr = Regex("(?is)\\s+href\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")

/** Links in feed text may lead anywhere (`intent:`, `tel:`, ...): only web addresses stay tappable. */
internal fun stripUnsafeLinks(html: String): String = anchorTag.replace(html) { tag ->
    hrefAttr.replace(tag.value) { href ->
        val target = href.groupValues.drop(2).firstOrNull { it.isNotEmpty() }
        if (isWebUrl(target?.let(::decodeEntities))) href.value else ""
    }
}

private fun decodeEntities(s: String) = s.replace("&amp;", "&").replace("&#38;", "&")

/** HTML (or plain text with line breaks) as styled text with tappable links; null when there's no text. */
fun htmlText(raw: String?, linkColor: Color): AnnotatedString? {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val html = if (text.contains('<')) stripUnsafeLinks(text.replace(dropped, "")) else text.replace("\n", "<br>")
    return AnnotatedString.fromHtml(
        html.trim(),
        linkStyles = TextLinkStyles(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    ).takeIf { it.text.isNotBlank() }
}

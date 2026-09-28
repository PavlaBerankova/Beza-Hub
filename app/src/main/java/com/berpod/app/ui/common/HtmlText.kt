package com.berpod.app.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration

// Images, scripts and styles have no place in plain reading text (images would show as a placeholder glyph).
private val dropped = Regex("(?is)<(script|style)\\b.*?</\\1>|<img\\b[^>]*>|<figure\\b.*?</figure>|<iframe\\b.*?</iframe>")

/** HTML (or plain text with line breaks) as styled text with tappable links; null when there's no text. */
fun htmlText(raw: String?, linkColor: Color): AnnotatedString? {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val html = if (text.contains('<')) text.replace(dropped, "") else text.replace("\n", "<br>")
    return AnnotatedString.fromHtml(
        html.trim(),
        linkStyles = TextLinkStyles(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    ).takeIf { it.text.isNotBlank() }
}

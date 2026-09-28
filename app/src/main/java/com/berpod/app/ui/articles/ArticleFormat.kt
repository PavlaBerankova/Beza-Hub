package com.berpod.app.ui.articles

import com.berpod.app.ui.dot.upperCs
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dayMonth = DateTimeFormatter.ofPattern("dd.MM.")
private val fullDate = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** "PRÁVĚ TEĎ", "PŘED 25 MIN", "PŘED 3 H" (today), "VČERA", then "25.09." or, in another year, "25.09.2025". */
fun relativeTimeLabel(millis: Long?, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String? {
    if (millis == null) return null
    val then = Instant.ofEpochMilli(millis).atZone(zone)
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
    val minutes = (nowMillis - millis) / 60_000
    return when {
        then.toLocalDate() == now.toLocalDate() && minutes < 1 -> "PRÁVĚ TEĎ"
        then.toLocalDate() == now.toLocalDate() && minutes < 60 -> "PŘED $minutes MIN"
        then.toLocalDate() == now.toLocalDate() -> "PŘED ${minutes / 60} H"
        then.toLocalDate() == now.toLocalDate().minusDays(1) -> "VČERA"
        then.year == now.year -> then.format(dayMonth)
        else -> then.format(fullDate)
    }
}

private val htmlTag = Regex("<[^>]+>")
private val whitespace = Regex("\\s+")

/** Minutes to read [html] at 200 words a minute, at least 1; null without text. */
fun readingMinutes(html: String?): Int? {
    val words = html?.replace(htmlTag, " ")?.trim()?.takeIf { it.isNotEmpty() }?.split(whitespace)?.size ?: return null
    return ((words + 199) / 200).coerceAtLeast(1)
}

/** Two letters for a source's badge: the first letters of its first two words (skipping one-letter ones), or its first two letters. */
fun initials(title: String): String {
    val all = title.split(Regex("\\s+")).filter { it.isNotEmpty() && it.first().isLetterOrDigit() }
    // One-letter words ("z", "a") are prepositions: "Zprávy z vesmíru" is ZV.
    val words = all.filter { it.length > 1 }.ifEmpty { all }
    val letters = when {
        words.isEmpty() -> return "?"
        words.size == 1 -> words[0].take(2)
        else -> "${words[0].first()}${words[1].first()}"
    }
    return letters.upperCs()
}

/** "1 ZDROJ", "3 ZDROJE", "6 ZDROJŮ": Czech count forms, uppercased. */
fun czechCount(count: Int, one: String, few: String, many: String): String =
    "$count ${when (count) { 1 -> one; in 2..4 -> few; else -> many }}".upperCs()

/** The next unread article after [currentId] in [orderedIds], wrapping round to the top; null when none is left. */
fun nextUnreadId(orderedIds: List<Long>, unreadIds: Set<Long>, currentId: Long): Long? {
    val index = orderedIds.indexOf(currentId)
    val after = if (index < 0) orderedIds else orderedIds.drop(index + 1) + orderedIds.take(index)
    return after.firstOrNull { it in unreadIds && it != currentId }
}

/** Splits a list into the unread ones and the read ones, each keeping the list's own order. */
fun <T> unreadFirst(items: List<T>, isUnread: (T) -> Boolean): Pair<List<T>, List<T>> = items.partition(isUnread)

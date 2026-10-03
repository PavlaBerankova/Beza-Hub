package com.berpod.app.ui.newepisodes

import java.time.Instant
import java.time.ZoneId

/** How far back Nové reaches. Everything but [All] drops episodes without a publish date. */
enum class NewPeriod(val days: Long?) {
    All(null),
    Today(0),
    Week(7),
    TwoWeeks(14),
    Month(30)
}

/** The oldest publish time (epoch millis) an episode may have to be inside [period], or null for no limit. */
fun periodStartMillis(period: NewPeriod, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
    val days = period.days ?: return null
    // "Dnes" is since this morning's midnight; the others are a rolling window counted back from now.
    if (days == 0L) return Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    return nowMillis - days * 24 * 60 * 60 * 1000
}

fun <T> filterByPeriod(items: List<T>, period: NewPeriod, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault(), publishedAt: (T) -> Long?): List<T> {
    val start = periodStartMillis(period, nowMillis, zone) ?: return items
    return items.filter { item -> publishedAt(item)?.let { it >= start } == true }
}

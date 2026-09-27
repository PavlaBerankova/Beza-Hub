package com.berpod.app.ui.dot

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

enum class CellRole { Played, Current, Rest }

/** [count] cells: the played share, then one "current" cell, then the rest; null progress = all rest. */
fun dotProgressCells(fraction: Float?, count: Int): List<CellRole> {
    if (fraction == null) return List(count) { CellRole.Rest }
    val played = (fraction.coerceIn(0f, 1f) * count).toInt()
    return List(count) { i ->
        when {
            i < played -> CellRole.Played
            i == played -> CellRole.Current
            else -> CellRole.Rest
        }
    }
}

data class RingDot(val x: Float, val y: Float, val role: CellRole)

/** [count] dots on a circle, clockwise from the top, coloured by [fraction] like [dotProgressCells]. */
fun ringDots(count: Int, center: Float, radius: Float, fraction: Float?): List<RingDot> {
    val roles = dotProgressCells(fraction, count)
    return List(count) { i ->
        val angle = Math.toRadians(-90.0 + i * 360.0 / count)
        RingDot(
            x = (center + radius * cos(angle)).toFloat(),
            y = (center + radius * sin(angle)).toFloat(),
            role = roles[i]
        )
    }
}

/** Decorative wave column heights (2..rows), stable for an episode [seed]. Not real audio. */
fun waveHeights(seed: Long, columns: Int = 36, rows: Int = 9): List<Int> {
    var state = seed * 6364136223846793005L + 1442695040888963407L
    var previous = rows / 2
    return List(columns) {
        state = state * 6364136223846793005L + 1442695040888963407L
        val step = ((state ushr 33) % 5).toInt() - 2 // -2..2, keeps neighbours close like a waveform
        previous = (previous + step).coerceIn(2, rows)
        previous
    }
}

/** Where along the wave [x] falls, 0..1. */
fun waveSeekFraction(x: Float, width: Float): Float = if (width <= 0f) 0f else (x / width).coerceIn(0f, 1f)

private val dotDate = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val dayMonth = DateTimeFormatter.ofPattern("dd.MM")
private val year = DateTimeFormatter.ofPattern("yyyy")

private fun Long.local(zone: ZoneId) = Instant.ofEpochMilli(this).atZone(zone)

fun formatDotDate(millis: Long?, zone: ZoneId = ZoneId.systemDefault()): String? = millis?.local(zone)?.format(dotDate)
fun formatDayMonth(millis: Long?, zone: ZoneId = ZoneId.systemDefault()): String? = millis?.local(zone)?.format(dayMonth)
fun formatYear(millis: Long?, zone: ZoneId = ZoneId.systemDefault()): String? = millis?.local(zone)?.format(year)

fun isPublishedToday(millis: Long?, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
    millis != null && millis.local(zone).toLocalDate() == LocalDate.ofInstant(Instant.ofEpochMilli(nowMillis), zone)

/** Published within the last [days] days. */
fun isRecentEpisode(millis: Long?, nowMillis: Long, days: Long = 7): Boolean =
    millis != null && nowMillis - millis in 0..days * 24 * 60 * 60 * 1000

private val czech = Locale.forLanguageTag("cs")

fun String.upperCs(): String = uppercase(czech)

/** "1 ODBĚR", "3 ODBĚRY", "5 ODBĚRŮ". */
fun subscriptionCountText(count: Int): String = when (count) {
    1 -> "1 ODBĚR"
    in 2..4 -> "$count ODBĚRY"
    else -> "$count ODBĚRŮ"
}

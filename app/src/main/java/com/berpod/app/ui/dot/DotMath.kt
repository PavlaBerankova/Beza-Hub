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

/**
 * [count] dots evenly spaced along a rounded square (half side [half], corners [cornerRadius]), clockwise from
 * the top centre, coloured by [fraction] like [dotProgressCells].
 */
fun roundedSquareDots(count: Int, center: Float, half: Float, cornerRadius: Float, fraction: Float?): List<RingDot> {
    val roles = dotProgressCells(fraction, count)
    val straight = half - cornerRadius // half of each straight side
    val arc = (Math.PI * cornerRadius / 2).toFloat()
    val perimeter = 8 * straight + 4 * arc
    return List(count) { i ->
        // Walk the outline one eighth-side / corner at a time; by symmetry each quarter is "half side, corner, half side".
        var s = i * perimeter / count
        val quarter = (s / (2 * straight + arc)).toInt().coerceAtMost(3)
        s -= quarter * (2 * straight + arc)
        // Position in the top-right quarter (from top centre to right centre), with y pointing down.
        val (qx, qy) = when {
            s <= straight -> s to -half
            s <= straight + arc -> {
                val angle = -Math.PI / 2 + (s - straight) / cornerRadius
                (straight + cornerRadius * cos(angle)).toFloat() to (-straight + cornerRadius * sin(angle)).toFloat()
            }
            else -> half to -straight + (s - straight - arc)
        }
        // Rotate that quarter by 90° steps clockwise.
        val (x, y) = when (quarter) {
            0 -> qx to qy
            1 -> -qy to qx
            2 -> -qx to -qy
            else -> qy to -qx
        }
        RingDot(center + x, center + y, roles[i])
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

package com.berpod.app.ui.dot

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.Dp
import com.berpod.app.ui.theme.DotTheme

enum class IconRole { Primary, Muted, Accent, OnAccent }

/** One SVG path of an icon; [stroke] = line width in viewport units, null = filled. */
data class IconPart(val d: String, val role: IconRole, val stroke: Float? = null)

class DotIconSpec(val width: Float, val height: Float, val parts: List<IconPart>)

private fun rect(x: Float, y: Float, w: Float, h: Float) = "M$x ${y}h${w}v${h}h${-w}z"

private fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) =
    "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"

private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

private fun px(x: Float, y: Float, s: Float = 2f, role: IconRole = IconRole.Accent) = IconPart(rect(x, y, s, s), role)

/** Icons copied from the Dot Matrix canvas ("04 · Ikony" and the screen artboards). */
object DotIcons {
    val Play = DotIconSpec(24f, 24f, listOf(IconPart("M8 5.5v13L18.5 12z", IconRole.Primary, 2.4f), px(10f, 11f)))
    val Pause = DotIconSpec(32f, 32f, listOf(
        IconPart(roundRect(7f, 7f, 5f, 18f, 2f), IconRole.Primary, 3f),
        IconPart(roundRect(20f, 7f, 5f, 18f, 2f), IconRole.Primary, 3f),
        px(15f, 10f), px(15f, 15f), px(15f, 20f)
    ))
    val Previous = DotIconSpec(24f, 24f, listOf(IconPart("M6 5v14", IconRole.Muted, 2.4f), IconPart("M19 5.5L10 12l9 6.5z", IconRole.Primary, 2.4f), px(14.6f, 11f)))
    val Next = DotIconSpec(24f, 24f, listOf(IconPart("M18 5v14", IconRole.Muted, 2.4f), IconPart("M5 5.5L14 12l-9 6.5z", IconRole.Primary, 2.4f), px(7.4f, 11f)))
    val Back15 = DotIconSpec(24f, 24f, listOf(IconPart("M4.5 12a7.5 7.5 0 1 0 2.2-5.3", IconRole.Primary, 2.2f), IconPart("M6.7 3v3.7H3", IconRole.Muted, 2.2f), px(18.3f, 10.8f, 2.4f)))
    val Forward30 = DotIconSpec(24f, 24f, listOf(IconPart("M19.5 12a7.5 7.5 0 1 1-2.2-5.3", IconRole.Primary, 2.2f), IconPart("M17.3 3v3.7H21", IconRole.Muted, 2.2f), px(3.3f, 10.8f, 2.4f)))
    val Speed = DotIconSpec(24f, 24f, listOf(IconPart("M4 17a8 8 0 1 1 16 0", IconRole.Primary, 2.4f), IconPart("M12 7v1.5M6.3 9.3l1 1M17.7 9.3l-1 1", IconRole.Muted, 2.4f), IconPart("M12 17l3.5-4.5", IconRole.Primary, 2.4f), px(11f, 16f)))
    val Sleep = DotIconSpec(24f, 24f, listOf(IconPart("M19 14.5A7.5 7.5 0 1 1 9.5 5a6 6 0 0 0 9.5 9.5z", IconRole.Primary, 2.4f), px(16f, 3.5f), px(19.5f, 7f, 1.8f, IconRole.Muted)))
    /** Solid play triangle for filled (accent) buttons. */
    val PlaySolid = DotIconSpec(24f, 24f, listOf(IconPart("M8 5v14l11-7z", IconRole.OnAccent)))
    val PlayLarge = DotIconSpec(32f, 32f, listOf(
        IconPart("M10 6.5v19L26 16z", IconRole.Primary, 3f),
        px(12f, 11f), px(14f, 13f), px(16f, 15f), px(14f, 17f), px(12f, 19f)
    ))
    val PauseLarge = Pause
    val Equalizer = DotIconSpec(24f, 24f, listOf(
        4.5f to 17f, 4.5f to 12.6f, 10.3f to 17f, 10.3f to 12.6f, 10.3f to 8.2f, 10.3f to 3.8f,
        16.1f to 17f, 16.1f to 12.6f, 16.1f to 8.2f
    ).map { (x, y) -> px(x, y, 3.4f) })
    val Queue = DotIconSpec(24f, 24f, listOf(
        IconPart("M4 6h12M4 11h12", IconRole.Primary, 2.4f),
        IconPart("M4 16h6", IconRole.Muted, 2.4f),
        px(14f, 13.5f, 1.9f), px(16.2f, 15.6f, 1.9f), px(14f, 17.7f, 1.9f)
    ))
    val Share = DotIconSpec(24f, 24f, listOf(
        IconPart("M12 4v10M8 7.5L12 4l4 3.5", IconRole.Primary, 2.4f),
        IconPart("M6 12v6.5A1.5 1.5 0 0 0 7.5 20h9a1.5 1.5 0 0 0 1.5-1.5V12", IconRole.Muted, 2.4f),
        px(11f, 16.5f)
    ))
    /** "In the queue": a check with the accent pixel, for outlined buttons. */
    val Queued = DotIconSpec(24f, 24f, listOf(IconPart("M5 12.5l4.5 4.5L19 7.5", IconRole.Primary, 2.4f), px(11f, 19.5f)))
    val Close = DotIconSpec(24f, 24f, listOf(IconPart("M6 6l12 12M18 6L6 18", IconRole.Primary, 2.6f)))
    val OpenExternal = DotIconSpec(24f, 24f, listOf(
        IconPart("M13 5h6v6M19 5l-8 8", IconRole.Primary, 2.4f),
        IconPart("M17 14v4a1.5 1.5 0 0 1-1.5 1.5h-9A1.5 1.5 0 0 1 5 18V8.5A1.5 1.5 0 0 1 6.5 7H10", IconRole.Muted, 2.4f),
        px(10f, 13f)
    ))
    val Trash = DotIconSpec(24f, 24f, listOf(
        IconPart("M5 7h14M10 7V5h4v2", IconRole.Primary, 2.4f),
        IconPart("M7 7l1 12.5h8L17 7", IconRole.Muted, 2.4f),
        px(11f, 12f)
    ))
    val Forward = DotIconSpec(24f, 24f, listOf(IconPart("M9 5l7 7-7 7", IconRole.Primary, 2.6f)))
    val Download = DotIconSpec(24f, 24f, listOf(
        IconPart("M12 4v9.5M7.5 9L12 13.5 16.5 9", IconRole.Primary, 2.4f),
        IconPart("M5 16v4h14v-4", IconRole.Muted, 2.4f),
        px(11f, 16.6f)
    ))
    val Add = DotIconSpec(24f, 24f, listOf(IconPart("M12 5v4.5M12 14.5V19M5 12h4.5M14.5 12H19", IconRole.Primary, 2.4f), px(11f, 11f)))
    val AddOnAccent = DotIconSpec(24f, 24f, listOf(IconPart("M12 5v14M5 12h14", IconRole.OnAccent, 2.6f)))
    val Back = DotIconSpec(24f, 24f, listOf(IconPart("M15 5l-7 7 7 7", IconRole.Primary, 2.6f)))
    val CloseDown = DotIconSpec(24f, 24f, listOf(IconPart("M6 9l6 6 6-6", IconRole.Primary, 2.6f)))
    val Tag = DotIconSpec(24f, 24f, listOf(IconPart("M3.5 12V4h8l9.5 9.5-8 8z", IconRole.Primary, 2.3f), IconPart(circle(8f, 8.5f, 1.8f), IconRole.Accent)))
    val Refresh = DotIconSpec(24f, 24f, listOf(
        IconPart("M20 11a8 8 0 1 0-2.3 5.7", IconRole.Primary, 2.3f),
        IconPart("M20 4v7h-7", IconRole.Muted, 2.3f),
        IconPart(circle(12f, 12f, 1.8f), IconRole.Accent)
    ))
    val Drag = DotIconSpec(24f, 24f, listOf(7.5f, 13.3f).flatMap { x ->
        listOf(4.5f, 10.4f, 16.3f).map { y -> px(x, y, 3.2f, IconRole.Muted) }
    })
    val Check = DotIconSpec(24f, 24f, listOf(IconPart("M5 12.5l4.5 4.5L19 7.5", IconRole.OnAccent, 3f)))
    val Podcasts = DotIconSpec(32f, 32f, listOf(
        IconPart(roundRect(12.5f, 5f, 7f, 13f, 3.5f), IconRole.Primary, 2.4f),
        IconPart(circle(16f, 10f, 1.7f), IconRole.Accent),
        IconPart("M9.5 14a6.5 6.5 0 0 0 13 0M16 20.5v5M12 26h8", IconRole.Primary, 2.4f),
        IconPart("M6 9a11 11 0 0 0 0 10M26 9a11 11 0 0 1 0 10", IconRole.Muted, 2.4f)
    ))
    val YouTube = DotIconSpec(32f, 32f, listOf(
        IconPart(roundRect(5f, 7f, 22f, 18f, 3f), IconRole.Primary, 2.4f),
        IconPart("M9.5 7v18M22.5 7v18M14.5 13l3.5 3-3.5 3", IconRole.Primary, 2.4f)
    ))
    val Articles = DotIconSpec(32f, 32f, listOf(
        IconPart("M7 7a18 18 0 0 1 18 18M7 13a12 12 0 0 1 12 12", IconRole.Primary, 2.4f),
        IconPart(circle(8.5f, 23.5f, 2f), IconRole.Primary, 2.4f)
    ))
    val WaveGlyph = DotIconSpec(34f, 14f, listOf(
        IconPart("M2 7h10", IconRole.Muted, 2.2f),
        IconPart(circle(9f, 7f, 2.4f), IconRole.Accent),
        IconPart("M16 4v6M20 2v10M24 5v4M28 3v8M32 6v2", IconRole.Accent, 2.2f)
    ))
}

/**
 * Draws a Dot Matrix icon at [size] (height; width follows the viewport ratio). Parts take the theme colour of
 * their role, or [monochrome] for all of them (unselected nav items).
 */
@Composable
fun DotIcon(spec: DotIconSpec, size: Dp, modifier: Modifier = Modifier, monochrome: Color? = null) {
    val colors = DotTheme.colors
    val paths = remember(spec) { spec.parts.map { PathParser().parsePathString(it.d).toPath() } }
    val strokes = remember(spec) { spec.parts.map { p -> p.stroke?.let { Stroke(width = it, cap = StrokeCap.Round, join = StrokeJoin.Round) } } }
    Canvas(modifier = modifier.size(width = size * (spec.width / spec.height), height = size)) {
        val s = this.size.height / spec.height
        scale(s, s, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            spec.parts.forEachIndexed { i, part ->
                val color = monochrome ?: when (part.role) {
                    IconRole.Primary -> colors.text
                    IconRole.Muted -> colors.textMuted
                    IconRole.Accent -> colors.accent
                    IconRole.OnAccent -> colors.onAccent
                }
                val stroke = strokes[i]
                if (stroke != null) {
                    drawPath(paths[i], color, style = stroke)
                } else {
                    drawPath(paths[i], color)
                }
            }
        }
    }
}

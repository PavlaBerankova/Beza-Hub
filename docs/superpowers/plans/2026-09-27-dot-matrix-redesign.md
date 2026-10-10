# Dot Matrix Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle the whole app in the "Dot Matrix" language (Oxanium, light + dark themes following the system, two-tone pixel icons, outlined square controls, dot-matrix progress) per the user's canvas, keeping current behaviour.

**Architecture:** A small design-system layer under `ui/dot/` (colors via `LocalDotColors`, Oxanium typography, a `DotIcon` renderer that draws SVG path data per colour role, shared components, and pure layout math with unit tests). Screens are then rewritten on top of it one by one. Behaviour (view models, repositories, drag/swipe, tags) is untouched except a podcast count for the hub header.

**Tech Stack:** Kotlin 2.4, Compose (BOM 2026.09.00, Material 3 1.4), Coil 3, reorderable 3.1.0, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-27-dot-matrix-redesign-design.md`

## Global Constraints

- Colors exactly as the spec's table; theme follows `isSystemInDarkTheme()`, no dynamic color.
- Oxanium variable font (`wght` 200–800), bundled as `app/src/main/res/font/oxanium.ttf`, used by every text style.
- Labels, meta lines, buttons, chips, titles: uppercase with `Locale("cs")`.
- Icons are drawn from SVG path data copied from the canvas; roles Primary / Muted / Accent follow the theme.
- Odběry keeps the 4-column grid, no "NOVĚ VYŠLO", a small square "+" bottom-right.
- Player step 1: only play/pause and seeking; no dead buttons.
- Touch targets ≥ 44 dp. Branch `feature/podcast-tags`. Tests `./gradlew :app:testDebugUnitTest`, build `./gradlew :app:assembleDebug`, from the repo root.

## Review Focus

1. Dark mode: every screen must read correctly (no hard-coded light colours left, status/nav bar icons light on dark) — Task 1 wires `enableEdgeToEdge`, manual pass in both modes in Task 8.
2. A podcast without artwork, and very long titles/uppercase Czech text, must not break layouts (ellipsis, fallback cover) — `CoverImage` fallback in Task 3; manual check.
3. Drag-and-drop, swipe-to-remove with undo and TalkBack actions in Fronta must keep working after the row rewrite — Task 5 keeps the same modifiers/semantics; manual drag + swipe.
4. Seeking on the wave must map taps/drags to the right position and not seek past the end — pinned in Task 2 (`waveSeekFraction clamps`).
5. Episode dates near midnight / "today" and "new" (≤7 days) must use the device time zone — pinned in Task 2 (`isPublishedToday` / `isRecentEpisode` tests with an explicit zone).

---

### Task 1: Theme — colors, Oxanium, typography, system bars

**Files:**
- Create: `app/src/main/res/font/oxanium.ttf` (from `https://github.com/google/fonts/raw/main/ofl/oxanium/Oxanium%5Bwght%5D.ttf`)
- Replace: `app/src/main/java/com/berpod/app/ui/theme/Color.kt`, `Type.kt`, `Theme.kt`
- Modify: `app/src/main/java/com/berpod/app/MainActivity.kt`, `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/values-night/themes.xml`

**Interfaces:**
- Produces: `data class DotColors(background, surface, text, textMuted, textStrong, outline, divider, accent, onAccent, waveIdle, waveMuted, ringIdle: Color)`; `LightDotColors`, `DarkDotColors`; `val LocalDotColors`; `object DotTheme { val colors: DotColors @Composable get }`; `val Oxanium: FontFamily`; `object DotType` with `display, showTitle, title, rowTitle, body, button, chip, label, meta` `TextStyle`s; `BerpodTheme(content)` choosing by `isSystemInDarkTheme()`.

- [ ] **Step 1: Add the font**

```bash
mkdir -p app/src/main/res/font
curl -sL -o app/src/main/res/font/oxanium.ttf "https://github.com/google/fonts/raw/main/ofl/oxanium/Oxanium%5Bwght%5D.ttf"
```

- [ ] **Step 2: Colors** — `Color.kt`:

```kotlin
package com.berpod.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The Dot Matrix palette (spec table). */
data class DotColors(
    val background: Color,
    val surface: Color,
    val text: Color,
    val textMuted: Color,
    val textStrong: Color,
    val outline: Color,
    val divider: Color,
    val accent: Color,
    val onAccent: Color,
    val waveIdle: Color,
    val waveMuted: Color,
    val ringIdle: Color
)

val LightDotColors = DotColors(
    background = Color(0xFFF5F4F8),
    surface = Color(0xFFFFFFFF),
    text = Color(0xFF16141C),
    textMuted = Color(0xFF6B6875),
    textStrong = Color(0xFF3F3C48),
    outline = Color(0xFFC9C5D3),
    divider = Color(0xFFDCD9E3),
    accent = Color(0xFF5E2E91),
    onAccent = Color(0xFFFFFFFF),
    waveIdle = Color(0xFFECE9F1),
    waveMuted = Color(0xFFBDB8CA),
    ringIdle = Color(0xFFE0DCE7)
)

val DarkDotColors = DotColors(
    background = Color(0xFF0A0B0D),
    surface = Color(0xFF141519),
    text = Color(0xFFF2F2F4),
    textMuted = Color(0xFF9A9BA3),
    textStrong = Color(0xFFC4C5CC),
    outline = Color(0xFF3A3B42),
    divider = Color(0xFF2A2B31),
    accent = Color(0xFFA67CFF),
    onAccent = Color(0xFF0A0B0E),
    waveIdle = Color(0xFF17181C),
    waveMuted = Color(0xFF4A4B53),
    ringIdle = Color(0xFF26272D)
)

val LocalDotColors = staticCompositionLocalOf { LightDotColors }

object DotTheme {
    val colors: DotColors
        @Composable @ReadOnlyComposable get() = LocalDotColors.current
}
```

- [ ] **Step 3: Type** — `Type.kt`:

```kotlin
package com.berpod.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.berpod.app.R

private fun oxanium(weight: Int) = Font(
    R.font.oxanium,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

val Oxanium = FontFamily(oxanium(400), oxanium(500), oxanium(600), oxanium(700), oxanium(800))

private fun style(size: Int, weight: Int, tracking: Double = 0.0, lineHeight: Double? = null) = TextStyle(
    fontFamily = Oxanium,
    fontSize = size.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.sp,
    lineHeight = lineHeight?.em ?: TextStyle.Default.lineHeight
)

/** Dot Matrix text styles (spec "Type"); callers uppercase the text where the spec says so. */
object DotType {
    val display = style(36, 800, 2.0, 1.0)
    val showTitle = style(34, 800, 1.0, 1.0)
    val title = style(18, 700, lineHeight = 1.25)
    val rowTitle = style(15, 600, lineHeight = 1.3)
    val body = style(14, 400, lineHeight = 1.45)
    val button = style(14, 700, 1.5)
    val segment = style(13, 600, 1.5)
    val chip = style(12, 600, 1.0)
    val label = style(11, 600, 1.5)
    val meta = style(11, 500, 1.0)
}

val Typography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Oxanium),
        displayMedium = base.displayMedium.copy(fontFamily = Oxanium),
        displaySmall = base.displaySmall.copy(fontFamily = Oxanium),
        headlineLarge = base.headlineLarge.copy(fontFamily = Oxanium),
        headlineMedium = base.headlineMedium.copy(fontFamily = Oxanium),
        headlineSmall = base.headlineSmall.copy(fontFamily = Oxanium),
        titleLarge = base.titleLarge.copy(fontFamily = Oxanium),
        titleMedium = base.titleMedium.copy(fontFamily = Oxanium),
        titleSmall = base.titleSmall.copy(fontFamily = Oxanium),
        bodyLarge = base.bodyLarge.copy(fontFamily = Oxanium),
        bodyMedium = base.bodyMedium.copy(fontFamily = Oxanium),
        bodySmall = base.bodySmall.copy(fontFamily = Oxanium),
        labelLarge = base.labelLarge.copy(fontFamily = Oxanium),
        labelMedium = base.labelMedium.copy(fontFamily = Oxanium),
        labelSmall = base.labelSmall.copy(fontFamily = Oxanium)
    )
}
```

- [ ] **Step 4: Theme** — `Theme.kt`:

```kotlin
package com.berpod.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private fun DotColors.toMaterial(dark: Boolean) = (if (dark) darkColorScheme() else lightColorScheme()).copy(
    primary = accent,
    onPrimary = onAccent,
    primaryContainer = accent,
    onPrimaryContainer = onAccent,
    secondaryContainer = accent.copy(alpha = 0.16f),
    onSecondaryContainer = text,
    background = background,
    onBackground = text,
    surface = background,
    onSurface = text,
    surfaceVariant = divider,
    onSurfaceVariant = textMuted,
    surfaceContainer = surface,
    surfaceContainerLow = surface,
    surfaceContainerHigh = surface,
    surfaceContainerHighest = surface,
    inverseSurface = text,
    inverseOnSurface = background,
    outline = outline,
    outlineVariant = divider
)

/** Dot Matrix theme, light or dark following the system setting. */
@Composable
fun BerpodTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) DarkDotColors else LightDotColors
    CompositionLocalProvider(LocalDotColors provides colors) {
        MaterialTheme(colorScheme = colors.toMaterial(dark), typography = Typography, content = content)
    }
}
```

- [ ] **Step 5: System bars** — in `MainActivity.onCreate`, before `setContent`, add `enableEdgeToEdge()` (import `androidx.activity.enableEdgeToEdge`); it picks light/dark bar icons from the system theme. `values/themes.xml`: `<style name="Theme.Berpod" parent="android:Theme.Material.Light.NoActionBar"><item name="android:windowBackground">#F5F4F8</item></style>`; new `values-night/themes.xml`: `<resources><style name="Theme.Berpod" parent="android:Theme.Material.NoActionBar"><item name="android:windowBackground">#0A0B0D</item></style></resources>`.

- [ ] **Step 6: Build and commit**

Run: `./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug` — Expected: PASS, BUILD SUCCESSFUL.

```bash
git add app/src/main/res/font app/src/main/res/values/themes.xml app/src/main/res/values-night app/src/main/java/com/berpod/app/ui/theme app/src/main/java/com/berpod/app/MainActivity.kt
git commit -m "Add the Dot Matrix theme: palette, Oxanium type, light and dark following the system"
```

---

### Task 2: Pure helpers with tests

**Files:**
- Create: `app/src/main/java/com/berpod/app/ui/dot/DotMath.kt`
- Test: `app/src/test/java/com/berpod/app/ui/dot/DotMathTest.kt`

**Interfaces:**
- Produces: `enum class CellRole { Played, Current, Rest }`; `fun dotProgressCells(fraction: Float?, count: Int): List<CellRole>`; `data class RingDot(val x: Float, val y: Float, val role: CellRole)`; `fun ringDots(count: Int, center: Float, radius: Float, fraction: Float?): List<RingDot>`; `fun waveHeights(seed: Long, columns: Int = 36, rows: Int = 9): List<Int>`; `fun waveSeekFraction(x: Float, width: Float): Float`; `fun formatDotDate(millis: Long?, zone: ZoneId = ZoneId.systemDefault()): String?` ("14.09.2026"); `fun formatDayMonth(millis, zone)` ("27.09"); `fun formatYear(millis, zone)` ("2026"); `fun isPublishedToday(millis: Long?, nowMillis: Long, zone): Boolean`; `fun isRecentEpisode(millis: Long?, nowMillis: Long, days: Long = 7): Boolean`; `fun String.upperCs(): String`.

- [ ] **Step 1: Failing tests** — `DotMathTest.kt`:

```kotlin
package com.berpod.app.ui.dot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.hypot

class DotMathTest {

    private val prague = ZoneId.of("Europe/Prague")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, prague).toInstant().toEpochMilli()

    @Test
    fun `progress cells split into played, one current and rest`() {
        val cells = dotProgressCells(0.3f, 10)
        assertEquals(3, cells.count { it == CellRole.Played })
        assertEquals(CellRole.Current, cells[3])
        assertEquals(6, cells.count { it == CellRole.Rest })
    }

    @Test
    fun `progress cells clamp and handle missing progress`() {
        assertEquals(List(5) { CellRole.Played }, dotProgressCells(1.4f, 5))
        assertEquals(List(5) { CellRole.Rest }, dotProgressCells(null, 5))
        assertEquals(CellRole.Current, dotProgressCells(0f, 5).first())
    }

    @Test
    fun `ring dots sit on the circle starting at the top`() {
        val dots = ringDots(count = 56, center = 140f, radius = 132f, fraction = 0.25f)
        assertEquals(56, dots.size)
        assertTrue(dots.all { abs(hypot(it.x - 140f, it.y - 140f) - 132f) < 0.01f })
        assertEquals(140f, dots[0].x, 0.01f)
        assertEquals(8f, dots[0].y, 0.01f)
        assertEquals(14, dots.count { it.role == CellRole.Played })
    }

    @Test
    fun `wave heights are deterministic and in range`() {
        val a = waveHeights(seed = 42)
        assertEquals(a, waveHeights(seed = 42))
        assertEquals(36, a.size)
        assertTrue(a.all { it in 2..9 })
        assertTrue(a != waveHeights(seed = 43))
    }

    @Test
    fun `wave seek fraction clamps to the wave`() {
        assertEquals(0.5f, waveSeekFraction(50f, 100f), 0.001f)
        assertEquals(0f, waveSeekFraction(-10f, 100f), 0.001f)
        assertEquals(1f, waveSeekFraction(130f, 100f), 0.001f)
        assertEquals(0f, waveSeekFraction(10f, 0f), 0.001f)
    }

    @Test
    fun `dates format the Dot Matrix way`() {
        assertEquals("14.09.2026", formatDotDate(at(2026, 9, 14), prague))
        assertEquals("27.09", formatDayMonth(at(2026, 9, 27), prague))
        assertEquals("2026", formatYear(at(2026, 9, 27), prague))
        assertEquals(null, formatDotDate(null, prague))
    }

    @Test
    fun `today uses the device zone`() {
        val now = at(2026, 9, 27, h = 0) + 30 * 60_000 // 00:30 local
        assertTrue(isPublishedToday(at(2026, 9, 27, h = 0) + 5 * 60_000, now, prague))
        assertFalse(isPublishedToday(at(2026, 9, 26, h = 23), now, prague))
        assertFalse(isPublishedToday(null, now, prague))
    }

    @Test
    fun `recent means within seven days`() {
        val now = at(2026, 9, 27)
        assertTrue(isRecentEpisode(at(2026, 9, 21), now))
        assertFalse(isRecentEpisode(at(2026, 9, 19), now))
        assertFalse(isRecentEpisode(null, now))
    }

    @Test
    fun `czech uppercase`() {
        assertEquals("VINOHRADSKÁ 12 · ZBÝVÁ", "Vinohradská 12 · zbývá".upperCs())
    }
}
```

- [ ] **Step 2: Run — Expected FAIL** (unresolved references).

- [ ] **Step 3: Implement** — `DotMath.kt`:

```kotlin
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
```

- [ ] **Step 4: Run — Expected PASS** (all tests).

- [ ] **Step 5: Commit** — `git add app/src/main/java/com/berpod/app/ui/dot/DotMath.kt app/src/test/java/com/berpod/app/ui/dot/DotMathTest.kt && git commit -m "Add Dot Matrix layout helpers: dot progress, ring, decorative wave, dates"`

---

### Task 3: Icons and shared components

**Files:**
- Create: `app/src/main/java/com/berpod/app/ui/dot/DotIcons.kt`, `app/src/main/java/com/berpod/app/ui/dot/DotComponents.kt`

**Interfaces:**
- Produces: `enum class IconRole { Primary, Muted, Accent, OnAccent }`; `data class IconPart(val d: String, val role: IconRole, val stroke: Float? /* null = fill */)`; `class DotIconSpec(val width: Float, val height: Float, val parts: List<IconPart>)`; `object DotIcons { Play, Pause, PlayLarge, PauseLarge, Equalizer, Queue, Download, Add, Back, CloseDown, Tag, Refresh, Drag, Check, Podcasts, YouTube, Articles, WaveGlyph }`; `@Composable fun DotIcon(spec, size: Dp, modifier = Modifier, monochrome: Color? = null)`; components `DotSegmented`, `DotChip`, `DotIconButton`, `DotPrimaryButton`, `SectionSeparator`, `DotProgress`, `CoverImage`, `DotTitle`.

- [ ] **Step 1: `DotIcons.kt`**

```kotlin
package com.berpod.app.ui.dot

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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
                if (part.stroke != null) {
                    drawPath(paths[i], color, style = Stroke(width = part.stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                } else {
                    drawPath(paths[i], color)
                }
            }
        }
    }
}
```

(Remove the unused `androidx.compose.ui.draw.scale` import if the compiler warns.)

- [ ] **Step 2: `DotComponents.kt`**

```kotlin
package com.berpod.app.ui.dot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType

/** Screen title: uppercase display type. */
@Composable
fun DotTitle(text: String, modifier: Modifier = Modifier) {
    Text(text.upperCs(), style = DotType.display, color = DotTheme.colors.text, modifier = modifier)
}

/** Bordered segmented switch; the selected segment is filled with the text colour. */
@Composable
fun DotSegmented(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = DotTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, c.outline, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) c.text else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .semantics { this.selected = selected },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label.upperCs(),
                    style = DotType.segment.copy(fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.W700 else androidx.compose.ui.text.font.FontWeight.W600),
                    color = if (selected) c.background else c.textMuted
                )
            }
        }
    }
}

/** Tag chip: outlined, or accent-filled with a check when selected. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DotChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null) {
    val c = DotTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(c.accent) else Modifier.border(1.dp, c.outline, shape))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { this.selected = selected; role = Role.Tab }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (selected) DotIcon(DotIcons.Check, 14.dp)
        Text(
            label.upperCs(),
            style = DotType.chip.copy(fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.W700 else androidx.compose.ui.text.font.FontWeight.W600),
            color = if (selected) c.onAccent else c.textStrong,
            maxLines = 1
        )
    }
}

/** Outlined square icon button (44 dp by default). */
@Composable
fun DotIconButton(
    icon: DotIconSpec,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    radius: Dp = 14.dp,
    iconSize: Dp = 20.dp,
    accentBorder: Boolean = false,
    filled: Color? = null,
    content: (@Composable () -> Unit)? = null
) {
    val c = DotTheme.colors
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .then(if (filled != null) Modifier.background(filled) else Modifier)
            .border(if (accentBorder) 1.5.dp else 1.dp, if (accentBorder) c.accent else c.outline, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        if (content != null) content() else DotIcon(icon, iconSize)
    }
}

/** Full-width accent button with an uppercase label. */
@Composable
fun DotPrimaryButton(label: String, icon: DotIconSpec?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = DotTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.accent)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            DotIcon(icon, 18.dp, monochrome = c.onAccent)
            Spacer(Modifier.width(10.dp))
        }
        Text(label.upperCs(), style = DotType.button, color = c.onAccent)
    }
}

/** "LEFT ——— RIGHT" section label; [leadingSquare] draws a small accent square first. */
@Composable
fun SectionSeparator(left: String, right: String? = null, modifier: Modifier = Modifier, leadingSquare: Boolean = false) {
    val c = DotTheme.colors
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (leadingSquare) Box(Modifier.size(7.dp).clip(RoundedCornerShape(1.dp)).background(c.accent))
        Text(left.upperCs(), style = DotType.label, color = c.textMuted)
        HorizontalDivider(modifier = Modifier.weight(1f), color = c.divider)
        if (right != null) Text(right.upperCs(), style = DotType.label, color = c.textMuted)
    }
}

/** A row of square progress cells (played accent, current text colour, rest divider). */
@Composable
fun DotProgress(fraction: Float?, cells: Int, modifier: Modifier = Modifier) {
    val c = DotTheme.colors
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        dotProgressCells(fraction, cells).forEach { role ->
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        when (role) {
                            CellRole.Played -> c.accent
                            CellRole.Current -> c.text
                            CellRole.Rest -> c.divider
                        }
                    )
            )
        }
    }
}

/** Podcast cover with a 1 dp divider border and a podcast-icon fallback. */
@Composable
fun CoverImage(url: String?, size: Dp?, radius: Dp, modifier: Modifier = Modifier) {
    val c = DotTheme.colors
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .then(if (size != null) Modifier.size(size) else Modifier.aspectRatio(1f))
            .clip(shape)
            .background(c.divider)
            .border(1.dp, c.divider, shape),
        contentAlignment = Alignment.Center
    ) {
        DotIcon(DotIcons.Podcasts, 28.dp, monochrome = c.textMuted)
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}
```

(Remove unused imports such as `TextOverflow`, `BorderStroke`, `padding`/`weight` duplicates the compiler flags; `weight` is a `RowScope` member so the `layout.weight` import is not needed.)

- [ ] **Step 3: Build and commit** — `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL. `git add app/src/main/java/com/berpod/app/ui/dot && git commit -m "Add Dot Matrix icons and shared components"`

---

### Task 4: Podcasts hub, Odběry, tag chips, navigation bar, placeholders

**Files:**
- Modify: `ui/podcasts/PodcastsHubScreen.kt`, `ui/podcastlist/PodcastListScreen.kt`, `ui/podcastlist/PodcastListViewModel.kt` (add `podcastCount`), `ui/common/TagChipRow.kt`, `ui/navigation/BerpodApp.kt`, `ui/comingsoon/ComingSoonScreen.kt`, `ui/common/EmptyState.kt`

- [ ] **Step 1: View model count** — `PodcastListUiState` gains `val podcastCount: Int = 0`; the `repository.observeFeeds()` observer sets `hasAnyPodcasts = feeds.isNotEmpty(), podcastCount = feeds.size`.

- [ ] **Step 2: Tag chips** — in `TagChipRow`, replace the private `TagChip` call sites with `DotChip(label, selected, onClick, onLongClick = ...)`; delete the private `TagChip`; row `contentPadding = PaddingValues(horizontal = 20.dp)`, spacing 8 dp. The "all" chip label stays `stringResource(R.string.filter_all)`.

- [ ] **Step 3: Hub** — `PodcastsHubScreen` body becomes:

```kotlin
    val listViewModel: PodcastListViewModel = viewModel(factory = subscriptionsFactory)
    val listState by listViewModel.uiState.collectAsState()
    val c = DotTheme.colors
    Column(modifier = Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            DotTitle(stringResource(R.string.nav_podcasts), Modifier.weight(1f))
            when (section) {
                PodcastsSection.Subscriptions -> {
                    val tagName = listState.tags.firstOrNull { it.id == listState.selectedTagId }?.name
                    Text(
                        buildString {
                            append("%02d ODBĚRY".format(listState.podcastCount))
                            if (tagName != null) append("\nFILTR: ").append(tagName.upperCs())
                        },
                        style = DotType.meta.copy(lineHeight = 1.5.em),
                        color = c.textMuted,
                        textAlign = TextAlign.End
                    )
                }
                PodcastsSection.New -> if (newEpisodesState.isRefreshing) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = c.accent, strokeWidth = 2.dp)
                    }
                } else {
                    DotIconButton(DotIcons.Refresh, stringResource(R.string.action_refresh), newEpisodesViewModel::onRefresh)
                }
                PodcastsSection.Queue -> Unit
            }
        }
        DotSegmented(
            options = PodcastsSection.entries.map { stringResource(it.labelRes) },
            selectedIndex = section.ordinal,
            onSelect = { section = PodcastsSection.entries[it] },
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(18.dp))
        Box(modifier = Modifier.weight(1f)) {
            when (section) {
                PodcastsSection.Subscriptions -> PodcastListScreen(subscriptionsFactory, onPodcastClick)
                PodcastsSection.Queue -> QueueScreen(queueFactory)
                PodcastsSection.New -> NewEpisodesScreen(newEpisodesFactory)
            }
        }
    }
```

(Remove `TopAppBar`, `SegmentedButton*` imports; add `DotTheme`, `DotType`, `DotTitle`, `DotSegmented`, `DotIconButton`, `DotIcons`, `upperCs`, `statusBarsPadding`, `em`, `TextAlign`, `background`.)

- [ ] **Step 4: Odběry** — in `PodcastListScreen`: `Scaffold(containerColor = DotTheme.colors.background, …)`; the FAB becomes

```kotlin
        floatingActionButton = {
            DotIconButton(
                icon = DotIcons.AddOnAccent,
                contentDescription = stringResource(R.string.fab_add_podcast),
                onClick = viewModel::onFabClick,
                size = 56.dp,
                radius = 16.dp,
                iconSize = 24.dp,
                filled = DotTheme.colors.accent
            )
        },
```

grid `contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 96.dp)`, spacing 12 dp, and `PodcastGridItem` draws `CoverImage(feed.imageUrl, size = null, radius = 14.dp)` inside the existing `combinedClickable` modifier (the modifier moves onto `CoverImage`'s `modifier`).

- [ ] **Step 5: Navigation bar** — in `BerpodApp.kt` replace `NavigationBar { … }` with:

```kotlin
                if (showNavigation) {
                    val c = DotTheme.colors
                    Column(Modifier.background(c.background)) {
                        HorizontalDivider(color = c.divider)
                        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(82.dp)) {
                            bottomTabs.forEach { screen ->
                                val selected = currentRoute == screen.route
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable(role = Role.Tab) {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                        .semantics { this.selected = selected },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    DotIcon(iconFor(screen), 30.dp, monochrome = if (selected && screen == Screen.Podcasts) null else if (selected) c.text else c.textMuted)
                                    Spacer(Modifier.height(6.dp))
                                    Text(labelFor(screen).upperCs(), style = DotType.label, color = if (selected) c.text else c.textMuted)
                                }
                            }
                        }
                    }
                }
```

with `iconFor` returning `DotIconSpec` (`Screen.Podcasts -> DotIcons.Podcasts`, `Screen.YouTube -> DotIcons.YouTube`, `Screen.Articles -> DotIcons.Articles`, else `DotIcons.Podcasts`). Remove Material nav imports and the `Icons.Filled.*` nav icons. The outer `Scaffold` gets `containerColor = DotTheme.colors.background`.

- [ ] **Step 6: Placeholders and empty state** — `ComingSoonScreen(title, icon: DotIconSpec)`: `Column(fillMaxSize, background, statusBarsPadding)` with `DotTitle(title)` padded 20/16 and the empty state below. `EmptyState` becomes `EmptyState(icon: DotIconSpec, title, subtitle)`: `DotIcon(icon, 56.dp)`, title `DotType.rowTitle.copy(fontWeight = W700)` uppercase text color, subtitle `DotType.body` muted, centred. Update callers: Odběry empty → `DotIcons.Podcasts`; tag filter empty → `DotIcons.Tag`; Queue empty → `DotIcons.Queue`; Nové empty → `DotIcons.Refresh`; detail empty → `DotIcons.Podcasts`; YouTube → `DotIcons.YouTube`; Články → `DotIcons.Articles`.

- [ ] **Step 7: Build, quick look, commit** — `./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug`; install on the emulator, screenshot Odběry and YouTube (light). `git add -A app/src && git commit -m "Restyle the Podcasts tab, Odběry, tag chips and navigation in Dot Matrix"`

---

### Task 5: Fronta

**Files:** `ui/queue/QueueScreen.kt`

- [ ] **Step 1:** Scaffold `containerColor = DotTheme.colors.background`. Replace the summary `Text` with `SectionSeparator(left = episodeCountText(n), right = total?.let { "CELKEM $it" })` — split `queueSummary` into count and total: add `fun queueTotalText(items): String?` in `QueueSummary.kt` returning `formatDuration(total)` (and keep `queueSummary` for its tests), padded 20 dp; chips below (existing `TagChipRow`); list `contentPadding = PaddingValues(horizontal = 12.dp)`.

- [ ] **Step 2:** Rewrite `QueueRow` (signature unchanged):

```kotlin
@Composable
private fun QueueRow(item: QueueItemWithEpisode, uiState: QueueUiState, viewModel: QueueViewModel, handleModifier: Modifier) {
    val c = DotTheme.colors
    val episode = item.episode
    val isCurrent = uiState.currentlyPlayingEpisodeId == episode.id
    val isPlaying = isCurrent && uiState.isPlaying
    val fraction = episodeProgressFraction(episode.lastPositionMs, episode.durationSeconds)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isCurrent) c.surface else c.background)
            .padding(start = 4.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = handleModifier.size(width = 28.dp, height = 44.dp), contentAlignment = Alignment.Center) {
            DotIcon(DotIcons.Drag, 22.dp)
        }
        CoverImage(item.podcastImageUrl, 64.dp, 12.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                listOfNotNull(item.podcastTitle, formatDotDate(episode.pubDateMillis)).joinToString(" · ").upperCs(),
                style = DotType.meta.copy(fontSize = 10.sp, fontWeight = FontWeight.W600), color = c.textMuted,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(episode.title, style = DotType.rowTitle, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                remainingText(episode)?.let {
                    Text(it.upperCs(), style = DotType.chip.copy(fontWeight = FontWeight.W700), color = c.accent, maxLines = 1)
                }
                if (fraction != null) DotProgress(fraction, cells = 16, modifier = Modifier.weight(1f))
            }
        }
        DotIconButton(
            icon = if (isPlaying) DotIcons.Equalizer else DotIcons.Play,
            contentDescription = stringResource(if (isPlaying) R.string.action_pause else R.string.action_play),
            onClick = { viewModel.onPlayOrPause(item) },
            size = 48.dp,
            iconSize = 22.dp,
            accentBorder = isCurrent
        )
    }
    if (!isCurrent) HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = c.divider)
}
```

(Keep `handleModifier`'s semantics/drag; `ReorderableQueueList`, swipe box and snackbar unchanged; `RemoveBackground` keeps using `MaterialTheme.colorScheme.error`.)

- [ ] **Step 3:** Build; emulator: drag a row, swipe one away and undo. Commit `"Restyle the queue in Dot Matrix"`.

---

### Task 6: Nové

**Files:** `ui/newepisodes/NewEpisodesScreen.kt`

- [ ] **Step 1:** Scaffold background; above the list `SectionSeparator("DNES", "NEJNOVĚJŠÍ", leadingSquare = true)` padded 20 dp; list `contentPadding = PaddingValues(horizontal = 20.dp)`; each item:

```kotlin
                    val c = DotTheme.colors
                    val today = isPublishedToday(item.episode.pubDateMillis, System.currentTimeMillis())
                    val isPlaying = uiState.currentlyPlayingEpisodeId == item.episode.id && uiState.isPlaying
                    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        CoverImage(item.podcastImageUrl, 72.dp, 14.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (today) Box(Modifier.size(7.dp).clip(RoundedCornerShape(1.dp)).background(c.accent))
                                Text(
                                    listOfNotNull(formatDotDate(item.episode.pubDateMillis), formatDuration(item.episode.durationSeconds)).joinToString(" · ").upperCs(),
                                    style = DotType.meta.copy(fontWeight = FontWeight.W600), color = c.textMuted
                                )
                            }
                            Text(item.episode.title, style = DotType.rowTitle, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DotIconButton(DotIcons.Download, stringResource(R.string.action_download), {}, radius = 12.dp, iconSize = 22.dp)
                                DotIconButton(DotIcons.Queue, stringResource(R.string.action_add_to_queue), { viewModel.onAddToQueue(item.episode.id) }, radius = 12.dp, iconSize = 22.dp)
                                Spacer(Modifier.weight(1f))
                                DotIconButton(
                                    if (isPlaying) DotIcons.Equalizer else DotIcons.Play,
                                    stringResource(if (isPlaying) R.string.action_pause else R.string.action_play),
                                    { viewModel.onPlay(item) }, size = 50.dp, iconSize = 22.dp, accentBorder = isPlaying
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = c.divider)
```

- [ ] **Step 2:** Build, emulator screenshot, commit `"Restyle new episodes in Dot Matrix"`.

---

### Task 7: Detail podcastu and mini player

**Files:** `ui/podcastlist/PodcastEpisodesScreen.kt`, `PodcastHeader.kt`, `ShowEpisodeRow.kt`, `ShowPage.kt` (+ `ShowPageTest.kt`), `ui/player/MiniPlayerBar.kt`, `app/build.gradle.kts`, `gradle/libs.versions.toml`

- [ ] **Step 1: Remove the colour header pieces** — delete `pickHeaderColor`, `isLightColor` (and their tests in `ShowPageTest`), `isBarOverHeader` + `HeaderBarTest.kt`, `HeaderTagChip`, the palette dependency (`libs.androidx.palette` line and its catalog entries), `coverColor`.

- [ ] **Step 2: `PodcastHeader`** — new signature `PodcastHeader(title, author, imageUrl, tags: List<String>, playButton: HeaderPlayButton?, onBack, onTagClick, onRefresh, isRefreshing)`, content:

```kotlin
    val c = DotTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DotIconButton(DotIcons.Back, stringResource(R.string.action_back), onBack)
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                tags.forEach { DotChip(it, selected = true, onClick = onTagClick) }
            }
            DotIconButton(DotIcons.Tag, stringResource(R.string.action_manage_tags), onTagClick)
            if (isRefreshing) Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), color = c.accent, strokeWidth = 2.dp) }
            else DotIconButton(DotIcons.Refresh, stringResource(R.string.action_refresh), onRefresh)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            CoverImage(imageUrl, 150.dp, 20.dp)
            Column(Modifier.weight(1f).padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(listOfNotNull("PODCAST", tags.firstOrNull()).joinToString(" · ").upperCs(), style = DotType.label.copy(letterSpacing = 2.sp), color = c.textMuted)
                Text(title.upperCs(), style = DotType.showTitle, color = c.text, maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (author != null) Text(author, style = DotType.body.copy(fontWeight = FontWeight.W500), color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (playButton != null) DotPrimaryButton(playButton.label, playButton.icon, playButton.onClick)
        SectionSeparator(stringResource(R.string.section_episodes), stringResource(R.string.section_newest))
    }
```

`HeaderPlayButton.icon` becomes `DotIconSpec` (`DotIcons.Play` / `DotIcons.Pause`). Strings: `action_back` "Zpět", `section_episodes` "Epizody", `section_newest` "Nejnovější".

- [ ] **Step 3: `ShowEpisodeRow`**:

```kotlin
    val c = DotTheme.colors
    val recent = isRecentEpisode(episode.pubDateMillis, System.currentTimeMillis())
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            formatDayMonth(episode.pubDateMillis)?.let { Text(it, style = DotType.rowTitle.copy(fontWeight = FontWeight.W700, letterSpacing = 1.sp), color = if (recent) c.accent else c.text) }
            formatYear(episode.pubDateMillis)?.let { Text(it, style = DotType.meta.copy(fontSize = 12.sp), color = c.textMuted) }
            Spacer(Modifier.weight(1f))
            if (recent) Text("NOVÁ", style = DotType.label.copy(fontSize = 10.sp, fontWeight = FontWeight.W700), color = c.onAccent,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(c.accent).padding(horizontal = 8.dp, vertical = 3.dp))
        }
        Text(episode.title, style = DotType.title, color = c.text)
        descriptionSnippet(episode.description)?.let { Text(it, style = DotType.body, color = c.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DotIconButton(DotIcons.Queue, stringResource(R.string.action_add_to_queue), onAddToQueue, radius = 12.dp, iconSize = 22.dp)
            DotIconButton(DotIcons.Download, stringResource(R.string.action_download), {}, radius = 12.dp, iconSize = 22.dp)
            Spacer(Modifier.weight(1f))
            remainingText(episode)?.let { Text(it.upperCs(), style = DotType.rowTitle.copy(letterSpacing = 1.sp), color = c.text) }
            DotIconButton(
                if (isCurrentlyPlaying) DotIcons.Equalizer else DotIcons.Play,
                stringResource(if (isCurrentlyPlaying) R.string.action_pause else R.string.action_play),
                onPlayOrPause, size = 50.dp, iconSize = 22.dp, accentBorder = recent || isCurrentlyPlaying
            )
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = c.divider)
```

- [ ] **Step 4: Screen** — `PodcastEpisodesScreen`: `Scaffold(containerColor = background, contentWindowInsets = WindowInsets(0))` with no `topBar`; body `Box { LazyColumn(state, Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 96.dp)) { item { PodcastHeader(...) } … }; AnimatedVisibility(visible = listState.firstVisibleItemIndex > 0) { solid bar } }` where the solid bar is a `Row(Modifier.fillMaxWidth().background(c.background).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp))` with back `DotIconButton`, title `Text(uiState.feedTitle.upperCs(), DotType.button, maxLines 1, weight 1)`, tag and refresh `DotIconButton`s, and a divider under it. Remove `TopAppBar`, colour extraction, `onSizeChanged` header/bar measuring.

- [ ] **Step 5: Mini player** — `MiniPlayerBar` card: `Surface(shape = RoundedCornerShape(18.dp), color = c.surface, border = BorderStroke(1.dp, c.divider), shadowElevation = 0.dp)`, inner `Column(padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp), spacedBy(6.dp))`: a row with `CoverImage(artworkUrl, 46.dp, 10.dp)`, texts (title `DotType.rowTitle.copy(fontSize = 14.sp)`; meta `"${podcastTitle} · ${formatPlaybackTime(positionMs)}".upperCs()` `DotType.meta` muted), `DotIconButton(if (isPlaying) DotIcons.Pause else DotIcons.Play, …, radius = 12.dp, iconSize = 22.dp, accentBorder = true)`; then `DotProgress(if (durationMs > 0) positionMs / durationMs.toFloat() else 0f, cells = 52)`.

- [ ] **Step 6:** `./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug`; emulator: detail header, scroll (solid bar), tag dialog, refresh, play; mini player. Commit `"Restyle the podcast detail and mini player in Dot Matrix"`.

---

### Task 8: Player and final pass

**Files:** `ui/player/PlayerScreen.kt`

- [ ] **Step 1:** Rewrite `PlayerScreen(viewModel, onBack)`:

```kotlin
@Composable
fun PlayerScreen(viewModel: PlayerViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val c = DotTheme.colors
    var draggingFraction by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1L)
    val fraction = draggingFraction ?: (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val heights = remember(state.currentEpisodeId) { waveHeights(state.currentEpisodeId ?: 0L) }

    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DotIconButton(DotIcons.CloseDown, stringResource(R.string.action_close_player), onBack)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                DotIcon(DotIcons.WaveGlyph, 14.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.now_playing).upperCs(), style = DotType.chip.copy(fontWeight = FontWeight.W700, letterSpacing = 2.5.sp), color = c.text)
            }
            Spacer(Modifier.size(44.dp))
        }
        Box(Modifier.padding(top = 18.dp).size(280.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val scale = size.width / 280f
                ringDots(56, center = 140f, radius = 132f, fraction = fraction).forEach { dot ->
                    drawCircle(
                        color = when (dot.role) { CellRole.Played -> c.accent; CellRole.Current -> c.text; CellRole.Rest -> c.ringIdle },
                        radius = 3f * scale,
                        center = Offset(dot.x * scale, dot.y * scale)
                    )
                }
            }
            AsyncImage(
                model = state.artworkUrl, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(210.dp).clip(CircleShape).border(1.dp, c.divider, CircleShape).background(c.divider)
            )
        }
        Text(state.title, style = DotType.title.copy(fontSize = 20.sp), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 18.dp))
        Text(state.podcastTitle.upperCs(), style = DotType.chip.copy(letterSpacing = 2.sp), color = c.textMuted, modifier = Modifier.padding(top = 6.dp))
        val seekLabel = stringResource(R.string.action_seek)
        Canvas(
            Modifier
                .padding(top = 20.dp)
                .fillMaxWidth()
                .aspectRatio(36f / 9f)
                .semantics { contentDescription = seekLabel }
                .pointerInput(Unit) {
                    detectTapGestures { viewModel.seekTo((waveSeekFraction(it.x, size.width.toFloat()) * duration).toLong()) }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = { draggingFraction?.let { viewModel.seekTo((it * duration).toLong()) }; draggingFraction = null },
                        onDragCancel = { draggingFraction = null }
                    ) { change, _ -> draggingFraction = waveSeekFraction(change.position.x, size.width.toFloat()) }
                }
        ) {
            val gap = 2.dp.toPx()
            val cell = (size.width - gap * 35) / 36
            val playedColumns = (fraction * 36).toInt()
            for (col in 0 until 36) for (row in 0 until 9) {
                val lit = row >= 9 - heights[col]
                drawRoundRect(
                    color = if (!lit) c.waveIdle else if (col < playedColumns) c.accent else c.waveMuted,
                    topLeft = Offset(col * (cell + gap), row * (cell + gap)),
                    size = Size(cell, cell),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(formatPlaybackTime((fraction * duration).toLong()), style = DotType.rowTitle.copy(letterSpacing = 1.sp), color = c.text)
            Spacer(Modifier.weight(1f))
            Text("−" + formatPlaybackTime(duration - (fraction * duration).toLong()), style = DotType.rowTitle.copy(letterSpacing = 1.sp), color = c.textMuted)
        }
        DotIconButton(
            if (state.isPlaying) DotIcons.PauseLarge else DotIcons.PlayLarge,
            stringResource(if (state.isPlaying) R.string.action_pause else R.string.action_play),
            viewModel::togglePlayPause,
            modifier = Modifier.padding(top = 24.dp),
            size = 78.dp, radius = 24.dp, iconSize = 36.dp, accentBorder = true, filled = c.surface
        )
    }
}
```

Strings: `action_close_player` "Zavřít přehrávač", `now_playing` "Přehrává se", `action_seek` "Posunout přehrávání". (`DotIconButton`'s accent border is 1.5 dp; the design uses 2 dp here — acceptable.)

- [ ] **Step 2:** `./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug`.

- [ ] **Step 3: Full manual pass** in light and dark (`adb shell cmd uimode night yes|no`): Odběry, Fronta (drag, swipe+undo), Nové, detail (scroll bar, tags, refresh, play), mini player, player (tap/drag seek, play/pause), YouTube, Články; status bar icons readable in both modes.

- [ ] **Step 4:** Commit `"Restyle the player in Dot Matrix: dot ring and seekable wave"`, then install on the phone if connected.

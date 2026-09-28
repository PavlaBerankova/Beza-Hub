package com.berpod.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabAccentTest {

    private fun luminance(color: Color): Double {
        fun channel(v: Float): Double {
            val d = v.toDouble()
            return if (d <= 0.03928) d / 12.92 else Math.pow((d + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    /** WCAG contrast ratio, 1..21. */
    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sorted().reversed()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun `every tab accent is readable on its background and under its own text`() {
        listOf(false, true).forEach { dark ->
            val base = if (dark) DarkDotColors else LightDotColors
            TabAccent.entries.forEach { tab ->
                val colors = base.withAccent(tab, dark)
                assertTrue(
                    "$tab accent on ${if (dark) "dark" else "light"} background",
                    contrast(colors.accent, colors.background) >= 4.5
                )
                assertTrue(
                    "$tab text on its own accent (${if (dark) "dark" else "light"})",
                    contrast(colors.onAccent, colors.accent) >= 4.5
                )
            }
        }
    }

    @Test
    fun `the three tab accents are told apart by hue, not only brightness`() {
        listOf(false, true).forEach { dark ->
            val base = if (dark) DarkDotColors else LightDotColors
            val accents = TabAccent.entries.map { base.withAccent(it, dark).accent }
            accents.forEachIndexed { i, a ->
                accents.drop(i + 1).forEach { b ->
                    val distance = listOf(a.red - b.red, a.green - b.green, a.blue - b.blue).maxOf { kotlin.math.abs(it) }
                    assertTrue("accents $a and $b differ", distance > 0.25f)
                }
            }
        }
    }

    @Test
    fun `routes map to their tab's colour, detail screens included`() {
        assertEquals(TabAccent.Podcasts, tabAccentFor("podcasts"))
        assertEquals(TabAccent.Podcasts, tabAccentFor("podcast/{feedId}"))
        assertEquals(TabAccent.Podcasts, tabAccentFor("episode/{episodeId}"))
        assertEquals(TabAccent.Podcasts, tabAccentFor("player"))
        assertEquals(TabAccent.Podcasts, tabAccentFor(null))
        assertEquals(TabAccent.YouTube, tabAccentFor("youtube"))
        assertEquals(TabAccent.Articles, tabAccentFor("articles"))
        assertEquals(TabAccent.Articles, tabAccentFor("article/{articleId}?tag={tagId}"))
        assertEquals(TabAccent.Articles, tabAccentFor("article-sources"))
    }
}

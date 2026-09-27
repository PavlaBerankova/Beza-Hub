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

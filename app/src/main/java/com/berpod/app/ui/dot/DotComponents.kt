package com.berpod.app.ui.dot

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
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
            .minimumInteractiveComponentSize()
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
            // Filled buttons (the accent "+") have no outline unless an accent border is asked for.
            .then(if (filled != null && !accentBorder) Modifier else Modifier.border(if (accentBorder) 1.5.dp else 1.dp, if (accentBorder) c.accent else c.outline, shape))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        if (content != null) content() else DotIcon(icon, iconSize)
    }
}

/** Full-width accent button with an uppercase label. */
@Composable
fun DotPrimaryButton(label: String, icon: DotIconSpec?, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = DotTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(14.dp))
            // Disabled: the same button, faded, not clickable.
            .background(if (enabled) c.accent else c.accent.copy(alpha = 0.35f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
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

/** "——— LABEL ———": a section label centred between two rules. */
@Composable
fun CenteredSeparator(label: String, modifier: Modifier = Modifier) {
    val c = DotTheme.colors
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = c.divider)
        Text(label.upperCs(), style = DotType.label, color = c.textMuted)
        HorizontalDivider(modifier = Modifier.weight(1f), color = c.divider)
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

/** A row of square progress cells (played accent, current text colour, rest divider), drawn in one Canvas. */
@Composable
fun DotProgress(fraction: Float?, cells: Int, modifier: Modifier = Modifier) {
    val c = DotTheme.colors
    val roles = remember(fraction?.let { (it.coerceIn(0f, 1f) * cells).toInt() }, cells) { dotProgressCells(fraction, cells) }
    // As tall as one cell: the width minus the 2 dp gaps, divided by the cell count.
    val sized = Modifier.layout { measurable, constraints ->
        val width = constraints.maxWidth
        val height = ((width - 2.dp.roundToPx() * (cells - 1)) / cells).coerceAtLeast(1)
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(width, height) { placeable.place(0, 0) }
    }
    Canvas(modifier = modifier.fillMaxWidth().then(sized)) {
        val gap = 2.dp.toPx()
        val cell = (size.width - gap * (cells - 1)) / cells
        val radius = CornerRadius(1.dp.toPx())
        roles.forEachIndexed { i, role ->
            drawRoundRect(
                color = when (role) {
                    CellRole.Played -> c.accent
                    CellRole.Current -> c.text
                    CellRole.Rest -> c.divider
                },
                topLeft = Offset(i * (cell + gap), 0f),
                size = Size(cell, cell),
                cornerRadius = radius
            )
        }
    }
}

/** Accent tag label that opens the tag dialog (not a selectable filter chip). */
@Composable
fun DotTagLabel(label: String, onClick: () -> Unit) {
    val c = DotTheme.colors
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(c.accent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label.upperCs(), style = DotType.chip.copy(fontWeight = FontWeight.W700), color = c.onAccent, maxLines = 1)
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

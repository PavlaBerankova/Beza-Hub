package com.berpod.app.ui.articles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType

/** A source's badge: its initials on an accent square. */
@Composable
fun SourceBadge(title: String, size: Dp = 48.dp) {
    val c = DotTheme.colors
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(c.accent),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initials(title),
            style = DotType.chip.copy(fontSize = (size.value / 3).sp, fontWeight = FontWeight.W800, letterSpacing = 1.sp),
            color = c.onAccent
        )
    }
}

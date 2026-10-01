package com.berpod.app.ui.youtube.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.berpod.app.ui.articles.initials
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType

/**
 * A channel's avatar: its real thumbnail via Coil when known, else a gradient badge with initials
 * (YouTube's own feed carries no channel image, so the gradient fallback is the common case).
 */
@Composable
fun ChannelAvatar(thumbnailUrl: String?, title: String, size: Dp, unreadCount: Int = 0) {
    val c = DotTheme.colors
    Box {
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(
                Brush.linearGradient(listOf(c.accent, c.accent.copy(alpha = 0.55f)))
            ),
            contentAlignment = Alignment.Center
        ) {
            if (thumbnailUrl != null) {
                AsyncImage(model = thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(CircleShape))
            } else {
                Text(
                    initials(title),
                    style = DotType.chip.copy(fontSize = (size.value / 3).sp, fontWeight = FontWeight.W800, letterSpacing = 1.sp),
                    color = c.onAccent
                )
            }
        }
        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(c.accent)
                    .border(2.dp, c.background, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(unreadCount.toString(), style = DotType.chip.copy(fontSize = 11.sp, fontWeight = FontWeight.W800), color = c.onAccent)
            }
        }
    }
}

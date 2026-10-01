package com.berpod.app.ui.youtube.newvideos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.berpod.app.data.local.YoutubeVideoEntity
import com.berpod.app.ui.dot.DotIcon
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.dot.DotProgress
import com.berpod.app.ui.dot.upperCs
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType
import com.berpod.app.ui.youtube.remainingVideoText

/**
 * Compact Dot Matrix video row (Nové, Fronta): 16:9 thumbnail, "CHANNEL · WHEN", title, optional
 * [actions] (the queue button). No play/pause square here — opening the row is how a video plays.
 */
@Composable
fun CompactVideoRow(
    video: YoutubeVideoEntity,
    channelTitle: String,
    whenLabel: String?,
    onClick: () -> Unit,
    showDivider: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val c = DotTheme.colors
    val fraction = episodeProgressFractionMs(video.lastPositionMs, video.durationMs)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(width = 132.dp, height = 74.dp).clip(RoundedCornerShape(10.dp)).background(c.divider)) {
                DotIcon(DotIcons.YouTube, 24.dp, monochrome = c.textMuted, modifier = Modifier.align(Alignment.Center))
                if (video.thumbnailUrl != null) {
                    AsyncImage(model = video.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().size(width = 132.dp, height = 74.dp))
                }
                if (fraction != null) {
                    DotProgress(fraction, cells = 24, modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 4.dp, vertical = 4.dp))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    listOfNotNull(channelTitle, whenLabel).joinToString(" · ").upperCs(),
                    style = DotType.meta.copy(fontSize = 10.sp, fontWeight = FontWeight.W600),
                    color = c.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(video.title, style = DotType.rowTitle, color = c.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                remainingVideoText(video)?.let {
                    Text(it.upperCs(), style = DotType.chip.copy(fontWeight = FontWeight.W700), color = c.accent, maxLines = 1)
                }
            }
            actions()
        }
        if (showDivider) HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = c.divider)
    }
}

private fun episodeProgressFractionMs(lastPositionMs: Long, durationMs: Long?): Float? {
    if (lastPositionMs <= 0L || durationMs == null || durationMs <= 0L) return null
    return (lastPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

package com.berpod.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.berpod.app.R
import com.berpod.app.data.local.EpisodeEntity
import com.berpod.app.ui.dot.CoverImage
import com.berpod.app.ui.dot.DotIconButton
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.dot.DotProgress
import com.berpod.app.ui.dot.formatDotDate
import com.berpod.app.ui.dot.upperCs
import com.berpod.app.ui.podcastlist.remainingText
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType

/**
 * Compact Dot Matrix row (Fronta, Nové): cover, "PODCAST · DATE", title, time with dot progress,
 * optional [actions] and a square play button. The current episode gets a surface background.
 */
@Composable
fun CompactEpisodeRow(
    episode: EpisodeEntity,
    podcastTitle: String,
    podcastImageUrl: String?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlayOrPause: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val c = DotTheme.colors
    val fraction = episodeProgressFraction(episode.lastPositionMs, episode.durationSeconds)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (isCurrent) c.surface else c.background)
                .padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CoverImage(podcastImageUrl, 64.dp, 12.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    listOfNotNull(podcastTitle, formatDotDate(episode.pubDateMillis)).joinToString(" · ").upperCs(),
                    style = DotType.meta.copy(fontSize = 10.sp, fontWeight = FontWeight.W600),
                    color = c.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(episode.title, style = DotType.rowTitle, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    remainingText(episode)?.let {
                        Text(it.upperCs(), style = DotType.chip.copy(fontWeight = FontWeight.W700), color = c.accent, maxLines = 1)
                    }
                    if (fraction != null) DotProgress(fraction, cells = 16, modifier = Modifier.weight(1f))
                }
            }
            actions()
            DotIconButton(
                icon = if (isPlaying) DotIcons.Equalizer else DotIcons.Play,
                contentDescription = stringResource(if (isPlaying) R.string.action_pause else R.string.action_play),
                onClick = onPlayOrPause,
                size = 48.dp,
                iconSize = 22.dp,
                accentBorder = isCurrent
            )
        }
        if (!isCurrent) HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = c.divider)
    }
}

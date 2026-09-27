package com.berpod.app.ui.podcastlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.berpod.app.ui.dot.formatDayMonth
import com.berpod.app.ui.dot.formatYear
import com.berpod.app.ui.dot.isRecentEpisode
import com.berpod.app.ui.dot.upperCs
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType

/** Dot Matrix episode row: date (accent + "NOVÁ" when recent), title, description, queue/download, time and play. */
@Composable
fun ShowEpisodeRow(
    episode: EpisodeEntity,
    isCurrentlyPlaying: Boolean,
    onPlayOrPause: () -> Unit,
    onAddToQueue: () -> Unit,
    nowMillis: Long,
    /** Mixed lists (Nové) show which podcast the episode is from: its cover and name above the date. */
    podcastTitle: String? = null,
    podcastImageUrl: String? = null
) {
    val c = DotTheme.colors
    val recent = isRecentEpisode(episode.pubDateMillis, nowMillis)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (podcastTitle != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CoverImage(podcastImageUrl, 48.dp, 10.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        podcastTitle.upperCs(),
                        style = DotType.label.copy(letterSpacing = 1.sp),
                        color = c.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    EpisodeDateLine(episode, recent)
                }
            }
        } else {
            EpisodeDateLine(episode, recent)
        }
        Text(episode.title, style = DotType.title, color = c.text)
        descriptionSnippet(episode.description)?.let {
            Text(it, style = DotType.body, color = c.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DotIconButton(DotIcons.Queue, stringResource(R.string.action_add_to_queue), onAddToQueue, radius = 12.dp, iconSize = 22.dp)
            DotIconButton(DotIcons.Download, stringResource(R.string.action_download), {}, radius = 12.dp, iconSize = 22.dp)
            Spacer(Modifier.weight(1f))
            remainingText(episode)?.let {
                Text(it.upperCs(), style = DotType.rowTitle.copy(letterSpacing = 1.sp), color = c.text, maxLines = 1)
            }
            DotIconButton(
                if (isCurrentlyPlaying) DotIcons.Equalizer else DotIcons.Play,
                stringResource(if (isCurrentlyPlaying) R.string.action_pause else R.string.action_play),
                onPlayOrPause,
                size = 50.dp,
                iconSize = 22.dp,
                accentBorder = recent || isCurrentlyPlaying
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = c.divider)
}

/** "27.09  2026 … [NOVÁ]": accent date and badge for episodes from the last 7 days. */
@Composable
private fun EpisodeDateLine(episode: EpisodeEntity, recent: Boolean) {
    val c = DotTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        formatDayMonth(episode.pubDateMillis)?.let {
            Text(it, style = DotType.rowTitle.copy(fontWeight = FontWeight.W700, letterSpacing = 1.sp), color = if (recent) c.accent else c.text)
        }
        formatYear(episode.pubDateMillis)?.let {
            Text(it, style = DotType.meta.copy(fontSize = 12.sp), color = c.textMuted)
        }
        Spacer(Modifier.weight(1f))
        if (recent) {
            Text(
                stringResource(R.string.badge_new).upperCs(),
                style = DotType.label.copy(fontSize = 10.sp, fontWeight = FontWeight.W700, letterSpacing = 1.sp),
                color = c.onAccent,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(c.accent).padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

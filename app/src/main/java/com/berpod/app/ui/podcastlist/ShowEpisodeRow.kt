package com.berpod.app.ui.podcastlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.berpod.app.R
import com.berpod.app.data.local.EpisodeEntity
import com.berpod.app.ui.common.formatPubDate

/** Apple Podcasts-style episode row: date, bold title, two-line description, then actions left and time + play right. */
@Composable
fun ShowEpisodeRow(
    episode: EpisodeEntity,
    isCurrentlyPlaying: Boolean,
    onPlayOrPause: () -> Unit,
    onAddToQueue: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
        formatPubDate(episode.pubDateMillis)?.let { date ->
            Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = episode.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp, end = 8.dp)
        )
        descriptionSnippet(episode.description)?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp, end = 8.dp)
            )
        }
        Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onAddToQueue, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = stringResource(R.string.action_add_to_queue),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = {}, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = stringResource(R.string.action_download),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.weight(1f))
            remainingText(episode)?.let { time ->
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 2.dp)
                )
            }
            // Nudged so the circle sits 16dp from the edge, mirroring the icons on the left.
            FilledTonalIconButton(onClick = onPlayOrPause, modifier = Modifier.offset(x = (-4).dp)) {
                Icon(
                    imageVector = if (isCurrentlyPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (isCurrentlyPlaying) R.string.action_pause else R.string.action_play),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

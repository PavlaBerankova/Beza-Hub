package com.berpod.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.berpod.app.R
import com.berpod.app.data.local.EpisodeEntity

@Composable
fun EpisodeListItem(
    episode: EpisodeEntity,
    podcastTitle: String?,
    podcastImageUrl: String?,
    onAddToQueue: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    onPlay: (() -> Unit)? = null,
    isCurrentlyPlaying: Boolean = false,
    onDownload: (() -> Unit)? = null,
    progressFraction: Float? = null,
    showArtwork: Boolean = false,
    leadingContent: @Composable (() -> Unit)? = null
) {
    val metaParts = listOfNotNull(formatPubDate(episode.pubDateMillis), formatDuration(episode.durationSeconds))

    Row(
        // A leading drag handle is a 48dp touch target; a 4dp start inset puts its icon 16dp from the edge.
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (leadingContent != null) 4.dp else 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingContent != null) {
            leadingContent()
        }
        if (showArtwork) {
            PodcastArtwork(imageUrl = podcastImageUrl, modifier = Modifier.padding(end = 12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            if (podcastTitle != null) {
                Text(
                    text = podcastTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (metaParts.isNotEmpty()) {
                Text(
                    text = metaParts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
            if (progressFraction != null) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
            }
            if (onDownload != null || onAddToQueue != null) {
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (onDownload != null) {
                        IconButton(onClick = onDownload, modifier = Modifier.size(48.dp)) {
                            Icon(
                                Icons.Filled.Download,
                                contentDescription = stringResource(R.string.action_download),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (onAddToQueue != null) {
                        IconButton(onClick = onAddToQueue, modifier = Modifier.size(48.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = stringResource(R.string.action_add_to_queue),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        if (onPlay != null) {
            OutlinedIconButton(
                onClick = onPlay,
                modifier = Modifier.padding(start = 12.dp).size(38.dp)
            ) {
                Icon(
                    imageVector = if (isCurrentlyPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(
                        if (isCurrentlyPlaying) R.string.action_pause else R.string.action_play
                    )
                )
            }
        }

        if (trailingContent != null) {
            trailingContent()
        }
    }
}

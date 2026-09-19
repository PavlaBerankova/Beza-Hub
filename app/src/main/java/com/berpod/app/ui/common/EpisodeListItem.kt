package com.berpod.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.berpod.app.R
import com.berpod.app.data.local.EpisodeEntity

@Composable
fun EpisodeListItem(
    episode: EpisodeEntity,
    podcastTitle: String?,
    podcastImageUrl: String?,
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    val subtitleParts = listOfNotNull(formatPubDate(episode.pubDateMillis), formatDuration(episode.durationSeconds))

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = episode.imageUrl ?: podcastImageUrl,
            contentDescription = null,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
        )
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (podcastTitle != null) {
                Text(
                    text = podcastTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (subtitleParts.isNotEmpty()) {
                Text(
                    text = subtitleParts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        if (trailingContent != null) {
            trailingContent()
        } else if (onAddToQueue != null || onAddToPlaylist != null) {
            IconButton(onClick = { isMenuExpanded = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = null)
            }
            DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }) {
                if (onAddToQueue != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_add_to_queue)) },
                        onClick = { isMenuExpanded = false; onAddToQueue() }
                    )
                }
                if (onAddToPlaylist != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_add_to_playlist)) },
                        onClick = { isMenuExpanded = false; onAddToPlaylist() }
                    )
                }
            }
        }
    }
}

package com.berpod.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.berpod.app.R
import com.berpod.app.data.local.DownloadStatus
import com.berpod.app.data.repository.DownloadToggle
import com.berpod.app.ui.dot.DotIconButton
import com.berpod.app.ui.dot.DotIcon
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.theme.DotTheme

/** What the three-dots menu of an episode row can do. */
enum class EpisodeMenuAction {
    Download, CancelDownload, DeleteDownload,
    AddToQueue, RemoveFromQueue,
    MarkPlayed, MarkUnplayed,
    OpenEpisode, OpenPodcast
}

/**
 * The menu's entries for an episode in its current state, in the order they are shown: its offline copy, its place
 * in the queue, played or not, then the two detail pages. [canOpenPodcast] is false on the podcast's own page.
 */
fun episodeMenuActions(
    isQueued: Boolean,
    isPlayed: Boolean,
    download: DownloadStatus?,
    canOpenPodcast: Boolean
): List<EpisodeMenuAction> = buildList {
    add(
        when (download) {
            null, DownloadStatus.FAILED -> EpisodeMenuAction.Download
            DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> EpisodeMenuAction.CancelDownload
            DownloadStatus.COMPLETED -> EpisodeMenuAction.DeleteDownload
        }
    )
    add(if (isQueued) EpisodeMenuAction.RemoveFromQueue else EpisodeMenuAction.AddToQueue)
    add(if (isPlayed) EpisodeMenuAction.MarkUnplayed else EpisodeMenuAction.MarkPlayed)
    add(EpisodeMenuAction.OpenEpisode)
    if (canOpenPodcast) add(EpisodeMenuAction.OpenPodcast)
}

private fun EpisodeMenuAction.labelRes(): Int = when (this) {
    EpisodeMenuAction.Download -> R.string.action_download
    EpisodeMenuAction.CancelDownload -> R.string.download_cancel_description
    EpisodeMenuAction.DeleteDownload -> R.string.download_remove_description
    EpisodeMenuAction.AddToQueue -> R.string.action_add_to_queue
    EpisodeMenuAction.RemoveFromQueue -> R.string.action_remove_from_queue
    EpisodeMenuAction.MarkPlayed -> R.string.menu_mark_played
    EpisodeMenuAction.MarkUnplayed -> R.string.menu_mark_unplayed
    EpisodeMenuAction.OpenEpisode -> R.string.menu_open_episode
    EpisodeMenuAction.OpenPodcast -> R.string.menu_open_podcast
}

/** The menu itself: the entries fit the episode's state; the download entry follows [LocalDownloads]. */
@Composable
fun EpisodeDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    episodeId: Long,
    isQueued: Boolean,
    isPlayed: Boolean,
    canOpenPodcast: Boolean,
    onAction: (EpisodeMenuAction) -> Unit
) {
    val download = LocalDownloads.current[episodeId]?.status
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        episodeMenuActions(isQueued, isPlayed, download, canOpenPodcast).forEach { action ->
            DropdownMenuItem(
                text = { Text(stringResource(action.labelRes())) },
                onClick = {
                    onDismiss()
                    onAction(action)
                }
            )
        }
    }
}

/** The three-dots button of an episode row and the menu it opens (the Fronta rows, where a long press drags). */
@Composable
fun EpisodeRowMenu(
    episodeId: Long,
    isQueued: Boolean,
    isPlayed: Boolean,
    canOpenPodcast: Boolean,
    onAction: (EpisodeMenuAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        // Borderless: just the dots, so the play button stays the row's one framed control.
        DotIconButton(
            DotIcons.MoreHoriz,
            stringResource(R.string.action_more),
            { expanded = true },
            // Narrower than a full button and pushed a little into the row's padding, so the dots sit at the edge.
            modifier = Modifier.offset(x = 6.dp),
            size = 30.dp,
            radius = 12.dp,
            iconSize = 22.dp,
            filled = Color.Transparent,
            // Muted, so the dots stay in the background of the row.
            content = { DotIcon(DotIcons.MoreHoriz, 22.dp, monochrome = DotTheme.colors.textMuted) }
        )
        EpisodeDropdownMenu(expanded, { expanded = false }, episodeId, isQueued, isPlayed, canOpenPodcast, onAction)
    }
}

/** The snackbar text for what a download button just did. */
@Composable
fun downloadToggleText(result: DownloadToggle): String = stringResource(
    when (result) {
        DownloadToggle.Started -> R.string.download_started
        DownloadToggle.Cancelled -> R.string.download_cancelled
        DownloadToggle.Removed -> R.string.download_removed
    }
)

package com.berpod.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.berpod.app.R
import com.berpod.app.data.local.DownloadStatus
import com.berpod.app.data.local.EpisodeDownloadEntity
import com.berpod.app.data.repository.progressPercent
import com.berpod.app.ui.dot.DotIcon
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.dot.upperCs
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType

/** Every episode's offline-copy state, so the episode rows can mark downloaded ones without each screen passing it down. */
val LocalDownloads = compositionLocalOf<Map<Long, EpisodeDownloadEntity>> { emptyMap() }

/**
 * The offline state of an episode, in quiet grey: the download arrow for a finished copy, a paler one for a copy
 * waiting its turn, and the arrow with the percentage while it downloads. Nothing otherwise. It sits at the right end
 * of the row, next to the three dots.
 */
@Composable
fun DownloadBadge(download: EpisodeDownloadEntity?, modifier: Modifier = Modifier) {
    if (download == null || download.status == DownloadStatus.FAILED) return
    val c = DotTheme.colors
    val percent = if (download.status == DownloadStatus.DOWNLOADING) progressPercent(download.downloadedBytes, download.totalBytes) else null
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (percent != null) {
            Text("$percent %".upperCs(), style = DotType.chip.copy(fontWeight = FontWeight.W700), color = c.textMuted, maxLines = 1)
        }
        // The same arrow as the swipe-to-download gesture, in the same quiet grey as the queue mark; paler while it waits.
        DotIcon(
            DotIcons.Download,
            18.dp,
            monochrome = if (download.status == DownloadStatus.QUEUED) c.textMuted.copy(alpha = 0.5f) else c.textMuted
        )
    }
}

/** The small "this episode is in the queue" mark of a row, quiet grey so it does not compete with the download badge. */
@Composable
fun QueuedMark(modifier: Modifier = Modifier) {
    DotIcon(DotIcons.Queued, 20.dp, modifier = modifier, monochrome = DotTheme.colors.textMuted)
}

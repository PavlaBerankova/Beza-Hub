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

/** "✓ STAŽENO" for an offline copy, its percentage while it downloads, "ČEKÁ" while it waits. Nothing otherwise. */
@Composable
fun DownloadBadge(download: EpisodeDownloadEntity?, modifier: Modifier = Modifier) {
    if (download == null || download.status == DownloadStatus.FAILED) return
    val c = DotTheme.colors
    val text = when (download.status) {
        DownloadStatus.COMPLETED -> stringResource(R.string.download_done)
        DownloadStatus.DOWNLOADING ->
            progressPercent(download.downloadedBytes, download.totalBytes)?.let { "$it %" } ?: stringResource(R.string.download_downloading)
        else -> stringResource(R.string.download_waiting)
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DotIcon(
            if (download.status == DownloadStatus.COMPLETED) DotIcons.DownloadDone else DotIcons.Download,
            14.dp,
            monochrome = c.accent
        )
        Text(text.upperCs(), style = DotType.chip.copy(fontWeight = FontWeight.W700), color = c.accent, maxLines = 1)
    }
}

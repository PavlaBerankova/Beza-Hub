package com.berpod.app.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.berpod.app.R
import com.berpod.app.data.repository.PlaybackUiState
import com.berpod.app.ui.common.formatPlaybackTime
import com.berpod.app.ui.dot.CoverImage
import com.berpod.app.ui.dot.DotIconButton
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.dot.DotProgress
import com.berpod.app.ui.dot.upperCs
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType

/** Floating Dot Matrix mini player: cover, title, "PODCAST · TIME", play/pause and a dot progress row. */
@Composable
fun MiniPlayerBar(
    state: PlaybackUiState,
    onClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    // True when nothing below it (no bottom navigation) keeps it clear of the system navigation bar.
    padForSystemBar: Boolean = false
) {
    val c = DotTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = c.surface,
        border = BorderStroke(1.dp, c.divider),
        modifier = Modifier
            .then(if (padForSystemBar) Modifier.navigationBarsPadding() else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CoverImage(state.artworkUrl, 46.dp, 10.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        state.title,
                        style = DotType.rowTitle.copy(fontSize = 14.sp),
                        color = c.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${state.podcastTitle} · ${formatPlaybackTime(state.positionMs)}".upperCs(),
                        style = DotType.meta,
                        color = c.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                DotIconButton(
                    if (state.isPlaying) DotIcons.Pause else DotIcons.Play,
                    stringResource(if (state.isPlaying) R.string.action_pause else R.string.action_play),
                    onPlayPauseClick,
                    radius = 12.dp,
                    iconSize = 22.dp,
                    accentBorder = true
                )
            }
            DotProgress(
                fraction = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f,
                cells = 52,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

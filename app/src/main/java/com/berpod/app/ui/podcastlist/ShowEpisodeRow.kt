package com.berpod.app.ui.podcastlist

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.berpod.app.data.local.EpisodeEntity
import com.berpod.app.ui.common.AddToQueueButton
import com.berpod.app.ui.common.CompactEpisodeRow

/** Episode row of a podcast's own page: the same compact row as Fronta and Nové, minus the cover. */
@Composable
fun ShowEpisodeRow(
    episode: EpisodeEntity,
    isCurrentlyPlaying: Boolean,
    onPlayOrPause: () -> Unit,
    isQueued: Boolean,
    onAddToQueue: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CompactEpisodeRow(
        episode = episode,
        podcastTitle = null,
        podcastImageUrl = null,
        isCurrent = isCurrentlyPlaying,
        isPlaying = isCurrentlyPlaying,
        onPlayOrPause = onPlayOrPause,
        onClick = onClick,
        showCover = false,
        isPlayed = episode.playedAtMs != null,
        modifier = modifier
    ) {
        AddToQueueButton(isQueued, onAddToQueue)
    }
}

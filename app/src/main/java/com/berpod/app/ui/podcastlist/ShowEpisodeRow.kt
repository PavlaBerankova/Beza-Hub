package com.berpod.app.ui.podcastlist

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.berpod.app.data.local.EpisodeEntity
import com.berpod.app.ui.common.CompactEpisodeRow
import com.berpod.app.ui.common.EpisodeMenuAction
import com.berpod.app.ui.common.EpisodeRowMenu
import com.berpod.app.ui.dot.isRecentEpisode

/** Episode row of a podcast's own page: the same compact row as Fronta and Nové, minus the cover. */
@Composable
fun ShowEpisodeRow(
    episode: EpisodeEntity,
    isCurrentlyPlaying: Boolean,
    onPlayOrPause: () -> Unit,
    isQueued: Boolean,
    onMenuAction: (EpisodeMenuAction) -> Unit,
    nowMillis: Long,
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
        // A played episode isn't "new" any more, whatever its date; neither is one that was seen on an earlier visit.
        isNew = episode.playedAtMs == null && !episode.hiddenFromNew && isRecentEpisode(episode.pubDateMillis, nowMillis),
        showQueuedMark = isQueued,
        modifier = modifier
    ) {
        // On the podcast's own page the menu has no entry for the podcast itself.
        EpisodeRowMenu(episode.id, isQueued = isQueued, isPlayed = episode.playedAtMs != null, canOpenPodcast = false, onAction = onMenuAction)
    }
}

package com.berpod.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.berpod.app.R
import com.berpod.app.data.local.EpisodeEntity
import com.berpod.app.ui.dot.CoverImage
import com.berpod.app.ui.dot.DotIcon
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.dot.formatDotDate
import com.berpod.app.ui.dot.upperCs
import com.berpod.app.ui.podcastlist.remainingText
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType

/** The text block (title and description) always holds this many lines, so every row has the same height. */
private const val TEXT_LINES = 4
private const val MAX_TITLE_LINES = 2

private val textLineHeight = 22.sp

/** A slimmer play button than the old 44 dp one, so it does not outweigh the title. */
private val PILL_HEIGHT = 36.dp

/** In Fronta and Nové the cover and the column beside it (date, title, play) are exactly this tall, so they line up. */
private val COVER_SIZE = 92.dp

/** The compact rows (title only) use slightly smaller type and a slimmer play button to fit the cover's height. */
private val COMPACT_LINE_HEIGHT = 20.sp
private val COMPACT_PILL_HEIGHT = 30.dp

/** The breathing room between the title and its description. */
private val TITLE_GAP = 3.dp

/**
 * Episode row (Fronta, Nové, a podcast's page), after Apple Podcasts: "DATE · PODCAST" and the "NOVÁ" badge on top,
 * then the title (at most two lines) with, on the podcast's page, the description in lighter type under it, filling a
 * block of four lines whatever their length (Fronta and Nové show the title alone, in a block of two lines). The cover
 * sits on the left; the column beside it ends with the play button with the time left and, at the far right, the offline
 * state and the optional [actions] (the three dots). The current episode gets a surface background.
 */
@Composable
fun CompactEpisodeRow(
    episode: EpisodeEntity,
    podcastTitle: String?,
    podcastImageUrl: String?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlayOrPause: () -> Unit,
    onClick: () -> Unit,
    /** False inside a podcast's own page, where the cover (and podcast name) would only repeat. */
    showCover: Boolean = true,
    isPlayed: Boolean = false,
    /** Shows the "NOVÁ" badge after the date. */
    isNew: Boolean = false,
    /** False in Fronta and Nové, which show just the title (two lines at most); the podcast's own page adds the description. */
    showDescription: Boolean = true,
    /** Shows the small queue mark at the right (left off where every row is queued anyway, like in Fronta). */
    showQueuedMark: Boolean = false,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val c = DotTheme.colors
    val description = remember(episode.id, episode.description) { plainDescription(episode.description) }
    // A one-line title leaves room for a third line of description; known once the title has been laid out.
    var titleLines by remember(episode.id) { mutableIntStateOf(MAX_TITLE_LINES) }
    val compact = !showDescription
    val lineHeight = if (compact) COMPACT_LINE_HEIGHT else textLineHeight
    val blockLines = if (showDescription) TEXT_LINES else MAX_TITLE_LINES
    val textBlockHeight = with(LocalDensity.current) { (lineHeight * blockLines).toDp() } + if (showDescription) TITLE_GAP else 0.dp

    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (isCurrent) c.surface else c.background)
                .clickable(onClick = onClick)
                .padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showCover) CoverImage(podcastImageUrl, COVER_SIZE, 14.dp)
            // One column holds everything else, so the eye goes down it once: date, title, then the controls.
            Column(
                modifier = Modifier.weight(1f).then(if (compact) Modifier.height(COVER_SIZE) else Modifier),
                // Compact: date at the top, play at the bottom, the title between, spread over exactly the cover's height.
                verticalArrangement = if (compact) Arrangement.SpaceBetween else Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        listOfNotNull(formatDotDate(episode.pubDateMillis), podcastTitle.takeIf { showCover }).joinToString(" · ").upperCs(),
                        style = DotType.meta.copy(fontSize = 10.sp, fontWeight = FontWeight.W600),
                        color = c.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isNew) {
                        Text(
                            stringResource(R.string.badge_new).upperCs(),
                            style = DotType.label.copy(fontSize = 10.sp, fontWeight = FontWeight.W700, letterSpacing = 1.sp),
                            color = c.onAccent,
                            maxLines = 1,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(c.accent).padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Column(modifier = Modifier.fillMaxWidth().height(textBlockHeight)) {
                    Text(
                        episode.title,
                        style = DotType.episodeTitle.copy(fontSize = if (compact) 15.sp else 16.sp, lineHeight = lineHeight),
                        color = if (isPlayed) c.textMuted else c.text,
                        maxLines = MAX_TITLE_LINES,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { titleLines = it.lineCount.coerceIn(1, MAX_TITLE_LINES) }
                    )
                    if (showDescription && description.isNotEmpty()) {
                        Spacer(Modifier.height(TITLE_GAP))
                        Text(
                            description,
                            style = DotType.body.copy(fontSize = 14.sp, lineHeight = textLineHeight),
                            color = c.textMuted.copy(alpha = 0.85f),
                            maxLines = TEXT_LINES - titleLines,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PlayPill(
                        isPlaying = isPlaying,
                        isCurrent = isCurrent,
                        // What the button says next to its icon: how much is left, or that the episode is done.
                        label = if (isPlayed) stringResource(R.string.status_played) else remainingText(episode),
                        isPlayed = isPlayed,
                        height = if (compact) COMPACT_PILL_HEIGHT else PILL_HEIGHT,
                        onClick = onPlayOrPause
                    )
                    Spacer(Modifier.weight(1f))
                    if (showQueuedMark) QueuedMark()
                    DownloadBadge(LocalDownloads.current[episode.id])
                    actions()
                }
            }
        }
    }
}

/**
 * The play button, after Apple Podcasts: the play (or pause) icon and, next to it, the time left, all in one squared,
 * outlined button. The one of the episode that is loaded in the player has the accent-coloured outline.
 */
@Composable
private fun PlayPill(isPlaying: Boolean, isCurrent: Boolean, label: String?, isPlayed: Boolean, height: Dp, onClick: () -> Unit) {
    val c = DotTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val description = stringResource(if (isPlaying) R.string.action_pause else R.string.action_play)
    Row(
        modifier = Modifier
            .height(height)
            .clip(shape)
            .border(if (isCurrent) 1.5.dp else 1.dp, if (isCurrent) c.accent else c.outline, shape)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DotIcon(if (isPlaying) DotIcons.Pause else DotIcons.Play, 16.dp)
        if (label != null) {
            Text(
                label.upperCs(),
                style = DotType.chip.copy(fontWeight = FontWeight.W600),
                color = if (isPlayed) c.textMuted else c.accent,
                maxLines = 1
            )
        }
    }
}

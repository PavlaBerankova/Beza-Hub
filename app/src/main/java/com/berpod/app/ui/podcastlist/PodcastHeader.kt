package com.berpod.app.ui.podcastlist

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import com.berpod.app.ui.dot.DotIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.berpod.app.R
import com.berpod.app.ui.articles.czechCount
import com.berpod.app.ui.dot.CoverImage
import com.berpod.app.ui.dot.DotIconButton
import com.berpod.app.ui.dot.DotIconSpec
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.dot.DotPrimaryButton
import com.berpod.app.ui.dot.upperCs
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.LocalDotColors
import com.berpod.app.ui.theme.DotType

/** The play button in the header: its label, icon and action. */
data class HeaderPlayButton(val label: String, val icon: DotIconSpec, val onClick: () -> Unit)

/** Round-the-header action row: back, then the tag, edit-RSS and refresh buttons. */
@Composable
fun PodcastActionsRow(
    isRefreshing: Boolean,
    onBack: () -> Unit,
    onTagClick: () -> Unit,
    onRefresh: () -> Unit,
    onEditRss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = DotTheme.colors
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DotIconButton(DotIcons.Back, stringResource(R.string.action_back), onBack)
        Spacer(Modifier.weight(1f))
        DotIconButton(DotIcons.Tag, stringResource(R.string.action_manage_tags), onTagClick)
        DotIconButton(DotIcons.Edit, stringResource(R.string.action_edit_rss), onEditRss)
        if (isRefreshing) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), color = c.accent, strokeWidth = 2.dp)
            }
        } else {
            DotIconButton(DotIcons.Refresh, stringResource(R.string.action_refresh), onRefresh)
        }
    }
}

/**
 * Dot Matrix show header: actions row, 150 dp cover with "PODCAST · TAG", title and author beside it,
 * the primary play button and the "EPIZODY ——— NEJNOVĚJŠÍ" separator.
 */
@Composable
fun PodcastHeader(
    title: String,
    author: String?,
    imageUrl: String?,
    tags: List<String>,
    playButton: HeaderPlayButton?,
    isRefreshing: Boolean,
    onBack: () -> Unit,
    onTagClick: () -> Unit,
    onRefresh: () -> Unit,
    onEditRss: () -> Unit,
    episodeCount: Int,
    /** "každý pátek"…; null when it can't be told. */
    frequency: String?,
    sort: EpisodeSort,
    filter: EpisodeFilter,
    /** A played/unplayed filter is on; marked with a dot on the button. */
    isFiltered: Boolean,
    onViewClick: () -> Unit,
    /** The cover's block colour, once known; the header is then a solid block of it, behind the status bar too. */
    blockColor: Color?
) {
    val base = DotTheme.colors
    val block by animateColorAsState(blockColor ?: base.background, label = "headerBlock")
    val c = if (blockColor == null) base else colorsOnBlock(base, block)
    Column {
    CompositionLocalProvider(LocalDotColors provides c) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(block)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        PodcastActionsRow(isRefreshing, onBack, onTagClick, onRefresh, onEditRss)
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            CoverImage(imageUrl, 130.dp, 18.dp)
            // Centred beside the cover while it's short; a long title grows the column instead of being cut.
            Column(
                modifier = Modifier.weight(1f).heightIn(min = 130.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
            ) {
                tags.firstOrNull()?.let {
                    Text(
                        it.upperCs(),
                        style = DotType.label.copy(letterSpacing = 2.sp),
                        color = c.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(title.upperCs(), style = DotType.showTitle.copy(fontSize = 30.sp), color = c.text)
                if (author != null) {
                    Text(
                        author,
                        style = DotType.body.copy(fontWeight = FontWeight.W500),
                        color = c.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (playButton != null) DotPrimaryButton(playButton.label, playButton.icon, playButton.onClick)
        // "37 EPIZOD · KAŽDÝ PÁTEK", centred under the button
        Text(
            listOfNotNull(czechCount(episodeCount, "epizoda", "epizody", "epizod"), frequency?.upperCs()).joinToString(" · "),
            style = DotType.label.copy(letterSpacing = 1.5.sp),
            color = c.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
    }
    // Below the block, in the normal colours.
    EpisodesRow(
        text = stringResource(filter.shownRes()).upperCs(),
        sortLabel = stringResource(sort.labelRes()),
        isFiltered = isFiltered,
        onClick = onViewClick,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp)
    )
    }
}

/**
 * "VŠECHNY EPIZODY ——— [NEJNOVĚJŠÍ ⌄]": what is listed, and the button for sorting and filtering.
 * The line runs behind both ends so the text takes the room it needs and is only cut when it really doesn't fit.
 */
@Composable
private fun EpisodesRow(text: String, sortLabel: String, isFiltered: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = DotTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        HorizontalDivider(color = c.divider)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text,
                style = DotType.label,
                color = c.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).background(c.background).padding(end = 10.dp)
            )
            Box(Modifier.background(c.background).padding(start = 10.dp)) {
                Row(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(shape)
                        .border(1.dp, if (isFiltered) c.accent else c.outline, shape)
                        .clickable(role = Role.Button, onClick = onClick)
                        .padding(start = 10.dp, end = 6.dp)
                        .height(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isFiltered) Box(Modifier.size(7.dp).clip(RoundedCornerShape(1.dp)).background(c.accent))
                    Text(sortLabel.upperCs(), style = DotType.label.copy(fontWeight = FontWeight.W700), color = c.text, maxLines = 1)
                    DotIcon(DotIcons.CloseDown, 16.dp)
                }
            }
        }
    }
}

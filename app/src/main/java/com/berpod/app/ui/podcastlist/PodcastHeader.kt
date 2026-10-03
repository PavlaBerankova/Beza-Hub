package com.berpod.app.ui.podcastlist

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.berpod.app.R
import com.berpod.app.ui.dot.CoverImage
import com.berpod.app.ui.dot.DotTagLabel
import com.berpod.app.ui.dot.DotIconButton
import com.berpod.app.ui.dot.DotIconSpec
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.dot.DotPrimaryButton
import com.berpod.app.ui.dot.SectionSeparator
import com.berpod.app.ui.dot.upperCs
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType

/** The play button in the header: its label, icon and action. */
data class HeaderPlayButton(val label: String, val icon: DotIconSpec, val onClick: () -> Unit)

/** Round-the-header action row: back, the podcast's tags, tag and refresh buttons. */
@Composable
fun PodcastActionsRow(
    tags: List<String>,
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
        // Right-aligned when they fit, scrollable when they don't.
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tags.forEach { DotTagLabel(it, onClick = onTagClick) }
            }
        }
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
    onEditRss: () -> Unit
) {
    val c = DotTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        PodcastActionsRow(tags, isRefreshing, onBack, onTagClick, onRefresh, onEditRss)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            CoverImage(imageUrl, 150.dp, 20.dp)
            Column(modifier = Modifier.weight(1f).padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    listOfNotNull(stringResource(R.string.label_podcast), tags.firstOrNull()).joinToString(" · ").upperCs(),
                    style = DotType.label.copy(letterSpacing = 2.sp),
                    color = c.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(title.upperCs(), style = DotType.showTitle, color = c.text, maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (author != null) {
                    Text(
                        author,
                        style = DotType.body.copy(fontWeight = FontWeight.W500),
                        color = c.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (playButton != null) DotPrimaryButton(playButton.label, playButton.icon, playButton.onClick)
        SectionSeparator(stringResource(R.string.section_episodes), stringResource(R.string.section_newest))
    }
}

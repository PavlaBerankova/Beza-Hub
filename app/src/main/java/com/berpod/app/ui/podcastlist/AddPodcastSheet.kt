package com.berpod.app.ui.podcastlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.berpod.app.R
import com.berpod.app.data.remote.ParsedFeedMeta
import com.berpod.app.ui.articles.czechCount
import com.berpod.app.ui.common.AddFeedController
import com.berpod.app.ui.common.AddFeedSheet
import com.berpod.app.ui.common.AddFeedState
import com.berpod.app.ui.common.AddFeedTexts
import com.berpod.app.ui.dot.CoverImage
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType

/** "PŘIDAT PODCAST": the shared sheet, previewing the podcast with its cover. */
@Composable
fun AddPodcastSheet(state: AddFeedState<ParsedFeedMeta>, controller: AddFeedController<ParsedFeedMeta>) {
    val c = DotTheme.colors
    AddFeedSheet(
        state = state,
        controller = controller,
        texts = AddFeedTexts(
            title = stringResource(R.string.add_feed_dialog_title),
            urlLabel = stringResource(R.string.add_podcast_label),
            urlHint = stringResource(R.string.add_podcast_hint),
            searching = stringResource(R.string.podcast_searching),
            notFound = stringResource(R.string.podcast_not_found),
            found = stringResource(R.string.podcast_found),
            tagsLabel = stringResource(R.string.tags),
            newTagChip = stringResource(R.string.new_tag_chip),
            newTagTitle = stringResource(R.string.new_tag_title),
            newTagLabel = stringResource(R.string.tag_name_label)
        ),
        feedUrlOf = { it.feedUrl }
    ) { meta ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CoverImage(meta.imageUrl, 48.dp, 10.dp)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    meta.title,
                    style = DotType.rowTitle.copy(fontSize = 16.sp, fontWeight = FontWeight.W700),
                    color = c.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(meta.author, czechCount(meta.episodes.size, "epizoda", "epizody", "epizod")).joinToString(" · "),
                    style = DotType.label.copy(letterSpacing = 1.sp),
                    color = c.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

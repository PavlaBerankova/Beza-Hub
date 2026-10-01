package com.berpod.app.ui.youtube.channels

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
import com.berpod.app.data.remote.ParsedYoutubeChannel
import com.berpod.app.ui.articles.czechCount
import com.berpod.app.ui.common.AddFeedController
import com.berpod.app.ui.common.AddFeedSheet
import com.berpod.app.ui.common.AddFeedState
import com.berpod.app.ui.common.AddFeedTexts
import com.berpod.app.ui.common.youtubeCategoryTexts
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType

/** "PŘIDAT KANÁL": the shared sheet, previewing a YouTube channel. */
@Composable
fun AddChannelSheet(state: AddFeedState<ParsedYoutubeChannel>, controller: AddFeedController<ParsedYoutubeChannel>) {
    val c = DotTheme.colors
    AddFeedSheet(
        state = state,
        controller = controller,
        texts = AddFeedTexts(
            title = stringResource(R.string.add_channel_title),
            urlLabel = stringResource(R.string.add_channel_label),
            urlHint = stringResource(R.string.add_channel_hint),
            searching = stringResource(R.string.channel_searching),
            notFound = stringResource(R.string.channel_not_found),
            found = stringResource(R.string.channel_found),
            tags = youtubeCategoryTexts()
        ),
        feedUrlOf = { it.feedUrl }
    ) { channel ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChannelAvatar(channel.thumbnailUrl, channel.title, 48.dp)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    channel.title,
                    style = DotType.rowTitle.copy(fontSize = 16.sp, fontWeight = FontWeight.W700),
                    color = c.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "RSS · " + czechCount(channel.videos.size, "video", "videa", "videí"),
                    style = DotType.label.copy(letterSpacing = 1.sp),
                    color = c.textMuted
                )
            }
        }
    }
}

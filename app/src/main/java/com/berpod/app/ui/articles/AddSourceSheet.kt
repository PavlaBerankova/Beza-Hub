package com.berpod.app.ui.articles

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
import com.berpod.app.data.remote.ParsedArticleFeed
import com.berpod.app.ui.common.AddFeedController
import com.berpod.app.ui.common.AddFeedSheet
import com.berpod.app.ui.common.AddFeedState
import com.berpod.app.ui.common.AddFeedTexts
import com.berpod.app.ui.common.articleCategoryTexts
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType

/** "PŘIDAT ZDROJ": the shared sheet, previewing an article source. */
@Composable
fun AddSourceSheet(state: AddFeedState<ParsedArticleFeed>, controller: AddFeedController<ParsedArticleFeed>) {
    val c = DotTheme.colors
    AddFeedSheet(
        state = state,
        controller = controller,
        texts = AddFeedTexts(
            title = stringResource(R.string.add_source_title),
            urlLabel = stringResource(R.string.add_source_label),
            urlHint = stringResource(R.string.add_source_hint),
            searching = stringResource(R.string.source_searching),
            notFound = stringResource(R.string.source_not_found),
            found = stringResource(R.string.source_found),
            tags = articleCategoryTexts()
        ),
        feedUrlOf = { it.feedUrl }
    ) { feed ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SourceBadge(feed.title)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    feed.title,
                    style = DotType.rowTitle.copy(fontSize = 16.sp, fontWeight = FontWeight.W700),
                    color = c.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "RSS · " + czechCount(feed.articles.size, "článek", "články", "článků"),
                    style = DotType.label.copy(letterSpacing = 1.sp),
                    color = c.textMuted
                )
            }
        }
    }
}

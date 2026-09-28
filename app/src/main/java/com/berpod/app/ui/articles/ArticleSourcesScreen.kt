package com.berpod.app.ui.articles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berpod.app.R
import com.berpod.app.ui.common.EmptyState
import com.berpod.app.ui.common.TagPickerSheet
import com.berpod.app.ui.common.articleCategoryTexts
import com.berpod.app.ui.dot.DotIconButton
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.dot.upperCs
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType

/** The article sources: tap one to set its categories, the bin deletes it with its articles. */
@Composable
fun ArticleSourcesScreen(viewModelFactory: ViewModelProvider.Factory, onBack: () -> Unit) {
    val viewModel: ArticleSourcesViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    val c = DotTheme.colors

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            DotIconButton(DotIcons.Back, stringResource(R.string.action_back), onBack)
            Text(
                stringResource(R.string.sources).upperCs(),
                style = DotType.chip.copy(fontWeight = FontWeight.W700, letterSpacing = 2.5.sp),
                color = c.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.size(44.dp))
        }
        if (uiState.feeds.isEmpty()) {
            EmptyState(icon = DotIcons.Articles, title = stringResource(R.string.articles_empty), subtitle = stringResource(R.string.articles_empty_subtitle))
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                items(uiState.feeds, key = { it.feed.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClickLabel = stringResource(R.string.action_edit_categories, item.feed.title)) { viewModel.onOpenCategories(item.feed) }
                            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SourceBadge(item.feed.title, 44.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(item.feed.title, style = DotType.rowTitle.copy(fontWeight = FontWeight.W700), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(
                                    (item.feed.siteUrl ?: item.feed.rssUrl).removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/'),
                                    czechCount(item.articleCount, "článek", "články", "článků")
                                ).joinToString(" · "),
                                style = DotType.label.copy(letterSpacing = 0.5.sp),
                                color = c.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        DotIconButton(DotIcons.Trash, stringResource(R.string.action_delete_source, item.feed.title), { viewModel.onRequestDelete(item.feed) })
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = c.divider)
                }
            }
        }
    }

    uiState.categories?.let { state ->
        TagPickerSheet(
            title = state.podcastTitle,
            subtitle = state.subtitle,
            texts = articleCategoryTexts(),
            tags = state.tags,
            checkedTagIds = state.checkedTagIds,
            onToggle = viewModel::onToggleCategory,
            onCreate = viewModel::onCreateCategory,
            onDelete = viewModel::onDeleteCategory,
            errorMessage = state.errorMessage,
            onDismiss = viewModel::onDismissCategories,
            badge = { SourceBadge(state.podcastTitle) }
        )
    }
    uiState.deleting?.let { feed ->
        AlertDialog(
            onDismissRequest = viewModel::onDismissDelete,
            title = { Text(stringResource(R.string.delete_source_title)) },
            text = { Text(stringResource(R.string.delete_source_message, feed.title)) },
            confirmButton = { TextButton(onClick = viewModel::onConfirmDelete) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = viewModel::onDismissDelete) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

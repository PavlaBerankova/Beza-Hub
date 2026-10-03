package com.berpod.app.ui.articles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berpod.app.R
import com.berpod.app.data.local.ArticleWithFeed
import com.berpod.app.ui.common.DeleteTagDialog
import com.berpod.app.ui.common.EmptyState
import com.berpod.app.ui.common.MarkPlayedSwipe
import com.berpod.app.ui.common.RenameTagDialog
import com.berpod.app.ui.common.TagChipRow
import com.berpod.app.ui.common.articleCategoryTexts
import com.berpod.app.ui.dot.DotIconButton
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.dot.CenteredSeparator
import com.berpod.app.ui.dot.DotTitle
import com.berpod.app.ui.dot.rememberNowMillis
import com.berpod.app.ui.dot.upperCs
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType

/** Články tab (canvas "C1 · Články"): unread count, categories, articles; + adds a source. */
@Composable
fun ArticlesScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onArticleClick: (articleId: Long, tagId: Long?) -> Unit,
    onOpenSources: () -> Unit
) {
    val viewModel: ArticlesViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    val addSourceState by viewModel.addSource.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val c = DotTheme.colors

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onSnackbarMessageShown()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = c.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            DotIconButton(
                icon = DotIcons.AddOnAccent,
                contentDescription = stringResource(R.string.add_source_title),
                onClick = viewModel.addSource::open,
                size = 56.dp,
                radius = 16.dp,
                iconSize = 24.dp,
                filled = c.accent
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DotTitle(stringResource(R.string.nav_articles), Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DotIconButton(DotIcons.Sources, stringResource(R.string.sources), onOpenSources)
                    if (uiState.isRefreshing) {
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp), color = c.accent, strokeWidth = 2.dp)
                        }
                    } else {
                        DotIconButton(DotIcons.Refresh, stringResource(R.string.action_refresh), viewModel::onRefresh)
                    }
                }
            }
            if (uiState.hasAnySource) {
                // "■ 3 NEPŘEČTENÉ ——— 6 ZDROJŮ"; the source count opens the sources list.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(Modifier.size(7.dp).clip(RoundedCornerShape(1.dp)).background(c.accent))
                    Text(czechCount(uiState.unreadCount, "nepřečtený", "nepřečtené", "nepřečtených"), style = DotType.label, color = c.textMuted)
                    HorizontalDivider(modifier = Modifier.weight(1f), color = c.divider)
                    Text(
                        czechCount(uiState.sourceCount, "zdroj", "zdroje", "zdrojů"),
                        style = DotType.label,
                        color = c.textMuted,
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.sources), onClick = onOpenSources)
                            .padding(horizontal = 8.dp, vertical = 14.dp)
                    )
                }
            }
            if (uiState.tags.isNotEmpty()) {
                Box(Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                    TagChipRow(
                        tags = uiState.tags,
                        selectedTagId = uiState.selectedTagId,
                        onSelect = viewModel::onSelectTag,
                        onRenameRequest = viewModel::onRequestRenameTag,
                        onDeleteRequest = viewModel::onRequestDeleteTag
                    )
                }
            }
            when {
                !uiState.hasAnySource -> EmptyState(
                    icon = DotIcons.Articles,
                    title = stringResource(R.string.articles_empty),
                    subtitle = stringResource(R.string.articles_empty_subtitle)
                )
                uiState.unreadArticles.isEmpty() && uiState.readArticles.isEmpty() -> EmptyState(
                    icon = DotIcons.Tag,
                    title = stringResource(R.string.articles_filter_empty),
                    subtitle = stringResource(R.string.queue_tag_filter_empty_subtitle)
                )
                else -> {
                    val now = rememberNowMillis()
                    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                        itemsIndexed(uiState.unreadArticles, key = { _, it -> it.article.id }) { index, item ->
                            MarkPlayedSwipe(onToggle = { viewModel.onToggleRead(item) }) {
                                // The last row's divider would double up with the separator (or dangle at the end).
                                ArticleRow(item, now, showDivider = index < uiState.unreadArticles.lastIndex) {
                                    onArticleClick(item.article.id, uiState.selectedTagId)
                                }
                            }
                        }
                        // Read articles keep their place in the list, just below everything unread.
                        if (uiState.readArticles.isNotEmpty()) {
                            item(key = "read-separator") {
                                CenteredSeparator(
                                    label = stringResource(R.string.section_read),
                                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)
                                )
                            }
                        }
                        itemsIndexed(uiState.readArticles, key = { _, it -> it.article.id }) { index, item ->
                            MarkPlayedSwipe(onToggle = { viewModel.onToggleRead(item) }) {
                                ArticleRow(item, now, showDivider = index < uiState.readArticles.lastIndex) {
                                    onArticleClick(item.article.id, uiState.selectedTagId)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    addSourceState?.let { AddSourceSheet(it, viewModel.addSource) }
    uiState.renamingTag?.let { tag ->
        RenameTagDialog(tag, uiState.renameError, viewModel::onConfirmRenameTag, viewModel::onDismissRenameTag, articleCategoryTexts())
    }
    uiState.deletingTag?.let { tag ->
        DeleteTagDialog(tag, viewModel::onConfirmDeleteTag, viewModel::onDismissDeleteTag, articleCategoryTexts())
    }
}

@Composable
private fun ArticleRow(item: ArticleWithFeed, nowMillis: Long, showDivider: Boolean, onClick: () -> Unit) {
    val c = DotTheme.colors
    val unread = item.article.readAtMs == null
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (unread) Box(Modifier.size(7.dp).clip(RoundedCornerShape(1.dp)).background(c.accent))
                // The source stands out in the tab's colour; the time stays quiet beside it.
                Text(
                    item.feedTitle.upperCs(),
                    style = DotType.label.copy(fontWeight = FontWeight.W700),
                    color = c.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                relativeTimeLabel(item.article.pubDateMillis, nowMillis)?.let {
                    Text("·", style = DotType.label, color = c.textMuted)
                    Text(it, style = DotType.label, color = c.textMuted, maxLines = 1)
                }
            }
            Text(
                item.article.title,
                style = DotType.reading.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = if (unread) FontWeight.W600 else FontWeight.W400),
                color = if (unread) c.text else c.textMuted
            )
        }
        if (showDivider) HorizontalDivider(color = c.divider)
    }
}

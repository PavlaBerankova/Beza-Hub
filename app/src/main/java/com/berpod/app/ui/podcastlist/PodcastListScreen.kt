package com.berpod.app.ui.podcastlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import com.berpod.app.ui.theme.DotType
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berpod.app.R
import com.berpod.app.data.local.PodcastFeedEntity
import com.berpod.app.ui.common.DeleteTagDialog
import com.berpod.app.ui.common.EmptyState
import com.berpod.app.ui.common.RenameTagDialog
import com.berpod.app.ui.common.TagPickerSheet
import com.berpod.app.ui.common.podcastTagTexts
import com.berpod.app.ui.common.TagChipRow
import com.berpod.app.ui.dot.SectionSeparator
import com.berpod.app.ui.dot.subscriptionCountText
import com.berpod.app.ui.dot.CoverImage
import com.berpod.app.ui.dot.DotIconButton
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.theme.DotTheme


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastListScreen(viewModelFactory: ViewModelProvider.Factory, onPodcastClick: (Long) -> Unit) {
    val viewModel: PodcastListViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    val tagAssignState by viewModel.tagAssignment.state.collectAsState()
    val addPodcastState by viewModel.addPodcast.state.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.refreshMessage) {
        uiState.refreshMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onRefreshMessageShown()
        }
    }

    Scaffold(
        // Embedded in the Podcasts tab, which draws the title and the section switcher.
        contentWindowInsets = WindowInsets(0),
        containerColor = DotTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            DotIconButton(
                icon = DotIcons.AddOnAccent,
                contentDescription = stringResource(R.string.fab_add_podcast),
                onClick = viewModel.addPodcast::open,
                size = 56.dp,
                radius = 16.dp,
                iconSize = 24.dp,
                filled = DotTheme.colors.accent
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (uiState.tags.isNotEmpty()) {
                TagChipRow(
                    tags = uiState.tags,
                    selectedTagId = uiState.selectedTagId,
                    onSelect = viewModel::onSelectTag,
                    onRenameRequest = viewModel::onRequestRenameTag,
                    onDeleteRequest = viewModel::onRequestDeleteTag
                )
            }
            if (uiState.hasAnyPodcasts) {
                val tagName = uiState.tags.firstOrNull { it.id == uiState.selectedTagId }?.name
                SectionSeparator(
                    left = subscriptionCountText(uiState.feeds.size),
                    right = tagName,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp)
                )
            }
            when {
                !uiState.hasAnyPodcasts -> EmptyState(
                    icon = DotIcons.Podcasts,
                    title = stringResource(R.string.empty_state_title),
                    subtitle = stringResource(R.string.empty_state_subtitle)
                )
                uiState.feeds.isEmpty() -> EmptyState(
                    icon = DotIcons.Tag,
                    title = stringResource(R.string.tag_filter_empty),
                    subtitle = stringResource(R.string.tag_filter_empty_subtitle)
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.feeds, key = { it.id }) { feed ->
                        PodcastGridItem(
                            feed = feed,
                            newCount = uiState.newCounts[feed.id] ?: 0,
                            onClick = { onPodcastClick(feed.id) },
                            onLongClick = { viewModel.onOpenTagDialog(feed) }
                        )
                    }
                }
            }
        }

        tagAssignState?.let { state ->
            TagPickerSheet(
                title = state.podcastTitle,
                subtitle = state.subtitle,
                texts = podcastTagTexts(),
                tags = state.tags,
                checkedTagIds = state.checkedTagIds,
                onToggle = viewModel.tagAssignment::setAssigned,
                onCreate = viewModel.tagAssignment::createAndAssign,
                onDelete = viewModel.tagAssignment::deleteTag,
                errorMessage = state.errorMessage,
                onDismiss = viewModel.tagAssignment::dismiss,
                badge = { CoverImage(state.imageUrl, 48.dp, 10.dp) }
            )
        }
        uiState.renamingTag?.let { tag ->
            RenameTagDialog(
                tag = tag,
                errorMessage = uiState.renameError,
                onConfirm = viewModel::onConfirmRenameTag,
                onDismiss = viewModel::onDismissRenameTag
            )
        }
        uiState.deletingTag?.let { tag ->
            DeleteTagDialog(
                tag = tag,
                onConfirm = viewModel::onConfirmDeleteTag,
                onDismiss = viewModel::onDismissDeleteTag
            )
        }
        addPodcastState?.let { AddPodcastSheet(it, viewModel.addPodcast) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PodcastGridItem(feed: PodcastFeedEntity, newCount: Int, onClick: () -> Unit, onLongClick: () -> Unit) {
    val description = if (newCount > 0) "${feed.title}, ${stringResource(R.string.badge_new_count_description, newCount)}" else feed.title
    // The badge sits on the cover's corner and sticks out of it, so only the cover itself is clipped.
    Box(modifier = Modifier.semantics { contentDescription = description }) {
        CoverImage(
            url = feed.imageUrl,
            size = null,
            radius = 14.dp,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        )
        if (newCount > 0) NewCountBadge(newCount, Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp))
    }
}

/** The number of new episodes, in a small squared accent badge on the cover's corner (99+ when it is more). */
@Composable
private fun NewCountBadge(count: Int, modifier: Modifier = Modifier) {
    val c = DotTheme.colors
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .clearAndSetSemantics {}
            .clip(shape)
            .background(c.accent)
            .border(2.dp, c.background, shape)
            .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            style = DotType.label.copy(fontSize = 11.sp, fontWeight = FontWeight.W800, letterSpacing = 0.sp),
            color = c.onAccent,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

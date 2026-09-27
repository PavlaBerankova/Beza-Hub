package com.berpod.app.ui.podcastlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.berpod.app.R
import com.berpod.app.data.local.PodcastFeedEntity
import com.berpod.app.ui.common.DeleteTagDialog
import com.berpod.app.ui.common.EmptyState
import com.berpod.app.ui.common.RenameTagDialog
import com.berpod.app.ui.common.TagAssignDialog
import com.berpod.app.ui.common.TagChipRow

private val CoverShape = RoundedCornerShape(12.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastListScreen(viewModelFactory: ViewModelProvider.Factory, onPodcastClick: (Long) -> Unit) {
    val viewModel: PodcastListViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    val tagAssignState by viewModel.tagAssignment.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::onFabClick) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.fab_add_podcast))
            }
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
            when {
                !uiState.hasAnyPodcasts -> EmptyState(
                    icon = Icons.Filled.Podcasts,
                    title = stringResource(R.string.empty_state_title),
                    subtitle = stringResource(R.string.empty_state_subtitle)
                )
                uiState.feeds.isEmpty() -> EmptyState(
                    icon = Icons.AutoMirrored.Outlined.Label,
                    title = stringResource(R.string.tag_filter_empty),
                    subtitle = stringResource(R.string.tag_filter_empty_subtitle)
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.feeds, key = { it.id }) { feed ->
                        PodcastGridItem(
                            feed = feed,
                            onClick = { onPodcastClick(feed.id) },
                            onLongClick = { viewModel.onOpenTagDialog(feed) }
                        )
                    }
                }
            }
        }

        if (uiState.isAddDialogOpen) {
            AddFeedDialog(
                isSubmitting = uiState.isSubmitting,
                errorMessage = uiState.errorMessage,
                onDismiss = viewModel::onDismissAddDialog,
                onSubmit = viewModel::onAddFeed
            )
        }
        tagAssignState?.let { state ->
            TagAssignDialog(
                state = state,
                onToggle = viewModel.tagAssignment::setAssigned,
                onCreate = viewModel.tagAssignment::createAndAssign,
                onDismiss = viewModel.tagAssignment::dismiss
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
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PodcastGridItem(feed: PodcastFeedEntity, onClick: () -> Unit, onLongClick: () -> Unit) {
    AsyncImage(
        model = feed.imageUrl,
        contentDescription = feed.title,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .aspectRatio(1f)
            .clip(CoverShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    )
}

package com.berpod.app.ui.youtube.channels

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berpod.app.R
import com.berpod.app.data.local.YoutubeChannelEntity
import com.berpod.app.ui.articles.czechCount
import com.berpod.app.ui.common.DeleteTagDialog
import com.berpod.app.ui.common.EmptyState
import com.berpod.app.ui.common.RenameTagDialog
import com.berpod.app.ui.common.TagChipRow
import com.berpod.app.ui.common.TagPickerSheet
import com.berpod.app.ui.common.youtubeCategoryTexts
import com.berpod.app.ui.dot.DotIconButton
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.dot.SectionSeparator
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType

/** Kanály tab (canvas "Y1 · Kanály"): channel grid, categories, + opens the add-channel sheet. */
@Composable
fun YoutubeChannelsScreen(viewModelFactory: ViewModelProvider.Factory, onChannelClick: (Long) -> Unit) {
    val viewModel: YoutubeChannelsViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    val addChannelState by viewModel.addChannel.state.collectAsState()
    val c = DotTheme.colors

    Scaffold(
        // Embedded in the YouTube tab, which draws the title and the section switcher.
        contentWindowInsets = WindowInsets(0),
        containerColor = c.background,
        floatingActionButton = {
            DotIconButton(
                icon = DotIcons.AddOnAccent,
                contentDescription = stringResource(R.string.action_add_channel),
                onClick = viewModel.addChannel::open,
                size = 56.dp,
                radius = 16.dp,
                iconSize = 24.dp,
                filled = c.accent
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (uiState.hasAnyChannels) {
                val unreadTotal = uiState.channels.sumOf { uiState.unreadCounts[it.id] ?: 0 }
                SectionSeparator(
                    left = czechCount(uiState.channels.size, "kanál", "kanály", "kanálů"),
                    right = czechCount(unreadTotal, "nezhlédnuté", "nezhlédnutá", "nezhlédnutých"),
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
                )
            }
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
                !uiState.hasAnyChannels -> EmptyState(
                    icon = DotIcons.YouTube,
                    title = stringResource(R.string.channels_empty),
                    subtitle = stringResource(R.string.channels_empty_subtitle)
                )
                uiState.channels.isEmpty() -> EmptyState(
                    icon = DotIcons.Tag,
                    title = stringResource(R.string.channels_tag_filter_empty),
                    subtitle = stringResource(R.string.channels_tag_filter_empty_subtitle)
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(uiState.channels, key = { it.id }) { channel ->
                        ChannelGridItem(
                            channel = channel,
                            unreadCount = uiState.unreadCounts[channel.id] ?: 0,
                            onClick = { onChannelClick(channel.id) },
                            onLongClick = { viewModel.onOpenCategories(channel) }
                        )
                    }
                }
            }
        }

        uiState.categories?.let { state ->
            TagPickerSheet(
                title = state.podcastTitle,
                subtitle = state.subtitle,
                texts = youtubeCategoryTexts(),
                tags = state.tags,
                checkedTagIds = state.checkedTagIds,
                onToggle = viewModel::onToggleCategory,
                onCreate = viewModel::onCreateCategory,
                onDelete = viewModel::onDeleteCategory,
                errorMessage = state.errorMessage,
                onDismiss = viewModel::onDismissCategories,
                badge = { ChannelAvatar(null, state.podcastTitle, 48.dp) }
            )
        }
        uiState.renamingTag?.let { tag ->
            RenameTagDialog(tag, uiState.renameError, viewModel::onConfirmRenameTag, viewModel::onDismissRenameTag, youtubeCategoryTexts())
        }
        uiState.deletingTag?.let { tag ->
            DeleteTagDialog(tag, viewModel::onConfirmDeleteTag, viewModel::onDismissDeleteTag, youtubeCategoryTexts())
        }
        addChannelState?.let { AddChannelSheet(it, viewModel.addChannel) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelGridItem(channel: YoutubeChannelEntity, unreadCount: Int, onClick: () -> Unit, onLongClick: () -> Unit) {
    val c = DotTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = channel.title },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ChannelAvatar(channel.thumbnailUrl, channel.title, 70.dp, unreadCount)
        Text(
            channel.title,
            style = DotType.label.copy(letterSpacing = 0.5.sp),
            color = c.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

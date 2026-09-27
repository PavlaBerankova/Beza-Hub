package com.berpod.app.ui.queue

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.berpod.app.data.local.QueueItemWithEpisode
import com.berpod.app.data.local.moveAfterDrag
import com.berpod.app.data.local.stepAnchors
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berpod.app.R
import com.berpod.app.ui.common.EmptyState
import com.berpod.app.ui.common.EpisodeListItem
import com.berpod.app.ui.common.TagChipRow
import com.berpod.app.ui.common.episodeProgressFraction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(viewModelFactory: ViewModelProvider.Factory) {
    val viewModel: QueueViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_queue)) }) }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (uiState.tags.isNotEmpty()) {
                TagChipRow(
                    tags = uiState.tags,
                    selectedTagId = uiState.selectedTagId,
                    onSelect = viewModel::onSelectTag
                )
            }
            when {
                uiState.items.isEmpty() -> EmptyState(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    title = stringResource(R.string.queue_empty_title),
                    subtitle = stringResource(R.string.queue_empty_subtitle)
                )
                uiState.visibleItems.isEmpty() -> EmptyState(
                    icon = Icons.AutoMirrored.Outlined.Label,
                    title = stringResource(R.string.queue_tag_filter_empty),
                    subtitle = stringResource(R.string.queue_tag_filter_empty_subtitle)
                )
                else -> ReorderableQueueList(uiState = uiState, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun ReorderableQueueList(uiState: QueueUiState, viewModel: QueueViewModel) {
    val haptics = LocalHapticFeedback.current
    // Local copy reordered live while dragging; the database is written once on drop.
    var localItems by remember { mutableStateOf(uiState.visibleItems) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragStartOrder by remember { mutableStateOf<List<Long>>(emptyList()) }
    // The drag callbacks outlive recompositions, so they must read the latest list through this.
    val latestVisibleItems by rememberUpdatedState(uiState.visibleItems)
    // The queue re-emits during playback (position saves); never overwrite the list mid-drag.
    LaunchedEffect(uiState.visibleItems) {
        if (draggingId == null) localItems = uiState.visibleItems
    }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        localItems = localItems.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }
    val moveUpLabel = stringResource(R.string.action_move_up)
    val moveDownLabel = stringResource(R.string.action_move_down)
    val dragLabel = stringResource(R.string.action_drag_to_reorder)

    fun stepMove(movedId: Long, up: Boolean): Boolean {
        val anchors = stepAnchors(localItems.map { it.queueEntryId }, movedId, up) ?: return false
        viewModel.onMoveEntry(movedId, anchors.first, anchors.second)
        return true
    }

    LazyColumn(state = lazyListState) {
        items(localItems, key = { it.queueEntryId }) { item ->
            ReorderableItem(reorderState, key = item.queueEntryId) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "dragElevation")
                Surface(shadowElevation = elevation) {
                    QueueRow(
                        item = item,
                        uiState = uiState,
                        viewModel = viewModel,
                        handleModifier = Modifier
                            .draggableHandle(
                                // Only State reads in here: this lambda is not replaced on recomposition.
                                onDragStarted = {
                                    dragStartOrder = localItems.map { it.queueEntryId }
                                    draggingId = item.queueEntryId
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragStopped = {
                                    draggingId = null
                                    val move = moveAfterDrag(
                                        startOrder = dragStartOrder,
                                        endOrder = localItems.map { it.queueEntryId },
                                        movedId = item.queueEntryId
                                    )
                                    if (move != null) {
                                        viewModel.onMoveEntry(item.queueEntryId, move.first, move.second)
                                    } else {
                                        // Nothing to store, so no emission will follow: apply any skipped update now.
                                        localItems = latestVisibleItems
                                    }
                                }
                            )
                            // Label and move actions on the same node, so TalkBack offers them on the handle.
                            .semantics {
                                contentDescription = dragLabel
                                customActions = listOf(
                                    CustomAccessibilityAction(moveUpLabel) { stepMove(item.queueEntryId, up = true) },
                                    CustomAccessibilityAction(moveDownLabel) { stepMove(item.queueEntryId, up = false) }
                                )
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    item: QueueItemWithEpisode,
    uiState: QueueUiState,
    viewModel: QueueViewModel,
    handleModifier: Modifier
) {
    EpisodeListItem(
        episode = item.episode,
        podcastTitle = null,
        podcastImageUrl = item.podcastImageUrl,
        onPlay = { viewModel.onPlay(item) },
        isCurrentlyPlaying = uiState.currentlyPlayingEpisodeId == item.episode.id && uiState.isPlaying,
        progressFraction = episodeProgressFraction(item.episode.lastPositionMs, item.episode.durationSeconds),
        showArtwork = true,
        leadingContent = {
            // Not a button: the handle only drags (and offers move actions to screen readers).
            Box(modifier = handleModifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.DragIndicator, contentDescription = null)
            }
        },
        trailingContent = {
            IconButton(onClick = { viewModel.onRemove(item.queueEntryId) }) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_remove_from_queue)
                )
            }
        }
    )
}

package com.berpod.app.ui.queue

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import com.berpod.app.ui.dot.DotIcon
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berpod.app.R
import com.berpod.app.data.local.QueueItemWithEpisode
import com.berpod.app.data.local.moveAfterDrag
import com.berpod.app.data.local.stepAnchors
import com.berpod.app.ui.common.CompactEpisodeRow
import com.berpod.app.ui.common.EmptyState
import com.berpod.app.ui.common.MarkPlayedBackground
import com.berpod.app.ui.common.TagChipRow
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.theme.DotType
import com.berpod.app.ui.dot.upperCs
import com.berpod.app.ui.dot.DotFilterButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import com.berpod.app.ui.theme.DotTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(viewModelFactory: ViewModelProvider.Factory, onEpisodeClick: (Long) -> Unit) {
    val viewModel: QueueViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val removedMessage = stringResource(R.string.queue_removed)
    val undoLabel = stringResource(R.string.action_undo)
    // A new removal replaces the previous snackbar (the effect restarts with the new entry).
    LaunchedEffect(uiState.removedEntry) {
        if (uiState.removedEntry == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = removedMessage,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.onUndoRemove() else viewModel.onRemovedMessageDismissed()
    }
    val playedMessage = stringResource(R.string.marked_played)
    LaunchedEffect(uiState.playedUndo) {
        if (uiState.playedUndo == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(playedMessage, actionLabel = undoLabel, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) viewModel.onUndoMarkPlayed() else viewModel.onPlayedMessageDismissed()
    }

    val sortedMessage = stringResource(R.string.queue_sorted)
    LaunchedEffect(uiState.sortUndo) {
        if (uiState.sortUndo == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(sortedMessage, actionLabel = undoLabel, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) viewModel.onUndoSort() else viewModel.onSortMessageDismissed()
    }

    val downloadMessage = stringResource(R.string.download_started)
    LaunchedEffect(uiState.downloadStarted) {
        if (!uiState.downloadStarted) return@LaunchedEffect
        snackbarHostState.showSnackbar(downloadMessage)
        viewModel.onDownloadMessageShown()
    }

    Scaffold(
        // Embedded in the Podcasts tab, which draws the title and the section switcher.
        contentWindowInsets = WindowInsets(0),
        containerColor = DotTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (uiState.tags.isNotEmpty()) {
                TagChipRow(
                    tags = uiState.tags,
                    selectedTagId = uiState.selectedTagId,
                    onSelect = viewModel::onSelectTag
                )
            }
            if (uiState.visibleItems.isNotEmpty()) {
                QueueInfoRow(
                    text = queueSummary(uiState.visibleItems).orEmpty(),
                    order = queueOrderOf(uiState.items),
                    onClick = viewModel::onToggleSort,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)
                )
            }
            when {
                uiState.items.isEmpty() -> EmptyState(
                    icon = DotIcons.Queue,
                    title = stringResource(R.string.queue_empty_title),
                    subtitle = stringResource(R.string.queue_empty_subtitle)
                )
                uiState.visibleItems.isEmpty() -> EmptyState(
                    icon = DotIcons.Tag,
                    title = stringResource(R.string.queue_tag_filter_empty),
                    subtitle = stringResource(R.string.queue_tag_filter_empty_subtitle)
                )
                else -> ReorderableQueueList(uiState = uiState, viewModel = viewModel, onEpisodeClick = onEpisodeClick)
            }
        }
    }
}

@Composable
private fun ReorderableQueueList(uiState: QueueUiState, viewModel: QueueViewModel, onEpisodeClick: (Long) -> Unit) {
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
    val removeLabel = stringResource(R.string.action_remove_from_queue)

    fun stepMove(movedId: Long, up: Boolean): Boolean {
        val anchors = stepAnchors(localItems.map { it.queueEntryId }, movedId, up) ?: return false
        viewModel.onMoveEntry(movedId, anchors.first, anchors.second)
        return true
    }

    LazyColumn(
        state = lazyListState,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp)
    ) {
        items(localItems, key = { it.queueEntryId }) { item ->
            ReorderableItem(reorderState, key = item.queueEntryId) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "dragElevation")
                // Swipe left to download (the row springs back, it stays queued), right to mark played
                // (which also takes it out of the queue); a played row stays swiped until the queue emission drops it.
                val swipeScope = rememberCoroutineScope()
                val swipeState = rememberSwipeToDismissBoxState()
                SwipeToDismissBox(
                    // Same shape as the row, so the swipe background never shows in its corners.
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                    state = swipeState,
                    gesturesEnabled = !isDragging,
                    onDismiss = { direction ->
                        if (direction == SwipeToDismissBoxValue.StartToEnd) viewModel.onMarkPlayed(item.episode.id)
                        else {
                            viewModel.onDownload(item.episode.id)
                            swipeScope.launch { swipeState.reset() }
                        }
                    },
                    backgroundContent = {
                        when (swipeState.dismissDirection) {
                            SwipeToDismissBoxValue.EndToStart -> DownloadBackground()
                            SwipeToDismissBoxValue.StartToEnd -> MarkPlayedBackground()
                            SwipeToDismissBoxValue.Settled -> Unit
                        }
                    }
                ) {
                    Surface(
                        shadowElevation = elevation,
                        shape = RoundedCornerShape(16.dp),
                        color = DotTheme.colors.background,
                        // Long-press anywhere on the row, then drag it to its new place.
                        modifier = Modifier
                                .longPressDraggableHandle(
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
                                .semantics {
                                    customActions = listOf(
                                        CustomAccessibilityAction(moveUpLabel) { stepMove(item.queueEntryId, up = true) },
                                        CustomAccessibilityAction(moveDownLabel) { stepMove(item.queueEntryId, up = false) },
                                        CustomAccessibilityAction(removeLabel) { viewModel.onRemove(item.queueEntryId); true }
                                    )
                                }
                    ) {
                        QueueRow(item = item, uiState = uiState, viewModel = viewModel, onClick = { onEpisodeClick(item.episode.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DotTheme.colors.accent)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterEnd
    ) { DotIcon(DotIcons.Download, 28.dp, monochrome = DotTheme.colors.onAccent) }
}

@Composable
private fun QueueRow(item: QueueItemWithEpisode, uiState: QueueUiState, viewModel: QueueViewModel, onClick: () -> Unit) {
    val isCurrent = uiState.currentlyPlayingEpisodeId == item.episode.id
    CompactEpisodeRow(
        episode = item.episode,
        podcastTitle = item.podcastTitle,
        podcastImageUrl = item.podcastImageUrl,
        isCurrent = isCurrent,
        isPlaying = isCurrent && uiState.isPlaying,
        onPlayOrPause = { viewModel.onPlayOrPause(item) },
        onClick = onClick
    )
}

/** "3 EPIZODY · CELKEM 2 H 28 MIN ——— [NEJNOVĚJŠÍ ⌄]": the queue's size and listening time, and its sort button. */
@Composable
private fun QueueInfoRow(text: String, order: QueueOrder, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = DotTheme.colors
    val label = when (order) {
        QueueOrder.NewestFirst -> stringResource(R.string.section_newest)
        QueueOrder.OldestFirst -> stringResource(R.string.sort_oldest)
        QueueOrder.Custom -> stringResource(R.string.queue_order_custom)
    }
    // The line runs behind both ends so the text takes the room it needs and is only cut when it really doesn't fit.
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        HorizontalDivider(color = c.divider)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text.upperCs(),
                style = DotType.label,
                color = c.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).background(c.background).padding(end = 10.dp)
            )
            Box(Modifier.background(c.background).padding(start = 10.dp)) {
                DotFilterButton(label, highlighted = false, onClick = onClick, flipped = order == QueueOrder.OldestFirst)
            }
        }
    }
}

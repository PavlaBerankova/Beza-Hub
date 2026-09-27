package com.berpod.app.ui.queue

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.berpod.app.R
import com.berpod.app.data.local.QueueItemWithEpisode
import com.berpod.app.data.local.moveAfterDrag
import com.berpod.app.data.local.stepAnchors
import com.berpod.app.ui.common.EmptyState
import com.berpod.app.ui.common.TagChipRow
import com.berpod.app.ui.common.formatPubDate
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.podcastlist.remainingText
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(viewModelFactory: ViewModelProvider.Factory) {
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

    Scaffold(
        // Embedded in the Podcasts tab, which draws the title and the section switcher.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            queueSummary(uiState.visibleItems)?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                )
            }
            if (uiState.tags.isNotEmpty()) {
                TagChipRow(
                    tags = uiState.tags,
                    selectedTagId = uiState.selectedTagId,
                    onSelect = viewModel::onSelectTag
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
    val removeLabel = stringResource(R.string.action_remove_from_queue)

    fun stepMove(movedId: Long, up: Boolean): Boolean {
        val anchors = stepAnchors(localItems.map { it.queueEntryId }, movedId, up) ?: return false
        viewModel.onMoveEntry(movedId, anchors.first, anchors.second)
        return true
    }

    LazyColumn(state = lazyListState) {
        items(localItems, key = { it.queueEntryId }) { item ->
            ReorderableItem(reorderState, key = item.queueEntryId) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "dragElevation")
                // Swipe left to remove; the row stays swiped until the queue emission drops it.
                SwipeToDismissBox(
                    state = rememberSwipeToDismissBoxState(),
                    enableDismissFromStartToEnd = false,
                    gesturesEnabled = !isDragging,
                    onDismiss = { viewModel.onRemove(item.queueEntryId) },
                    backgroundContent = { RemoveBackground() }
                ) {
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
                                        CustomAccessibilityAction(moveDownLabel) { stepMove(item.queueEntryId, up = false) },
                                        CustomAccessibilityAction(removeLabel) { viewModel.onRemove(item.queueEntryId); true }
                                    )
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoveBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.error)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onError)
    }
}

private val RowArtworkShape = RoundedCornerShape(10.dp)

/** Queue row in the podcast-detail style: handle, cover, date, bold title, remaining time and a round play button. */
@Composable
private fun QueueRow(
    item: QueueItemWithEpisode,
    uiState: QueueUiState,
    viewModel: QueueViewModel,
    handleModifier: Modifier
) {
    val episode = item.episode
    val isPlaying = uiState.currentlyPlayingEpisodeId == episode.id && uiState.isPlaying
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Not a button: the handle only drags (and offers move/remove actions to screen readers).
            Box(modifier = handleModifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.DragIndicator, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RowArtworkShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Podcasts, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.podcastImageUrl != null) {
                    AsyncImage(
                        model = item.podcastImageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                }
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                formatPubDate(episode.pubDateMillis)?.let { date ->
                    Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                remainingText(episode)?.let { time ->
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            FilledTonalIconButton(onClick = { viewModel.onPlayOrPause(item) }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (isPlaying) R.string.action_pause else R.string.action_play),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

package com.berpod.app.ui.youtube.queue

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berpod.app.R
import com.berpod.app.data.local.QueueVideoItem
import com.berpod.app.data.local.moveAfterDrag
import com.berpod.app.data.local.stepAnchors
import com.berpod.app.ui.common.EmptyState
import com.berpod.app.ui.common.MarkPlayedBackground
import com.berpod.app.ui.common.TagChipRow
import com.berpod.app.ui.dot.DotIcon
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.dot.SectionSeparator
import com.berpod.app.ui.dot.upperCs
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType
import com.berpod.app.ui.youtube.newvideos.CompactVideoRow
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoQueueScreen(viewModelFactory: ViewModelProvider.Factory, onVideoClick: (Long) -> Unit) {
    val viewModel: VideoQueueViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    val c = DotTheme.colors
    val uriHandler = LocalUriHandler.current

    val snackbarHostState = remember { SnackbarHostState() }
    val removedMessage = stringResource(R.string.queue_removed)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(uiState.removedEntry) {
        if (uiState.removedEntry == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(removedMessage, actionLabel = undoLabel, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) viewModel.onUndoRemove() else viewModel.onRemovedMessageDismissed()
    }
    val watchedMessage = stringResource(R.string.marked_watched)
    LaunchedEffect(uiState.watchedUndo) {
        if (uiState.watchedUndo == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(watchedMessage, actionLabel = undoLabel, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) viewModel.onUndoMarkWatched() else viewModel.onWatchedMessageDismissed()
    }

    Scaffold(
        // Embedded in the YouTube tab, which draws the title and the section switcher.
        contentWindowInsets = WindowInsets(0),
        containerColor = c.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (uiState.visibleItems.isNotEmpty()) {
                SectionSeparator(
                    left = videoCountText(uiState.visibleItems.size),
                    right = videoQueueTotalText(uiState.visibleItems)?.let { stringResource(R.string.queue_total, it) },
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
                )
                val first = uiState.visibleItems.first()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(3f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(c.accent)
                            .clickable { onVideoClick(first.video.id) },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DotIcon(DotIcons.PlaySolid, 16.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_resume).upperCs(), style = DotType.button, color = c.onAccent)
                    }
                    Row(
                        modifier = Modifier
                            .weight(2f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, c.outline, RoundedCornerShape(14.dp))
                            .clickable { uriHandler.openUri(first.video.videoUrl) },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DotIcon(DotIcons.OpenExternal, 18.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.in_youtube_button).upperCs(), style = DotType.button.copy(fontSize = 12.sp), color = c.text, maxLines = 1)
                    }
                }
            }
            if (uiState.tags.isNotEmpty()) {
                TagChipRow(tags = uiState.tags, selectedTagId = uiState.selectedTagId, onSelect = viewModel::onSelectTag)
            }
            when {
                uiState.items.isEmpty() -> EmptyState(
                    icon = DotIcons.Queue,
                    title = stringResource(R.string.video_queue_empty_title),
                    subtitle = stringResource(R.string.video_queue_empty_subtitle)
                )
                uiState.visibleItems.isEmpty() -> EmptyState(
                    icon = DotIcons.Tag,
                    title = stringResource(R.string.video_queue_tag_filter_empty),
                    subtitle = stringResource(R.string.queue_tag_filter_empty_subtitle)
                )
                else -> ReorderableVideoQueueList(uiState = uiState, viewModel = viewModel, onVideoClick = onVideoClick)
            }
        }
    }
}

@Composable
private fun ReorderableVideoQueueList(uiState: VideoQueueUiState, viewModel: VideoQueueViewModel, onVideoClick: (Long) -> Unit) {
    val haptics = LocalHapticFeedback.current
    var localItems by remember { mutableStateOf(uiState.visibleItems) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragStartOrder by remember { mutableStateOf<List<Long>>(emptyList()) }
    val latestVisibleItems by rememberUpdatedState(uiState.visibleItems)
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

    LazyColumn(state = lazyListState, contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp)) {
        items(localItems, key = { it.queueEntryId }) { item ->
            ReorderableItem(reorderState, key = item.queueEntryId) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "dragElevation")
                val swipeState = rememberSwipeToDismissBoxState()
                SwipeToDismissBox(
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                    state = swipeState,
                    gesturesEnabled = !isDragging,
                    onDismiss = { direction ->
                        if (direction == SwipeToDismissBoxValue.StartToEnd) viewModel.onMarkWatched(item.video.id)
                        else viewModel.onRemove(item.queueEntryId)
                    },
                    backgroundContent = {
                        when (swipeState.dismissDirection) {
                            SwipeToDismissBoxValue.EndToStart -> RemoveBackground()
                            SwipeToDismissBoxValue.StartToEnd -> MarkPlayedBackground()
                            SwipeToDismissBoxValue.Settled -> Unit
                        }
                    }
                ) {
                    Surface(
                        shadowElevation = elevation,
                        shape = RoundedCornerShape(16.dp),
                        color = DotTheme.colors.background,
                        modifier = Modifier
                            .longPressDraggableHandle(
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
                        CompactVideoRow(
                            video = item.video,
                            channelTitle = item.channelTitle,
                            whenLabel = null,
                            onClick = { onVideoClick(item.video.id) },
                            showDivider = true
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
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.error).padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onError)
    }
}

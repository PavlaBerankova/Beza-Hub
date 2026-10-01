package com.berpod.app.ui.youtube.newvideos

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berpod.app.R
import com.berpod.app.ui.articles.relativeTimeLabel
import com.berpod.app.ui.common.AddToQueueButton
import com.berpod.app.ui.common.EmptyState
import com.berpod.app.ui.common.MarkPlayedSwipe
import com.berpod.app.ui.common.TagChipRow
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.dot.SectionSeparator
import com.berpod.app.ui.dot.rememberNowMillis
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.youtube.groupByDay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewVideosScreen(viewModelFactory: ViewModelProvider.Factory, onVideoClick: (Long) -> Unit) {
    val viewModel: NewVideosViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var revealVideoId by remember { mutableStateOf<Long?>(null) }
    val c = DotTheme.colors

    val watchedMessage = stringResource(R.string.marked_watched)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(uiState.watchedUndo) {
        if (uiState.watchedUndo == null) return@LaunchedEffect
        val undoneId = uiState.watchedUndo?.videoId
        val result = snackbarHostState.showSnackbar(watchedMessage, actionLabel = undoLabel, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) {
            revealVideoId = undoneId
            viewModel.onUndoMarkWatched()
        } else {
            viewModel.onWatchedMessageDismissed()
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onSnackbarMessageShown()
        }
    }

    Scaffold(
        // Embedded in the YouTube tab, which draws the title and the section switcher.
        contentWindowInsets = WindowInsets(0),
        containerColor = c.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (uiState.tags.isNotEmpty()) {
                TagChipRow(tags = uiState.tags, selectedTagId = uiState.selectedTagId, onSelect = viewModel::onSelectTag)
            }
            when {
                uiState.isRefreshing && !uiState.hasAnyVideos -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = c.accent, strokeWidth = 2.dp)
                }
                !uiState.hasAnyVideos -> EmptyState(
                    icon = DotIcons.Refresh,
                    title = stringResource(R.string.new_videos_empty),
                    subtitle = stringResource(R.string.action_refresh)
                )
                uiState.videos.isEmpty() -> EmptyState(
                    icon = DotIcons.Tag,
                    title = stringResource(R.string.new_videos_tag_empty),
                    subtitle = stringResource(R.string.queue_tag_filter_empty_subtitle)
                )
                else -> {
                    val now = rememberNowMillis()
                    val groups = groupByDay(uiState.videos, now)
                    LazyColumn(state = listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp)) {
                        groups.forEach { (label, videos) ->
                            item(key = "day-$label") {
                                SectionSeparator(
                                    left = label,
                                    right = videos.size.toString(),
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                                    leadingSquare = label == "DNES"
                                )
                            }
                            items(videos, key = { it.video.id }) { item ->
                                val isQueued = item.video.id in uiState.queuedVideoIds
                                MarkPlayedSwipe(onToggle = { viewModel.onMarkWatched(item.video.id) }) {
                                    CompactVideoRow(
                                        video = item.video,
                                        channelTitle = item.channelTitle,
                                        whenLabel = relativeTimeLabel(item.video.pubDateMillis, now),
                                        onClick = { onVideoClick(item.video.id) }
                                    ) {
                                        AddToQueueButton(isQueued) { viewModel.onAddToQueue(item.video.id) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

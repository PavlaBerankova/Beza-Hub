package com.berpod.app.ui.youtube.channels

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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berpod.app.R
import com.berpod.app.ui.common.AddToQueueButton
import com.berpod.app.ui.common.EmptyState
import com.berpod.app.ui.common.MarkPlayedSwipe
import com.berpod.app.ui.dot.DotIconButton
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.dot.rememberNowMillis
import com.berpod.app.ui.dot.upperCs
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType
import com.berpod.app.ui.articles.relativeTimeLabel
import com.berpod.app.ui.youtube.newvideos.CompactVideoRow

/** One channel's own videos (tapping a tile in Kanály). */
@Composable
fun YoutubeChannelScreen(viewModelFactory: ViewModelProvider.Factory, onBack: () -> Unit, onVideoClick: (Long) -> Unit) {
    val viewModel: YoutubeChannelViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val c = DotTheme.colors

    val watchedMessage = stringResource(R.string.marked_watched)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(uiState.watchedUndo) {
        if (uiState.watchedUndo == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(watchedMessage, actionLabel = undoLabel, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) viewModel.onUndoMarkWatched() else viewModel.onWatchedMessageDismissed()
    }
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onSnackbarMessageShown()
        }
    }

    Scaffold(containerColor = c.background, snackbarHost = { SnackbarHost(snackbarHostState) }) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                DotIconButton(DotIcons.Back, stringResource(R.string.action_back), onBack)
                Text(
                    uiState.channelTitle.upperCs(),
                    style = DotType.chip.copy(fontWeight = FontWeight.W700, letterSpacing = 2.5.sp),
                    color = c.text,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.size(44.dp))
            }
            if (uiState.videos.isEmpty()) {
                EmptyState(icon = DotIcons.YouTube, title = stringResource(R.string.new_videos_empty), subtitle = "")
            } else {
                val now = rememberNowMillis()
                LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
                    items(uiState.videos, key = { it.video.id }) { item ->
                        val isQueued = item.video.id in uiState.queuedVideoIds
                        MarkPlayedSwipe(onToggle = { viewModel.onMarkWatched(item.video.id) }) {
                            CompactVideoRow(
                                video = item.video,
                                channelTitle = uiState.channelTitle,
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

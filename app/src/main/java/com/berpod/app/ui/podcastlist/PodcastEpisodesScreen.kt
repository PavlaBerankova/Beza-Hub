package com.berpod.app.ui.podcastlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berpod.app.R
import com.berpod.app.ui.common.EmptyState
import com.berpod.app.ui.common.MarkPlayedSwipe
import com.berpod.app.ui.common.TagAssignDialog
import com.berpod.app.ui.dot.DotIconButton
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.dot.rememberNowMillis
import com.berpod.app.ui.dot.upperCs
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType

@Composable
fun PodcastEpisodesScreen(viewModelFactory: ViewModelProvider.Factory, onBack: () -> Unit) {
    val viewModel: PodcastEpisodesViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    val tagAssignState by viewModel.tagAssignment.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val c = DotTheme.colors

    val playedMessage = stringResource(R.string.marked_played)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(uiState.playedUndo) {
        if (uiState.playedUndo == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(playedMessage, actionLabel = undoLabel, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) viewModel.onUndoMarkPlayed() else viewModel.onPlayedMessageDismissed()
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onSnackbarMessageShown()
        }
    }

    val listState = rememberLazyListState()
    // The header carries its own action row; a compact bar with the title appears once it scrolls away.
    val showCompactBar by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val now = rememberNowMillis()
    val playButton = pickHeaderEpisode(uiState.episodes)?.let { (episode, isResume) ->
        val isThisPlaying = uiState.currentlyPlayingEpisodeId == episode.id && uiState.isPlaying
        HeaderPlayButton(
            label = stringResource(
                when {
                    isThisPlaying -> R.string.action_pause
                    isResume -> R.string.action_resume
                    else -> R.string.action_latest_episode
                }
            ),
            icon = if (isThisPlaying) DotIcons.Pause else DotIcons.PlaySolid,
            onClick = { viewModel.onPlayOrPause(episode) }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = c.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item(key = "header") {
                    PodcastHeader(
                        title = uiState.feedTitle,
                        author = uiState.feedAuthor,
                        imageUrl = uiState.feedImageUrl,
                        tags = uiState.feedTags,
                        playButton = playButton,
                        isRefreshing = uiState.isRefreshing,
                        onBack = onBack,
                        onTagClick = viewModel::onOpenTagDialog,
                        onRefresh = viewModel::onRefresh
                    )
                }
                if (uiState.episodes.isEmpty()) {
                    item(key = "empty") {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 48.dp)) {
                            EmptyState(
                                icon = DotIcons.Podcasts,
                                title = stringResource(R.string.podcast_episodes_empty),
                                subtitle = stringResource(R.string.action_refresh)
                            )
                        }
                    }
                } else {
                    items(uiState.episodes, key = { it.id }) { episode ->
                        MarkPlayedSwipe(onToggle = { viewModel.onTogglePlayed(episode) }) {
                            ShowEpisodeRow(
                                episode = episode,
                                isCurrentlyPlaying = uiState.currentlyPlayingEpisodeId == episode.id && uiState.isPlaying,
                                onPlayOrPause = { viewModel.onPlayOrPause(episode) },
                                isQueued = episode.id in uiState.queuedEpisodeIds,
                                onAddToQueue = { viewModel.onAddToQueue(episode.id) },
                                nowMillis = now
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showCompactBar, enter = fadeIn(), exit = fadeOut()) {
                Column(modifier = Modifier.background(c.background).statusBarsPadding()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DotIconButton(DotIcons.Back, stringResource(R.string.action_back), onBack)
                        Text(
                            uiState.feedTitle.upperCs(),
                            style = DotType.button,
                            color = c.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                        )
                        DotIconButton(DotIcons.Tag, stringResource(R.string.action_manage_tags), viewModel::onOpenTagDialog)
                        if (uiState.isRefreshing) {
                            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(22.dp), color = c.accent, strokeWidth = 2.dp)
                            }
                        } else {
                            DotIconButton(DotIcons.Refresh, stringResource(R.string.action_refresh), viewModel::onRefresh)
                        }
                    }
                    HorizontalDivider(color = c.divider)
                }
            }
        }

        tagAssignState?.let { state ->
            TagAssignDialog(
                state = state,
                onToggle = viewModel.tagAssignment::setAssigned,
                onCreate = viewModel.tagAssignment::createAndAssign,
                onDismiss = viewModel.tagAssignment::dismiss
            )
        }
    }
}

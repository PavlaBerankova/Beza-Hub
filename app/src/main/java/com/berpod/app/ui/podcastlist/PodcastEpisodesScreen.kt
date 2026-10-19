package com.berpod.app.ui.podcastlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import android.app.Activity
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berpod.app.R
import com.berpod.app.ui.common.EmptyState
import com.berpod.app.ui.common.EpisodeMenuAction
import com.berpod.app.ui.common.downloadToggleText
import com.berpod.app.ui.common.MarkPlayedSwipe
import com.berpod.app.ui.common.TagPickerSheet
import com.berpod.app.ui.common.podcastTagTexts
import com.berpod.app.ui.dot.CoverImage
import com.berpod.app.ui.dot.DotIconButton
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.dot.rememberNowMillis
import com.berpod.app.ui.dot.upperCs
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.LocalDarkTheme
import com.berpod.app.ui.theme.DotType

@Composable
fun PodcastEpisodesScreen(viewModelFactory: ViewModelProvider.Factory, onBack: () -> Unit, onEpisodeClick: (Long) -> Unit, onOpenQueue: () -> Unit) {
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

    val downloadText = uiState.downloadMessage?.let { downloadToggleText(it) }
    LaunchedEffect(uiState.downloadMessage) {
        val message = downloadText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onDownloadMessageShown()
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onSnackbarMessageShown()
        }
    }

    // The podcast is gone: back to where the user came from.
    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) onBack()
    }

    val listState = rememberLazyListState()
    // The header carries its own action row; a compact bar with the title appears once it scrolls away.
    val showCompactBar by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val now = rememberNowMillis()
    val visibleEpisodes = remember(uiState.episodes, uiState.episodeSort, uiState.episodeFilter) {
        applyEpisodeView(uiState.episodes, uiState.episodeSort, uiState.episodeFilter)
    }
    val frequency = remember(uiState.episodes) { publishingFrequency(uiState.episodes.map { it.pubDateMillis }) }
    val blockColor: Color? = headerBlockColor()
    val menu = PodcastMenuActions(
        hasUnplayed = uiState.episodes.any { it.playedAtMs == null },
        hasPlayed = uiState.episodes.any { it.playedAtMs != null },
        onMarkAllPlayed = { viewModel.onRequestMarkAll(true) },
        onMarkAllUnplayed = { viewModel.onRequestMarkAll(false) },
        onEditRss = viewModel::onEditRss,
        onDelete = viewModel::onRequestDelete
    )

    // The system bar icons follow the header block while it is on screen (dark icons on a bright block, light on a deep one).
    val view = LocalView.current
    val darkIcons = !LocalDarkTheme.current
    val iconsOnBlock = blockColor?.let(::isLightTint)
    val showIconsOnBlock = iconsOnBlock != null && !showCompactBar
    DisposableEffect(showIconsOnBlock, iconsOnBlock, darkIcons) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = if (showIconsOnBlock) iconsOnBlock == true else darkIcons
        onDispose { controller?.isAppearanceLightStatusBars = darkIcons }
    }
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
                modifier = Modifier.fillMaxSize(),
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
                        onRefresh = viewModel::onRefresh,
                        onOpenQueue = onOpenQueue,
                        menu = menu,
                        episodeCount = uiState.episodes.size,
                        frequency = frequency,
                        sort = uiState.episodeSort,
                        filter = uiState.episodeFilter,
                        onFilterClick = viewModel::onCycleFilter,
                        onSortClick = viewModel::onToggleSort,
                        blockColor = blockColor
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
                } else if (visibleEpisodes.isEmpty()) {
                    item(key = "filtered-empty") {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 48.dp)) {
                            EmptyState(
                                icon = DotIcons.Podcasts,
                                title = stringResource(R.string.episodes_filtered_empty),
                                subtitle = stringResource(R.string.episodes_filtered_empty_hint)
                            )
                        }
                    }
                } else {
                    items(visibleEpisodes, key = { it.id }) { episode ->
                        MarkPlayedSwipe(
                            onToggle = { viewModel.onTogglePlayed(episode) },
                            modifier = Modifier.padding(horizontal = 10.dp).clip(RoundedCornerShape(16.dp))
                        ) {
                            ShowEpisodeRow(
                                episode = episode,
                                isCurrentlyPlaying = uiState.currentlyPlayingEpisodeId == episode.id && uiState.isPlaying,
                                onPlayOrPause = { viewModel.onPlayOrPause(episode) },
                                isQueued = episode.id in uiState.queuedEpisodeIds,
                                onMenuAction = { action ->
                                    when (action) {
                                        EpisodeMenuAction.Download, EpisodeMenuAction.CancelDownload, EpisodeMenuAction.DeleteDownload ->
                                            viewModel.onToggleDownload(episode.id)
                                        EpisodeMenuAction.AddToQueue -> viewModel.onAddToQueue(episode.id)
                                        EpisodeMenuAction.RemoveFromQueue -> viewModel.onRemoveFromQueue(episode.id)
                                        EpisodeMenuAction.MarkPlayed, EpisodeMenuAction.MarkUnplayed -> viewModel.onTogglePlayed(episode)
                                        EpisodeMenuAction.OpenEpisode -> onEpisodeClick(episode.id)
                                        EpisodeMenuAction.OpenPodcast, EpisodeMenuAction.RemoveFromNew -> Unit
                                    }
                                },
                                nowMillis = now,
                                onClick = { onEpisodeClick(episode.id) }
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
                        DotIconButton(DotIcons.QueueList, stringResource(R.string.action_open_queue), onOpenQueue)
                        DotIconButton(DotIcons.Tag, stringResource(R.string.action_manage_tags), viewModel::onOpenTagDialog)
                        if (uiState.isRefreshing) {
                            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(22.dp), color = c.accent, strokeWidth = 2.dp)
                            }
                        } else {
                            DotIconButton(DotIcons.Refresh, stringResource(R.string.action_refresh), viewModel::onRefresh)
                        }
                        PodcastMoreMenu(menu)
                    }
                    HorizontalDivider(color = c.divider)
                }
            }
        }

        if (uiState.confirmDelete) {
            DeletePodcastDialog(uiState.feedTitle, onConfirm = viewModel::onConfirmDelete, onDismiss = viewModel::onCancelDelete)
        }
        uiState.confirmMarkAllPlayed?.let { markPlayed ->
            MarkAllDialog(markPlayed, onConfirm = viewModel::onConfirmMarkAll, onDismiss = viewModel::onCancelMarkAll)
        }

        if (uiState.isEditingRss) {
            EditRssDialog(
                initialUrl = uiState.feedRssUrl,
                isSaving = uiState.isSavingRss,
                errorMessage = uiState.rssError,
                onSave = viewModel::onSaveRss,
                onDismiss = viewModel::onEditRssDismissed
            )
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
    }
}

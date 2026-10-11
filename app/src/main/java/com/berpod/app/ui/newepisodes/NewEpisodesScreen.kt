package com.berpod.app.ui.newepisodes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import com.berpod.app.ui.dot.DotIconButton
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.berpod.app.ui.theme.DotType
import com.berpod.app.ui.dot.upperCs
import com.berpod.app.ui.dot.DotFilterButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berpod.app.R
import com.berpod.app.ui.common.CompactEpisodeRow
import com.berpod.app.ui.common.EmptyState
import com.berpod.app.ui.common.MarkPlayedSwipe
import com.berpod.app.ui.common.EpisodeMenuAction
import com.berpod.app.ui.common.EpisodeRowMenu
import com.berpod.app.ui.common.downloadToggleText
import com.berpod.app.ui.common.TagChipRow
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.podcastlist.labelRes
import com.berpod.app.ui.podcastlist.EpisodeSort
import com.berpod.app.ui.theme.DotTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEpisodesScreen(viewModelFactory: ViewModelProvider.Factory, onEpisodeClick: (Long) -> Unit, onOpenPodcast: (Long) -> Unit) {
    val viewModel: NewEpisodesViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var revealEpisodeId by remember { mutableStateOf<Long?>(null) }

    val playedMessage = stringResource(R.string.marked_played)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(uiState.playedUndo) {
        if (uiState.playedUndo == null) return@LaunchedEffect
        val undoneId = uiState.playedUndo?.episodeId
        val result = snackbarHostState.showSnackbar(playedMessage, actionLabel = undoLabel, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) {
            revealEpisodeId = undoneId
            viewModel.onUndoMarkPlayed()
        } else {
            viewModel.onPlayedMessageDismissed()
        }
    }

    // The list keeps its scroll anchor, so an episode restored by "Zpět" can land above the viewport; bring it back.
    LaunchedEffect(revealEpisodeId, uiState.episodes) {
        val id = revealEpisodeId ?: return@LaunchedEffect
        val index = uiState.episodes.indexOfFirst { it.episode.id == id }
        if (index < 0) return@LaunchedEffect
        if (index < listState.firstVisibleItemIndex) listState.animateScrollToItem(index)
        revealEpisodeId = null
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

    Scaffold(
        // Embedded in the Podcasts tab, which draws the title and the section switcher.
        contentWindowInsets = WindowInsets(0),
        containerColor = DotTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (uiState.tags.isNotEmpty()) {
                TagChipRow(tags = uiState.tags, selectedTagId = uiState.selectedTagId, onSelect = viewModel::onSelectTag)
            }
            if (uiState.hasAnyEpisodes) {
                PeriodRow(
                    period = uiState.period,
                    sort = uiState.sort,
                    onPeriodClick = viewModel::onCyclePeriod,
                    onSortClick = viewModel::onToggleSort,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)
                )
            }
            when {
                !uiState.hasAnyEpisodes -> EmptyState(
                    icon = DotIcons.Refresh,
                    title = stringResource(R.string.new_episodes_empty),
                    subtitle = stringResource(R.string.action_refresh)
                )
                uiState.episodes.isEmpty() && uiState.period != NewPeriod.All -> EmptyState(
                    icon = DotIcons.Refresh,
                    title = stringResource(R.string.new_episodes_period_empty),
                    subtitle = stringResource(R.string.new_episodes_period_empty_hint)
                )
                uiState.episodes.isEmpty() -> EmptyState(
                    icon = DotIcons.Tag,
                    title = stringResource(R.string.new_episodes_tag_empty),
                    subtitle = stringResource(R.string.queue_tag_filter_empty_subtitle)
                )
                else -> {
                    LazyColumn(state = listState, contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp)) {
                        items(uiState.episodes, key = { it.episode.id }) { item ->
                            val isCurrent = uiState.currentlyPlayingEpisodeId == item.episode.id
                            // Same shape as the row, so the swipe background never shows in its corners.
                            MarkPlayedSwipe(onToggle = { viewModel.onMarkPlayed(item.episode.id) }, modifier = Modifier.clip(RoundedCornerShape(16.dp))) {
                                CompactEpisodeRow(
                                    episode = item.episode,
                                    podcastTitle = item.podcastTitle,
                                    podcastImageUrl = item.podcastImageUrl,
                                    isCurrent = isCurrent,
                                    isPlaying = isCurrent && uiState.isPlaying,
                                    onPlayOrPause = { viewModel.onPlayOrPause(item) },
                                    onClick = { onEpisodeClick(item.episode.id) },
                                    showDescription = false
                                ) {
                                    // Nové never lists queued or played episodes.
                                    EpisodeRowMenu(item.episode.id, isQueued = false, isPlayed = false, canOpenPodcast = true) { action ->
                                        when (action) {
                                            EpisodeMenuAction.Download, EpisodeMenuAction.CancelDownload, EpisodeMenuAction.DeleteDownload ->
                                                viewModel.onToggleDownload(item.episode.id)
                                            EpisodeMenuAction.AddToQueue -> viewModel.onAddToQueue(item.episode.id)
                                            EpisodeMenuAction.MarkPlayed -> viewModel.onMarkPlayed(item.episode.id)
                                            EpisodeMenuAction.OpenEpisode -> onEpisodeClick(item.episode.id)
                                            EpisodeMenuAction.OpenPodcast -> onOpenPodcast(item.episode.feedId)
                                            EpisodeMenuAction.RemoveFromQueue, EpisodeMenuAction.MarkUnplayed -> Unit
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        uiState.confirmRemoveCount?.let { count ->
            RemoveAllNewDialog(count, onConfirm = viewModel::onConfirmRemoveAll, onDismiss = viewModel::onCancelRemoveAll)
        }
    }
}

/**
 * "[VŠECHNY NOVÉ ⌄] ——— [NEJNOVĚJŠÍ ⌄]": the left button steps through how far back Nové reaches, the right one
 * flips the order.
 */
@Composable
private fun PeriodRow(
    period: NewPeriod,
    sort: EpisodeSort,
    onPeriodClick: () -> Unit,
    onSortClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = DotTheme.colors
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // DotFilterButton pads its start; pulled back so the label lines up with the page edge.
        DotFilterButton(
            stringResource(period.titleRes()),
            highlighted = period != NewPeriod.All,
            onClick = onPeriodClick,
            modifier = Modifier.offset(x = (-10).dp)
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = c.divider)
        DotFilterButton(stringResource(sort.labelRes()), highlighted = false, onClick = onSortClick, flipped = sort == EpisodeSort.OldestFirst)
    }
}

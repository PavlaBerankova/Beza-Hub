package com.berpod.app.ui.podcastlist

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berpod.app.R
import com.berpod.app.ui.common.EmptyState
import com.berpod.app.ui.common.TagAssignDialog
import com.berpod.app.ui.dot.DotIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastEpisodesScreen(viewModelFactory: ViewModelProvider.Factory, onBack: () -> Unit) {
    val viewModel: PodcastEpisodesViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    val tagAssignState by viewModel.tagAssignment.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onSnackbarMessageShown()
        }
    }

    val listState = rememberLazyListState()
    // Transparent bar over the header; solid with the title once the header has scrolled away.
    var headerHeightPx by remember { mutableIntStateOf(0) }
    var barHeightPx by remember { mutableIntStateOf(0) }
    val isHeaderVisible by remember {
        derivedStateOf {
            isBarOverHeader(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, headerHeightPx, barHeightPx)
        }
    }
    var coverColor by remember { mutableStateOf<Color?>(null) }
    val headerBackground = coverColor ?: MaterialTheme.colorScheme.surfaceVariant
    val headerContent = if (isLightColor(headerBackground.toArgb())) Color.Black else Color.White
    val barContentColor = if (isHeaderVisible) headerContent else MaterialTheme.colorScheme.onSurface
    val headerEpisode = pickHeaderEpisode(uiState.episodes)
    val playButton = headerEpisode?.let { (episode, isResume) ->
        val isThisPlaying = uiState.currentlyPlayingEpisodeId == episode.id && uiState.isPlaying
        HeaderPlayButton(
            label = stringResource(
                when {
                    isThisPlaying -> R.string.action_pause
                    isResume -> R.string.action_resume
                    else -> R.string.action_latest_episode
                }
            ),
            icon = if (isThisPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            onClick = { viewModel.onPlayOrPause(episode) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.onSizeChanged { barHeightPx = it.height },
                title = {
                    if (!isHeaderVisible) {
                        Text(uiState.feedTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else if (uiState.feedTags.isNotEmpty()) {
                        // Over the header the bar has no title, so the podcast's tags sit here instead.
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                uiState.feedTags.forEach { name -> HeaderTagChip(name, headerContent) }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isHeaderVisible) Color.Transparent else MaterialTheme.colorScheme.surface,
                    navigationIconContentColor = barContentColor,
                    actionIconContentColor = barContentColor,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::onOpenTagDialog) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Label,
                            contentDescription = stringResource(R.string.action_manage_tags)
                        )
                    }
                    if (uiState.isRefreshing) {
                        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = barContentColor, strokeWidth = 2.dp)
                        }
                    } else {
                        IconButton(onClick = viewModel::onRefresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        // The header draws under the transparent top bar, so only the bottom padding is applied here.
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(bottom = paddingValues.calculateBottomPadding())
        ) {
            item(key = "header") {
                PodcastHeader(
                    title = uiState.feedTitle,
                    author = uiState.feedAuthor,
                    imageUrl = uiState.feedImageUrl,
                    topPadding = paddingValues.calculateTopPadding(),
                    background = headerBackground,
                    contentColor = headerContent,
                    playButton = playButton,
                    onColorExtracted = { coverColor = it },
                    modifier = Modifier.onSizeChanged { headerHeightPx = it.height }
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
                    ShowEpisodeRow(
                        episode = episode,
                        isCurrentlyPlaying = uiState.currentlyPlayingEpisodeId == episode.id && uiState.isPlaying,
                        onPlayOrPause = { viewModel.onPlayOrPause(episode) },
                        onAddToQueue = { viewModel.onAddToQueue(episode.id) }
                    )
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

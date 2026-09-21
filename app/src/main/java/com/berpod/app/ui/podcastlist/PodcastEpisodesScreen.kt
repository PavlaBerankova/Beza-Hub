package com.berpod.app.ui.podcastlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berpod.app.R
import com.berpod.app.ui.common.AddToPlaylistDialog
import com.berpod.app.ui.common.EmptyState
import com.berpod.app.ui.common.EpisodeListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastEpisodesScreen(viewModelFactory: ViewModelProvider.Factory, onBack: () -> Unit) {
    val viewModel: PodcastEpisodesViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onSnackbarMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.feedTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (uiState.isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 8.dp))
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
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (uiState.episodes.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Podcasts,
                    title = stringResource(R.string.podcast_episodes_empty),
                    subtitle = stringResource(R.string.action_refresh)
                )
            } else {
                LazyColumn {
                    items(uiState.episodes, key = { it.id }) { episode ->
                        EpisodeListItem(
                            episode = episode,
                            podcastTitle = null,
                            podcastImageUrl = uiState.feedImageUrl,
                            onAddToQueue = { viewModel.onAddToQueue(episode.id) },
                            onAddToPlaylist = { viewModel.onOpenPlaylistPicker(episode.id) },
                            onPlay = { viewModel.onPlay(episode) },
                            isCurrentlyPlaying = uiState.currentlyPlayingEpisodeId == episode.id && uiState.isPlaying
                        )
                    }
                }
            }
        }

        if (uiState.playlistPickerEpisodeId != null) {
            AddToPlaylistDialog(
                playlists = uiState.playlists,
                isSubmitting = uiState.isCreatingPlaylist,
                errorMessage = uiState.playlistPickerError,
                onDismiss = viewModel::onDismissPlaylistPicker,
                onAddToExisting = viewModel::onAddToExistingPlaylist,
                onCreateNew = viewModel::onCreatePlaylistAndAdd
            )
        }
    }
}

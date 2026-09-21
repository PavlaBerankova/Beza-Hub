package com.berpod.app.ui.newepisodes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberNew
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
fun NewEpisodesScreen(viewModelFactory: ViewModelProvider.Factory) {
    val viewModel: NewEpisodesViewModel = viewModel(factory = viewModelFactory)
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
                title = { Text(stringResource(R.string.new_episodes_title)) },
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
                    icon = Icons.Filled.FiberNew,
                    title = stringResource(R.string.new_episodes_empty),
                    subtitle = stringResource(R.string.action_refresh)
                )
            } else {
                LazyColumn {
                    items(uiState.episodes, key = { it.episode.id }) { item ->
                        EpisodeListItem(
                            episode = item.episode,
                            podcastTitle = item.podcastTitle,
                            podcastImageUrl = item.podcastImageUrl,
                            onAddToQueue = { viewModel.onAddToQueue(item.episode.id) },
                            onAddToPlaylist = { viewModel.onOpenPlaylistPicker(item.episode.id) },
                            onPlay = { viewModel.onPlay(item) },
                            isCurrentlyPlaying = uiState.currentlyPlayingEpisodeId == item.episode.id && uiState.isPlaying
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

package com.berpod.app.ui.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berpod.app.R
import com.berpod.app.data.local.PlaylistEntity
import com.berpod.app.ui.common.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(viewModelFactory: ViewModelProvider.Factory, onPlaylistClick: (Long) -> Unit) {
    val viewModel: PlaylistsViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.playlists_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::onFabClick) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.fab_add_playlist))
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (uiState.playlists.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    title = stringResource(R.string.playlists_empty),
                    subtitle = stringResource(R.string.fab_add_playlist)
                )
            } else {
                LazyColumn {
                    items(uiState.playlists, key = { it.id }) { playlist ->
                        PlaylistRow(playlist = playlist, onClick = { onPlaylistClick(playlist.id) })
                    }
                }
            }
        }

        if (uiState.isCreateDialogOpen) {
            CreatePlaylistDialog(
                isSubmitting = uiState.isSubmitting,
                errorMessage = uiState.errorMessage,
                onDismiss = viewModel::onDismissDialog,
                onSubmit = viewModel::onCreatePlaylist
            )
        }
    }
}

@Composable
private fun PlaylistRow(playlist: PlaylistEntity, onClick: () -> Unit) {
    Text(
        text = playlist.name,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    )
}

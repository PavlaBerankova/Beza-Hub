package com.berpod.app.ui.queue

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berpod.app.R
import com.berpod.app.ui.common.EmptyState
import com.berpod.app.ui.common.EpisodeListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(viewModelFactory: ViewModelProvider.Factory) {
    val viewModel: QueueViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_queue)) }) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (uiState.items.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    title = stringResource(R.string.queue_empty_title),
                    subtitle = stringResource(R.string.queue_empty_subtitle)
                )
            } else {
                LazyColumn {
                    itemsIndexed(uiState.items, key = { _, item -> item.queueEntryId }) { index, item ->
                        EpisodeListItem(
                            episode = item.episode,
                            podcastTitle = item.podcastTitle,
                            podcastImageUrl = item.podcastImageUrl,
                            onPlay = { viewModel.onPlay(item) },
                            isCurrentlyPlaying = uiState.currentlyPlayingEpisodeId == item.episode.id && uiState.isPlaying,
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { viewModel.onMoveUp(index) }, enabled = index > 0) {
                                        Icon(
                                            Icons.Filled.KeyboardArrowUp,
                                            contentDescription = stringResource(R.string.action_move_up)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.onMoveDown(index) },
                                        enabled = index < uiState.items.lastIndex
                                    ) {
                                        Icon(
                                            Icons.Filled.KeyboardArrowDown,
                                            contentDescription = stringResource(R.string.action_move_down)
                                        )
                                    }
                                    IconButton(onClick = { viewModel.onRemove(item.queueEntryId) }) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = stringResource(R.string.action_remove_from_queue)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

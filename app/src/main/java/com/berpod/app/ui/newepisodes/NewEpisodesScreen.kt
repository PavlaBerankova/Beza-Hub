package com.berpod.app.ui.newepisodes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.berpod.app.ui.common.EmptyState
import com.berpod.app.ui.common.TagChipRow
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.dot.SectionSeparator
import com.berpod.app.ui.dot.rememberNowMillis
import com.berpod.app.ui.podcastlist.ShowEpisodeRow
import com.berpod.app.ui.theme.DotTheme

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
        // Embedded in the Podcasts tab, which draws the title and the section switcher.
        contentWindowInsets = WindowInsets(0),
        containerColor = DotTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (uiState.hasAnyEpisodes) {
                SectionSeparator(
                    stringResource(R.string.section_today),
                    stringResource(R.string.section_newest),
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                    leadingSquare = true
                )
            }
            if (uiState.tags.isNotEmpty()) {
                TagChipRow(tags = uiState.tags, selectedTagId = uiState.selectedTagId, onSelect = viewModel::onSelectTag)
            }
            when {
                !uiState.hasAnyEpisodes -> EmptyState(
                    icon = DotIcons.Refresh,
                    title = stringResource(R.string.new_episodes_empty),
                    subtitle = stringResource(R.string.action_refresh)
                )
                uiState.episodes.isEmpty() -> EmptyState(
                    icon = DotIcons.Tag,
                    title = stringResource(R.string.new_episodes_tag_empty),
                    subtitle = stringResource(R.string.queue_tag_filter_empty_subtitle)
                )
                else -> {
                    val now = rememberNowMillis()
                    LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)) {
                        items(uiState.episodes, key = { it.episode.id }) { item ->
                            ShowEpisodeRow(
                                episode = item.episode,
                                isCurrentlyPlaying = uiState.currentlyPlayingEpisodeId == item.episode.id && uiState.isPlaying,
                                onPlayOrPause = { viewModel.onPlayOrPause(item) },
                                onAddToQueue = { viewModel.onAddToQueue(item.episode.id) },
                                nowMillis = now,
                                podcastTitle = item.podcastTitle,
                                podcastImageUrl = item.podcastImageUrl
                            )
                        }
                    }
                }
            }
        }
    }
}

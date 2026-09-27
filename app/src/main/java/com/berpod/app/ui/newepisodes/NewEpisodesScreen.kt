package com.berpod.app.ui.newepisodes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berpod.app.R
import com.berpod.app.ui.common.EmptyState
import com.berpod.app.ui.common.formatDuration
import com.berpod.app.ui.dot.CoverImage
import com.berpod.app.ui.dot.DotIconButton
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.dot.SectionSeparator
import com.berpod.app.ui.dot.formatDotDate
import com.berpod.app.ui.dot.isPublishedToday
import com.berpod.app.ui.dot.rememberNowMillis
import com.berpod.app.ui.dot.upperCs
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType

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
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (uiState.episodes.isEmpty()) {
                EmptyState(
                    icon = DotIcons.Refresh,
                    title = stringResource(R.string.new_episodes_empty),
                    subtitle = stringResource(R.string.action_refresh)
                )
            } else {
                val c = DotTheme.colors
                val now = rememberNowMillis()
                LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
                    item(key = "separator") { SectionSeparator(stringResource(R.string.section_today), stringResource(R.string.section_newest), leadingSquare = true) }
                    items(uiState.episodes, key = { it.episode.id }) { item ->
                        val episode = item.episode
                        val today = isPublishedToday(episode.pubDateMillis, now)
                        val isPlaying = uiState.currentlyPlayingEpisodeId == episode.id && uiState.isPlaying
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CoverImage(item.podcastImageUrl, 72.dp, 14.dp)
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (today) Box(Modifier.size(7.dp).clip(RoundedCornerShape(1.dp)).background(c.accent))
                                    Text(
                                        listOfNotNull(formatDotDate(episode.pubDateMillis), formatDuration(episode.durationSeconds))
                                            .joinToString(" · ").upperCs(),
                                        style = DotType.meta.copy(fontWeight = FontWeight.W600),
                                        color = c.textMuted
                                    )
                                }
                                Text(episode.title, style = DotType.rowTitle, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Row(
                                    modifier = Modifier.padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    DotIconButton(DotIcons.Download, stringResource(R.string.action_download), {}, radius = 12.dp, iconSize = 22.dp)
                                    DotIconButton(
                                        DotIcons.Queue,
                                        stringResource(R.string.action_add_to_queue),
                                        { viewModel.onAddToQueue(episode.id) },
                                        radius = 12.dp,
                                        iconSize = 22.dp
                                    )
                                    Spacer(Modifier.weight(1f))
                                    DotIconButton(
                                        if (isPlaying) DotIcons.Equalizer else DotIcons.Play,
                                        stringResource(if (isPlaying) R.string.action_pause else R.string.action_play),
                                        { viewModel.onPlay(item) },
                                        size = 50.dp,
                                        iconSize = 22.dp,
                                        accentBorder = isPlaying
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = c.divider)
                    }
                }
            }
        }

    }
}

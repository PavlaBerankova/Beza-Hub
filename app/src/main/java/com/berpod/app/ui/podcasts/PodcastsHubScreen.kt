package com.berpod.app.ui.podcasts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berpod.app.R
import com.berpod.app.ui.newepisodes.NewEpisodesScreen
import com.berpod.app.ui.newepisodes.NewEpisodesViewModel
import com.berpod.app.ui.podcastlist.PodcastListScreen
import com.berpod.app.ui.queue.QueueScreen

/** The sections of the Podcasts tab, in switcher order. */
enum class PodcastsSection(val labelRes: Int) {
    Subscriptions(R.string.section_subscriptions),
    Queue(R.string.nav_queue),
    New(R.string.nav_new_episodes)
}

/**
 * The Podcasts tab: a large title with a section switcher (Odběry · Fronta · Nové) above the chosen screen.
 * The chosen section survives visits to a podcast's detail and back, for the session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastsHubScreen(
    subscriptionsFactory: ViewModelProvider.Factory,
    queueFactory: ViewModelProvider.Factory,
    newEpisodesFactory: ViewModelProvider.Factory,
    onPodcastClick: (Long) -> Unit
) {
    var section by rememberSaveable { mutableStateOf(PodcastsSection.Subscriptions) }
    // Same instance NewEpisodesScreen gets (same owner and class), so the bar's refresh drives that screen.
    val newEpisodesViewModel: NewEpisodesViewModel = viewModel(factory = newEpisodesFactory)
    val newEpisodesState by newEpisodesViewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    stringResource(R.string.nav_podcasts),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            actions = {
                if (section == PodcastsSection.New) {
                    if (newEpisodesState.isRefreshing) {
                        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        IconButton(onClick = newEpisodesViewModel::onRefresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                        }
                    }
                }
            }
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
        ) {
            PodcastsSection.entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = section == entry,
                    onClick = { section = entry },
                    shape = SegmentedButtonDefaults.itemShape(index, PodcastsSection.entries.size),
                    label = { Text(stringResource(entry.labelRes)) }
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            when (section) {
                PodcastsSection.Subscriptions -> PodcastListScreen(subscriptionsFactory, onPodcastClick)
                PodcastsSection.Queue -> QueueScreen(queueFactory)
                PodcastsSection.New -> NewEpisodesScreen(newEpisodesFactory)
            }
        }
    }
}

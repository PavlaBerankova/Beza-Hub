package com.berpod.app.ui.podcasts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berpod.app.R
import com.berpod.app.ui.dot.DotIconButton
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.dot.DotSegmented
import com.berpod.app.ui.dot.DotTitle
import com.berpod.app.ui.newepisodes.NewEpisodesScreen
import com.berpod.app.ui.newepisodes.NewEpisodesViewModel
import com.berpod.app.ui.podcastlist.PodcastListScreen
import com.berpod.app.ui.podcastlist.PodcastListViewModel
import com.berpod.app.ui.queue.QueueScreen
import com.berpod.app.ui.theme.DotTheme

/** The sections of the Podcasts tab, in switcher order. */
enum class PodcastsSection(val labelRes: Int) {
    Subscriptions(R.string.section_subscriptions),
    Queue(R.string.nav_queue),
    New(R.string.nav_new_episodes)
}

/**
 * The Podcasts tab: a large title with a section switcher (Odběry · Fronta · Nové) above the chosen screen.
 * The chosen section is owned by the caller so other screens (the player's FRONTA) can switch it.
 */
@Composable
fun PodcastsHubScreen(
    subscriptionsFactory: ViewModelProvider.Factory,
    queueFactory: ViewModelProvider.Factory,
    newEpisodesFactory: ViewModelProvider.Factory,
    onPodcastClick: (Long) -> Unit,
    onEpisodeClick: (Long) -> Unit,
    section: PodcastsSection,
    onSectionChange: (PodcastsSection) -> Unit
) {
    // Same instance NewEpisodesScreen gets (same owner and class), so the bar's refresh drives that screen.
    val newEpisodesViewModel: NewEpisodesViewModel = viewModel(factory = newEpisodesFactory)
    val newEpisodesState by newEpisodesViewModel.uiState.collectAsState()

    val listViewModel: PodcastListViewModel = viewModel(factory = subscriptionsFactory)
    val listState by listViewModel.uiState.collectAsState()
    val c = DotTheme.colors
    Column(modifier = Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DotTitle(stringResource(R.string.nav_podcasts), Modifier.weight(1f))
            when (section) {
                PodcastsSection.Subscriptions -> Unit
                PodcastsSection.New -> if (newEpisodesState.isRefreshing) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = c.accent, strokeWidth = 2.dp)
                    }
                } else {
                    DotIconButton(DotIcons.Refresh, stringResource(R.string.action_refresh), newEpisodesViewModel::onRefresh)
                }
                PodcastsSection.Queue -> Unit
            }
            if (section == PodcastsSection.New) {
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    DotIconButton(DotIcons.More, stringResource(R.string.action_more), { menuOpen = true })
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_remove_all_new)) },
                            enabled = newEpisodesState.episodes.isNotEmpty(),
                            onClick = { menuOpen = false; newEpisodesViewModel.onRequestRemoveAll() }
                        )
                    }
                }
            }
        }
        DotSegmented(
            options = PodcastsSection.entries.map { stringResource(it.labelRes) },
            selectedIndex = section.ordinal,
            onSelect = { onSectionChange(PodcastsSection.entries[it]) },
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(18.dp))
        Box(modifier = Modifier.weight(1f)) {
            when (section) {
                PodcastsSection.Subscriptions -> PodcastListScreen(subscriptionsFactory, onPodcastClick)
                PodcastsSection.Queue -> QueueScreen(queueFactory, onEpisodeClick, onPodcastClick)
                PodcastsSection.New -> NewEpisodesScreen(newEpisodesFactory, onEpisodeClick, onPodcastClick)
            }
        }
    }
}

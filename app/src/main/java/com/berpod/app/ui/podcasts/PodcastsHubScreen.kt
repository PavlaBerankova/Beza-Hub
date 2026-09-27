package com.berpod.app.ui.podcasts

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.berpod.app.R
import com.berpod.app.ui.dot.DotIconButton
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.dot.DotSegmented
import com.berpod.app.ui.dot.DotTitle
import com.berpod.app.ui.dot.subscriptionCountText
import com.berpod.app.ui.dot.upperCs
import com.berpod.app.ui.newepisodes.NewEpisodesScreen
import com.berpod.app.ui.newepisodes.NewEpisodesViewModel
import com.berpod.app.ui.podcastlist.PodcastListScreen
import com.berpod.app.ui.podcastlist.PodcastListViewModel
import com.berpod.app.ui.queue.QueueScreen
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType

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

    val listViewModel: PodcastListViewModel = viewModel(factory = subscriptionsFactory)
    val listState by listViewModel.uiState.collectAsState()
    val c = DotTheme.colors
    Column(modifier = Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            DotTitle(stringResource(R.string.nav_podcasts), Modifier.weight(1f))
            when (section) {
                PodcastsSection.Subscriptions -> {
                    val tagName = listState.tags.firstOrNull { it.id == listState.selectedTagId }?.name
                    // Bounded so a long tag name can't squeeze the title.
                    Column(modifier = Modifier.widthIn(max = 150.dp), horizontalAlignment = Alignment.End) {
                        Text(
                            subscriptionCountText(listState.podcastCount),
                            style = DotType.meta.copy(lineHeight = 1.5.em),
                            color = c.textMuted,
                            maxLines = 1
                        )
                        if (tagName != null) {
                            Text(
                                stringResource(R.string.filter_label, tagName).upperCs(),
                                style = DotType.meta.copy(lineHeight = 1.5.em),
                                color = c.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
                PodcastsSection.New -> if (newEpisodesState.isRefreshing) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = c.accent, strokeWidth = 2.dp)
                    }
                } else {
                    DotIconButton(DotIcons.Refresh, stringResource(R.string.action_refresh), newEpisodesViewModel::onRefresh)
                }
                PodcastsSection.Queue -> Unit
            }
        }
        DotSegmented(
            options = PodcastsSection.entries.map { stringResource(it.labelRes) },
            selectedIndex = section.ordinal,
            onSelect = { section = PodcastsSection.entries[it] },
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(18.dp))
        Box(modifier = Modifier.weight(1f)) {
            when (section) {
                PodcastsSection.Subscriptions -> PodcastListScreen(subscriptionsFactory, onPodcastClick)
                PodcastsSection.Queue -> QueueScreen(queueFactory)
                PodcastsSection.New -> NewEpisodesScreen(newEpisodesFactory)
            }
        }
    }
}

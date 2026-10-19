package com.berpod.app.ui.youtube

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.berpod.app.ui.youtube.channels.YoutubeChannelsScreen
import com.berpod.app.ui.youtube.newvideos.NewVideosScreen
import com.berpod.app.ui.youtube.newvideos.NewVideosViewModel
import com.berpod.app.ui.youtube.queue.VideoQueueScreen
import com.berpod.app.ui.theme.DotTheme

/** The sections of the YouTube tab, in switcher order (canvas "Y1/Y2/Y3"). */
enum class YoutubeSection(val labelRes: Int) {
    Channels(R.string.nav_channels),
    Queue(R.string.nav_queue),
    New(R.string.nav_new_episodes)
}

/** The YouTube tab: a large title with a section switcher (Kanály · Fronta · Nové) above the chosen screen. */
@Composable
fun YoutubeHubScreen(
    channelsFactory: ViewModelProvider.Factory,
    queueFactory: ViewModelProvider.Factory,
    newVideosFactory: ViewModelProvider.Factory,
    onChannelClick: (Long) -> Unit,
    onVideoClick: (Long) -> Unit,
    onOpenChannelSources: () -> Unit,
    section: YoutubeSection,
    onSectionChange: (YoutubeSection) -> Unit
) {
    // Same instance NewVideosScreen gets (same owner and class), so the bar's refresh drives that screen.
    val newVideosViewModel: NewVideosViewModel = viewModel(factory = newVideosFactory)
    val newVideosState by newVideosViewModel.uiState.collectAsState()
    val c = DotTheme.colors

    Column(modifier = Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DotTitle(stringResource(R.string.nav_youtube), Modifier.weight(1f))
            if (section == YoutubeSection.Channels) {
                DotIconButton(DotIcons.Sources, stringResource(R.string.sources), onOpenChannelSources)
            }
            // Refreshes every channel; a spinner takes its place while that runs.
            if (newVideosState.isRefreshing) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = c.accent, strokeWidth = 2.dp)
                }
            } else {
                DotIconButton(DotIcons.Refresh, stringResource(R.string.action_refresh_all_channels), newVideosViewModel::onRefresh)
            }
        }
        DotSegmented(
            options = YoutubeSection.entries.map { stringResource(it.labelRes) },
            selectedIndex = section.ordinal,
            onSelect = { onSectionChange(YoutubeSection.entries[it]) },
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(18.dp))
        Box(modifier = Modifier.weight(1f)) {
            when (section) {
                YoutubeSection.Channels -> YoutubeChannelsScreen(channelsFactory, onChannelClick)
                YoutubeSection.Queue -> VideoQueueScreen(queueFactory, onVideoClick)
                YoutubeSection.New -> NewVideosScreen(newVideosFactory, onVideoClick)
            }
        }
    }
}

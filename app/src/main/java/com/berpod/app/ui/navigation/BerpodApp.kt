package com.berpod.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.berpod.app.R
import com.berpod.app.di.AppContainer
import com.berpod.app.ui.comingsoon.ComingSoonScreen
import com.berpod.app.ui.dot.DotIcon
import com.berpod.app.ui.dot.DotIconSpec
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.dot.upperCs
import com.berpod.app.ui.newepisodes.NewEpisodesViewModel
import com.berpod.app.ui.player.MiniPlayerBar
import com.berpod.app.ui.player.PlayerScreen
import com.berpod.app.ui.player.PlayerViewModel
import com.berpod.app.ui.podcastlist.PodcastEpisodesScreen
import com.berpod.app.ui.podcastlist.PodcastEpisodesViewModel
import com.berpod.app.ui.podcastlist.PodcastListViewModel
import com.berpod.app.ui.podcasts.PodcastsHubScreen
import com.berpod.app.ui.queue.QueueViewModel
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType

private val bottomTabs = listOf(Screen.Podcasts, Screen.YouTube, Screen.Articles)

private fun iconFor(screen: Screen): DotIconSpec = when (screen) {
    Screen.Podcasts -> DotIcons.Podcasts
    Screen.YouTube -> DotIcons.YouTube
    Screen.Articles -> DotIcons.Articles
    else -> DotIcons.Podcasts
}

@Composable
private fun labelFor(screen: Screen): String = when (screen) {
    Screen.Podcasts -> stringResource(R.string.nav_podcasts)
    Screen.YouTube -> stringResource(R.string.nav_youtube)
    Screen.Articles -> stringResource(R.string.nav_articles)
    else -> ""
}

@Composable
fun BerpodApp(container: AppContainer) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    // Built once here (above the NavHost) so the mini-player and the full PlayerScreen
    // observe the exact same instance, instead of each screen creating its own via a factory.
    val playerViewModel: PlayerViewModel = viewModel(factory = PlayerViewModel.factory(container.playbackRepository))
    val playbackState by playerViewModel.uiState.collectAsState()

    Scaffold(
        containerColor = DotTheme.colors.background,
        bottomBar = {
            val showNavigation = bottomTabs.any { it.route == currentRoute }
            Column {
                // The full player already shows everything the mini player would.
                if (playbackState.currentEpisodeId != null && currentRoute != Screen.Player.route) {
                    MiniPlayerBar(
                        state = playbackState,
                        onClick = { navController.navigate(Screen.Player.route) { launchSingleTop = true } },
                        onPlayPauseClick = playerViewModel::togglePlayPause,
                        padForSystemBar = !showNavigation
                    )
                }
                if (showNavigation) {
                    val c = DotTheme.colors
                    Column(Modifier.background(c.background)) {
                        HorizontalDivider(color = c.divider)
                        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(82.dp)) {
                            bottomTabs.forEach { screen ->
                                val selected = currentRoute == screen.route
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable(role = Role.Tab) {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                        .semantics { this.selected = selected },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    // Selected: the icon's own colour roles; unselected: all muted.
                                    DotIcon(iconFor(screen), 30.dp, monochrome = if (selected) null else c.textMuted)
                                    Spacer(Modifier.height(6.dp))
                                    Text(labelFor(screen).upperCs(), style = DotType.label, color = if (selected) c.text else c.textMuted)
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Podcasts.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Podcasts.route) {
                PodcastsHubScreen(
                    subscriptionsFactory = PodcastListViewModel.factory(
                        container.podcastRepository,
                        container.episodeRepository,
                        container.tagRepository
                    ),
                    queueFactory = QueueViewModel.factory(
                        container.queueRepository,
                        container.playbackRepository,
                        container.tagRepository
                    ),
                    newEpisodesFactory = NewEpisodesViewModel.factory(
                        container.episodeRepository,
                        container.queueRepository,
                        container.playbackRepository
                    ),
                    onPodcastClick = { feedId -> navController.navigate(Screen.PodcastEpisodes.createRoute(feedId)) }
                )
            }
            composable(Screen.YouTube.route) {
                ComingSoonScreen(title = stringResource(R.string.nav_youtube), icon = DotIcons.YouTube)
            }
            composable(Screen.Articles.route) {
                ComingSoonScreen(title = stringResource(R.string.nav_articles), icon = DotIcons.Articles)
            }
            composable(
                route = Screen.PodcastEpisodes.route,
                arguments = listOf(navArgument(Screen.PodcastEpisodes.ARG_FEED_ID) { type = NavType.LongType })
            ) { backStackEntry ->
                val feedId = backStackEntry.arguments?.getLong(Screen.PodcastEpisodes.ARG_FEED_ID) ?: return@composable
                PodcastEpisodesScreen(
                    viewModelFactory = PodcastEpisodesViewModel.factory(
                        container.podcastRepository,
                        container.episodeRepository,
                        container.queueRepository,
                        container.playbackRepository,
                        container.tagRepository,
                        feedId
                    ),
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Player.route) {
                PlayerScreen(viewModel = playerViewModel, onBack = { navController.popBackStack() })
            }
        }
    }
}

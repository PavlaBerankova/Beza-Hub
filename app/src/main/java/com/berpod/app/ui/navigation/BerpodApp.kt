package com.berpod.app.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.berpod.app.R
import com.berpod.app.di.AppContainer
import com.berpod.app.ui.newepisodes.NewEpisodesScreen
import com.berpod.app.ui.newepisodes.NewEpisodesViewModel
import com.berpod.app.ui.player.MiniPlayerBar
import com.berpod.app.ui.player.PlayerScreen
import com.berpod.app.ui.player.PlayerViewModel
import com.berpod.app.ui.podcastlist.PodcastEpisodesScreen
import com.berpod.app.ui.podcastlist.PodcastEpisodesViewModel
import com.berpod.app.ui.podcastlist.PodcastListScreen
import com.berpod.app.ui.podcastlist.PodcastListViewModel
import com.berpod.app.ui.queue.QueueScreen
import com.berpod.app.ui.queue.QueueViewModel

private val bottomTabs = listOf(Screen.Podcasts, Screen.Queue, Screen.NewEpisodes)

private fun iconFor(screen: Screen): ImageVector = when (screen) {
    Screen.Podcasts -> Icons.Filled.Podcasts
    Screen.Queue -> Icons.AutoMirrored.Filled.QueueMusic
    Screen.NewEpisodes -> Icons.Filled.FiberNew
    else -> Icons.Filled.Podcasts
}

@Composable
private fun labelFor(screen: Screen): String = when (screen) {
    Screen.Podcasts -> stringResource(R.string.nav_podcasts)
    Screen.Queue -> stringResource(R.string.nav_queue)
    Screen.NewEpisodes -> stringResource(R.string.nav_new_episodes)
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
        bottomBar = {
            val showNavigation = bottomTabs.any { it.route == currentRoute }
            Column {
                if (playbackState.currentEpisodeId != null) {
                    MiniPlayerBar(
                        state = playbackState,
                        onClick = { navController.navigate(Screen.Player.route) { launchSingleTop = true } },
                        onPlayPauseClick = playerViewModel::togglePlayPause,
                        padForSystemBar = !showNavigation
                    )
                }
                if (showNavigation) {
                    NavigationBar {
                        bottomTabs.forEach { screen ->
                            NavigationBarItem(
                                selected = currentRoute == screen.route,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(iconFor(screen), contentDescription = null) },
                                label = { Text(labelFor(screen)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                )
                            )
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
                PodcastListScreen(
                    viewModelFactory = PodcastListViewModel.factory(
                        container.podcastRepository,
                        container.episodeRepository,
                        container.tagRepository
                    ),
                    onPodcastClick = { feedId -> navController.navigate(Screen.PodcastEpisodes.createRoute(feedId)) }
                )
            }
            composable(Screen.Queue.route) {
                QueueScreen(
                    viewModelFactory = QueueViewModel.factory(
                        container.queueRepository,
                        container.playbackRepository,
                        container.tagRepository
                    )
                )
            }
            composable(Screen.NewEpisodes.route) {
                NewEpisodesScreen(
                    viewModelFactory = NewEpisodesViewModel.factory(
                        container.episodeRepository,
                        container.queueRepository,
                        container.playbackRepository
                    )
                )
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

package com.berpod.app.ui.navigation

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.berpod.app.ui.dot.DotIcon
import com.berpod.app.ui.dot.DotIconSpec
import com.berpod.app.ui.dot.DotIcons
import com.berpod.app.ui.dot.upperCs
import com.berpod.app.ui.articles.ArticleReaderScreen
import com.berpod.app.ui.articles.ArticleReaderViewModel
import com.berpod.app.ui.articles.ArticleSourcesScreen
import com.berpod.app.ui.articles.ArticleSourcesViewModel
import com.berpod.app.ui.articles.ArticlesScreen
import com.berpod.app.ui.articles.ArticlesViewModel
import com.berpod.app.ui.episode.EpisodeDetailScreen
import com.berpod.app.ui.episode.EpisodeDetailViewModel
import com.berpod.app.ui.newepisodes.NewEpisodesViewModel
import com.berpod.app.ui.player.MiniPlayerBar
import com.berpod.app.ui.player.PlayerScreen
import com.berpod.app.ui.player.PlayerViewModel
import com.berpod.app.ui.podcastlist.PodcastEpisodesScreen
import com.berpod.app.ui.podcastlist.PodcastEpisodesViewModel
import com.berpod.app.ui.podcastlist.PodcastListViewModel
import com.berpod.app.ui.podcasts.PodcastsHubScreen
import com.berpod.app.ui.podcasts.PodcastsSection
import com.berpod.app.ui.queue.QueueViewModel
import com.berpod.app.ui.settings.SettingsScreen
import com.berpod.app.ui.youtube.YoutubeHubScreen
import com.berpod.app.ui.youtube.YoutubeSection
import com.berpod.app.ui.youtube.channels.YoutubeChannelScreen
import com.berpod.app.ui.youtube.channels.YoutubeChannelViewModel
import com.berpod.app.ui.youtube.channels.YoutubeChannelSourcesScreen
import com.berpod.app.ui.youtube.channels.YoutubeChannelSourcesViewModel
import com.berpod.app.ui.youtube.channels.YoutubeChannelsViewModel
import com.berpod.app.ui.youtube.newvideos.NewVideosViewModel
import com.berpod.app.ui.youtube.queue.VideoQueueViewModel
import com.berpod.app.ui.youtube.video.VideoDetailScreen
import com.berpod.app.ui.youtube.video.VideoDetailViewModel
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.TabAccent
import com.berpod.app.ui.theme.TabAccentTheme
import com.berpod.app.ui.theme.tabAccentFor
import com.berpod.app.ui.theme.DotType

private val bottomTabs = listOf(Screen.Podcasts, Screen.YouTube, Screen.Articles, Screen.Settings)

private fun iconFor(screen: Screen): DotIconSpec = when (screen) {
    Screen.Podcasts -> DotIcons.Podcasts
    Screen.YouTube -> DotIcons.YouTube
    Screen.Articles -> DotIcons.Articles
    Screen.Settings -> DotIcons.Settings
    else -> DotIcons.Podcasts
}

@Composable
private fun labelFor(screen: Screen): String = when (screen) {
    Screen.Podcasts -> stringResource(R.string.nav_podcasts)
    Screen.YouTube -> stringResource(R.string.nav_youtube)
    Screen.Articles -> stringResource(R.string.nav_articles)
    Screen.Settings -> stringResource(R.string.nav_settings)
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
    // Hoisted so the player's FRONTA button can open Podcasts › Fronta.
    var podcastsSection by rememberSaveable { mutableStateOf(PodcastsSection.Subscriptions) }
    var youtubeSection by rememberSaveable { mutableStateOf(YoutubeSection.Channels) }

    // Everything on screen — including the bottom bar — takes the current tab's accent colour.
    TabAccentTheme(tabAccentFor(currentRoute)) {
        Scaffold(
            containerColor = DotTheme.colors.background,
            // A podcast's and an episode's page tint their header up behind the status bar, so they pad for it themselves.
            contentWindowInsets = when (currentRoute) {
                Screen.PodcastEpisodes.route, Screen.EpisodeDetail.route -> ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.statusBars)
                else -> ScaffoldDefaults.contentWindowInsets
            },
            bottomBar = {
                val showNavigation = bottomTabs.any { it.route == currentRoute }
                Column {
                    // The full player already shows everything the mini player would.
                    if (playbackState.currentEpisodeId != null && currentRoute != Screen.Player.route) {
                        // Podcast playback keeps the podcast colour wherever the mini player shows up.
                        TabAccentTheme(TabAccent.Podcasts) {
                            MiniPlayerBar(
                                state = playbackState,
                                onClick = { navController.navigate(Screen.Player.route) { launchSingleTop = true } },
                                onPlayPauseClick = playerViewModel::togglePlayPause,
                                padForSystemBar = !showNavigation
                            )
                        }
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
                                        // Selected: the icon in its own colour roles and the label in the accent colour, bolder;
                                        // unselected: all muted. No pill behind it.
                                        DotIcon(iconFor(screen), 24.dp, monochrome = if (selected) null else c.textMuted)
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            labelFor(screen).upperCs(),
                                            style = DotType.label.copy(fontWeight = if (selected) FontWeight.W800 else FontWeight.W600),
                                            color = if (selected) c.accent else c.textMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            val openEpisode: (Long) -> Unit = { id -> navController.navigate(Screen.EpisodeDetail.createRoute(id)) }
            val openVideo: (Long) -> Unit = { id -> navController.navigate(Screen.YoutubeVideo.createRoute(id)) }
            NavHost(
                navController = navController,
                startDestination = Screen.Podcasts.route,
                // Screens pad for the status bar themselves; consume the Scaffold's insets so they aren't applied twice.
                modifier = Modifier.padding(paddingValues).consumeWindowInsets(paddingValues)
            ) {
                composable(Screen.Podcasts.route) {
                    PodcastsHubScreen(
                        subscriptionsFactory = PodcastListViewModel.factory(
                            container.podcastRepository,
                            container.episodeRepository,
                            container.tagRepository,
                            container.queueRepository
                        ),
                        queueFactory = QueueViewModel.factory(
                            container.queueRepository,
                            container.playbackRepository,
                            container.tagRepository,
                            container.episodeRepository,
                            container.queueFilterRepository,
                            container.downloadRepository
                        ),
                        newEpisodesFactory = NewEpisodesViewModel.factory(
                            container.episodeRepository,
                            container.queueRepository,
                            container.playbackRepository,
                            container.tagRepository
                        ),
                        onPodcastClick = { feedId -> navController.navigate(Screen.PodcastEpisodes.createRoute(feedId)) },
                        onEpisodeClick = openEpisode,
                        section = podcastsSection,
                        onSectionChange = { podcastsSection = it }
                    )
                }
                composable(Screen.YouTube.route) {
                    YoutubeHubScreen(
                        channelsFactory = YoutubeChannelsViewModel.factory(container.youtubeRepository),
                        queueFactory = VideoQueueViewModel.factory(
                            container.videoQueueRepository,
                            container.youtubeRepository,
                            container.videoQueueFilterRepository
                        ),
                        newVideosFactory = NewVideosViewModel.factory(container.youtubeRepository, container.videoQueueRepository),
                        onChannelClick = { channelId -> navController.navigate(Screen.YoutubeChannel.createRoute(channelId)) },
                        onVideoClick = openVideo,
                        onOpenChannelSources = { navController.navigate(Screen.YoutubeChannelSources.route) },
                        section = youtubeSection,
                        onSectionChange = { youtubeSection = it }
                    )
                }
                composable(Screen.Settings.route) { SettingsScreen(container.downloadSettings, container.downloadRepository) }
                composable(Screen.Articles.route) {
                    ArticlesScreen(
                        viewModelFactory = ArticlesViewModel.factory(container.articleRepository),
                        onArticleClick = { id, tagId -> navController.navigate(Screen.Article.createRoute(id, tagId)) },
                        onOpenSources = { navController.navigate(Screen.ArticleSources.route) }
                    )
                }
                composable(
                    route = Screen.Article.route,
                    arguments = listOf(
                        navArgument(Screen.Article.ARG_ARTICLE_ID) { type = NavType.LongType },
                        navArgument(Screen.Article.ARG_TAG_ID) { type = NavType.LongType; defaultValue = -1L }
                    )
                ) { backStackEntry ->
                    val articleId = backStackEntry.arguments?.getLong(Screen.Article.ARG_ARTICLE_ID) ?: return@composable
                    val tagId = backStackEntry.arguments?.getLong(Screen.Article.ARG_TAG_ID)?.takeIf { it >= 0 }
                    val context = LocalContext.current
                    ArticleReaderScreen(
                        viewModelFactory = ArticleReaderViewModel.factory(
                            container.articleRepository,
                            context.getSharedPreferences("reader", Context.MODE_PRIVATE),
                            articleId,
                            tagId
                        ),
                        onBack = { navController.popBackStack() },
                        // The next article replaces this one, so Back still returns to the list.
                        onOpenArticle = { nextId ->
                            navController.navigate(Screen.Article.createRoute(nextId, tagId)) {
                                popUpTo(Screen.Article.route) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Screen.ArticleSources.route) {
                    ArticleSourcesScreen(
                        viewModelFactory = ArticleSourcesViewModel.factory(container.articleRepository),
                        onBack = { navController.popBackStack() }
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
                        onBack = { navController.popBackStack() },
                        onEpisodeClick = openEpisode
                    )
                }
                composable(
                    route = Screen.EpisodeDetail.route,
                    arguments = listOf(navArgument(Screen.EpisodeDetail.ARG_EPISODE_ID) { type = NavType.LongType })
                ) { backStackEntry ->
                    val episodeId = backStackEntry.arguments?.getLong(Screen.EpisodeDetail.ARG_EPISODE_ID) ?: return@composable
                    EpisodeDetailScreen(
                        viewModelFactory = EpisodeDetailViewModel.factory(
                            container.episodeRepository,
                            container.queueRepository,
                            container.playbackRepository,
                            container.downloadRepository,
                            episodeId
                        ),
                        onBack = { navController.popBackStack() },
                        onOpenPodcast = { feedId ->
                            // Coming from that podcast's page: go back to it instead of stacking a second one.
                            val previous = navController.previousBackStackEntry
                            if (previous?.destination?.route == Screen.PodcastEpisodes.route &&
                                previous.arguments?.getLong(Screen.PodcastEpisodes.ARG_FEED_ID) == feedId
                            ) {
                                navController.popBackStack()
                            } else {
                                navController.navigate(Screen.PodcastEpisodes.createRoute(feedId))
                            }
                        }
                    )
                }
                composable(Screen.Player.route) {
                    PlayerScreen(
                        viewModel = playerViewModel,
                        onBack = { navController.popBackStack() },
                        // From the player, so "back" comes home to it.
                        onOpenPodcast = { feedId -> navController.navigate(Screen.PodcastEpisodes.createRoute(feedId)) },
                        onOpenEpisode = openEpisode,
                        onOpenQueue = {
                            podcastsSection = PodcastsSection.Queue
                            if (!navController.popBackStack(Screen.Podcasts.route, inclusive = false)) {
                                navController.navigate(Screen.Podcasts.route) {
                                    popUpTo(navController.graph.startDestinationId)
                                    launchSingleTop = true
                                }
                            }
                        }
                    )
                }
                composable(
                    route = Screen.YoutubeChannel.route,
                    arguments = listOf(navArgument(Screen.YoutubeChannel.ARG_CHANNEL_ID) { type = NavType.LongType })
                ) { backStackEntry ->
                    val channelId = backStackEntry.arguments?.getLong(Screen.YoutubeChannel.ARG_CHANNEL_ID) ?: return@composable
                    YoutubeChannelScreen(
                        viewModelFactory = YoutubeChannelViewModel.factory(container.youtubeRepository, container.videoQueueRepository, channelId),
                        onBack = { navController.popBackStack() },
                        onVideoClick = openVideo
                    )
                }
                composable(
                    route = Screen.YoutubeVideo.route,
                    arguments = listOf(navArgument(Screen.YoutubeVideo.ARG_VIDEO_ID) { type = NavType.LongType })
                ) { backStackEntry ->
                    val videoId = backStackEntry.arguments?.getLong(Screen.YoutubeVideo.ARG_VIDEO_ID) ?: return@composable
                    VideoDetailScreen(
                        viewModelFactory = VideoDetailViewModel.factory(container.youtubeRepository, container.videoQueueRepository, videoId),
                        onBack = { navController.popBackStack() },
                        onVideoClick = { nextId ->
                            navController.navigate(Screen.YoutubeVideo.createRoute(nextId)) {
                                popUpTo(Screen.YoutubeVideo.route) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Screen.YoutubeChannelSources.route) {
                    YoutubeChannelSourcesScreen(
                        viewModelFactory = YoutubeChannelSourcesViewModel.factory(container.youtubeRepository),
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

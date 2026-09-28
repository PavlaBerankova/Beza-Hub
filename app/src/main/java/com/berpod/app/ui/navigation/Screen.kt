package com.berpod.app.ui.navigation

sealed class Screen(val route: String) {
    data object Podcasts : Screen("podcasts")
    data object YouTube : Screen("youtube")
    data object Articles : Screen("articles")
    data object PodcastEpisodes : Screen("podcast/{feedId}") {
        const val ARG_FEED_ID = "feedId"
        fun createRoute(feedId: Long) = "podcast/$feedId"
    }
    data object EpisodeDetail : Screen("episode/{episodeId}") {
        const val ARG_EPISODE_ID = "episodeId"
        fun createRoute(episodeId: Long) = "episode/$episodeId"
    }
    data object Player : Screen("player")
}

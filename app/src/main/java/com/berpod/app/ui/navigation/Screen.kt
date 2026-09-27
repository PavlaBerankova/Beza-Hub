package com.berpod.app.ui.navigation

sealed class Screen(val route: String) {
    data object Podcasts : Screen("podcasts")
    data object Queue : Screen("queue")
    data object NewEpisodes : Screen("new_episodes")
    data object PodcastEpisodes : Screen("podcast/{feedId}") {
        const val ARG_FEED_ID = "feedId"
        fun createRoute(feedId: Long) = "podcast/$feedId"
    }
    data object Player : Screen("player")
}

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
    data object Article : Screen("article/{articleId}?tag={tagId}") {
        const val ARG_ARTICLE_ID = "articleId"
        const val ARG_TAG_ID = "tagId"
        /** [tagId]: the category the list was filtered by (for "Další nepřečtený"); -1 = none. */
        fun createRoute(articleId: Long, tagId: Long?) = "article/$articleId?tag=${tagId ?: -1}"
    }
    data object ArticleSources : Screen("article-sources")
    data object Player : Screen("player")
}

package com.berpod.app.data.repository

import com.berpod.app.data.local.EpisodeDao
import com.berpod.app.data.local.EpisodeEntity
import com.berpod.app.data.local.EpisodeWithFeed
import com.berpod.app.data.local.PodcastFeedDao
import com.berpod.app.data.local.PodcastFeedEntity
import com.berpod.app.data.remote.FeedFetcher
import kotlinx.coroutines.flow.Flow

class EpisodeRepository(
    private val episodeDao: EpisodeDao,
    private val podcastFeedDao: PodcastFeedDao,
    private val feedFetcher: FeedFetcher
) {

    fun observeNewEpisodes(): Flow<List<EpisodeWithFeed>> = episodeDao.observeAllEpisodesWithFeed()

    /** Marks an episode played now, or clears the mark ([played] = false). */
    suspend fun unplayedIdsForFeed(feedId: Long): List<Long> = episodeDao.unplayedIdsForFeed(feedId)

    suspend fun markAllPlayed(feedId: Long) = episodeDao.markAllPlayed(feedId, System.currentTimeMillis())

    suspend fun markAllUnplayed(feedId: Long) = episodeDao.markAllUnplayed(feedId)

    suspend fun setPlayed(episodeId: Long, played: Boolean) =
        episodeDao.setPlayed(episodeId, if (played) System.currentTimeMillis() else null)

    fun observeEpisode(episodeId: Long): Flow<EpisodeWithFeed?> = episodeDao.observeEpisodeWithFeed(episodeId)

    fun observeEpisodesForFeed(feedId: Long): Flow<List<EpisodeEntity>> = episodeDao.observeEpisodesForFeed(feedId)

    suspend fun syncEpisodesForFeed(feed: PodcastFeedEntity): Result<Unit> = runCatching {
        val meta = feedFetcher.fetchAndParse(feed.rssUrl)
        // Fills the author for podcasts added before it was stored; a missing author never erases one.
        meta.author?.let { podcastFeedDao.updateAuthor(feed.id, it) }
        val now = System.currentTimeMillis()
        episodeDao.insertAll(
            meta.episodes.map { ep ->
                EpisodeEntity(
                    feedId = feed.id,
                    guid = ep.guid,
                    title = ep.title,
                    audioUrl = ep.audioUrl,
                    pubDateMillis = ep.pubDateMillis,
                    durationSeconds = ep.durationSeconds,
                    description = ep.description,
                    imageUrl = ep.imageUrl,
                    dateFetched = now
                )
            }
        )
    }

    suspend fun syncEpisodesForFeedId(feedId: Long): Result<Unit> = runCatching {
        val feed = podcastFeedDao.getById(feedId) ?: error("Feed nenalezen")
        syncEpisodesForFeed(feed).getOrThrow()
    }

    // TODO: no background/WorkManager sync in this iteration — manual refresh only.
    suspend fun refreshAllFeeds(): Result<Int> = runCatching {
        podcastFeedDao.getAll().count { feed -> syncEpisodesForFeed(feed).isFailure }
    }
}

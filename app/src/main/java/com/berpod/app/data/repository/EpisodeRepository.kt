package com.berpod.app.data.repository

import com.berpod.app.data.local.EpisodeDao
import com.berpod.app.data.local.EpisodeEntity
import com.berpod.app.data.local.EpisodeWithFeed
import com.berpod.app.data.local.PodcastFeedDao
import com.berpod.app.data.local.PodcastFeedEntity
import com.berpod.app.data.remote.FeedFetcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val PARALLEL_SYNCS = 4

class EpisodeRepository(
    private val episodeDao: EpisodeDao,
    private val podcastFeedDao: PodcastFeedDao,
    private val feedFetcher: FeedFetcher
) {

    fun observeNewEpisodes(): Flow<List<EpisodeWithFeed>> = episodeDao.observeAllEpisodesWithFeed()

    suspend fun unplayedIdsForFeed(feedId: Long): List<Long> = episodeDao.unplayedIdsForFeed(feedId)

    /** In batches: SQLite takes at most 999 bound values per statement on older Android versions. */
    suspend fun markPlayed(episodeIds: List<Long>) {
        val now = System.currentTimeMillis()
        episodeIds.chunked(500).forEach { episodeDao.markPlayed(it, now) }
    }

    suspend fun markAllPlayed(feedId: Long) = episodeDao.markAllPlayed(feedId, System.currentTimeMillis())

    suspend fun markAllUnplayed(feedId: Long) = episodeDao.markAllUnplayed(feedId)

    /** Marks an episode played now, or clears the mark ([played] = false). */
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
                    dateFetched = now,
                    episodeNumber = ep.episodeNumber,
                    fileSizeBytes = ep.fileSizeBytes
                )
            }
        )
        // Episodes stored before the number and size were kept get them on the next refresh.
        meta.episodes.filter { it.episodeNumber != null || it.fileSizeBytes != null }.forEach { ep ->
            episodeDao.updateNumberAndSize(feed.id, ep.guid, ep.episodeNumber, ep.fileSizeBytes)
        }
    }

    suspend fun syncEpisodesForFeedId(feedId: Long): Result<Unit> = runCatching {
        val feed = podcastFeedDao.getById(feedId) ?: error("Feed nenalezen")
        syncEpisodesForFeed(feed).getOrThrow()
    }

    // TODO: no background/WorkManager sync in this iteration — manual refresh only.
    suspend fun refreshAllFeeds(): Result<Int> = runCatching {
        val limit = Semaphore(PARALLEL_SYNCS)
        coroutineScope {
            podcastFeedDao.getAll()
                .map { feed -> async { limit.withPermit { syncEpisodesForFeed(feed).isFailure } } }
                .awaitAll()
                .count { failed -> failed }
        }
    }
}

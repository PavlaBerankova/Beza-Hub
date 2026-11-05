package com.berpod.app.data.repository

import com.berpod.app.data.local.EpisodeDao
import com.berpod.app.data.local.EpisodeEntity
import com.berpod.app.data.local.EpisodeWithFeed
import com.berpod.app.data.local.PodcastFeedDao
import com.berpod.app.data.local.PodcastFeedEntity
import com.berpod.app.data.remote.FeedFetchResult
import com.berpod.app.data.remote.FeedFetcher
import com.berpod.app.data.remote.FeedValidators
import com.berpod.app.data.remote.ParsedFeedMeta
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val PARALLEL_SYNCS = 4

/** Opening the app does not re-check podcasts that were checked less than this long ago. */
const val STARTUP_REFRESH_MIN_AGE_MS = 5L * 60 * 1000

class EpisodeRepository(
    private val episodeDao: EpisodeDao,
    private val podcastFeedDao: PodcastFeedDao,
    private val feedFetcher: FeedFetcher
) {

    fun observeNewEpisodes(): Flow<List<EpisodeWithFeed>> = episodeDao.observeAllEpisodesWithFeed()

    suspend fun unplayedIdsForFeed(feedId: Long): List<Long> = episodeDao.unplayedIdsForFeed(feedId)

    suspend fun idsForFeed(feedId: Long): List<Long> = episodeDao.idsForFeed(feedId)

    /** In batches: SQLite takes at most 999 bound values per statement on older Android versions. */
    suspend fun markPlayed(episodeIds: List<Long>) {
        val now = System.currentTimeMillis()
        episodeIds.chunked(500).forEach { episodeDao.markPlayed(it, now) }
    }

    /** Takes the episode out of Nové (or puts it back) without touching its played state. */
    suspend fun setHiddenFromNew(episodeId: Long, hidden: Boolean) = episodeDao.setHiddenFromNew(episodeId, hidden)

    /** Leaving a podcast's page: what was new there has been seen, so it no longer counts as new. */
    suspend fun markFeedSeen(feedId: Long) = episodeDao.markFeedSeen(feedId)

    suspend fun markAllPlayed(feedId: Long) = episodeDao.markAllPlayed(feedId, System.currentTimeMillis())

    suspend fun markAllUnplayed(feedId: Long) = episodeDao.markAllUnplayed(feedId)

    /** Marks an episode played now, or clears the mark ([played] = false). */
    suspend fun setPlayed(episodeId: Long, played: Boolean) =
        episodeDao.setPlayed(episodeId, if (played) System.currentTimeMillis() else null)

    fun observeEpisode(episodeId: Long): Flow<EpisodeWithFeed?> = episodeDao.observeEpisodeWithFeed(episodeId)

    fun observeEpisodesForFeed(feedId: Long): Flow<List<EpisodeEntity>> = episodeDao.observeEpisodesForFeed(feedId)

    suspend fun syncEpisodesForFeed(feed: PodcastFeedEntity): Result<Unit> = runCatching {
        val known = FeedValidators(feed.etag, feed.lastModified)
        when (val fetched = feedFetcher.fetchIfChanged(feed.rssUrl, known)) {
            // Nothing new on the server: only note that the feed was checked.
            is FeedFetchResult.NotModified ->
                podcastFeedDao.markFetched(feed.id, System.currentTimeMillis(), feed.etag, feed.lastModified)
            is FeedFetchResult.Changed -> {
                storeEpisodes(feed, fetched.meta)
                podcastFeedDao.markFetched(feed.id, System.currentTimeMillis(), fetched.validators.etag, fetched.validators.lastModified)
            }
        }
    }

    private suspend fun storeEpisodes(feed: PodcastFeedEntity, meta: ParsedFeedMeta) {
        // Fills the author for podcasts added before it was stored; a missing author never erases one.
        meta.author?.let { podcastFeedDao.updateAuthor(feed.id, it) }
        val now = System.currentTimeMillis()
        // Read before the insert: it tells which episodes were stored earlier and what they hold.
        val stored = episodeDao.numbersAndSizesForFeed(feed.id).associateBy { it.guid }
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
        // Episodes stored before the number and size were kept get them now; only those that differ are written.
        meta.episodes
            .filter { it.episodeNumber != null || it.fileSizeBytes != null }
            .filter { ep -> stored[ep.guid]?.let { it.episodeNumber != ep.episodeNumber || it.fileSizeBytes != ep.fileSizeBytes } == true }
            .forEach { ep -> episodeDao.updateNumberAndSize(feed.id, ep.guid, ep.episodeNumber, ep.fileSizeBytes) }
    }

    suspend fun syncEpisodesForFeedId(feedId: Long): Result<Unit> = runCatching {
        val feed = podcastFeedDao.getById(feedId) ?: error("Feed nenalezen")
        syncEpisodesForFeed(feed).getOrThrow()
    }

    // TODO: no background/WorkManager sync in this iteration — manual refresh only.
    /** [skipCheckedWithinMs]: podcasts successfully checked that recently are left alone (0 = refresh them all). */
    suspend fun refreshAllFeeds(skipCheckedWithinMs: Long = 0): Result<Int> = runCatching {
        val limit = Semaphore(PARALLEL_SYNCS)
        val now = System.currentTimeMillis()
        coroutineScope {
            podcastFeedDao.getAll()
                .filter { feed -> feed.lastFetchedAt?.let { now - it >= skipCheckedWithinMs } ?: true }
                .map { feed -> async { limit.withPermit { syncEpisodesForFeed(feed).isFailure } } }
                .awaitAll()
                .count { failed -> failed }
        }
    }
}

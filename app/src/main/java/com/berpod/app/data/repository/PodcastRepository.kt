package com.berpod.app.data.repository

import com.berpod.app.data.local.PodcastFeedDao
import com.berpod.app.data.local.PodcastFeedEntity
import com.berpod.app.data.remote.FeedFetcher
import com.berpod.app.data.remote.ParsedFeedMeta
import com.berpod.app.data.remote.normalizeSourceUrl
import kotlinx.coroutines.flow.Flow

class PodcastRepository(
    private val dao: PodcastFeedDao,
    private val feedFetcher: FeedFetcher
) {

    fun observeFeeds(): Flow<List<PodcastFeedEntity>> = dao.observeAll()

    fun observeFeed(feedId: Long): Flow<PodcastFeedEntity?> = dao.observeById(feedId)

    suspend fun getFeedById(feedId: Long): PodcastFeedEntity? = dao.getById(feedId)

    /** Finds the podcast behind an address the user typed (a feed or a web page), without saving it. */
    suspend fun resolve(rawUrl: String): Result<ParsedFeedMeta> = runCatching {
        val url = requireNotNull(normalizeSourceUrl(rawUrl)) { "Zadejte adresu webu nebo RSS" }
        feedFetcher.fetchAndParse(url)
    }

    suspend fun addFeed(meta: ParsedFeedMeta): Result<Long> = runCatching {
        require(!dao.existsByUrl(meta.feedUrl)) { "Tento podcast už je přidaný" }
        val now = System.currentTimeMillis()
        dao.insert(
            PodcastFeedEntity(
                rssUrl = meta.feedUrl,
                title = meta.title,
                imageUrl = meta.imageUrl,
                description = meta.description,
                dateAdded = now,
                lastFetchedAt = now,
                author = meta.author
            )
        )
    }

    /** Resolves and adds in one step. */
    suspend fun addFeed(rawUrl: String): Result<Long> = resolve(rawUrl).mapCatching { addFeed(it).getOrThrow() }
}

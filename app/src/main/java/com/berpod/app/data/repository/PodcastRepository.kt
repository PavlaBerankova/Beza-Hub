package com.berpod.app.data.repository

import com.berpod.app.data.local.PodcastFeedDao
import com.berpod.app.data.local.PodcastFeedEntity
import com.berpod.app.data.remote.FeedFetcher
import kotlinx.coroutines.flow.Flow

class PodcastRepository(
    private val dao: PodcastFeedDao,
    private val feedFetcher: FeedFetcher
) {

    fun observeFeeds(): Flow<List<PodcastFeedEntity>> = dao.observeAll()

    fun observeFeed(feedId: Long): Flow<PodcastFeedEntity?> = dao.observeById(feedId)

    suspend fun getFeedById(feedId: Long): PodcastFeedEntity? = dao.getById(feedId)

    suspend fun addFeed(rawUrl: String): Result<Long> = runCatching {
        val url = rawUrl.trim()
        require(url.startsWith("http://") || url.startsWith("https://")) {
            "Zadejte platnou URL adresu (musí začínat http:// nebo https://)"
        }
        require(!dao.existsByUrl(url)) { "Tento podcast už je přidaný" }

        val meta = feedFetcher.fetchAndParse(url)
        val now = System.currentTimeMillis()

        dao.insert(
            PodcastFeedEntity(
                rssUrl = url,
                title = meta.title,
                imageUrl = meta.imageUrl,
                description = meta.description,
                dateAdded = now,
                lastFetchedAt = now,
                author = meta.author
            )
        )
    }
}

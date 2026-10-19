package com.berpod.app.data.repository

import com.berpod.app.data.local.ArticleDao
import com.berpod.app.data.local.ArticleEntity
import com.berpod.app.data.local.ArticleFeedEntity
import com.berpod.app.data.local.ArticleFeedTagCrossRef
import com.berpod.app.data.local.ArticleFeedWithCount
import com.berpod.app.data.local.ArticleTagEntity
import com.berpod.app.data.local.ArticleWithFeed
import com.berpod.app.data.remote.ArticleFeedFetcher
import com.berpod.app.data.remote.ParsedArticle
import com.berpod.app.data.remote.ParsedArticleFeed
import com.berpod.app.data.remote.normalizeSourceUrl
import kotlinx.coroutines.flow.Flow

/** Články: article sources, their articles and categories. */
class ArticleRepository(
    private val dao: ArticleDao,
    private val fetcher: ArticleFeedFetcher
) {

    fun observeArticles(): Flow<List<ArticleWithFeed>> = dao.observeArticles()

    fun observeArticle(articleId: Long): Flow<ArticleWithFeed?> = dao.observeArticle(articleId)

    fun observeFeeds(): Flow<List<ArticleFeedEntity>> = dao.observeFeeds()

    fun observeFeedsWithCount(): Flow<List<ArticleFeedWithCount>> = dao.observeFeedsWithCount()

    /** Finds the source behind an address the user typed (a feed or a web page), without saving it. */
    suspend fun resolve(rawUrl: String): Result<ParsedArticleFeed> = runCatching {
        val url = requireNotNull(normalizeSourceUrl(rawUrl)) { "Zadejte adresu webu nebo RSS" }
        fetcher.resolve(url)
    }

    suspend fun addFeed(feed: ParsedArticleFeed, tagIds: Set<Long>): Result<Long> = runCatching {
        require(!dao.feedExists(feed.feedUrl)) { "Tento zdroj už je přidaný" }
        val now = System.currentTimeMillis()
        dao.insertFeedWithArticles(
            ArticleFeedEntity(
                rssUrl = feed.feedUrl,
                title = feed.title,
                siteUrl = feed.siteUrl,
                imageUrl = feed.imageUrl,
                dateAdded = now,
                lastFetchedAt = now
            ),
            feed.articles.map { it.toEntity(feedId = 0, now = now) },
            tagIds
        )
    }

    private var refreshedThisSession = false

    /** The first call after the app starts refreshes (there is no background sync); later ones do nothing. */
    suspend fun refreshOncePerSession(): Result<Int>? {
        if (refreshedThisSession) return null
        refreshedThisSession = true
        return refreshAll()
    }

    /** Fetches every source; new articles are added, known ones left as they are. Returns how many failed. */
    suspend fun refreshAll(): Result<Int> = runCatching {
        var failures = 0
        dao.getFeeds().forEach { feed ->
            runCatching { fetcher.resolve(feed.rssUrl) }
                .onSuccess { parsed ->
                    val now = System.currentTimeMillis()
                    dao.insertArticles(parsed.articles.map { it.toEntity(feed.id, now) })
                    dao.setFetchedAt(feed.id, now)
                }
                .onFailure { failures++ }
        }
        failures
    }

    suspend fun setRead(articleId: Long, read: Boolean) =
        dao.setRead(articleId, if (read) System.currentTimeMillis() else null)

    suspend fun deleteFeed(feedId: Long) = dao.deleteFeed(feedId)

    fun observeTags(): Flow<List<ArticleTagEntity>> = dao.observeTags()

    fun observeTagsInUse(): Flow<List<ArticleTagEntity>> = dao.observeTagsInUse()

    fun observeFeedIdsWithTag(tagId: Long): Flow<List<Long>> = dao.observeFeedIdsWithTag(tagId)

    fun observeTagIdsForFeed(feedId: Long): Flow<List<Long>> = dao.observeTagIdsForFeed(feedId)

    /** The id of the category called [rawName], created when it doesn't exist yet. */
    suspend fun createTag(rawName: String): Result<Long> = runCatching {
        val name = requireNotNull(normalizeTagName(rawName)) { "Zadejte název kategorie" }
        dao.findTagByName(name)?.id ?: dao.insertTag(ArticleTagEntity(name = name, position = dao.maxTagPosition() + 1))
    }

    suspend fun createAndAssign(feedId: Long, rawName: String): Result<Unit> =
        createTag(rawName).map { tagId -> dao.assignTag(ArticleFeedTagCrossRef(feedId, tagId)) }

    suspend fun setAssigned(feedId: Long, tagId: Long, assigned: Boolean) {
        if (assigned) dao.assignTag(ArticleFeedTagCrossRef(feedId, tagId)) else dao.unassignTag(feedId, tagId)
    }

    suspend fun renameTag(tagId: Long, rawName: String): Result<Unit> = runCatching {
        val name = requireNotNull(normalizeTagName(rawName)) { "Zadejte název kategorie" }
        val existing = dao.findTagByName(name)
        require(existing == null || existing.id == tagId) { "Kategorie s tímto názvem už existuje" }
        dao.renameTag(tagId, name)
    }

    suspend fun deleteTag(tagId: Long) = dao.deleteTag(tagId)

    /** Saves the user's own order of the categories: [tagIds] from first to last. */
    suspend fun reorderTags(tagIds: List<Long>) = dao.applyTagOrder(tagIds)
}

private fun ParsedArticle.toEntity(feedId: Long, now: Long) = ArticleEntity(
    feedId = feedId,
    guid = guid,
    title = title,
    link = link,
    author = author,
    pubDateMillis = pubDateMillis,
    summary = summary,
    content = content,
    dateFetched = now
)

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Only what the repository's add/refresh paths use; observing isn't exercised here. */
private class FakeArticleDao : ArticleDao {
    val feeds = mutableListOf<ArticleFeedEntity>()
    val articles = mutableListOf<ArticleEntity>()
    val links = mutableSetOf<ArticleFeedTagCrossRef>()

    override fun observeFeeds(): Flow<List<ArticleFeedEntity>> = emptyFlow()
    override fun observeFeedsWithCount(): Flow<List<ArticleFeedWithCount>> = emptyFlow()
    override suspend fun getFeeds() = feeds.toList()
    override suspend fun feedExists(rssUrl: String) = feeds.any { it.rssUrl == rssUrl }
    override suspend fun insertFeed(feed: ArticleFeedEntity): Long {
        val withId = feed.copy(id = feeds.size + 1L); feeds += withId; return withId.id
    }
    override suspend fun setFetchedAt(feedId: Long, at: Long) {
        feeds.replaceAll { if (it.id == feedId) it.copy(lastFetchedAt = at) else it }
    }
    override suspend fun deleteFeed(feedId: Long) { feeds.removeAll { it.id == feedId } }
    override suspend fun insertArticles(articles: List<ArticleEntity>): List<Long> = articles.map { a ->
        if (this.articles.any { it.feedId == a.feedId && it.guid == a.guid }) -1L
        else { this.articles += a.copy(id = this.articles.size + 1L); this.articles.size.toLong() }
    }
    override fun observeArticles(): Flow<List<ArticleWithFeed>> = emptyFlow()
    override fun observeArticle(articleId: Long): Flow<ArticleWithFeed?> = emptyFlow()
    override suspend fun setRead(articleId: Long, readAtMs: Long?) {}
    override fun observeTags(): Flow<List<ArticleTagEntity>> = emptyFlow()
    override fun observeTagsInUse(): Flow<List<ArticleTagEntity>> = emptyFlow()
    override fun observeFeedIdsWithTag(tagId: Long): Flow<List<Long>> = emptyFlow()
    override fun observeTagIdsForFeed(feedId: Long): Flow<List<Long>> = emptyFlow()
    override suspend fun findTagByName(name: String): ArticleTagEntity? = null
    override suspend fun insertTag(tag: ArticleTagEntity): Long = 1
    override suspend fun maxTagPosition(): Int = -1
    override suspend fun setTagPosition(tagId: Long, position: Int) {}
    override suspend fun renameTag(tagId: Long, name: String) {}
    override suspend fun deleteTag(tagId: Long) {}
    override suspend fun assignTag(crossRef: ArticleFeedTagCrossRef) { links += crossRef }
    override suspend fun unassignTag(feedId: Long, tagId: Long) { links.remove(ArticleFeedTagCrossRef(feedId, tagId)) }
}

private class FakeFetcher(val feeds: MutableMap<String, ParsedArticleFeed>) : ArticleFeedFetcher {
    override suspend fun resolve(url: String) = feeds[url] ?: throw IllegalArgumentException("Na této adrese není RSS zdroj")
}

private fun article(guid: String) = ParsedArticle(guid, "Title $guid", "https://x.cz/$guid", null, 1_000L, "summary", null)

class ArticleRepositoryTest {

    private val feed = ParsedArticleFeed("https://x.cz/feed", "X", "https://x.cz", null, listOf(article("a"), article("b")))

    @Test
    fun `adding a source stores it with its articles and categories`() = runTest {
        val dao = FakeArticleDao()
        val repository = ArticleRepository(dao, FakeFetcher(mutableMapOf()))

        val id = repository.addFeed(feed, tagIds = setOf(7L)).getOrThrow()

        assertEquals(listOf("https://x.cz/feed"), dao.feeds.map { it.rssUrl })
        assertEquals(listOf("a", "b"), dao.articles.filter { it.feedId == id }.map { it.guid })
        assertEquals(setOf(ArticleFeedTagCrossRef(id, 7L)), dao.links)
    }

    @Test
    fun `the same source cannot be added twice`() = runTest {
        val repository = ArticleRepository(FakeArticleDao(), FakeFetcher(mutableMapOf()))
        repository.addFeed(feed, emptySet()).getOrThrow()

        val second = repository.addFeed(feed, emptySet())

        assertTrue(second.isFailure)
        assertEquals("Tento zdroj už je přidaný", second.exceptionOrNull()?.message)
    }

    @Test
    fun `refresh adds only new articles and counts sources that failed`() = runTest {
        val dao = FakeArticleDao()
        val remote = mutableMapOf("https://x.cz/feed" to feed)
        val repository = ArticleRepository(dao, FakeFetcher(remote))
        repository.addFeed(feed, emptySet()).getOrThrow()
        repository.addFeed(feed.copy(feedUrl = "https://gone.cz/feed"), emptySet()).getOrThrow()
        remote["https://x.cz/feed"] = feed.copy(articles = listOf(article("b"), article("c")))

        val failures = repository.refreshAll().getOrThrow()

        assertEquals(1, failures)
        assertEquals(listOf("a", "b", "c"), dao.articles.filter { it.feedId == 1L }.map { it.guid })
    }

    @Test
    fun `resolving an address normalises it first`() = runTest {
        val repository = ArticleRepository(FakeArticleDao(), FakeFetcher(mutableMapOf("https://x.cz" to feed)))
        assertEquals("X", repository.resolve(" x.cz ").getOrThrow().title)
        assertEquals("Zadejte adresu webu nebo RSS", repository.resolve("  ").exceptionOrNull()?.message)
    }
}

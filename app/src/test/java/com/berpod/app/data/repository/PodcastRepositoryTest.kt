package com.berpod.app.data.repository

import com.berpod.app.data.local.PodcastFeedDao
import com.berpod.app.data.local.PodcastFeedEntity
import com.berpod.app.data.remote.FeedFetcher
import com.berpod.app.data.remote.ParsedFeedMeta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeDao : PodcastFeedDao {
    private val state = MutableStateFlow<List<PodcastFeedEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<PodcastFeedEntity>> = state

    override suspend fun existsByUrl(rssUrl: String): Boolean =
        state.value.any { it.rssUrl == rssUrl }

    override suspend fun existsByUrlExcept(rssUrl: String, feedId: Long): Boolean =
        state.value.any { it.rssUrl == rssUrl && it.id != feedId }

    override suspend fun updateRssUrl(feedId: Long, rssUrl: String) {
        state.value = state.value.map { if (it.id == feedId) it.copy(rssUrl = rssUrl) else it }
    }

    override suspend fun insert(feed: PodcastFeedEntity): Long {
        val withId = feed.copy(id = nextId++)
        state.value = state.value + withId
        return withId.id
    }

    override suspend fun delete(feed: PodcastFeedEntity) {
        state.value = state.value.filterNot { it.id == feed.id }
    }

    override suspend fun getAll(): List<PodcastFeedEntity> = state.value

    override suspend fun getById(feedId: Long): PodcastFeedEntity? =
        state.value.find { it.id == feedId }

    override fun observeById(feedId: Long): Flow<PodcastFeedEntity?> =
        state.map { list -> list.find { it.id == feedId } }

    override suspend fun updateAuthor(feedId: Long, author: String) {
        state.value = state.value.map { if (it.id == feedId) it.copy(author = author) else it }
    }
}

private class FakeFeedFetcher(
    private val result: ParsedFeedMeta = ParsedFeedMeta("", "Fake Podcast", null, "A fake podcast", emptyList()),
    private val shouldFail: Boolean = false
) : FeedFetcher {
    override suspend fun fetchAndParse(url: String): ParsedFeedMeta {
        if (shouldFail) error("Fetch failed")
        return result.copy(feedUrl = url)
    }
}

class PodcastRepositoryTest {

    @Test
    fun `addFeed rejects invalid url`() = runTest {
        val repository = PodcastRepository(FakeDao(), FakeFeedFetcher())

        val result = repository.addFeed("not a url")

        assertTrue(result.isFailure)
    }

    @Test
    fun `addFeed inserts a valid new feed`() = runTest {
        val dao = FakeDao()
        val repository = PodcastRepository(dao, FakeFeedFetcher())

        val result = repository.addFeed("https://example.com/feed.xml")

        assertTrue(result.isSuccess)
        assertTrue(dao.existsByUrl("https://example.com/feed.xml"))
    }

    @Test
    fun `addFeed rejects duplicate url`() = runTest {
        val dao = FakeDao()
        val repository = PodcastRepository(dao, FakeFeedFetcher())

        repository.addFeed("https://example.com/feed.xml")
        val result = repository.addFeed("https://example.com/feed.xml")

        assertTrue(result.isFailure)
    }

    @Test
    fun `addFeed surfaces fetch failure`() = runTest {
        val repository = PodcastRepository(FakeDao(), FakeFeedFetcher(shouldFail = true))

        val result = repository.addFeed("https://example.com/feed.xml")

        assertTrue(result.isFailure)
    }

    @Test
    fun `addFeed stores the parsed author`() = runTest {
        val dao = FakeDao()
        val fetcher = FakeFeedFetcher(ParsedFeedMeta("", "Fake Podcast", null, "A fake podcast", emptyList(), author = "Autor"))
        val repository = PodcastRepository(dao, fetcher)

        val id = repository.addFeed("https://example.com/feed.xml").getOrThrow()

        assertEquals("Autor", dao.getById(id)?.author)
    }

    @Test
    fun `resolve finds the podcast behind a typed address without saving it`() = runTest {
        val dao = FakeDao()
        val repository = PodcastRepository(dao, FakeFeedFetcher())

        val meta = repository.resolve(" example.com/feed.xml ").getOrThrow()

        assertEquals("Fake Podcast", meta.title)
        assertEquals("https://example.com/feed.xml", meta.feedUrl)
        assertEquals(emptyList<PodcastFeedEntity>(), dao.getAll())
        assertEquals("Zadejte adresu webu nebo RSS", repository.resolve("  ").exceptionOrNull()?.message)
    }

    @Test
    fun `adding a resolved podcast stores it under the feed's own address`() = runTest {
        val dao = FakeDao()
        val repository = PodcastRepository(dao, FakeFeedFetcher())
        val meta = repository.resolve("https://example.com/page").getOrThrow().copy(feedUrl = "https://example.com/feed.xml")

        val id = repository.addFeed(meta).getOrThrow()

        assertEquals("https://example.com/feed.xml", dao.getById(id)?.rssUrl)
        assertTrue(repository.addFeed(meta).isFailure)
    }

    @Test
    fun `updateRssUrl repoints a podcast to a working address and keeps it`() = runTest {
        val dao = FakeDao()
        val repository = PodcastRepository(dao, FakeFeedFetcher())
        val id = repository.addFeed("https://example.com/broken.xml").getOrThrow()

        repository.updateRssUrl(id, "example.com/fixed.xml").getOrThrow()

        assertEquals("https://example.com/fixed.xml", dao.getById(id)?.rssUrl)
        assertEquals("Fake Podcast", dao.getById(id)?.title)
    }

    @Test
    fun `updateRssUrl rejects an address that does not fetch and keeps the old one`() = runTest {
        val dao = FakeDao()
        val id = PodcastRepository(dao, FakeFeedFetcher()).addFeed("https://example.com/old.xml").getOrThrow()

        val result = PodcastRepository(dao, FakeFeedFetcher(shouldFail = true)).updateRssUrl(id, "https://example.com/new.xml")

        assertTrue(result.isFailure)
        assertEquals("https://example.com/old.xml", dao.getById(id)?.rssUrl)
    }

    @Test
    fun `updateRssUrl rejects an address another podcast already uses`() = runTest {
        val dao = FakeDao()
        val repository = PodcastRepository(dao, FakeFeedFetcher())
        val first = repository.addFeed("https://example.com/a.xml").getOrThrow()
        repository.addFeed("https://example.com/b.xml").getOrThrow()

        assertTrue(repository.updateRssUrl(first, "https://example.com/b.xml").isFailure)
        assertEquals("https://example.com/a.xml", dao.getById(first)?.rssUrl)
    }
}

package com.berpod.app.data.repository

import com.berpod.app.data.local.PodcastTagCrossRef
import com.berpod.app.testutil.FakePodcastFeedDao
import com.berpod.app.testutil.FakeTagDao
import com.berpod.app.testutil.feed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagRepositoryTest {

    private val feedDao = FakePodcastFeedDao(listOf(feed(1), feed(2), feed(3)))
    private val tagDao = FakeTagDao(feedDao.feeds)
    private val repository = TagRepository(tagDao, feedDao)

    @Test
    fun `normalizeTagName trims and rejects blank`() {
        assertEquals("News", normalizeTagName("  News  "))
        assertNull(normalizeTagName(""))
        assertNull(normalizeTagName("   "))
    }

    @Test
    fun `createAndAssign creates tag and links podcast`() = runTest {
        val result = repository.createAndAssign(podcastId = 1, rawName = " Health ")

        assertTrue(result.isSuccess)
        assertEquals(listOf("Health"), tagDao.tags.value.map { it.name })
        assertEquals(setOf(PodcastTagCrossRef(1, tagDao.tags.value.single().id)), tagDao.links.value)
    }

    @Test
    fun `createAndAssign reuses existing tag case-insensitively`() = runTest {
        repository.createAndAssign(podcastId = 1, rawName = "News")

        val result = repository.createAndAssign(podcastId = 2, rawName = "  news ")

        assertTrue(result.isSuccess)
        assertEquals(1, tagDao.tags.value.size)
        val tagId = tagDao.tags.value.single().id
        assertEquals(setOf(PodcastTagCrossRef(1, tagId), PodcastTagCrossRef(2, tagId)), tagDao.links.value)
    }

    @Test
    fun `createAndAssign rejects blank name`() = runTest {
        assertTrue(repository.createAndAssign(podcastId = 1, rawName = "  ").isFailure)
        assertTrue(tagDao.tags.value.isEmpty())
    }

    @Test
    fun `rename rejects blank and duplicate names`() = runTest {
        repository.createAndAssign(1, "Health")
        repository.createAndAssign(1, "News")
        val newsId = tagDao.findByName("News")!!.id

        assertTrue(repository.rename(newsId, " ").isFailure)
        val duplicate = repository.rename(newsId, "health")
        assertTrue(duplicate.isFailure)
        assertEquals("Štítek s tímto názvem už existuje", duplicate.exceptionOrNull()?.message)
        assertEquals("News", tagDao.tags.value.first { it.id == newsId }.name)
    }

    @Test
    fun `rename allows changing case of own name`() = runTest {
        repository.createAndAssign(1, "news")
        val id = tagDao.tags.value.single().id

        assertTrue(repository.rename(id, "News").isSuccess)
        assertEquals("News", tagDao.tags.value.single().name)
    }

    @Test
    fun `setAssigned adds and removes a link`() = runTest {
        repository.createAndAssign(1, "News")
        val id = tagDao.tags.value.single().id

        repository.setAssigned(podcastId = 2, tagId = id, assigned = true)
        assertTrue(PodcastTagCrossRef(2, id) in tagDao.links.value)

        repository.setAssigned(podcastId = 2, tagId = id, assigned = false)
        assertTrue(PodcastTagCrossRef(2, id) !in tagDao.links.value)
    }

    @Test
    fun `observeFeeds returns all feeds for null filter and tagged feeds otherwise`() = runTest {
        repository.createAndAssign(1, "News")
        repository.createAndAssign(3, "News")
        val id = tagDao.tags.value.single().id

        assertEquals(listOf(3L, 2L, 1L), repository.observeFeeds(null).first().map { it.id })
        assertEquals(listOf(3L, 1L), repository.observeFeeds(id).first().map { it.id })
    }

    @Test
    fun `delete removes tag and its links`() = runTest {
        repository.createAndAssign(1, "News")
        val id = tagDao.tags.value.single().id

        repository.delete(id)

        assertTrue(tagDao.tags.value.isEmpty())
        assertTrue(tagDao.links.value.isEmpty())
    }
}

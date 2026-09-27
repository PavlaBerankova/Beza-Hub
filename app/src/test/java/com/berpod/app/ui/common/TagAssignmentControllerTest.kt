package com.berpod.app.ui.common

import com.berpod.app.data.repository.TagRepository
import com.berpod.app.testutil.FakePodcastFeedDao
import com.berpod.app.testutil.FakeTagDao
import com.berpod.app.testutil.feed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TagAssignmentControllerTest {

    private val feedDao = FakePodcastFeedDao(listOf(feed(1), feed(2)))
    private val tagDao = FakeTagDao(feedDao.feeds)
    private val repository = TagRepository(tagDao, feedDao)

    @Test
    fun `open shows all tags and the podcast's checked ones`() = runTest(UnconfinedTestDispatcher()) {
        repository.createAndAssign(1, "News")
        repository.createAndAssign(2, "Health")
        val controller = TagAssignmentController(repository, backgroundScope)

        controller.open(podcastId = 1, podcastTitle = "Podcast 1")

        val state = controller.state.value!!
        assertEquals("Podcast 1", state.podcastTitle)
        assertEquals(listOf("Health", "News"), state.tags.map { it.name })
        assertEquals(setOf(tagDao.findByName("News")!!.id), state.checkedTagIds)
    }

    @Test
    fun `toggling and creating update the open dialog live`() = runTest(UnconfinedTestDispatcher()) {
        repository.createAndAssign(2, "Health")
        val healthId = tagDao.findByName("Health")!!.id
        val controller = TagAssignmentController(repository, backgroundScope)
        controller.open(1, "Podcast 1")

        controller.setAssigned(healthId, true)
        assertEquals(setOf(healthId), controller.state.value!!.checkedTagIds)

        controller.createAndAssign(" News ")
        val newsId = tagDao.findByName("News")!!.id
        assertEquals(setOf(healthId, newsId), controller.state.value!!.checkedTagIds)

        controller.setAssigned(healthId, false)
        assertEquals(setOf(newsId), controller.state.value!!.checkedTagIds)
    }

    @Test
    fun `reopening for another podcast drops previous checks and dismiss clears state`() =
        runTest(UnconfinedTestDispatcher()) {
            repository.createAndAssign(1, "News")
            val controller = TagAssignmentController(repository, backgroundScope)

            controller.open(1, "Podcast 1")
            controller.dismiss()
            assertNull(controller.state.value)

            controller.open(2, "Podcast 2")
            assertEquals(2L, controller.state.value!!.podcastId)
            assertEquals(emptySet<Long>(), controller.state.value!!.checkedTagIds)

            // A late change to podcast 1 must not leak into podcast 2's dialog.
            repository.setAssigned(1, tagDao.findByName("News")!!.id, false)
            assertEquals(2L, controller.state.value!!.podcastId)
        }
}

package com.berpod.app.data.repository

import com.berpod.app.data.local.VideoWithChannel
import com.berpod.app.data.local.YoutubeChannelEntity
import com.berpod.app.data.local.YoutubeChannelTagCrossRef
import com.berpod.app.data.local.YoutubeChannelWithCount
import com.berpod.app.data.local.YoutubeDao
import com.berpod.app.data.local.YoutubeTagEntity
import com.berpod.app.data.local.YoutubeVideoEntity
import com.berpod.app.data.remote.ParsedYoutubeChannel
import com.berpod.app.data.remote.ParsedYoutubeVideo
import com.berpod.app.data.remote.YoutubeChannelFetcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Only what the repository's add/refresh paths use; observing isn't exercised here. */
private class FakeYoutubeDao : YoutubeDao {
    val channels = mutableListOf<YoutubeChannelEntity>()
    val videos = mutableListOf<YoutubeVideoEntity>()
    val links = mutableSetOf<YoutubeChannelTagCrossRef>()

    override fun observeChannels(): Flow<List<YoutubeChannelEntity>> = emptyFlow()
    override fun observeChannelsWithCount(): Flow<List<YoutubeChannelWithCount>> = emptyFlow()
    override suspend fun getChannels() = channels.toList()
    override suspend fun getChannelById(channelId: Long) = channels.find { it.id == channelId }
    override suspend fun channelExists(rssUrl: String) = channels.any { it.rssUrl == rssUrl }
    override suspend fun insertChannel(channel: YoutubeChannelEntity): Long {
        val withId = channel.copy(id = channels.size + 1L); channels += withId; return withId.id
    }
    override suspend fun setFetchedAt(channelId: Long, at: Long) {
        channels.replaceAll { if (it.id == channelId) it.copy(lastFetchedAt = at) else it }
    }
    override suspend fun deleteChannel(channelId: Long) { channels.removeAll { it.id == channelId } }
    override suspend fun insertVideos(videos: List<YoutubeVideoEntity>): List<Long> = videos.map { v ->
        if (this.videos.any { it.feedId == v.feedId && it.guid == v.guid }) -1L
        else { this.videos += v.copy(id = this.videos.size + 1L); this.videos.size.toLong() }
    }
    override fun observeVideos(): Flow<List<VideoWithChannel>> = emptyFlow()
    override fun observeVideo(videoId: Long): Flow<VideoWithChannel?> = emptyFlow()
    override fun observeNewVideos(): Flow<List<VideoWithChannel>> = emptyFlow()
    override suspend fun markChannelSeen(channelId: Long) {
        videos.replaceAll { if (it.feedId == channelId) it.copy(hiddenFromNew = true) else it }
    }
    override suspend fun setWatched(videoId: Long, watchedAtMs: Long?) {}
    override suspend fun updatePosition(videoId: Long, positionMs: Long) {}
    override suspend fun updateDuration(videoId: Long, durationMs: Long) {}
    override fun observeTags(): Flow<List<YoutubeTagEntity>> = emptyFlow()
    override fun observeTagsInUse(): Flow<List<YoutubeTagEntity>> = emptyFlow()
    override fun observeChannelIdsWithTag(tagId: Long): Flow<List<Long>> = emptyFlow()
    override fun observeTagIdsForChannel(channelId: Long): Flow<List<Long>> = emptyFlow()
    override suspend fun findTagByName(name: String): YoutubeTagEntity? = null
    override suspend fun insertTag(tag: YoutubeTagEntity): Long = 1
    override suspend fun maxTagPosition(): Int = -1
    override suspend fun setTagPosition(tagId: Long, position: Int) {}
    override suspend fun renameTag(tagId: Long, name: String) {}
    override suspend fun deleteTag(tagId: Long) {}
    override suspend fun assignTag(crossRef: YoutubeChannelTagCrossRef) { links += crossRef }
    override suspend fun unassignTag(channelId: Long, tagId: Long) { links.remove(YoutubeChannelTagCrossRef(channelId, tagId)) }
}

private class FakeYoutubeFetcher(val channels: MutableMap<String, ParsedYoutubeChannel>) : YoutubeChannelFetcher {
    override suspend fun resolve(url: String) = channels[url] ?: throw IllegalArgumentException("Na této adrese není YouTube kanál")
}

private fun video(guid: String) = ParsedYoutubeVideo(guid, "Title $guid", "yt-$guid", "https://youtube.com/watch?v=$guid", null, null, 1_000L)

class YoutubeRepositoryTest {

    private val channel = ParsedYoutubeChannel("https://x.cz/feed", "UC1", "X", "https://youtube.com/channel/UC1", null, listOf(video("a"), video("b")))

    @Test
    fun `adding a channel stores it with its videos and categories`() = runTest {
        val dao = FakeYoutubeDao()
        val repository = YoutubeRepository(dao, FakeYoutubeFetcher(mutableMapOf()))

        val id = repository.addChannel(channel, tagIds = setOf(7L)).getOrThrow()

        assertEquals(listOf("https://x.cz/feed"), dao.channels.map { it.rssUrl })
        assertEquals(listOf("a", "b"), dao.videos.filter { it.feedId == id }.map { it.guid })
        assertEquals(setOf(YoutubeChannelTagCrossRef(id, 7L)), dao.links)
    }

    @Test
    fun `the same channel cannot be added twice`() = runTest {
        val repository = YoutubeRepository(FakeYoutubeDao(), FakeYoutubeFetcher(mutableMapOf()))
        repository.addChannel(channel, emptySet()).getOrThrow()

        val second = repository.addChannel(channel, emptySet())

        assertTrue(second.isFailure)
        assertEquals("Tento kanál už je přidaný", second.exceptionOrNull()?.message)
    }

    @Test
    fun `refresh adds only new videos and counts channels that failed`() = runTest {
        val dao = FakeYoutubeDao()
        val remote = mutableMapOf("https://x.cz/feed" to channel)
        val repository = YoutubeRepository(dao, FakeYoutubeFetcher(remote))
        repository.addChannel(channel, emptySet()).getOrThrow()
        repository.addChannel(channel.copy(feedUrl = "https://gone.cz/feed"), emptySet()).getOrThrow()
        remote["https://x.cz/feed"] = channel.copy(videos = listOf(video("b"), video("c")))

        val failures = repository.refreshAll().getOrThrow()

        assertEquals(1, failures)
        assertEquals(listOf("a", "b", "c"), dao.videos.filter { it.feedId == 1L }.map { it.guid })
    }

    @Test
    fun `resolving an address normalises it first, including a bare handle`() = runTest {
        val repository = YoutubeRepository(
            FakeYoutubeDao(),
            FakeYoutubeFetcher(mutableMapOf("https://x.cz" to channel, "https://www.youtube.com/@kosmoscz" to channel))
        )
        assertEquals("X", repository.resolve(" x.cz ").getOrThrow().title)
        assertEquals("X", repository.resolve("@kosmoscz").getOrThrow().title)
        assertEquals("Zadejte adresu kanálu, @handle nebo RSS", repository.resolve("  ").exceptionOrNull()?.message)
    }
}

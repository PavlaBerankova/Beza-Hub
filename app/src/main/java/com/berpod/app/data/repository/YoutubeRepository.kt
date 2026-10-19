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
import com.berpod.app.data.remote.normalizeChannelUrl
import kotlinx.coroutines.flow.Flow

/** YouTube: channels, their videos and categories. */
class YoutubeRepository(
    private val dao: YoutubeDao,
    private val fetcher: YoutubeChannelFetcher
) {

    fun observeVideos(): Flow<List<VideoWithChannel>> = dao.observeVideos()

    fun observeVideo(videoId: Long): Flow<VideoWithChannel?> = dao.observeVideo(videoId)

    /** Unwatched videos that still count as new (not hidden, not a channel's back catalogue). */
    fun observeNewVideos(): Flow<List<VideoWithChannel>> = dao.observeNewVideos()

    /** Leaving a channel's page: what was new there has been seen, so it no longer counts as new. */
    suspend fun markChannelSeen(channelId: Long) = dao.markChannelSeen(channelId)

    fun observeChannels(): Flow<List<YoutubeChannelEntity>> = dao.observeChannels()

    fun observeChannelsWithCount(): Flow<List<YoutubeChannelWithCount>> = dao.observeChannelsWithCount()

    /** Finds the channel behind an address the user typed (a feed, a channel page, or a @handle), without saving it. */
    suspend fun resolve(rawUrl: String): Result<ParsedYoutubeChannel> = runCatching {
        val url = requireNotNull(normalizeChannelUrl(rawUrl)) { "Zadejte adresu kanálu, @handle nebo RSS" }
        fetcher.resolve(url)
    }

    suspend fun addChannel(channel: ParsedYoutubeChannel, tagIds: Set<Long>): Result<Long> = runCatching {
        require(!dao.channelExists(channel.feedUrl)) { "Tento kanál už je přidaný" }
        val now = System.currentTimeMillis()
        dao.insertChannelWithVideos(
            YoutubeChannelEntity(
                rssUrl = channel.feedUrl,
                channelId = channel.channelId,
                title = channel.title,
                channelUrl = channel.channelUrl,
                thumbnailUrl = channel.thumbnailUrl,
                dateAdded = now,
                lastFetchedAt = now
            ),
            channel.videos.map { it.toEntity(feedId = 0, now = now) },
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

    /** Fetches every channel; new videos are added, known ones left as they are. Returns how many failed. */
    suspend fun refreshAll(): Result<Int> = runCatching {
        var failures = 0
        dao.getChannels().forEach { channel ->
            runCatching { fetcher.resolve(channel.rssUrl) }
                .onSuccess { parsed ->
                    val now = System.currentTimeMillis()
                    dao.insertVideos(parsed.videos.map { it.toEntity(channel.id, now) })
                    dao.setFetchedAt(channel.id, now)
                }
                .onFailure { failures++ }
        }
        failures
    }

    suspend fun setWatched(videoId: Long, watched: Boolean) =
        dao.setWatched(videoId, if (watched) System.currentTimeMillis() else null)

    suspend fun updatePosition(videoId: Long, positionMs: Long) = dao.updatePosition(videoId, positionMs)

    suspend fun updateDuration(videoId: Long, durationMs: Long) = dao.updateDuration(videoId, durationMs)

    suspend fun deleteChannel(channelId: Long) = dao.deleteChannel(channelId)

    fun observeTags(): Flow<List<YoutubeTagEntity>> = dao.observeTags()

    fun observeTagsInUse(): Flow<List<YoutubeTagEntity>> = dao.observeTagsInUse()

    fun observeChannelIdsWithTag(tagId: Long): Flow<List<Long>> = dao.observeChannelIdsWithTag(tagId)

    fun observeTagIdsForChannel(channelId: Long): Flow<List<Long>> = dao.observeTagIdsForChannel(channelId)

    /** The id of the category called [rawName], created when it doesn't exist yet. */
    suspend fun createTag(rawName: String): Result<Long> = runCatching {
        val name = requireNotNull(normalizeTagName(rawName)) { "Zadejte název kategorie" }
        dao.findTagByName(name)?.id ?: dao.insertTag(YoutubeTagEntity(name = name, position = dao.maxTagPosition() + 1))
    }

    suspend fun createAndAssign(channelId: Long, rawName: String): Result<Unit> =
        createTag(rawName).map { tagId -> dao.assignTag(YoutubeChannelTagCrossRef(channelId, tagId)) }

    suspend fun setAssigned(channelId: Long, tagId: Long, assigned: Boolean) {
        if (assigned) dao.assignTag(YoutubeChannelTagCrossRef(channelId, tagId)) else dao.unassignTag(channelId, tagId)
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

private fun ParsedYoutubeVideo.toEntity(feedId: Long, now: Long) = YoutubeVideoEntity(
    feedId = feedId,
    guid = guid,
    videoId = videoId,
    title = title,
    videoUrl = videoUrl,
    thumbnailUrl = thumbnailUrl,
    description = description,
    pubDateMillis = pubDateMillis,
    dateFetched = now
)

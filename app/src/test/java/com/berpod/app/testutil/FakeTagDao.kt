package com.berpod.app.testutil

import com.berpod.app.data.local.EpisodeEntity
import com.berpod.app.data.local.PodcastFeedDao
import com.berpod.app.data.local.PodcastFeedEntity
import com.berpod.app.data.local.PodcastTagCrossRef
import com.berpod.app.data.local.QueueItemWithEpisode
import com.berpod.app.data.local.TagDao
import com.berpod.app.data.local.TagEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

fun feed(id: Long, dateAdded: Long = id) = PodcastFeedEntity(
    id = id,
    rssUrl = "https://example.com/$id.xml",
    title = "Podcast $id",
    imageUrl = null,
    description = null,
    dateAdded = dateAdded,
    lastFetchedAt = null
)

fun episode(id: Long, feedId: Long) = EpisodeEntity(
    id = id,
    feedId = feedId,
    guid = "guid-$id",
    title = "Episode $id",
    audioUrl = "https://example.com/$id.mp3",
    pubDateMillis = null,
    durationSeconds = null,
    description = null,
    imageUrl = null,
    dateFetched = 0L
)

fun queueItem(entryId: Long, position: Int, feedId: Long) = QueueItemWithEpisode(
    episode = episode(id = entryId, feedId = feedId),
    podcastTitle = "Podcast $feedId",
    podcastImageUrl = null,
    queueEntryId = entryId,
    queuePosition = position
)

class FakePodcastFeedDao(initial: List<PodcastFeedEntity> = emptyList()) : PodcastFeedDao {
    val feeds = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<PodcastFeedEntity>> =
        feeds.map { list -> list.sortedByDescending { it.dateAdded } }

    override suspend fun existsByUrl(rssUrl: String) = feeds.value.any { it.rssUrl == rssUrl }
    override suspend fun existsByUrlExcept(rssUrl: String, feedId: Long) = feeds.value.any { it.rssUrl == rssUrl && it.id != feedId }
    override suspend fun updateRssUrl(feedId: Long, rssUrl: String) {
        feeds.value = feeds.value.map { if (it.id == feedId) it.copy(rssUrl = rssUrl) else it }
    }
    override suspend fun getAll() = feeds.value
    override suspend fun getById(feedId: Long) = feeds.value.find { it.id == feedId }
    override suspend fun insert(feed: PodcastFeedEntity): Long {
        feeds.value = feeds.value + feed
        return feed.id
    }
    override suspend fun delete(feed: PodcastFeedEntity) {
        feeds.value = feeds.value.filterNot { it.id == feed.id }
    }
    override fun observeById(feedId: Long): Flow<PodcastFeedEntity?> =
        feeds.map { list -> list.find { it.id == feedId } }
    override suspend fun updateAuthor(feedId: Long, author: String) {
        feeds.value = feeds.value.map { if (it.id == feedId) it.copy(author = author) else it }
    }
}

/** In-memory TagDao that mimics NOCASE lookups, IGNORE on assign, and cascade on delete. */
class FakeTagDao(private val feeds: StateFlow<List<PodcastFeedEntity>>) : TagDao {
    private val _tags = MutableStateFlow<List<TagEntity>>(emptyList())
    private val _links = MutableStateFlow<Set<PodcastTagCrossRef>>(emptySet())
    val tags: StateFlow<List<TagEntity>> = _tags
    val links: StateFlow<Set<PodcastTagCrossRef>> = _links
    private var nextId = 1L

    override fun observeAll(): Flow<List<TagEntity>> =
        _tags.map { list -> list.sortedBy { it.name.lowercase() } }

    override fun observeInUse(): Flow<List<TagEntity>> =
        combine(_tags, _links) { tags, links ->
            val used = links.map { it.tagId }.toSet()
            tags.filter { it.id in used }.sortedBy { it.name.lowercase() }
        }

    override fun observeTagIdsForPodcast(podcastId: Long): Flow<List<Long>> =
        _links.map { links -> links.filter { it.podcastId == podcastId }.map { it.tagId } }

    override fun observePodcastIdsWithTag(tagId: Long): Flow<List<Long>> =
        _links.map { links -> links.filter { it.tagId == tagId }.map { it.podcastId } }

    override fun observeFeedsWithTag(tagId: Long): Flow<List<PodcastFeedEntity>> =
        combine(feeds, _links) { feedList, links ->
            val ids = links.filter { it.tagId == tagId }.map { it.podcastId }.toSet()
            feedList.filter { it.id in ids }.sortedByDescending { it.dateAdded }
        }

    override suspend fun findByName(name: String): TagEntity? =
        _tags.value.find { it.name.equals(name, ignoreCase = true) }

    override suspend fun insert(tag: TagEntity): Long {
        check(findByName(tag.name) == null) { "UNIQUE constraint failed: tags.name" }
        val withId = tag.copy(id = nextId++)
        _tags.value = _tags.value + withId
        return withId.id
    }

    override suspend fun rename(tagId: Long, name: String) {
        _tags.value = _tags.value.map { if (it.id == tagId) it.copy(name = name) else it }
    }

    override suspend fun delete(tagId: Long) {
        _tags.value = _tags.value.filterNot { it.id == tagId }
        _links.value = _links.value.filterNot { it.tagId == tagId }.toSet()
    }

    override suspend fun assign(crossRef: PodcastTagCrossRef) {
        _links.value = _links.value + crossRef
    }

    override suspend fun unassign(podcastId: Long, tagId: Long) {
        _links.value = _links.value - PodcastTagCrossRef(podcastId, tagId)
    }
}

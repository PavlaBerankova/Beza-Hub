package com.berpod.app.data.repository

import com.berpod.app.data.local.PodcastFeedDao
import com.berpod.app.data.local.PodcastFeedEntity
import com.berpod.app.data.local.PodcastTagCrossRef
import com.berpod.app.data.local.TagDao
import com.berpod.app.data.local.TagEntity
import kotlinx.coroutines.flow.Flow

/** Returns the trimmed tag name, or null when it's blank. */
fun normalizeTagName(raw: String): String? = raw.trim().takeIf { it.isNotEmpty() }

class TagRepository(
    private val tagDao: TagDao,
    private val podcastFeedDao: PodcastFeedDao
) {

    fun observeTags(): Flow<List<TagEntity>> = tagDao.observeAll()

    fun observeTagsInUse(): Flow<List<TagEntity>> = tagDao.observeInUse()

    fun observeTagIdsForPodcast(podcastId: Long): Flow<List<Long>> = tagDao.observeTagIdsForPodcast(podcastId)

    fun observePodcastIdsWithTag(tagId: Long): Flow<List<Long>> = tagDao.observePodcastIdsWithTag(tagId)

    /** All feeds when [filterTagId] is null, otherwise only feeds carrying that tag. */
    fun observeFeeds(filterTagId: Long?): Flow<List<PodcastFeedEntity>> =
        if (filterTagId == null) podcastFeedDao.observeAll() else tagDao.observeFeedsWithTag(filterTagId)

    suspend fun createAndAssign(podcastId: Long, rawName: String): Result<Unit> = runCatching {
        val name = requireNotNull(normalizeTagName(rawName)) { "Zadejte název štítku" }
        val tagId = tagDao.findByName(name)?.id ?: tagDao.insert(TagEntity(name = name))
        tagDao.assign(PodcastTagCrossRef(podcastId, tagId))
    }

    suspend fun setAssigned(podcastId: Long, tagId: Long, assigned: Boolean) {
        if (assigned) tagDao.assign(PodcastTagCrossRef(podcastId, tagId)) else tagDao.unassign(podcastId, tagId)
    }

    suspend fun rename(tagId: Long, rawName: String): Result<Unit> = runCatching {
        val name = requireNotNull(normalizeTagName(rawName)) { "Zadejte název štítku" }
        val existing = tagDao.findByName(name)
        require(existing == null || existing.id == tagId) { "Štítek s tímto názvem už existuje" }
        tagDao.rename(tagId, name)
    }

    suspend fun delete(tagId: Long) = tagDao.delete(tagId)
}

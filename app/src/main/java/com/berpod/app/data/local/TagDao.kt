package com.berpod.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY name")
    fun observeAll(): Flow<List<TagEntity>>

    /** Tags assigned to at least one podcast: the filter chips skip empty ones. */
    @Query("SELECT * FROM tags WHERE id IN (SELECT tagId FROM podcast_tags) ORDER BY name")
    fun observeInUse(): Flow<List<TagEntity>>

    @Query("SELECT tagId FROM podcast_tags WHERE podcastId = :podcastId")
    fun observeTagIdsForPodcast(podcastId: Long): Flow<List<Long>>

    @Query("SELECT podcastId FROM podcast_tags WHERE tagId = :tagId")
    fun observePodcastIdsWithTag(tagId: Long): Flow<List<Long>>

    @Query(
        """
        SELECT podcast_feeds.* FROM podcast_feeds
        INNER JOIN podcast_tags ON podcast_feeds.id = podcast_tags.podcastId
        WHERE podcast_tags.tagId = :tagId
        ORDER BY podcast_feeds.dateAdded DESC
        """
    )
    fun observeFeedsWithTag(tagId: Long): Flow<List<PodcastFeedEntity>>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(tag: TagEntity): Long

    @Query("UPDATE tags SET name = :name WHERE id = :tagId")
    suspend fun rename(tagId: Long, name: String)

    @Query("DELETE FROM tags WHERE id = :tagId")
    suspend fun delete(tagId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun assign(crossRef: PodcastTagCrossRef)

    @Query("DELETE FROM podcast_tags WHERE podcastId = :podcastId AND tagId = :tagId")
    suspend fun unassign(podcastId: Long, tagId: Long)
}

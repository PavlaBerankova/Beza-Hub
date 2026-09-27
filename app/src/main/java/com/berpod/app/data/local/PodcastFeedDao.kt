package com.berpod.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastFeedDao {

    @Query("SELECT * FROM podcast_feeds ORDER BY dateAdded DESC")
    fun observeAll(): Flow<List<PodcastFeedEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM podcast_feeds WHERE rssUrl = :rssUrl)")
    suspend fun existsByUrl(rssUrl: String): Boolean

    @Query("SELECT * FROM podcast_feeds")
    suspend fun getAll(): List<PodcastFeedEntity>

    @Query("SELECT * FROM podcast_feeds WHERE id = :feedId")
    suspend fun getById(feedId: Long): PodcastFeedEntity?

    @Query("SELECT * FROM podcast_feeds WHERE id = :feedId")
    fun observeById(feedId: Long): Flow<PodcastFeedEntity?>

    @Query("UPDATE podcast_feeds SET author = :author WHERE id = :feedId")
    suspend fun updateAuthor(feedId: Long, author: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(feed: PodcastFeedEntity): Long

    @Delete
    suspend fun delete(feed: PodcastFeedEntity)
}

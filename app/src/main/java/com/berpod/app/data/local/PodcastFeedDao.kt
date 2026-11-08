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

    @Query("SELECT EXISTS(SELECT 1 FROM podcast_feeds WHERE rssUrl = :rssUrl AND id != :feedId)")
    suspend fun existsByUrlExcept(rssUrl: String, feedId: Long): Boolean

    /** Records a successful refresh; the validators belong to the feed version that was just stored. */
    @Query("UPDATE podcast_feeds SET lastFetchedAt = :at, etag = :etag, lastModified = :lastModified WHERE id = :feedId")
    suspend fun markFetched(feedId: Long, at: Long, etag: String?, lastModified: String?)

    /** A new address is a different feed: validators of the old one must not be sent to it. */
    @Query("UPDATE podcast_feeds SET rssUrl = :rssUrl, etag = NULL, lastModified = NULL WHERE id = :feedId")
    suspend fun updateRssUrl(feedId: Long, rssUrl: String)

    @Query("UPDATE podcast_feeds SET linkTitle = :title, linkUrl = :url WHERE id = :feedId")
    suspend fun updateLink(feedId: Long, title: String?, url: String?)

    @Query("UPDATE podcast_feeds SET hasPaidSubscription = :paid WHERE id = :feedId")
    suspend fun updatePaidSubscription(feedId: Long, paid: Boolean)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(feed: PodcastFeedEntity): Long

    @Delete
    suspend fun delete(feed: PodcastFeedEntity)
}

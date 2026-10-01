package com.berpod.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface YoutubeDao {

    @Query("SELECT * FROM youtube_channels ORDER BY title COLLATE NOCASE")
    fun observeChannels(): Flow<List<YoutubeChannelEntity>>

    @Query(
        """
        SELECT youtube_channels.*, (SELECT COUNT(*) FROM youtube_videos WHERE youtube_videos.feedId = youtube_channels.id) AS videoCount
        FROM youtube_channels ORDER BY youtube_channels.title COLLATE NOCASE
        """
    )
    fun observeChannelsWithCount(): Flow<List<YoutubeChannelWithCount>>

    @Query("SELECT * FROM youtube_channels")
    suspend fun getChannels(): List<YoutubeChannelEntity>

    @Query("SELECT * FROM youtube_channels WHERE id = :channelId")
    suspend fun getChannelById(channelId: Long): YoutubeChannelEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM youtube_channels WHERE rssUrl = :rssUrl)")
    suspend fun channelExists(rssUrl: String): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertChannel(channel: YoutubeChannelEntity): Long

    @Query("UPDATE youtube_channels SET lastFetchedAt = :at WHERE id = :channelId")
    suspend fun setFetchedAt(channelId: Long, at: Long)

    @Query("DELETE FROM youtube_channels WHERE id = :channelId")
    suspend fun deleteChannel(channelId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVideos(videos: List<YoutubeVideoEntity>): List<Long>

    @Query(
        """
        SELECT youtube_videos.*, youtube_channels.title AS channelTitle, youtube_channels.thumbnailUrl AS channelThumbnailUrl
        FROM youtube_videos
        INNER JOIN youtube_channels ON youtube_channels.id = youtube_videos.feedId
        ORDER BY youtube_videos.pubDateMillis IS NULL, youtube_videos.pubDateMillis DESC, youtube_videos.id DESC
        """
    )
    fun observeVideos(): Flow<List<VideoWithChannel>>

    @Query(
        """
        SELECT youtube_videos.*, youtube_channels.title AS channelTitle, youtube_channels.thumbnailUrl AS channelThumbnailUrl
        FROM youtube_videos
        INNER JOIN youtube_channels ON youtube_channels.id = youtube_videos.feedId
        WHERE youtube_videos.id = :videoId
        """
    )
    fun observeVideo(videoId: Long): Flow<VideoWithChannel?>

    @Query("UPDATE youtube_videos SET watchedAtMs = :watchedAtMs WHERE id = :videoId")
    suspend fun setWatched(videoId: Long, watchedAtMs: Long?)

    @Query("UPDATE youtube_videos SET lastPositionMs = :positionMs WHERE id = :videoId")
    suspend fun updatePosition(videoId: Long, positionMs: Long)

    @Query("UPDATE youtube_videos SET durationMs = :durationMs WHERE id = :videoId")
    suspend fun updateDuration(videoId: Long, durationMs: Long)

    @Query("SELECT * FROM youtube_tags ORDER BY name")
    fun observeTags(): Flow<List<YoutubeTagEntity>>

    /** Categories some channel has: the filter chips skip empty ones. */
    @Query("SELECT * FROM youtube_tags WHERE id IN (SELECT tagId FROM youtube_channel_tags) ORDER BY name")
    fun observeTagsInUse(): Flow<List<YoutubeTagEntity>>

    @Query("SELECT channelId FROM youtube_channel_tags WHERE tagId = :tagId")
    fun observeChannelIdsWithTag(tagId: Long): Flow<List<Long>>

    @Query("SELECT tagId FROM youtube_channel_tags WHERE channelId = :channelId")
    fun observeTagIdsForChannel(channelId: Long): Flow<List<Long>>

    @Query("SELECT * FROM youtube_tags WHERE name = :name LIMIT 1")
    suspend fun findTagByName(name: String): YoutubeTagEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTag(tag: YoutubeTagEntity): Long

    @Query("UPDATE youtube_tags SET name = :name WHERE id = :tagId")
    suspend fun renameTag(tagId: Long, name: String)

    @Query("DELETE FROM youtube_tags WHERE id = :tagId")
    suspend fun deleteTag(tagId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun assignTag(crossRef: YoutubeChannelTagCrossRef)

    @Query("DELETE FROM youtube_channel_tags WHERE channelId = :channelId AND tagId = :tagId")
    suspend fun unassignTag(channelId: Long, tagId: Long)

    /** Adds a channel with its first videos and categories in one go. */
    @Transaction
    suspend fun insertChannelWithVideos(channel: YoutubeChannelEntity, videos: List<YoutubeVideoEntity>, tagIds: Set<Long>): Long {
        val channelId = insertChannel(channel)
        insertVideos(videos.map { it.copy(feedId = channelId) })
        tagIds.forEach { assignTag(YoutubeChannelTagCrossRef(channelId, it)) }
        return channelId
    }
}

package com.berpod.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** What counts as "new" for a channel, as for podcasts: published up to a week before the channel was added, or later. */
private const val NEW_GRACE_MS = 7L * 24 * 60 * 60 * 1000

/** Undated videos fetched by the first sync right after adding count as back catalogue, not as new. */
private const val FIRST_SYNC_MS = 60_000L

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

    /** The new videos: unwatched, not hidden, and not part of the channel's back catalogue. */
    @Query(
        """
        SELECT youtube_videos.*, youtube_channels.title AS channelTitle, youtube_channels.thumbnailUrl AS channelThumbnailUrl
        FROM youtube_videos
        INNER JOIN youtube_channels ON youtube_channels.id = youtube_videos.feedId
        WHERE youtube_videos.watchedAtMs IS NULL
          AND youtube_videos.hiddenFromNew = 0
          AND (
            youtube_videos.pubDateMillis >= youtube_channels.dateAdded - $NEW_GRACE_MS
            OR (youtube_videos.pubDateMillis IS NULL AND youtube_videos.dateFetched > youtube_channels.dateAdded + $FIRST_SYNC_MS)
          )
        ORDER BY youtube_videos.pubDateMillis IS NULL, youtube_videos.pubDateMillis DESC, youtube_videos.id DESC
        """
    )
    fun observeNewVideos(): Flow<List<VideoWithChannel>>

    /** The channel's page was seen: its videos that count as new stop counting (same rule as the list above). */
    @Query(
        """
        UPDATE youtube_videos SET hiddenFromNew = 1
        WHERE feedId = :channelId AND watchedAtMs IS NULL AND hiddenFromNew = 0
          AND (
            pubDateMillis >= (SELECT dateAdded FROM youtube_channels WHERE id = :channelId) - $NEW_GRACE_MS
            OR (pubDateMillis IS NULL AND dateFetched > (SELECT dateAdded FROM youtube_channels WHERE id = :channelId) + $FIRST_SYNC_MS)
          )
        """
    )
    suspend fun markChannelSeen(channelId: Long)

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

    @Query("SELECT * FROM youtube_tags ORDER BY position, name")
    fun observeTags(): Flow<List<YoutubeTagEntity>>

    /** Categories some channel has: the filter chips skip empty ones. */
    @Query("SELECT * FROM youtube_tags WHERE id IN (SELECT tagId FROM youtube_channel_tags) ORDER BY position, name")
    fun observeTagsInUse(): Flow<List<YoutubeTagEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM youtube_tags")
    suspend fun maxTagPosition(): Int

    @Query("UPDATE youtube_tags SET position = :position WHERE id = :tagId")
    suspend fun setTagPosition(tagId: Long, position: Int)

    /** Puts the tags in the order given: the first id gets position 0, and so on. */
    @Transaction
    suspend fun applyTagOrder(tagIds: List<Long>) {
        tagIds.forEachIndexed { index, id -> setTagPosition(id, index) }
    }

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

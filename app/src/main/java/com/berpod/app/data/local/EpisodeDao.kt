package com.berpod.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(episodes: List<EpisodeEntity>): List<Long>

    @Query(
        """
        SELECT episodes.*, podcast_feeds.title AS podcastTitle, podcast_feeds.imageUrl AS podcastImageUrl
        FROM episodes
        INNER JOIN podcast_feeds ON podcast_feeds.id = episodes.feedId
        ORDER BY episodes.pubDateMillis IS NULL, episodes.pubDateMillis DESC
        """
    )
    fun observeAllEpisodesWithFeed(): Flow<List<EpisodeWithFeed>>

    @Query("SELECT * FROM episodes WHERE feedId = :feedId ORDER BY pubDateMillis IS NULL, pubDateMillis DESC")
    fun observeEpisodesForFeed(feedId: Long): Flow<List<EpisodeEntity>>

    @Query("UPDATE episodes SET lastPositionMs = :positionMs WHERE id = :episodeId")
    suspend fun updatePosition(episodeId: Long, positionMs: Long)
}

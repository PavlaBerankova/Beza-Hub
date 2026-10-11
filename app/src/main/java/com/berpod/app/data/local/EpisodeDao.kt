package com.berpod.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * What counts as "new" for a podcast: episodes published up to a week before it was added, plus anything that
 * arrives later. Its older back catalogue stays out of Nové (it is all in the podcast's own page).
 */
private const val NEW_GRACE_MS = 7L * 24 * 60 * 60 * 1000

/** Undated episodes fetched by the first sync right after adding count as back catalogue, not as new. */
private const val FIRST_SYNC_MS = 60_000L

@Dao
interface EpisodeDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(episodes: List<EpisodeEntity>): List<Long>

    @Query(
        """
        SELECT episodes.*, podcast_feeds.title AS podcastTitle, podcast_feeds.imageUrl AS podcastImageUrl
        FROM episodes
        INNER JOIN podcast_feeds ON podcast_feeds.id = episodes.feedId
        WHERE episodes.playedAtMs IS NULL
          AND episodes.hiddenFromNew = 0
          AND (
            episodes.pubDateMillis >= podcast_feeds.dateAdded - $NEW_GRACE_MS
            OR (episodes.pubDateMillis IS NULL AND episodes.dateFetched > podcast_feeds.dateAdded + $FIRST_SYNC_MS)
          )
        ORDER BY episodes.pubDateMillis IS NULL, episodes.pubDateMillis DESC
        """
    )
    fun observeAllEpisodesWithFeed(): Flow<List<EpisodeWithFeed>>

    @Query(
        """
        SELECT episodes.*, podcast_feeds.title AS podcastTitle, podcast_feeds.imageUrl AS podcastImageUrl
        FROM episodes
        INNER JOIN podcast_feeds ON podcast_feeds.id = episodes.feedId
        WHERE episodes.id = :episodeId
        """
    )
    fun observeEpisodeWithFeed(episodeId: Long): Flow<EpisodeWithFeed?>

    @Query(
        """
        SELECT episodes.*, podcast_feeds.title AS podcastTitle, podcast_feeds.imageUrl AS podcastImageUrl
        FROM episodes
        INNER JOIN podcast_feeds ON podcast_feeds.id = episodes.feedId
        WHERE episodes.id = :episodeId
        """
    )
    suspend fun getWithFeed(episodeId: Long): EpisodeWithFeed?

    @Query("SELECT * FROM episodes WHERE feedId = :feedId ORDER BY pubDateMillis IS NULL, pubDateMillis DESC")
    fun observeEpisodesForFeed(feedId: Long): Flow<List<EpisodeEntity>>

    /** Marks played (and resets the position so a replay starts from 0), or clears the mark with null. */
    @Query("UPDATE episodes SET playedAtMs = :playedAtMs, lastPositionMs = CASE WHEN :playedAtMs IS NULL THEN lastPositionMs ELSE 0 END WHERE id = :episodeId")
    suspend fun setPlayed(episodeId: Long, playedAtMs: Long?)

    /** Marks the given unplayed episodes played, restarting each from 0 like [setPlayed] does. */
    @Query("UPDATE episodes SET playedAtMs = :playedAtMs, lastPositionMs = 0 WHERE id IN (:episodeIds) AND playedAtMs IS NULL")
    suspend fun markPlayed(episodeIds: List<Long>, playedAtMs: Long)

    /** Sets the number and size on an episode stored earlier (inserts ignore existing rows); only if they differ. */
    @Query(
        """
        UPDATE episodes SET episodeNumber = :episodeNumber, fileSizeBytes = :fileSizeBytes
        WHERE feedId = :feedId AND guid = :guid
          AND (episodeNumber IS NOT :episodeNumber OR fileSizeBytes IS NOT :fileSizeBytes)
        """
    )
    suspend fun updateNumberAndSize(feedId: Long, guid: String, episodeNumber: Int?, fileSizeBytes: Long?)

    @Query("UPDATE episodes SET hiddenFromNew = :hidden WHERE id = :episodeId")
    suspend fun setHiddenFromNew(episodeId: Long, hidden: Boolean)

    @Query("SELECT * FROM episodes WHERE id = :episodeId")
    suspend fun getById(episodeId: Long): EpisodeEntity?

    @Query("SELECT feedId FROM episodes WHERE id = :episodeId")
    suspend fun feedIdOf(episodeId: Long): Long?

    @Query("SELECT id FROM episodes WHERE feedId = :feedId")
    suspend fun idsForFeed(feedId: Long): List<Long>

    @Query("SELECT id FROM episodes WHERE feedId = :feedId AND playedAtMs IS NULL")
    suspend fun unplayedIdsForFeed(feedId: Long): List<Long>

    /** Marks every unplayed episode of the podcast played, restarting each from 0 like [setPlayed] does. */
    @Query("UPDATE episodes SET playedAtMs = :playedAtMs, lastPositionMs = 0 WHERE feedId = :feedId AND playedAtMs IS NULL")
    suspend fun markAllPlayed(feedId: Long, playedAtMs: Long)

    @Query("UPDATE episodes SET playedAtMs = NULL WHERE feedId = :feedId AND playedAtMs IS NOT NULL")
    suspend fun markAllUnplayed(feedId: Long)

    @Query("UPDATE episodes SET lastPositionMs = :positionMs WHERE id = :episodeId")
    suspend fun updatePosition(episodeId: Long, positionMs: Long)
}

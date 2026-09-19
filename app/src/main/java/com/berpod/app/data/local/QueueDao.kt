package com.berpod.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueDao {

    @Query(
        """
        SELECT episodes.*, podcast_feeds.title AS podcastTitle, podcast_feeds.imageUrl AS podcastImageUrl,
               queue_entries.id AS queueEntryId, queue_entries.position AS queuePosition
        FROM queue_entries
        INNER JOIN episodes ON episodes.id = queue_entries.episodeId
        INNER JOIN podcast_feeds ON podcast_feeds.id = episodes.feedId
        ORDER BY queue_entries.position ASC
        """
    )
    fun observeQueue(): Flow<List<QueueItemWithEpisode>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM queue_entries")
    suspend fun getMaxPosition(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM queue_entries WHERE episodeId = :episodeId)")
    suspend fun isQueued(episodeId: Long): Boolean

    @Insert
    suspend fun insert(entry: QueueEntryEntity): Long

    @Query("DELETE FROM queue_entries WHERE id = :queueEntryId")
    suspend fun deleteById(queueEntryId: Long)

    @Query("UPDATE queue_entries SET position = :position WHERE id = :queueEntryId")
    suspend fun updatePosition(queueEntryId: Long, position: Int)

    @Transaction
    suspend fun swapPositions(idA: Long, posA: Int, idB: Long, posB: Int) {
        updatePosition(idA, posB)
        updatePosition(idB, posA)
    }
}

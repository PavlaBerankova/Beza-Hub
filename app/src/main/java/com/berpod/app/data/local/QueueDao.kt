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

    @Query("SELECT position FROM queue_entries WHERE id = :queueEntryId")
    suspend fun getPosition(queueEntryId: Long): Int?

    // Reads the current positions inside the transaction, so a move issued from a stale UI list
    // (a second tap before the queue re-emits) can't leave two entries on the same position.
    @Transaction
    suspend fun swapEntries(idA: Long, idB: Long) {
        val posA = getPosition(idA) ?: return
        val posB = getPosition(idB) ?: return
        updatePosition(idA, posB)
        updatePosition(idB, posA)
    }
}

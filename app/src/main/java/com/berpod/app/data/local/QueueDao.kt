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

    @Query("SELECT episodeId FROM queue_entries")
    fun observeQueuedEpisodeIds(): Flow<List<Long>>

    @Query("SELECT EXISTS(SELECT 1 FROM queue_entries WHERE episodeId = :episodeId)")
    suspend fun isQueued(episodeId: Long): Boolean

    @Insert
    suspend fun insert(entry: QueueEntryEntity): Long

    @Query("SELECT id FROM queue_entries WHERE episodeId = :episodeId LIMIT 1")
    suspend fun findIdByEpisode(episodeId: Long): Long?

    @Query("DELETE FROM queue_entries WHERE id = :queueEntryId")
    suspend fun deleteById(queueEntryId: Long)

    @Query("UPDATE queue_entries SET position = :position WHERE id = :queueEntryId")
    suspend fun updatePosition(queueEntryId: Long, position: Int)

    @Query("SELECT * FROM queue_entries WHERE id = :queueEntryId")
    suspend fun getById(queueEntryId: Long): QueueEntryEntity?

    @Query("SELECT id FROM queue_entries ORDER BY position ASC")
    suspend fun getOrderedIds(): List<Long>

    // The new order is computed from the stored order inside the transaction, so a move issued
    // from a stale UI list can't leave two entries on the same position.
    @Transaction
    suspend fun moveEntry(movedId: Long, afterId: Long?, beforeId: Long?) {
        val current = getOrderedIds()
        val reordered = reorderQueue(current, movedId, afterId, beforeId)
        if (reordered == current) return
        reordered.forEachIndexed { index, id -> updatePosition(id, index) }
    }

    /**
     * Puts the queue in [preferred] order. Entries that are no longer queued are skipped and entries that
     * aren't named keep their relative order at the end, so a stale list can't corrupt the positions.
     */
    @Transaction
    suspend fun applyOrder(preferred: List<Long>) {
        val current = getOrderedIds()
        val present = current.toSet()
        val final = preferred.filter { it in present }.distinct().let { named -> named + current.filter { it !in named.toSet() } }
        if (final == current) return
        final.forEachIndexed { index, id -> updatePosition(id, index) }
    }

    /** Deletes the entry and returns what [restoreEntry] needs to put it back, or null if it's gone. */
    @Transaction
    suspend fun removeEntry(queueEntryId: Long): RemovedQueueEntry? {
        val entry = getById(queueEntryId) ?: return null
        val (afterId, beforeId) = anchorsFor(getOrderedIds(), queueEntryId)
        deleteById(queueEntryId)
        return RemovedQueueEntry(entry.episodeId, entry.dateAdded, afterId, beforeId)
    }

    /** Re-adds a removed entry between its old neighbours (at the end if both are gone); no-op if queued again. */
    @Transaction
    suspend fun restoreEntry(removed: RemovedQueueEntry) {
        if (isQueued(removed.episodeId)) return
        val newId = insert(
            QueueEntryEntity(episodeId = removed.episodeId, position = getMaxPosition() + 1, dateAdded = removed.dateAdded)
        )
        moveEntry(newId, removed.afterId, removed.beforeId)
    }

    /** Puts [episodeId] first in the queue unless it's already queued. */
    @Transaction
    suspend fun queueAtFront(episodeId: Long) {
        if (isQueued(episodeId)) return
        val firstId = getOrderedIds().firstOrNull()
        val newId = insert(QueueEntryEntity(episodeId = episodeId, position = getMaxPosition() + 1, dateAdded = System.currentTimeMillis()))
        if (firstId != null) moveEntry(newId, afterId = null, beforeId = firstId)
    }

    /** Removes the episode's queue entry, if any, returning what restoreEntry needs to put it back. */
    @Transaction
    suspend fun removeEpisode(episodeId: Long): RemovedQueueEntry? {
        val id = findIdByEpisode(episodeId) ?: return null
        return removeEntry(id)
    }
}

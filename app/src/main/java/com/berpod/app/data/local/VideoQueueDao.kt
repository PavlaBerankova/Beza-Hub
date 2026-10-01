package com.berpod.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoQueueDao {

    @Query(
        """
        SELECT youtube_videos.*, youtube_channels.title AS channelTitle, youtube_channels.thumbnailUrl AS channelThumbnailUrl,
               video_queue_entries.id AS queueEntryId, video_queue_entries.position AS queuePosition
        FROM video_queue_entries
        INNER JOIN youtube_videos ON youtube_videos.id = video_queue_entries.videoId
        INNER JOIN youtube_channels ON youtube_channels.id = youtube_videos.feedId
        ORDER BY video_queue_entries.position ASC
        """
    )
    fun observeQueue(): Flow<List<QueueVideoItem>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM video_queue_entries")
    suspend fun getMaxPosition(): Int

    @Query("SELECT videoId FROM video_queue_entries")
    fun observeQueuedVideoIds(): Flow<List<Long>>

    @Query("SELECT EXISTS(SELECT 1 FROM video_queue_entries WHERE videoId = :videoId)")
    suspend fun isQueued(videoId: Long): Boolean

    @Insert
    suspend fun insert(entry: VideoQueueEntryEntity): Long

    @Query("SELECT id FROM video_queue_entries WHERE videoId = :videoId LIMIT 1")
    suspend fun findIdByVideo(videoId: Long): Long?

    @Query("DELETE FROM video_queue_entries WHERE id = :queueEntryId")
    suspend fun deleteById(queueEntryId: Long)

    @Query("UPDATE video_queue_entries SET position = :position WHERE id = :queueEntryId")
    suspend fun updatePosition(queueEntryId: Long, position: Int)

    @Query("SELECT * FROM video_queue_entries WHERE id = :queueEntryId")
    suspend fun getById(queueEntryId: Long): VideoQueueEntryEntity?

    @Query("SELECT id FROM video_queue_entries ORDER BY position ASC")
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

    /** Deletes the entry and returns what [restoreEntry] needs to put it back, or null if it's gone. */
    @Transaction
    suspend fun removeEntry(queueEntryId: Long): RemovedVideoQueueEntry? {
        val entry = getById(queueEntryId) ?: return null
        val (afterId, beforeId) = anchorsFor(getOrderedIds(), queueEntryId)
        deleteById(queueEntryId)
        return RemovedVideoQueueEntry(entry.videoId, entry.dateAdded, afterId, beforeId)
    }

    /** Re-adds a removed entry between its old neighbours (at the end if both are gone); no-op if queued again. */
    @Transaction
    suspend fun restoreEntry(removed: RemovedVideoQueueEntry) {
        if (isQueued(removed.videoId)) return
        val newId = insert(
            VideoQueueEntryEntity(videoId = removed.videoId, position = getMaxPosition() + 1, dateAdded = removed.dateAdded)
        )
        moveEntry(newId, removed.afterId, removed.beforeId)
    }

    /** Puts [videoId] first in the queue unless it's already queued. */
    @Transaction
    suspend fun queueAtFront(videoId: Long) {
        if (isQueued(videoId)) return
        val firstId = getOrderedIds().firstOrNull()
        val newId = insert(VideoQueueEntryEntity(videoId = videoId, position = getMaxPosition() + 1, dateAdded = System.currentTimeMillis()))
        if (firstId != null) moveEntry(newId, afterId = null, beforeId = firstId)
    }

    /** Removes the video's queue entry, if any, returning what restoreEntry needs to put it back. */
    @Transaction
    suspend fun removeVideo(videoId: Long): RemovedVideoQueueEntry? {
        val id = findIdByVideo(videoId) ?: return null
        return removeEntry(id)
    }
}

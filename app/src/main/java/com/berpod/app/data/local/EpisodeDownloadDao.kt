package com.berpod.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDownloadDao {

    @Query("SELECT * FROM episode_downloads")
    fun observeAll(): Flow<List<EpisodeDownloadEntity>>

    @Query("SELECT * FROM episode_downloads WHERE episodeId = :episodeId")
    suspend fun get(episodeId: Long): EpisodeDownloadEntity?

    @Query("SELECT * FROM episode_downloads")
    suspend fun getAll(): List<EpisodeDownloadEntity>

    /** Only inserts: an episode already queued, running or finished keeps its row. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(downloads: List<EpisodeDownloadEntity>)

    @Upsert
    suspend fun upsert(download: EpisodeDownloadEntity)

    /** The next one to fetch: the oldest that is waiting (or was running when the app died). */
    @Query("SELECT * FROM episode_downloads WHERE status IN ('QUEUED', 'DOWNLOADING') ORDER BY queuedAtMs ASC LIMIT 1")
    suspend fun nextToDownload(): EpisodeDownloadEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM episode_downloads WHERE status IN ('QUEUED', 'DOWNLOADING'))")
    suspend fun hasPending(): Boolean

    // UPDATEs, not upserts: a download cancelled while it runs must stay gone.
    @Query("UPDATE episode_downloads SET status = :status WHERE episodeId = :episodeId")
    suspend fun setStatus(episodeId: Long, status: DownloadStatus)

    @Query("UPDATE episode_downloads SET status = 'DOWNLOADING', downloadedBytes = :downloadedBytes, totalBytes = :totalBytes WHERE episodeId = :episodeId")
    suspend fun updateProgress(episodeId: Long, downloadedBytes: Long, totalBytes: Long?)

    @Query(
        """
        UPDATE episode_downloads
        SET status = 'COMPLETED', downloadedBytes = :bytes, totalBytes = :bytes, filePath = :filePath, completedAtMs = :completedAtMs
        WHERE episodeId = :episodeId
        """
    )
    suspend fun markCompleted(episodeId: Long, bytes: Long, filePath: String, completedAtMs: Long)

    @Query("DELETE FROM episode_downloads WHERE episodeId = :episodeId")
    suspend fun delete(episodeId: Long)

    @Query("DELETE FROM episode_downloads")
    suspend fun deleteAll()

    /** Finished copies of episodes played after the copy was made, i.e. the ones "delete after playing" removes. */
    @Query(
        """
        SELECT episode_downloads.* FROM episode_downloads
        INNER JOIN episodes ON episodes.id = episode_downloads.episodeId
        WHERE episode_downloads.status = 'COMPLETED'
          AND episodes.playedAtMs IS NOT NULL
          AND episodes.playedAtMs >= episode_downloads.completedAtMs
        """
    )
    fun observePlayedCompleted(): Flow<List<EpisodeDownloadEntity>>
}

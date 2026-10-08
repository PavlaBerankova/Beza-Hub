package com.berpod.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

enum class DownloadStatus { QUEUED, DOWNLOADING, FAILED, COMPLETED }

/** Where an episode's offline copy stands. Kept apart from [EpisodeEntity], which feed syncs rewrite. */
@Entity(
    tableName = "episode_downloads",
    foreignKeys = [
        ForeignKey(
            entity = EpisodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["episodeId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class EpisodeDownloadEntity(
    @PrimaryKey val episodeId: Long,
    val status: DownloadStatus,
    val downloadedBytes: Long,
    /** The full size, once the server has told it. */
    val totalBytes: Long?,
    /** Absolute path of the finished file; null until [DownloadStatus.COMPLETED]. */
    val filePath: String?,
    /** Orders the downloads: they run one after another, oldest first. */
    val queuedAtMs: Long,
    val completedAtMs: Long?
)

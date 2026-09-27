package com.berpod.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = PodcastFeedEntity::class,
            parentColumns = ["id"],
            childColumns = ["feedId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["feedId", "guid"], unique = true),
        Index(value = ["feedId"]),
        Index(value = ["pubDateMillis"])
    ]
)
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val feedId: Long,
    val guid: String,
    val title: String,
    val audioUrl: String,
    val pubDateMillis: Long?,
    val durationSeconds: Int?,
    val description: String?,
    val imageUrl: String?,
    val dateFetched: Long,
    val lastPositionMs: Long = 0L,
    /** When the episode was finished or marked as played; null = not played. */
    val playedAtMs: Long? = null
)

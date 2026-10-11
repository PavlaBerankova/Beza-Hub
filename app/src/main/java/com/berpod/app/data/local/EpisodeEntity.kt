package com.berpod.app.data.local

import androidx.room.ColumnInfo
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
    val playedAtMs: Long? = null,
    /** The feed's own episode number (itunes:episode), when it gives one. */
    val episodeNumber: Int? = null,
    /** Size of the audio file in bytes (the enclosure's length), when the feed says. */
    val fileSizeBytes: Long? = null,
    /** Taken out of Nové by hand ("Odebrat z nových"): it stays unplayed but is not listed there any more. */
    @ColumnInfo(defaultValue = "0") val hiddenFromNew: Boolean = false
)

package com.berpod.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "queue_entries",
    foreignKeys = [
        ForeignKey(
            entity = EpisodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["episodeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["episodeId"], unique = true),
        Index(value = ["position"])
    ]
)
data class QueueEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val episodeId: Long,
    val position: Int,
    val dateAdded: Long
)

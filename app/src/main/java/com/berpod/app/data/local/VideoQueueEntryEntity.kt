package com.berpod.app.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "video_queue_entries",
    foreignKeys = [
        ForeignKey(entity = YoutubeVideoEntity::class, parentColumns = ["id"], childColumns = ["videoId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["videoId"], unique = true),
        Index(value = ["position"])
    ]
)
data class VideoQueueEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: Long,
    val position: Int,
    val dateAdded: Long
)

data class QueueVideoItem(
    @Embedded val video: YoutubeVideoEntity,
    val channelTitle: String,
    val channelThumbnailUrl: String?,
    val queueEntryId: Long,
    val queuePosition: Int
)

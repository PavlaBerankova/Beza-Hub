package com.berpod.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A YouTube channel (YouTube tab); own table, separate from podcast feeds and article sources. */
@Entity(tableName = "youtube_channels", indices = [Index(value = ["rssUrl"], unique = true)])
data class YoutubeChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The resolved .../feeds/videos.xml?channel_id=... address (post-redirect). */
    val rssUrl: String,
    /** YouTube's own channel id (youtubeChannelData.channelId); used to build channelUrl. */
    val channelId: String?,
    val title: String,
    /** The channel's page (https://www.youtube.com/channel/<channelId>), built at resolve time. */
    val channelUrl: String?,
    /** YouTube's feed carries no channel avatar, so this is usually null; ChannelAvatar falls back
     *  to a gradient + initials badge. Kept for a future enhancement. */
    val thumbnailUrl: String?,
    val dateAdded: Long,
    val lastFetchedAt: Long
)

@Entity(
    tableName = "youtube_videos",
    foreignKeys = [
        ForeignKey(entity = YoutubeChannelEntity::class, parentColumns = ["id"], childColumns = ["feedId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["feedId", "guid"], unique = true),
        Index(value = ["feedId"]),
        Index(value = ["pubDateMillis"])
    ]
)
data class YoutubeVideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val feedId: Long,
    val guid: String,
    /** YouTube's own video id — what the embedded player loads. */
    val videoId: String,
    val title: String,
    /** The watch-page link — what "Otevřít na YouTube" opens externally. */
    val videoUrl: String,
    val thumbnailUrl: String?,
    val description: String?,
    val pubDateMillis: Long?,
    val dateFetched: Long,
    /** The feed carries no duration; this stays null until the embedded player reports it once. */
    val durationMs: Long? = null,
    val lastPositionMs: Long = 0L,
    /** When finished or marked "zhlédnuto"; null = not watched. */
    val watchedAtMs: Long? = null,
    /** Taken out of "new" without being watched: its channel's page was left after it was seen. */
    val hiddenFromNew: Boolean = false
)

/** Categories for channels; a set of their own, apart from podcast and article tags. */
@Entity(tableName = "youtube_tags", indices = [Index(value = ["name"], unique = true)])
data class YoutubeTagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String
)

@Entity(
    tableName = "youtube_channel_tags",
    primaryKeys = ["channelId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = YoutubeChannelEntity::class, parentColumns = ["id"], childColumns = ["channelId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = YoutubeTagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["tagId"])]
)
data class YoutubeChannelTagCrossRef(val channelId: Long, val tagId: Long)

data class VideoWithChannel(
    @Embedded val video: YoutubeVideoEntity,
    val channelTitle: String,
    val channelThumbnailUrl: String?
)

data class YoutubeChannelWithCount(
    @Embedded val channel: YoutubeChannelEntity,
    val videoCount: Int
)

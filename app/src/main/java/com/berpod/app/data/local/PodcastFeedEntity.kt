package com.berpod.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "podcast_feeds",
    indices = [Index(value = ["rssUrl"], unique = true)]
)
data class PodcastFeedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rssUrl: String,
    val title: String,
    val imageUrl: String?,
    val description: String?,
    val dateAdded: Long,
    val lastFetchedAt: Long?,
    val author: String? = null,
    /** The user's own link for this podcast (e.g. its paid-subscription page) and what it is called. */
    val linkTitle: String? = null,
    val linkUrl: String? = null,
    /** `ETag` / `Last-Modified` of the feed as last downloaded, so the next refresh can skip it if unchanged. */
    val etag: String? = null,
    val lastModified: String? = null,
    /** The user marked that they pay for a subscription to this podcast. */
    @ColumnInfo(defaultValue = "0") val hasPaidSubscription: Boolean = false
)

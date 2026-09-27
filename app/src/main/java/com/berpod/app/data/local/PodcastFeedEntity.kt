package com.berpod.app.data.local

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
    val author: String? = null
)

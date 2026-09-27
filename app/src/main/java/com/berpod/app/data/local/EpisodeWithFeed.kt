package com.berpod.app.data.local

import androidx.room.Embedded

data class EpisodeWithFeed(
    @Embedded val episode: EpisodeEntity,
    val podcastTitle: String,
    val podcastImageUrl: String?
)

data class QueueItemWithEpisode(
    @Embedded val episode: EpisodeEntity,
    val podcastTitle: String,
    val podcastImageUrl: String?,
    val queueEntryId: Long,
    val queuePosition: Int
)

package com.berpod.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PodcastFeedEntity::class,
        EpisodeEntity::class,
        QueueEntryEntity::class,
        PlaylistEntity::class,
        PlaylistEpisodeCrossRef::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun podcastFeedDao(): PodcastFeedDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun queueDao(): QueueDao
    abstract fun playlistDao(): PlaylistDao
}

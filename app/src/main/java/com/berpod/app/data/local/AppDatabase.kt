package com.berpod.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PodcastFeedEntity::class,
        EpisodeEntity::class,
        QueueEntryEntity::class,
        TagEntity::class,
        PodcastTagCrossRef::class,
        ArticleFeedEntity::class,
        ArticleEntity::class,
        ArticleTagEntity::class,
        ArticleFeedTagCrossRef::class,
        YoutubeChannelEntity::class,
        YoutubeVideoEntity::class,
        YoutubeTagEntity::class,
        YoutubeChannelTagCrossRef::class,
        VideoQueueEntryEntity::class,
        EpisodeDownloadEntity::class
    ],
    version = 12,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun podcastFeedDao(): PodcastFeedDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun queueDao(): QueueDao
    abstract fun tagDao(): TagDao
    abstract fun articleDao(): ArticleDao
    abstract fun youtubeDao(): YoutubeDao
    abstract fun videoQueueDao(): VideoQueueDao
    abstract fun episodeDownloadDao(): EpisodeDownloadDao
}

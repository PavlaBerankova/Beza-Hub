package com.berpod.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `episodes` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `feedId` INTEGER NOT NULL,
                `guid` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `audioUrl` TEXT NOT NULL,
                `pubDateMillis` INTEGER,
                `durationSeconds` INTEGER,
                `description` TEXT,
                `imageUrl` TEXT,
                `dateFetched` INTEGER NOT NULL,
                FOREIGN KEY(`feedId`) REFERENCES `podcast_feeds`(`id`) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_episodes_feedId_guid` ON `episodes` (`feedId`, `guid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_episodes_feedId` ON `episodes` (`feedId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_episodes_pubDateMillis` ON `episodes` (`pubDateMillis`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `queue_entries` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `episodeId` INTEGER NOT NULL,
                `position` INTEGER NOT NULL,
                `dateAdded` INTEGER NOT NULL,
                FOREIGN KEY(`episodeId`) REFERENCES `episodes`(`id`) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_queue_entries_episodeId` ON `queue_entries` (`episodeId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_queue_entries_position` ON `queue_entries` (`position`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `playlists` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `playlist_episodes` (
                `playlistId` INTEGER NOT NULL,
                `episodeId` INTEGER NOT NULL,
                `position` INTEGER NOT NULL,
                `dateAdded` INTEGER NOT NULL,
                PRIMARY KEY(`playlistId`, `episodeId`),
                FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON DELETE CASCADE,
                FOREIGN KEY(`episodeId`) REFERENCES `episodes`(`id`) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_episodes_episodeId` ON `playlist_episodes` (`episodeId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_episodes_playlistId_position` ON `playlist_episodes` (`playlistId`, `position`)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE episodes ADD COLUMN lastPositionMs INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL COLLATE NOCASE)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `podcast_tags` (
                `podcastId` INTEGER NOT NULL,
                `tagId` INTEGER NOT NULL,
                PRIMARY KEY(`podcastId`, `tagId`),
                FOREIGN KEY(`podcastId`) REFERENCES `podcast_feeds`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_podcast_tags_tagId` ON `podcast_tags` (`tagId`)")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Child table first: playlist_episodes has a foreign key to playlists.
        db.execSQL("DROP TABLE IF EXISTS `playlist_episodes`")
        db.execSQL("DROP TABLE IF EXISTS `playlists`")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE podcast_feeds ADD COLUMN author TEXT")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE episodes ADD COLUMN playedAtMs INTEGER")
    }
}

/** Články: article sources, their articles, and a separate set of categories. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `article_feeds` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `rssUrl` TEXT NOT NULL, `title` TEXT NOT NULL, `siteUrl` TEXT, `imageUrl` TEXT, `dateAdded` INTEGER NOT NULL, `lastFetchedAt` INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_article_feeds_rssUrl` ON `article_feeds` (`rssUrl`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `articles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `feedId` INTEGER NOT NULL, `guid` TEXT NOT NULL, `title` TEXT NOT NULL, `link` TEXT, `author` TEXT, `pubDateMillis` INTEGER, `summary` TEXT, `content` TEXT, `dateFetched` INTEGER NOT NULL, `readAtMs` INTEGER, FOREIGN KEY(`feedId`) REFERENCES `article_feeds`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_articles_feedId_guid` ON `articles` (`feedId`, `guid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_articles_feedId` ON `articles` (`feedId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_articles_pubDateMillis` ON `articles` (`pubDateMillis`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `article_tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL COLLATE NOCASE)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_article_tags_name` ON `article_tags` (`name`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `article_feed_tags` (`feedId` INTEGER NOT NULL, `tagId` INTEGER NOT NULL, PRIMARY KEY(`feedId`, `tagId`), FOREIGN KEY(`feedId`) REFERENCES `article_feeds`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`tagId`) REFERENCES `article_tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_article_feed_tags_tagId` ON `article_feed_tags` (`tagId`)")
    }
}

/** YouTube: channels, their videos, a separate set of categories, and a video-specific queue. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `youtube_channels` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `rssUrl` TEXT NOT NULL, `channelId` TEXT, `title` TEXT NOT NULL, `channelUrl` TEXT, `thumbnailUrl` TEXT, `dateAdded` INTEGER NOT NULL, `lastFetchedAt` INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_youtube_channels_rssUrl` ON `youtube_channels` (`rssUrl`)")

        db.execSQL("CREATE TABLE IF NOT EXISTS `youtube_videos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `feedId` INTEGER NOT NULL, `guid` TEXT NOT NULL, `videoId` TEXT NOT NULL, `title` TEXT NOT NULL, `videoUrl` TEXT NOT NULL, `thumbnailUrl` TEXT, `description` TEXT, `pubDateMillis` INTEGER, `dateFetched` INTEGER NOT NULL, `durationMs` INTEGER, `lastPositionMs` INTEGER NOT NULL, `watchedAtMs` INTEGER, FOREIGN KEY(`feedId`) REFERENCES `youtube_channels`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_youtube_videos_feedId_guid` ON `youtube_videos` (`feedId`, `guid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_youtube_videos_feedId` ON `youtube_videos` (`feedId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_youtube_videos_pubDateMillis` ON `youtube_videos` (`pubDateMillis`)")

        db.execSQL("CREATE TABLE IF NOT EXISTS `youtube_tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL COLLATE NOCASE)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_youtube_tags_name` ON `youtube_tags` (`name`)")

        db.execSQL("CREATE TABLE IF NOT EXISTS `youtube_channel_tags` (`channelId` INTEGER NOT NULL, `tagId` INTEGER NOT NULL, PRIMARY KEY(`channelId`, `tagId`), FOREIGN KEY(`channelId`) REFERENCES `youtube_channels`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`tagId`) REFERENCES `youtube_tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_youtube_channel_tags_tagId` ON `youtube_channel_tags` (`tagId`)")

        db.execSQL("CREATE TABLE IF NOT EXISTS `video_queue_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `videoId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `dateAdded` INTEGER NOT NULL, FOREIGN KEY(`videoId`) REFERENCES `youtube_videos`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_video_queue_entries_videoId` ON `video_queue_entries` (`videoId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_video_queue_entries_position` ON `video_queue_entries` (`position`)")
    }
}

/** Episode number and audio file size, as the feeds state them. */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `episodes` ADD COLUMN `episodeNumber` INTEGER")
        db.execSQL("ALTER TABLE `episodes` ADD COLUMN `fileSizeBytes` INTEGER")
    }
}

/** Offline copies of episodes: one row per episode that was ever asked to download. */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `episode_downloads` (`episodeId` INTEGER NOT NULL, `status` TEXT NOT NULL, `downloadedBytes` INTEGER NOT NULL, `totalBytes` INTEGER, `filePath` TEXT, `queuedAtMs` INTEGER NOT NULL, `completedAtMs` INTEGER, PRIMARY KEY(`episodeId`), FOREIGN KEY(`episodeId`) REFERENCES `episodes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
    }
}

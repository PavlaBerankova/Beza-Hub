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

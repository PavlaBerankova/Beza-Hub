package com.berpod.app.di

import android.content.Context
import androidx.room.Room
import com.berpod.app.data.local.AppDatabase
import com.berpod.app.data.local.MIGRATION_1_2
import com.berpod.app.data.remote.FeedParser
import com.berpod.app.data.repository.EpisodeRepository
import com.berpod.app.data.repository.PlaylistRepository
import com.berpod.app.data.repository.PodcastRepository
import com.berpod.app.data.repository.QueueRepository

interface AppContainer {
    val podcastRepository: PodcastRepository
    val episodeRepository: EpisodeRepository
    val queueRepository: QueueRepository
    val playlistRepository: PlaylistRepository
}

class DefaultAppContainer(context: Context) : AppContainer {

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(context, AppDatabase::class.java, "berpod.db")
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    private val feedParser: FeedParser by lazy { FeedParser() }

    override val podcastRepository: PodcastRepository by lazy {
        PodcastRepository(database.podcastFeedDao(), feedParser)
    }

    override val episodeRepository: EpisodeRepository by lazy {
        EpisodeRepository(database.episodeDao(), database.podcastFeedDao(), feedParser)
    }

    override val queueRepository: QueueRepository by lazy {
        QueueRepository(database.queueDao())
    }

    override val playlistRepository: PlaylistRepository by lazy {
        PlaylistRepository(database.playlistDao())
    }
}

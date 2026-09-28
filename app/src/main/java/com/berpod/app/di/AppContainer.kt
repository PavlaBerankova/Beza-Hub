package com.berpod.app.di

import android.content.Context
import androidx.room.Room
import com.berpod.app.data.local.AppDatabase
import com.berpod.app.data.local.MIGRATION_1_2
import com.berpod.app.data.local.MIGRATION_2_3
import com.berpod.app.data.local.MIGRATION_3_4
import com.berpod.app.data.local.MIGRATION_4_5
import com.berpod.app.data.local.MIGRATION_5_6
import com.berpod.app.data.local.MIGRATION_6_7
import com.berpod.app.data.local.MIGRATION_7_8
import com.berpod.app.data.remote.ArticleFeedLoader
import com.berpod.app.data.remote.FeedParser
import com.berpod.app.data.repository.ArticleRepository
import com.berpod.app.data.repository.EpisodeRepository
import com.berpod.app.data.repository.PlaybackRepository
import com.berpod.app.data.repository.PodcastRepository
import com.berpod.app.data.repository.QueueRepository
import com.berpod.app.data.repository.TagRepository

interface AppContainer {
    val podcastRepository: PodcastRepository
    val episodeRepository: EpisodeRepository
    val queueRepository: QueueRepository
    val playbackRepository: PlaybackRepository
    val tagRepository: TagRepository
    val articleRepository: ArticleRepository
}

class DefaultAppContainer(context: Context) : AppContainer {

    private val appContext = context.applicationContext

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "berpod.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
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


    override val playbackRepository: PlaybackRepository by lazy {
        PlaybackRepository(appContext, database.episodeDao(), database.queueDao())
    }

    override val tagRepository: TagRepository by lazy {
        TagRepository(database.tagDao(), database.podcastFeedDao())
    }

    override val articleRepository: ArticleRepository by lazy {
        ArticleRepository(database.articleDao(), ArticleFeedLoader())
    }
}

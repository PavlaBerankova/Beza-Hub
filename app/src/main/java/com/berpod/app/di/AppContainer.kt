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
import com.berpod.app.data.local.MIGRATION_8_9
import com.berpod.app.data.local.MIGRATION_9_10
import com.berpod.app.data.local.MIGRATION_10_11
import com.berpod.app.data.remote.ArticleFeedLoader
import com.berpod.app.data.remote.FeedParser
import com.berpod.app.data.remote.YoutubeChannelLoader
import com.berpod.app.data.local.EpisodeDownloadDao
import com.berpod.app.data.repository.ArticleRepository
import com.berpod.app.data.repository.DownloadRepository
import com.berpod.app.data.repository.DownloadSettings
import com.berpod.app.download.EpisodeDownloader
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import com.berpod.app.data.repository.EpisodeRepository
import com.berpod.app.data.repository.PlaybackRepository
import com.berpod.app.data.repository.PodcastRepository
import com.berpod.app.data.repository.QueueFilterRepository
import com.berpod.app.data.repository.QueueRepository
import com.berpod.app.data.repository.PrefsQueueFilterStorage
import com.berpod.app.data.repository.TagRepository
import com.berpod.app.data.repository.ThemeSettings
import com.berpod.app.data.repository.VideoQueueRepository
import com.berpod.app.data.repository.YoutubeRepository

interface AppContainer {
    val podcastRepository: PodcastRepository
    val episodeRepository: EpisodeRepository
    val queueRepository: QueueRepository
    val playbackRepository: PlaybackRepository
    val tagRepository: TagRepository
    val articleRepository: ArticleRepository
    val queueFilterRepository: QueueFilterRepository
    val youtubeRepository: YoutubeRepository
    val videoQueueRepository: VideoQueueRepository
    /** The Fronta tag filter video Fronta remembers, kept separate from the podcast one. */
    val videoQueueFilterRepository: QueueFilterRepository
    val themeSettings: ThemeSettings
    val downloadSettings: DownloadSettings
    val downloadRepository: DownloadRepository
    val downloadDao: EpisodeDownloadDao
    val episodeDownloader: EpisodeDownloader
}

class DefaultAppContainer(context: Context) : AppContainer {

    private val appContext = context.applicationContext

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "berpod.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
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


    override val queueFilterRepository: QueueFilterRepository by lazy {
        QueueFilterRepository(PrefsQueueFilterStorage(appContext.getSharedPreferences("queueFilter", Context.MODE_PRIVATE)))
    }

    override val tagRepository: TagRepository by lazy {
        TagRepository(database.tagDao(), database.podcastFeedDao())
    }

    override val playbackRepository: PlaybackRepository by lazy {
        PlaybackRepository(appContext, database.episodeDao(), database.queueDao(), tagRepository, queueFilterRepository, downloadRepository)
    }

    private val downloadDir: File by lazy { File(appContext.filesDir, "episodes") }

    override val downloadSettings: DownloadSettings by lazy {
        DownloadSettings(appContext.getSharedPreferences("settings", Context.MODE_PRIVATE))
    }

    override val downloadDao: EpisodeDownloadDao by lazy { database.episodeDownloadDao() }

    override val downloadRepository: DownloadRepository by lazy {
        DownloadRepository(appContext, downloadDao, downloadSettings, downloadDir)
    }

    override val episodeDownloader: EpisodeDownloader by lazy {
        val client = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()
        EpisodeDownloader(downloadDao, database.episodeDao(), downloadDir, client)
    }

    override val articleRepository: ArticleRepository by lazy {
        ArticleRepository(database.articleDao(), ArticleFeedLoader())
    }

    override val youtubeRepository: YoutubeRepository by lazy {
        YoutubeRepository(database.youtubeDao(), YoutubeChannelLoader())
    }

    override val videoQueueRepository: VideoQueueRepository by lazy {
        VideoQueueRepository(database.videoQueueDao())
    }

    override val themeSettings: ThemeSettings by lazy {
        ThemeSettings(appContext.getSharedPreferences("settings", Context.MODE_PRIVATE))
    }

    override val videoQueueFilterRepository: QueueFilterRepository by lazy {
        QueueFilterRepository(PrefsQueueFilterStorage(appContext.getSharedPreferences("videoQueueFilter", Context.MODE_PRIVATE)))
    }
}

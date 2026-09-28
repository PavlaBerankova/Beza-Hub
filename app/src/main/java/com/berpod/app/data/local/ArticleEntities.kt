package com.berpod.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** An RSS source of articles (Články tab); separate from podcast feeds. */
@Entity(tableName = "article_feeds", indices = [Index(value = ["rssUrl"], unique = true)])
data class ArticleFeedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rssUrl: String,
    val title: String,
    val siteUrl: String?,
    val imageUrl: String?,
    val dateAdded: Long,
    val lastFetchedAt: Long
)

@Entity(
    tableName = "articles",
    foreignKeys = [
        ForeignKey(entity = ArticleFeedEntity::class, parentColumns = ["id"], childColumns = ["feedId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["feedId", "guid"], unique = true),
        Index(value = ["feedId"]),
        Index(value = ["pubDateMillis"])
    ]
)
data class ArticleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val feedId: Long,
    val guid: String,
    val title: String,
    val link: String?,
    val author: String?,
    val pubDateMillis: Long?,
    /** The feed's description: often just the opening. HTML. */
    val summary: String?,
    /** The full text when the feed carries it (content:encoded). HTML. */
    val content: String?,
    val dateFetched: Long,
    /** When it was opened or marked read; null = unread. */
    val readAtMs: Long? = null
)

/** Categories for article sources; a set of their own, apart from podcast tags. */
@Entity(tableName = "article_tags", indices = [Index(value = ["name"], unique = true)])
data class ArticleTagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String
)

@Entity(
    tableName = "article_feed_tags",
    primaryKeys = ["feedId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = ArticleFeedEntity::class, parentColumns = ["id"], childColumns = ["feedId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ArticleTagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["tagId"])]
)
data class ArticleFeedTagCrossRef(val feedId: Long, val tagId: Long)

data class ArticleWithFeed(
    @Embedded val article: ArticleEntity,
    val feedTitle: String
)

data class ArticleFeedWithCount(
    @Embedded val feed: ArticleFeedEntity,
    val articleCount: Int
)

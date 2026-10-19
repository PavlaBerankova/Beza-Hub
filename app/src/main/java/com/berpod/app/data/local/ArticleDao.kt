package com.berpod.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {

    @Query("SELECT * FROM article_feeds ORDER BY title COLLATE NOCASE")
    fun observeFeeds(): Flow<List<ArticleFeedEntity>>

    @Query(
        """
        SELECT article_feeds.*, (SELECT COUNT(*) FROM articles WHERE articles.feedId = article_feeds.id) AS articleCount
        FROM article_feeds ORDER BY article_feeds.title COLLATE NOCASE
        """
    )
    fun observeFeedsWithCount(): Flow<List<ArticleFeedWithCount>>

    @Query("SELECT * FROM article_feeds")
    suspend fun getFeeds(): List<ArticleFeedEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM article_feeds WHERE rssUrl = :rssUrl)")
    suspend fun feedExists(rssUrl: String): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFeed(feed: ArticleFeedEntity): Long

    @Query("UPDATE article_feeds SET lastFetchedAt = :at WHERE id = :feedId")
    suspend fun setFetchedAt(feedId: Long, at: Long)

    @Query("DELETE FROM article_feeds WHERE id = :feedId")
    suspend fun deleteFeed(feedId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArticles(articles: List<ArticleEntity>): List<Long>

    @Query(
        """
        SELECT articles.*, article_feeds.title AS feedTitle FROM articles
        INNER JOIN article_feeds ON article_feeds.id = articles.feedId
        ORDER BY articles.pubDateMillis IS NULL, articles.pubDateMillis DESC, articles.id DESC
        """
    )
    fun observeArticles(): Flow<List<ArticleWithFeed>>

    @Query(
        """
        SELECT articles.*, article_feeds.title AS feedTitle FROM articles
        INNER JOIN article_feeds ON article_feeds.id = articles.feedId
        WHERE articles.id = :articleId
        """
    )
    fun observeArticle(articleId: Long): Flow<ArticleWithFeed?>

    @Query("UPDATE articles SET readAtMs = :readAtMs WHERE id = :articleId")
    suspend fun setRead(articleId: Long, readAtMs: Long?)

    @Query("SELECT * FROM article_tags ORDER BY position, name")
    fun observeTags(): Flow<List<ArticleTagEntity>>

    /** Categories some source has: the filter chips skip empty ones. */
    @Query("SELECT * FROM article_tags WHERE id IN (SELECT tagId FROM article_feed_tags) ORDER BY position, name")
    fun observeTagsInUse(): Flow<List<ArticleTagEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM article_tags")
    suspend fun maxTagPosition(): Int

    @Query("UPDATE article_tags SET position = :position WHERE id = :tagId")
    suspend fun setTagPosition(tagId: Long, position: Int)

    /** Puts the tags in the order given: the first id gets position 0, and so on. */
    @Transaction
    suspend fun applyTagOrder(tagIds: List<Long>) {
        tagIds.forEachIndexed { index, id -> setTagPosition(id, index) }
    }

    @Query("SELECT feedId FROM article_feed_tags WHERE tagId = :tagId")
    fun observeFeedIdsWithTag(tagId: Long): Flow<List<Long>>

    @Query("SELECT tagId FROM article_feed_tags WHERE feedId = :feedId")
    fun observeTagIdsForFeed(feedId: Long): Flow<List<Long>>

    @Query("SELECT * FROM article_tags WHERE name = :name LIMIT 1")
    suspend fun findTagByName(name: String): ArticleTagEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTag(tag: ArticleTagEntity): Long

    @Query("UPDATE article_tags SET name = :name WHERE id = :tagId")
    suspend fun renameTag(tagId: Long, name: String)

    @Query("DELETE FROM article_tags WHERE id = :tagId")
    suspend fun deleteTag(tagId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun assignTag(crossRef: ArticleFeedTagCrossRef)

    @Query("DELETE FROM article_feed_tags WHERE feedId = :feedId AND tagId = :tagId")
    suspend fun unassignTag(feedId: Long, tagId: Long)

    /** Adds a source with its first articles and categories in one go. */
    @Transaction
    suspend fun insertFeedWithArticles(feed: ArticleFeedEntity, articles: List<ArticleEntity>, tagIds: Set<Long>): Long {
        val feedId = insertFeed(feed)
        insertArticles(articles.map { it.copy(feedId = feedId) })
        tagIds.forEach { assignTag(ArticleFeedTagCrossRef(feedId, it)) }
        return feedId
    }
}

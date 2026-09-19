package com.berpod.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM playlists WHERE name = :name)")
    suspend fun existsByName(name: String): Boolean

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getById(playlistId: Long): PlaylistEntity?

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query(
        """
        SELECT episodes.*, podcast_feeds.title AS podcastTitle, podcast_feeds.imageUrl AS podcastImageUrl,
               playlist_episodes.playlistId AS playlistId, playlist_episodes.position AS playlistPosition
        FROM playlist_episodes
        INNER JOIN episodes ON episodes.id = playlist_episodes.episodeId
        INNER JOIN podcast_feeds ON podcast_feeds.id = episodes.feedId
        WHERE playlist_episodes.playlistId = :playlistId
        ORDER BY playlist_episodes.position ASC
        """
    )
    fun observePlaylistEpisodes(playlistId: Long): Flow<List<PlaylistEpisodeWithEpisode>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_episodes WHERE playlistId = :playlistId")
    suspend fun getMaxPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(crossRef: PlaylistEpisodeCrossRef): Long

    @Query("DELETE FROM playlist_episodes WHERE playlistId = :playlistId AND episodeId = :episodeId")
    suspend fun removeEpisode(playlistId: Long, episodeId: Long)

    @Query("UPDATE playlist_episodes SET position = :position WHERE playlistId = :playlistId AND episodeId = :episodeId")
    suspend fun updatePosition(playlistId: Long, episodeId: Long, position: Int)

    @Transaction
    suspend fun swapPositions(playlistId: Long, episodeIdA: Long, posA: Int, episodeIdB: Long, posB: Int) {
        updatePosition(playlistId, episodeIdA, posB)
        updatePosition(playlistId, episodeIdB, posA)
    }
}

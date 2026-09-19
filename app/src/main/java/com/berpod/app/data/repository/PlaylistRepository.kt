package com.berpod.app.data.repository

import com.berpod.app.data.local.PlaylistDao
import com.berpod.app.data.local.PlaylistEntity
import com.berpod.app.data.local.PlaylistEpisodeCrossRef
import com.berpod.app.data.local.PlaylistEpisodeWithEpisode
import kotlinx.coroutines.flow.Flow

class PlaylistRepository(private val playlistDao: PlaylistDao) {

    fun observePlaylists(): Flow<List<PlaylistEntity>> = playlistDao.observePlaylists()

    fun observePlaylistEpisodes(playlistId: Long): Flow<List<PlaylistEpisodeWithEpisode>> =
        playlistDao.observePlaylistEpisodes(playlistId)

    suspend fun getPlaylistName(playlistId: Long): String? = playlistDao.getById(playlistId)?.name

    suspend fun createPlaylist(name: String): Result<Long> = runCatching {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Zadejte název playlistu" }
        require(!playlistDao.existsByName(trimmed)) { "Playlist s tímto názvem už existuje" }
        playlistDao.insertPlaylist(PlaylistEntity(name = trimmed, createdAt = System.currentTimeMillis()))
    }

    suspend fun addEpisode(playlistId: Long, episodeId: Long): Result<Unit> = runCatching {
        val nextPosition = playlistDao.getMaxPosition(playlistId) + 1
        val rowId = playlistDao.insertCrossRef(
            PlaylistEpisodeCrossRef(playlistId, episodeId, nextPosition, System.currentTimeMillis())
        )
        require(rowId != -1L) { "Epizoda v playlistu už je" }
    }

    suspend fun removeEpisode(playlistId: Long, episodeId: Long) = playlistDao.removeEpisode(playlistId, episodeId)

    suspend fun moveItem(currentOrder: List<PlaylistEpisodeWithEpisode>, fromIndex: Int, toIndex: Int) {
        if (fromIndex !in currentOrder.indices || toIndex !in currentOrder.indices) return
        val a = currentOrder[fromIndex]
        val b = currentOrder[toIndex]
        playlistDao.swapPositions(a.playlistId, a.episode.id, a.playlistPosition, b.episode.id, b.playlistPosition)
    }
}

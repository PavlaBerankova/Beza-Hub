package com.berpod.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "playlist_episodes",
    primaryKeys = ["playlistId", "episodeId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = EpisodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["episodeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["episodeId"]),
        Index(value = ["playlistId", "position"])
    ]
)
data class PlaylistEpisodeCrossRef(
    val playlistId: Long,
    val episodeId: Long,
    val position: Int,
    val dateAdded: Long
)

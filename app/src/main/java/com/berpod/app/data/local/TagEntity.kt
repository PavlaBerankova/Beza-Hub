package com.berpod.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // NOCASE makes the unique index and `name = :name` lookups case-insensitive (ASCII letters only).
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String
)

package com.flashbox.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,           // http(s) URL or file:// path to swf
    val isLocal: Boolean,
    val engine: String,        // engine id used
    val visitedAt: Long
)

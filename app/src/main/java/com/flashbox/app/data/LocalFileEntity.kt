package com.flashbox.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A user-added local SWF file or a scanned folder root. */
@Entity(tableName = "local_files")
data class LocalFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val path: String,          // absolute path or content uri
    val isFolder: Boolean,
    val addedAt: Long
)

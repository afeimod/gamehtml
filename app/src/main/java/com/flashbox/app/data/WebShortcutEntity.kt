package com.flashbox.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Default and user-defined web shortcuts shown on the home page. */
@Entity(tableName = "web_shortcuts")
data class WebShortcutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val mode: String,          // desktop / compat / mobile
    val isDefault: Boolean,
    val sortOrder: Int
)

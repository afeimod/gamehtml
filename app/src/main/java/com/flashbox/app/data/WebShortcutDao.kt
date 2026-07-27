package com.flashbox.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WebShortcutDao {
    @Query("SELECT * FROM web_shortcuts ORDER BY sortOrder ASC, id ASC")
    fun getAll(): List<WebShortcutEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: WebShortcutEntity): Long

    @Query("DELETE FROM web_shortcuts WHERE id = :id")
    fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM web_shortcuts WHERE isDefault = 1")
    fun defaultsCount(): Int
}

package com.flashbox.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY visitedAt DESC")
    fun getAll(): List<HistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: HistoryEntity): Long

    @Query("DELETE FROM history WHERE id = :id")
    fun delete(id: Long)

    @Query("DELETE FROM history")
    fun clearAll()

    @Query("SELECT COUNT(*) FROM history WHERE url = :url")
    fun countByUrl(url: String): Int
}

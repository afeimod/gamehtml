package com.flashbox.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getAll(): List<FavoriteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: FavoriteEntity): Long

    @Query("DELETE FROM favorites WHERE id = :id")
    fun delete(id: Long)

    @Query("DELETE FROM favorites WHERE url = :url")
    fun deleteByUrl(url: String)

    @Query("SELECT COUNT(*) FROM favorites WHERE url = :url")
    fun isFavorite(url: String): Int
}

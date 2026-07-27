package com.flashbox.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LocalFileDao {
    @Query("SELECT * FROM local_files ORDER BY isFolder DESC, name ASC")
    fun getAll(): List<LocalFileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: LocalFileEntity): Long

    @Query("DELETE FROM local_files WHERE id = :id")
    fun delete(id: Long)

    @Query("DELETE FROM local_files WHERE path = :path")
    fun deleteByPath(path: String)

    @Query("SELECT COUNT(*) FROM local_files WHERE path = :path")
    fun exists(path: String): Int

    @Query("DELETE FROM local_files")
    fun clearAll()
}

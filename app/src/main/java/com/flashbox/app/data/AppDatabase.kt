package com.flashbox.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        HistoryEntity::class,
        FavoriteEntity::class,
        LocalFileEntity::class,
        WebShortcutEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun localFileDao(): LocalFileDao
    abstract fun webShortcutDao(): WebShortcutDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "flashbox.db"
                )
                    .fallbackToDestructiveMigration()
                    .allowMainThreadQueries() // small local DB; queries are lightweight
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

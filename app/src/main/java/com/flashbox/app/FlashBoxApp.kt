package com.flashbox.app

import android.app.Application
import com.flashbox.app.data.AppDatabase
import com.flashbox.app.data.SettingsRepository

class FlashBoxApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        @Volatile
        lateinit var instance: FlashBoxApp
            private set
    }
}

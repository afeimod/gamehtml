package com.flashbox.app

import android.app.Application

class FlashBoxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
    }
}

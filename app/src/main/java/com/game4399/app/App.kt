package com.game4399.app

import android.app.Application
import com.game4399.app.data.PrefsManager

/**
 * 应用入口。初始化全局偏好与 WebView 安全浏览。
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        PrefsManager.init(this)
    }

    companion object {
        @Volatile
        lateinit var instance: App
            private set
    }
}

package com.game4399.app

import android.app.Application
import android.content.Context
import android.os.StrictMode
import android.webkit.WebView
import com.game4399.app.data.Prefs

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        Prefs.init(this)

        // 部分老 webview 在主线程磁盘 IO 会 ANR，宽容
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder().permitAll().build()
            )
        }

        // 预创建 WebView 让系统加好 chromium
        try {
            WebView.setWebContentsDebuggingEnabled(BuildConfig.ENABLE_WEBVIEW_DEBUG)
        } catch (_: Throwable) {}
    }

    companion object {
        lateinit var instance: App
            private set

        val ctx: Context get() = instance.applicationContext
    }
}

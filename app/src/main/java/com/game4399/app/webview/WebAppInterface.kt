package com.game4399.app.webview

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.game4399.app.data.FavoriteStore

/**
 * 注入到 WebView 的 JS 接口（window.Android）。
 * 提供给网页调用原生的能力：Toast、收藏、震动、获取当前 URL 等。
 */
class WebAppInterface(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun toast(msg: String?) {
        handler.post { Toast.makeText(context, msg ?: "", Toast.LENGTH_SHORT).show() }
    }

    @JavascriptInterface
    fun vibrate(durationMs: Int) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        @Suppress("DEPRECATION")
        vibrator.vibrate(durationMs.coerceIn(1, 500).toLong())
    }

    @JavascriptInterface
    fun addFavorite(url: String?, title: String?) {
        if (url.isNullOrEmpty()) return
        FavoriteStore.add(url, title ?: url)
        handler.post { Toast.makeText(context, "已加入收藏", Toast.LENGTH_SHORT).show() }
    }

    @JavascriptInterface
    fun log(tag: String?, msg: String?) {
        Log.d("WebApp:${tag ?: "JS"}", msg ?: "")
    }

    /**
     * 打开 SWF 播放器（WAFlash 检测脚本调用）。
     * 根据当前 Flash 引擎设置跳转到对应的播放器页面。
     */
    @JavascriptInterface
    fun openSwf(swfUrl: String?, pageUrl: String?) {
        if (swfUrl.isNullOrEmpty()) return
        Log.d("WebApp:WAFlash", "openSwf: $swfUrl (from: $pageUrl)")
        handler.post {
            val playerUrl = NavHelper.playerUrl(swfUrl, pageUrl, null)
            if (context is com.game4399.app.GameActivity) {
                val activity = context as com.game4399.app.GameActivity
                activity.loadSwfInWebView(playerUrl)
            }
        }
    }

    @JavascriptInterface
    fun finish() {
        if (context is Activity) handler.post { context.finish() }
    }
}

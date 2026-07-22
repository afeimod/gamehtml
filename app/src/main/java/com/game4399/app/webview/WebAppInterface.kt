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
 *
 * 注意：@JavascriptInterface 注解的方法是公开给不可信 JS 调用的，
 *       因此仅暴露必要能力，并做参数校验。
 */
class WebAppInterface(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())

    /** 弹 Toast（JS 调用） */
    @JavascriptInterface
    fun toast(msg: String?) {
        handler.post { Toast.makeText(context, msg ?: "", Toast.LENGTH_SHORT).show() }
    }

    /** 震动 30ms（游戏按键反馈） */
    @JavascriptInterface
    fun vibrate(durationMs: Int) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE)
            as android.os.Vibrator
        @Suppress("DEPRECATION")
        vibrator.vibrate(durationMs.coerceIn(1, 500).toLong())
    }

    /** 加入收藏（由网页按钮调用） */
    @JavascriptInterface
    fun addFavorite(url: String?, title: String?) {
        if (url.isNullOrEmpty()) return
        FavoriteStore.add(url, title ?: url)
        handler.post { Toast.makeText(context, "已加入收藏", Toast.LENGTH_SHORT).show() }
    }

    /** 打印日志到 logcat */
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
            if (context is android.app.Activity) {
                val webView = (context as? com.game4399.app.GameActivity)
                    ?.findViewById<android.webkit.WebView>(com.game4399.app.R.id.webView)
                webView?.loadUrl(playerUrl)
            }
        }
    }

    /** 退出当前 Activity（全屏播放器使用） */
    @JavascriptInterface
    fun finish() {
        if (context is Activity) handler.post { context.finish() }
    }
}

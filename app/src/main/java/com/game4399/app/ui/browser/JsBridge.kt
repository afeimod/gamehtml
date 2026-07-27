package com.game4399.app.ui.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.game4399.app.data.HistoryItem
import com.game4399.app.data.Prefs
import com.game4399.app.ui.settings.SettingsActivity
import android.widget.Toast

/**
 * Web 端的 window.AndroidBridge 实现。
 *
 * 注意：所有方法都必须在主线程中调用。
 * WebView 的 evaluateJavascript 由 addJavascriptInterface 注入。
 */
class JsBridge(
    private val ctx: Context,
    private val web: WebView
) {

    @JavascriptInterface
    fun openUrl(url: String) {
        web.post {
            if (url.startsWith("file:///android_asset/") || url.startsWith("file://") ||
                url.startsWith("http://") || url.startsWith("https://") || url.startsWith("content://")) {
                web.loadUrl(url)
            }
        }
    }

    @JavascriptInterface
    fun openFav() {
        // 简化：直接打开主页让用户管理
    }

    @JavascriptInterface
    fun engine(): String = Prefs.engine()

    @JavascriptInterface
    fun quality(): String = Prefs.quality()

    @JavascriptInterface
    fun aspect(): String = Prefs.aspect()

    @JavascriptInterface
    fun uiMode(): String = Prefs.uiMode()

    @JavascriptInterface
    fun pageZoom(): Float = Prefs.pageZoom()

    @JavascriptInterface
    fun setZoom(v: Float) {
        Prefs.setPageZoom(v)
        web.post {
            web.settings.textZoom = (v * 100).toInt().coerceIn(50, 200)
        }
    }

    @JavascriptInterface
    fun setUiMode(v: String) {
        Prefs.setUiMode(v)
    }

    @JavascriptInterface
    fun homePc(): String = Prefs.homePc()

    @JavascriptInterface
    fun homeMobile(): String = Prefs.homeMobile()

    @JavascriptInterface
    fun homeUrl(): String = if (Prefs.uiMode() == "pc") Prefs.homePc() else Prefs.homeMobile()

    @JavascriptInterface
    fun favoritesJson(): String = com.google.gson.Gson().toJson(Prefs.favorites())

    @JavascriptInterface
    fun historyJson(): String = com.google.gson.Gson().toJson(Prefs.history())

    @JavascriptInterface
    fun addFavorite(name: String, url: String) {
        Prefs.addFavorite(HistoryItem(name, url))
        web.post { Toast.makeText(ctx, "已加入收藏", Toast.LENGTH_SHORT).show() }
    }

    @JavascriptInterface
    fun clearHistory() {
        Prefs.clearHistory()
    }

    @JavascriptInterface
    fun notifyChange() {
        // 简单实现，调用方在收到 fav/history 变化时调用
    }

    @JavascriptInterface
    fun openSettings() {
        ctx.startActivity(Intent(ctx, SettingsActivity::class.java))
    }

    @JavascriptInterface
    fun close() {
        (ctx as? android.app.Activity)?.finish()
    }

    @JavascriptInterface
    fun toast(msg: String) {
        web.post { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
    }

    @JavascriptInterface
    fun onLoaded() {}

    @JavascriptInterface
    fun onError(msg: String) {}
}

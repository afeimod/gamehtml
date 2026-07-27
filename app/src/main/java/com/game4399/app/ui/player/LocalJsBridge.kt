package com.game4399.app.ui.player

import android.content.Context
import android.content.Intent
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.game4399.app.data.Prefs

class LocalJsBridge(
    private val ctx: Context,
    private val web: WebView
) {
    @JavascriptInterface
    fun engine(): String = Prefs.engine()

    @JavascriptInterface
    fun quality(): String = Prefs.quality()

    @JavascriptInterface
    fun aspect(): String = Prefs.aspect()

    @JavascriptInterface
    fun close() {
        (ctx as? android.app.Activity)?.finish()
    }

    @JavascriptInterface
    fun onLoaded() {
        web.post { (ctx as? android.app.Activity)?.runOnUiThread { /* hook */ } }
    }

    @JavascriptInterface
    fun onError(msg: String) {}

    @JavascriptInterface
    fun openSettings() {
        ctx.startActivity(Intent(ctx, com.game4399.app.ui.settings.SettingsActivity::class.java))
    }
}

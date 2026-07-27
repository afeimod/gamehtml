package com.flashbox.app.web

import android.webkit.JavascriptInterface

/**
 * Javascript interface exposed to web content.
 *
 * Methods here are called from JS via `AndroidBridge.<method>()`.
 * Android -> JS communication uses webView.evaluateJavascript() directly.
 */
class JsBridge {

    @Volatile var pageTitle: String = ""
    var onTitleChanged: ((String) -> Unit)? = null
    var onReady: (() -> Unit)? = null
    var onLog: ((String) -> Unit)? = null

    @JavascriptInterface
    fun reportTitle(title: String) {
        pageTitle = title
        onTitleChanged?.invoke(title)
    }

    @JavascriptInterface
    fun ready() {
        onReady?.invoke()
    }

    @JavascriptInterface
    fun log(msg: String) {
        onLog?.invoke(msg)
    }
}

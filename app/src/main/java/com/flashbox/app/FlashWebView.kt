package com.flashbox.app

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.webkit.WebSettings
import android.webkit.WebView

class FlashWebView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    val uaMobile =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    val uaDesktop =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    val uaCompat =
        "Mozilla/5.0 (Linux; Android 13; Tablet) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    @SuppressLint("SetJavaScriptEnabled")
    fun configure() {
        with(settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(true)
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            // App cache for offline support (deprecated but still functional pre-33)
            try {
                setAppCacheEnabled(true)
                setAppCachePath(context.cacheDir.absolutePath + "/webcache")
            } catch (_: Throwable) {}
            try { setGeolocationEnabled(true) } catch (_: Throwable) {}
        }
        isVerticalScrollBarEnabled = true
        isHorizontalScrollBarEnabled = true
        isFocusable = true
        isFocusableInTouchMode = true
        try { WebView.setWebContentsDebuggingEnabled(true) } catch (_: Throwable) {}
    }

    fun applyUa(mode: String) {
        settings.userAgentString = when (mode) {
            "desktop" -> uaDesktop
            "compat" -> uaCompat
            else -> uaMobile
        }
    }

    fun applyZoom(percent: Int) {
        // text zoom scales page text; our app shell also listens for a zoom event
        settings.textZoom = percent.coerceIn(25, 400)
        evaluateJavascript(
            "(function(){try{document.documentElement.style.zoom=($percent/100);}catch(e){}})();", null
        )
    }
}

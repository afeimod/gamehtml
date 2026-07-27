package com.flashbox.app.web

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.flashbox.app.data.SettingsRepository
import com.flashbox.app.engine.EngineAssets
import com.flashbox.app.engine.EngineType
import java.io.ByteArrayInputStream

/**
 * Advanced WebView preconfigured for Flash browsing & playback:
 *  - desktop/compat/mobile user agents
 *  - global page zoom (text + initial scale)
 *  - DOM/local storage, mixed content, caching
 *  - ad-blocking request interception
 *  - key event dispatching into the page
 */
class FlashWebView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : WebView(context, attrs), KeyDispatchBridge {

    val jsBridge = JsBridge()
    val adblocker = Adblocker()
    private lateinit var settingsRepo: SettingsRepository

    var onProgress: ((Int) -> Unit)? = null
    var onTitle: ((String) -> Unit)? = null
    var onUrlLoaded: ((String) -> Unit)? = null
    var onPageFinishedCb: ((String) -> Unit)? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun init(repo: SettingsRepository) {
        settingsRepo = repo
        adblocker.enabled = repo.adblockEnabled
        adblocker.load(context)

        with(this.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = if (repo.cacheEnabled)
                WebSettings.LOAD_DEFAULT else WebSettings.LOAD_NO_CACHE
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            javaScriptCanOpenWindowsAutomatically = true
            setGeolocationEnabled(true)
            blockNetworkLoads = false
            // Force-disable algorithmic darkening so flash canvases render correctly
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(this, false)
            }
        }

        addJavascriptInterface(jsBridge, "AndroidBridge")
        webViewClient = FlashWebViewClient(this, adblocker, settingsRepo)
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                onProgress?.invoke(newProgress)
            }
            override fun onReceivedTitle(view: WebView?, title: String?) {
                onTitle?.invoke(title ?: "")
            }
        }
    }

    fun applyMode(mode: WebMode) {
        when (mode) {
            WebMode.DESKTOP -> {
                settings.userAgentString = DESKTOP_UA
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
            }
            WebMode.COMPAT -> {
                settings.userAgentString = DESKTOP_UA
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = false
            }
            WebMode.MOBILE -> {
                settings.userAgentString = MOBILE_UA
                settings.useWideViewPort = false
                settings.loadWithOverviewMode = true
            }
        }
    }

    fun applyZoom(percent: Int) {
        settings.textZoom = percent
        // also nudge initial scale for canvas-based content
        setInitialScale(if (percent == 100) 0 else percent)
    }

    // ---- KeyDispatchBridge ----
    override fun sendKeyDown(key: String, code: Int) {
        val js = "(function(){var t=document.activeElement||document.body;" +
                "var o={bubbles:true,cancelable:true,key:${quote(key)},code:${quote(key)},keyCode:$code,which:$code};" +
                "t.dispatchEvent(new KeyboardEvent('keydown',o));" +
                "t.dispatchEvent(new KeyboardEvent('keypress',o));})();"
        post { evaluateJavascript(js, null) }
    }

    override fun sendKeyUp(key: String, code: Int) {
        val js = "(function(){var t=document.activeElement||document.body;" +
                "var o={bubbles:true,cancelable:true,key:${quote(key)},code:${quote(key)},keyCode:$code,which:$code};" +
                "t.dispatchEvent(new KeyboardEvent('keyup',o));})();"
        post { evaluateJavascript(js, null) }
    }

    private fun quote(s: String): String = "\"" + s.replace("\"", "\\\"") + "\""

    companion object {
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}

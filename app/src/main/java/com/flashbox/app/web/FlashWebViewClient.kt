package com.flashbox.app.web

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.flashbox.app.data.SettingsRepository
import com.flashbox.app.engine.EngineAssets
import com.flashbox.app.engine.EngineType
import java.io.ByteArrayInputStream

/**
 * WebViewClient that:
 *  - intercepts and blocks ad requests
 *  - upgrades http->https where appropriate (kept permissive for legacy sites)
 *  - injects the Ruffle polyfill + ad-hiding CSS after page load
 *  - never blocks navigation (no "unopenable" pages)
 */
class FlashWebViewClient(
    private val webView: FlashWebView,
    private val adblocker: Adblocker,
    private val settings: SettingsRepository
) : WebViewClient() {

    private val emptyResponse by lazy {
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }

    override fun shouldInterceptRequest(
        view: WebView?, request: WebResourceRequest?
    ): WebResourceResponse? {
        val url = request?.url?.toString() ?: return null
        if (adblocker.shouldBlock(url)) {
            return emptyResponse
        }
        return null
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        // keep all http/https/file navigation inside the webview
        return false
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        // be permissive so legacy https flash sites still load
        handler?.proceed()
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        url?.let { webView.onUrlLoaded?.invoke(it) }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        injectScripts()
        webView.onPageFinishedCb?.invoke(url ?: "")
        // report title back
        view?.evaluateJavascript(
            "(function(){try{AndroidBridge.reportTitle(document.title||location.href);}catch(e){}})();", null)
    }

    private fun injectScripts() {
        val ctx = webView.context
        // Ruffle polyfill (auto-replaces <object>/<embed> flash on the page)
        val ruffleDir = EngineAssets.engineDir(ctx, EngineType.RUFFLE)
        val publicPath = ruffleDir.absolutePath + "/"
        val polyfill = try {
            EngineAssets.injectDir(ctx).listFiles()?.find { it.name == "ruffle_polyfill.js" }
                ?.readText()?.replace("__RUFFLE_PATH__", "file://$publicPath")
        } catch (e: Exception) { null }

        if (polyfill != null) {
            webView.evaluateJavascript(polyfill, null)
        }
        // ad-hiding CSS
        val css = try {
            EngineAssets.injectDir(ctx).listFiles()?.find { it.name == "adblock.css" }?.readText()
        } catch (e: Exception) { null }
        if (css != null) {
            val js = "(function(){var s=document.createElement('style');s.textContent=" +
                    jsQuote(css) + ";document.head.appendChild(s);})();"
            webView.evaluateJavascript(js, null)
        }
        // desktop compat tweaks (force flash focus etc.)
        val compat = try {
            EngineAssets.injectDir(ctx).listFiles()?.find { it.name == "desktop_compat.js" }?.readText()
        } catch (e: Exception) { null }
        if (compat != null) {
            webView.evaluateJavascript(compat, null)
        }
    }

    private fun jsQuote(s: String): String {
        val b = StringBuilder("\"")
        for (c in s) when (c) {
            '\\' -> b.append("\\\\")
            '"' -> b.append("\\\"")
            '\n' -> b.append("\\n")
            '\r' -> b.append("\\r")
            '\t' -> b.append("\\t")
            else -> b.append(c)
        }
        b.append("\"")
        return b.toString()
    }
}

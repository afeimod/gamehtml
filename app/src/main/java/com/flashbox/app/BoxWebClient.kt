package com.flashbox.app

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.io.ByteArrayInputStream

/**
 * Central WebViewClient:
 *  - serves bundled assets & local SWF files via WebViewAssetLoader (https://app.local)
 *  - blocks ad/Tracker requests when adblock is on
 *  - injects the selected engine + virtual controls into online Flash pages
 *  - recovers from load errors so no page is ever "unopenable"
 */
class BoxWebClient(
    private val assetLoader: WebViewAssetLoader,
    private val host: BoxWebClientHost
) : WebViewClient() {

    interface BoxWebClientHost {
        fun adblockEnabled(): Boolean
        fun currentEngine(): String
        fun engineConfig(engine: String): JSONObject
        fun controlsJson(): String
        fun pageMode(): String
        fun zoomPercent(): Int
        fun zoomJs(): String
        fun runOnUi(r: Runnable)
        fun onNavChanged(url: String?, title: String?)
        fun onProgress(p: Int)
        fun onError(url: String?, code: Int, desc: String?)
        fun isAppShell(url: String?): Boolean
        fun isPlayer(url: String?): Boolean
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val url = request?.url ?: return null
        // ad blocking (skip for our own origin)
        if (host.adblockEnabled() && (url.scheme == "https" || url.scheme == "http")) {
            val hostStr = url.host ?: ""
            if (hostStr.isNotEmpty() && !hostStr.endsWith("app.local") && isAdHost(hostStr, url.toString())) {
                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
            }
        }
        // local assets + library files
        return assetLoader.shouldInterceptRequest(url)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        // route direct .swf links to our player
        if (url.lowercase().endsWith(".swf")) {
            host.runOnUi { view?.loadUrl("https://app.local/player.html?src=" + Uri.encode(url)) }
            return true
        }
        return false
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        host.onProgress(5)
        host.onNavChanged(url, null)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        host.onProgress(100)
        view?.let { v ->
            v.evaluateJavascript(host.zoomJs(), null)
            if (url != null && !host.isAppShell(url)) {
                injectIntoPage(v, url)
            }
        }
        host.onNavChanged(url, view?.title)
    }

    override fun onReceivedError(
        view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?
    ) {
        if (request?.isForMainFrame == true) {
            host.onError(request.url?.toString(), error?.errorCode ?: -1, error?.description?.toString())
        }
    }

    override fun onReceivedHttpError(
        view: WebView?, request: WebResourceRequest?, errorResponse: android.webkit.WebResourceResponse?
    ) {
        if (request?.isForMainFrame == true) {
            host.onError(request.url?.toString(), errorResponse?.statusCode ?: -1, errorResponse?.reasonPhrase)
        }
    }

    override fun onReceivedSslError(
        view: WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?
    ) {
        // proceed so https pages with broken certs still open (compat-first)
        handler?.proceed()
    }

    /** Inject engine + controls + ad-block css into a remote/online page. */
    private fun injectIntoPage(view: WebView, url: String) {
        if (host.isPlayer(url)) return // player page bootstraps itself
        val engine = host.currentEngine()
        val cfg = host.engineConfig(engine)
        val payload = JSONObject().apply {
            put("engine", engine)
            put("config", cfg)
            put("controls", JSONObject(host.controlsJson()))
            put("desktopMode", host.pageMode() == "desktop" || host.pageMode() == "compat")
            put("origin", "https://app.local")
            put("adblock", host.adblockEnabled())
        }.toString()
        val js = """
            (function(){
              if(window.__FB_INJECTED__) return; window.__FB_INJECTED__=true;
              try{ window.__FB_INJECT__ = JSON.parse(${jsString(payload)}); }catch(e){ window.__FB_INJECT__={engine:'ruffle'}; }
              var s=document.createElement('script');
              s.src='https://app.local/inject.js';
              s.onload=function(){};
              document.head.appendChild(s);
            })();
        """.trimIndent()
        view.evaluateJavascript(js, null)
    }

    // ---- ad host matching (substring + domain suffix) ----
    private val adPatterns = listOf(
        "doubleclick.net","googlesyndication.com","googleadservices.com","googletagservices.com",
        "google-analytics.com","googletagmanager.com","adservice.google.","adnxs.com","criteo.com",
        "scorecardresearch.com","moatads.com","adsystem.com","amazon-adsystem.com","pubmatic.com",
        "rubiconproject.com","openx.net","adroll.com","taboola.com","outbrain.com","quantserve.com",
        "yieldlab","casalemedia","adtech","smartadserver","teads.tv","trafficjunky","exoclick",
        "propellerads","popads","popcash","admaven","yllix","media.net","contextweb","districtm",
        "4399.com/my/", "/adimages/","/adbanner","/advertisement","/getad","/show_ad","/adserver/",
        "cpro.baidustatic.com","pos.baidu.com","cnzz.com","umeng","tanx.com","alimama","mmstat.com"
    )
    private fun isAdHost(hostStr: String, fullUrl: String): Boolean {
        if (adPatterns.any { hostStr.endsWith(it) || hostStr.contains(it) }) return true
        return adPatterns.any { fullUrl.contains(it) }
    }

    private fun jsString(s: String): String {
        // safely embed a JS string literal
        val sb = StringBuilder("'")
        for (ch in s) when (ch) {
            '\\' -> sb.append("\\\\")
            '\'' -> sb.append("\\'")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            else -> sb.append(ch)
        }
        sb.append("'")
        return sb.toString()
    }
}

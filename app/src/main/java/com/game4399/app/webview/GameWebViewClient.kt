package com.game4399.app.webview

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.game4399.app.data.PrefsManager
import java.io.ByteArrayInputStream

/**
 * 游戏页面 WebView 客户端：
 * 1. onPageFinished 注入 Ruffle（Flash 支持）
 * 2. 拦截 .swf 链接 → 跳转内置播放器页
 * 3. shouldInterceptRequest：本地 wasm 返回正确 MIME；广告拦截
 * 4. 错误回调
 */
open class GameWebViewClient(
    private val callback: Callback
) : WebViewClient() {

    interface Callback {
        fun onPageStarted(url: String?)
        fun onPageFinished(url: String?)
        fun onProgress(progress: Int)
        fun onError(url: String?, errorCode: Int, description: String?)
        fun onSwfIntercepted(swfUrl: String, pageUrl: String)
        fun shouldInjectRuffle(url: String?): Boolean
    }

    /** 常见广告域名（可扩展） */
    private val adHosts = setOf(
        "googleads.g.doubleclick.net", "pagead2.googlesyndication.com",
        "ad.4399.com", "stat.4399.com", "analytics.4399.com"
    )

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url?.toString() ?: return false
        // 拦截 .swf 直链 → 用 Ruffle 内置播放器
        if (url.endsWith(".swf", ignoreCase = true)) {
            callback.onSwfIntercepted(url, view.url ?: url)
            return true
        }
        return false
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url?.toString() ?: return null
        // 广告拦截
        if (PrefsManager.isBlockAds && adHosts.any { url.contains(it) }) {
            return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
        }
        // 本地 wasm：确保返回 application/wasm MIME（Ruffle 必需）
        if (PrefsManager.flashCdn == "local" && url.endsWith(".wasm", ignoreCase = true)) {
            try {
                val name = request.url?.lastPathSegment ?: return null
                val input = view.context.assets.open("ruffle/$name")
                return WebResourceResponse("application/wasm", null, input)
            } catch (e: Exception) { /* fallthrough */ }
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        callback.onPageStarted(url)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        // 注入 Ruffle（仅当启用 Flash 且当前页适合注入）
        if (PrefsManager.isFlashEnabled && callback.shouldInjectRuffle(url)) {
            view?.evaluateJavascript(RuffleInjector.fullInjection(), null)
        }
        // 注入一点 CSS：屏蔽 4399 PC 版的部分广告与边栏，让游戏区更突出
        view?.evaluateJavascript(CSS_INJECTION, null)
        callback.onPageFinished(url)
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        // 4399 部分资源证书问题，允许继续（仅游戏场景）
        handler?.proceed()
    }

    override fun onReceivedError(
        view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            callback.onError(
                request.url?.toString(),
                error?.errorCode ?: -1,
                error?.description?.toString()
            )
        }
    }

    /** 旧版错误回调兼容 */
    @Deprecated("Deprecated in Java")
    override fun onReceivedError(
        view: WebView?, errorCode: Int, description: String?, failingUrl: String?
    ) {
        super.onReceivedError(view, errorCode, description, failingUrl)
        callback.onError(failingUrl, errorCode, description)
    }

    companion object {
        /** 适度的页面美化（屏蔽部分广告位 / 居中游戏区） */
        private const val CSS_INJECTION = """
            (function(){
              if (window.__cssInjected) return; window.__cssInjected = true;
              var s = document.createElement('style');
              s.textContent = [
                '.advertise,.ad_box,[id^="ad_"],[class*="advert"]{display:none!important;}',
                'body{background:#000!important;}',
                'object,embed{max-width:100%!important;}'
              ].join('\n');
              document.head.appendChild(s);
            })();
        """
    }
}

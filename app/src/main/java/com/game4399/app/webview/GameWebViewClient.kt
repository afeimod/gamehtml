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
 * 1. onPageFinished 注入 Flash 引擎（Ruffle / swf2js）
 * 2. 拦截 .swf 链接 → 跳转内置播放器页
 * 3. shouldInterceptRequest：
 *    - 拦截 flash.local 虚拟域名，从 assets 返回引擎 JS/wasm 文件
 *    - 广告拦截
 * 4. 错误回调（仅主框架错误才显示错误页）
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

    /** 常见广告域名 */
    private val adHosts = setOf(
        "googleads.g.doubleclick.net", "pagead2.googlesyndication.com",
        "ad.4399.com", "stat.4399.com", "analytics.4399.com"
    )

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url?.toString() ?: return false
        if (url.endsWith(".swf", ignoreCase = true)) {
            callback.onSwfIntercepted(url, view.url ?: url)
            return true
        }
        return false
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url?.toString() ?: return null

        // 1. 广告拦截
        if (PrefsManager.isBlockAds && adHosts.any { url.contains(it) }) {
            return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
        }

        // 2. 拦截 flash.local 虚拟域名：从 assets 返回引擎文件
        //    路径格式：https://flash.local/ruffle/ruffle.js
        //             https://flash.local/ruffle/core.ruffle.xxx.js
        //             https://flash.local/ruffle/xxx.wasm
        //             https://flash.local/swf2js/swf2js.js
        if (url.contains("flash.local")) {
            try {
                val assetPath = url.substringAfter("flash.local/").substringBefore("?")
                val input = view.context.assets.open(assetPath)
                val (mime, charset) = when {
                    assetPath.endsWith(".wasm") -> "application/wasm" to null
                    assetPath.endsWith(".js") -> "application/javascript" to "UTF-8"
                    assetPath.endsWith(".html") -> "text/html" to "UTF-8"
                    assetPath.endsWith(".css") -> "text/css" to "UTF-8"
                    assetPath.endsWith(".data") -> "application/octet-stream" to null
                    else -> "application/octet-stream" to null
                }
                // 添加 CORS 头：允许任何页面跨域访问 flash.local 资源
                // Ruffle 内部用 fetch() 加载 core.js 和 .wasm，需要 CORS 头
                val headers = mapOf(
                    "Access-Control-Allow-Origin" to "*",
                    "Cache-Control" to "no-cache"
                )
                return WebResourceResponse(mime, charset, 200, "OK", headers, input)
            } catch (e: Exception) {
                android.util.Log.w("GameWebViewClient", "flash.local asset not found: $url", e)
            }
        }

        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        callback.onPageStarted(url)
        // 尽早注入：屏蔽"没有 Flash 插件"提示 + 预置引擎配置
        if (PrefsManager.isFlashEnabled && callback.shouldInjectRuffle(url)) {
            view?.evaluateJavascript(RuffleInjector.configScript(), null)
            view?.evaluateJavascript(FLASH_HIDE_SCRIPT, null)
        }
        view?.evaluateJavascript(VIEWPORT_SCRIPT, null)
    }

    override fun onPageCommitVisible(view: WebView?, url: String?) {
        super.onPageCommitVisible(view, url)
        // DOM 已构建：注入引擎加载器
        if (PrefsManager.isFlashEnabled && callback.shouldInjectRuffle(url)) {
            view?.evaluateJavascript(RuffleInjector.loaderScript(), null)
            view?.evaluateJavascript(FLASH_HIDE_SCRIPT, null)
        }
        view?.evaluateJavascript(VIEWPORT_SCRIPT, null)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        // 兜底注入 + 触发 polyfill
        if (PrefsManager.isFlashEnabled && callback.shouldInjectRuffle(url)) {
            view?.evaluateJavascript(RuffleInjector.fullInjection(), null)
        }
        view?.evaluateJavascript(CSS_INJECTION, null)
        callback.onPageFinished(url)
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        handler?.proceed()
    }

    /**
     * 新版错误回调（API 23+）：仅主框架错误才通知 callback。
     * 子资源（图片/脚本/CSS）加载失败不会显示错误页。
     */
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

    /**
     * 废弃版错误回调：minSdk=23 时不会调用，但部分 OEM WebView 可能仍会调用。
     * 不再触发 callback.onError，避免子资源错误误显示错误页。
     */
    @Deprecated("Deprecated in Java")
    override fun onReceivedError(
        view: WebView?, errorCode: Int, description: String?, failingUrl: String?
    ) {
        super.onReceivedError(view, errorCode, description, failingUrl)
        // 不调用 callback.onError，新版回调已处理主框架错误
    }

    companion object {
        private const val VIEWPORT_SCRIPT = """
            (function(){
              var meta = document.querySelector('meta[name="viewport"]');
              if (!meta) {
                meta = document.createElement('meta');
                meta.name = 'viewport';
                document.head.appendChild(meta);
              }
              meta.content = 'width=device-width, initial-scale=0.4, minimum-scale=0.1, maximum-scale=5.0, user-scalable=yes';
            })();
        """

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

        private const val FLASH_HIDE_SCRIPT = """
            (function(){
              if (window.__flashHideInjected) return; window.__flashHideInjected = true;
              try {
                Object.defineProperty(navigator, 'plugins', {
                  get: function() {
                    var arr = [];
                    arr.namedItem = function(name) { return name === 'Shockwave Flash' ? { name: name } : null; };
                    arr.refresh = function() {};
                    arr.item = function(i) { return arr[i] || null; };
                    return arr;
                  }
                });
              } catch(e) {}
              function hideFlashTips() {
                var selectors = [
                  '[class*="noflash"]','[id*="noflash"]',
                  '[class*="no-flash"]','[id*="no-flash"]',
                  '.flash_tip','.flash-tip',
                  '#flash_tip','#flash-tip',
                  '.prompt-flash','.prompt_flash'
                ];
                var sel = selectors.join(',');
                document.querySelectorAll(sel).forEach(function(el){
                  el.style.display = 'none';
                });
              }
              hideFlashTips();
              if (window.MutationObserver) {
                var mo = new MutationObserver(function(){ hideFlashTips(); });
                try { mo.observe(document.documentElement, {childList:true, subtree:true}); } catch(e) {}
              }
            })();
        """
    }
}

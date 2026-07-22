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
 * 1. onPageFinished 注入 Flash 引擎（Ruffle / swf2js / WAFlash）
 * 2. 拦截 .swf 链接 → 跳转内置播放器页
 * 3. shouldInterceptRequest：拦截 flash.local 虚拟域名，从 assets 返回引擎文件
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

        val isFlashPage = PrefsManager.isFlashEnabled && callback.shouldInjectRuffle(url)

        if (isFlashPage) {
            // 注入引擎配置 + 隐藏 Flash 提示
            view?.evaluateJavascript(RuffleInjector.configScript(), null)
            view?.evaluateJavascript(FLASH_HIDE_SCRIPT, null)

            // WAFlash：注入 SWF 检测+跳转脚本（WAFlash 用独立播放器页面）
            if (PrefsManager.flashEngine == "waflash") {
                view?.evaluateJavascript(WAFLASH_DETECT_SCRIPT, null)
            }
        }

        // 仅对 4399 页面注入 viewport 调整
        if (url != null && url.contains("4399.com")) {
            view?.evaluateJavascript(VIEWPORT_SCRIPT, null)
        }
    }

    override fun onPageCommitVisible(view: WebView?, url: String?) {
        super.onPageCommitVisible(view, url)
        val isFlashPage = PrefsManager.isFlashEnabled && callback.shouldInjectRuffle(url)
        if (isFlashPage) {
            view?.evaluateJavascript(RuffleInjector.loaderScript(), null)
            view?.evaluateJavascript(FLASH_HIDE_SCRIPT, null)
            // WAFlash 兜底检测
            if (PrefsManager.flashEngine == "waflash") {
                view?.evaluateJavascript(WAFLASH_DETECT_SCRIPT, null)
            }
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        val isFlashPage = PrefsManager.isFlashEnabled && callback.shouldInjectRuffle(url)
        if (isFlashPage) {
            // Ruffle / swf2js：兜底注入
            view?.evaluateJavascript(RuffleInjector.fullInjection(), null)
            // WAFlash：最终检测
            if (PrefsManager.flashEngine == "waflash") {
                view?.evaluateJavascript(WAFLASH_DETECT_SCRIPT, null)
            }
        }
        // 仅对 4399 Flash 页面注入 CSS（不影响其他页面）
        if (isFlashPage) {
            view?.evaluateJavascript(CSS_INJECTION, null)
        }
        callback.onPageFinished(url)
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        handler?.proceed()
    }

    /**
     * 新版错误回调（API 23+）：仅主框架错误才通知 callback。
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
     * 废弃版错误回调：不触发 callback.onError，避免子资源错误误显示错误页。
     */
    @Deprecated("Deprecated in Java")
    override fun onReceivedError(
        view: WebView?, errorCode: Int, description: String?, failingUrl: String?
    ) {
        super.onReceivedError(view, errorCode, description, failingUrl)
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
                document.querySelectorAll(selectors.join(',')).forEach(function(el){
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

        /**
         * WAFlash SWF 检测脚本：监控页面上的 <object>/<embed> Flash 元素，
         * 提取 SWF URL 后跳转到 waflash.html 播放器页面。
         * 4399 页面可能动态创建 Flash 元素，所以用 MutationObserver 持续监控。
         */
        private const val WAFLASH_DETECT_SCRIPT = """
            (function(){
              if (window.__waflashDetect) return;
              window.__waflashDetect = true;
              var redirected = false;

              function extractSwfUrl(el) {
                if (!el) return null;
                var src = el.getAttribute('data') || el.getAttribute('src') ||
                          el.getAttribute('movie') || el.getAttribute('url') || '';
                if (src && /\.swf/i.test(src)) return src;
                // 检查 <param name="movie" value="xxx.swf">
                var params = el.querySelectorAll('param[name="movie"], param[name="src"]');
                for (var i = 0; i < params.length; i++) {
                  var v = params[i].getAttribute('value') || '';
                  if (/\.swf/i.test(v)) return v;
                }
                return null;
              }

              function findAndRedirect() {
                if (redirected) return;
                // 查找所有 Flash 元素
                var elements = document.querySelectorAll(
                  'object[type="application/x-shockwave-flash"], ' +
                  'embed[type="application/x-shockwave-flash"], ' +
                  'object[data$=".swf" i], embed[src$=".swf" i], ' +
                  'object[classid*="D27CDB6E"], object[classid*="d27cdb6e"]'
                );
                for (var i = 0; i < elements.length; i++) {
                  var swfUrl = extractSwfUrl(elements[i]);
                  if (swfUrl) {
                    redirected = true;
                    console.log('[WAFlash] 检测到 SWF: ' + swfUrl);
                    // 获取基准 URL（用于解析相对路径）
                    var baseUrl = window.location.href;
                    // 构造绝对 URL
                    try {
                      swfUrl = new URL(swfUrl, baseUrl).href;
                    } catch(e) {}
                    // 通过 Android 接口跳转到 WAFlash 播放器
                    if (window.Android && window.Android.openSwf) {
                      window.Android.openSwf(swfUrl, baseUrl);
                    } else {
                      // 回退：直接跳转
                      var playerUrl = 'file:///android_asset/waflash.html?swf=' + encodeURIComponent(swfUrl);
                      window.location.href = playerUrl;
                    }
                    return;
                  }
                }
              }

              // 立即检测
              findAndRedirect();

              // 持续监控 DOM 变化（4399 可能动态创建 Flash 元素）
              if (window.MutationObserver) {
                var observer = new MutationObserver(function(mutations) {
                  if (redirected) {
                    observer.disconnect();
                    return;
                  }
                  findAndRedirect();
                });
                try {
                  observer.observe(document.documentElement, {childList: true, subtree: true});
                } catch(e) {}
                // 10 秒后停止监控
                setTimeout(function() { observer.disconnect(); }, 10000);
              }
            })();
        """
    }
}

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
            return interceptAsset(view, url.substringAfter("flash.local/").substringBefore("?"))
        }

        // 3. 拦截 file:///android_asset/waflash/ 请求
        if (url.startsWith("file:///android_asset/waflash/")) {
            val assetPath = url.removePrefix("file:///android_asset/").substringBefore("?")
            return interceptAsset(view, assetPath)
        }

        // 4. 拦截 SWF 文件请求：WAFlash 从 flash.local 页面用 fetch() 加载远程 SWF
        //    会被 CORS/混合内容策略阻止，这里原生下载后带 CORS 头返回
        //    支持 .swf 扩展名 + 4399 的 dw-XX 格式（非标准 SWF URL）
        val isSwfRequest = url.endsWith(".swf", ignoreCase = true) ||
            url.contains(".swf?", ignoreCase = true) ||
            (url.contains("4399.com") && (url.contains("/dw-") || url.contains("flash_tm3") || url.contains("flash20")))
        if (isSwfRequest) {
            return interceptSwf(url)
        }

        return super.shouldInterceptRequest(view, request)
    }

    /** 原生下载 SWF 文件，返回带 CORS 头的响应（含重试） */
    private fun interceptSwf(url: String): WebResourceResponse? {
        var lastError: Exception? = null
        for (attempt in 1..3) {
            try {
                android.util.Log.d("GameWebViewClient", "拦截 SWF 请求 (尝试 $attempt): $url")
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 20000
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                if (url.contains("4399.com")) {
                    conn.setRequestProperty("Referer", "https://www.4399.com/")
                }
                conn.instanceFollowRedirects = true
                conn.connect()
                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    val data = conn.inputStream.readBytes()
                    android.util.Log.d("GameWebViewClient", "SWF 下载完成: ${data.size} bytes")
                    val headers = mapOf(
                        "Access-Control-Allow-Origin" to "*",
                        "Content-Type" to "application/x-shockwave-flash",
                        "Cache-Control" to "no-cache"
                    )
                    return WebResourceResponse(
                        "application/x-shockwave-flash", null,
                        200, "OK", headers,
                        java.io.ByteArrayInputStream(data)
                    )
                } else if (responseCode in 500..599 && attempt < 3) {
                    android.util.Log.w("GameWebViewClient", "SWF 服务器错误 $responseCode, 重试...")
                    Thread.sleep(500L * attempt)
                    continue
                } else {
                    android.util.Log.w("GameWebViewClient", "SWF 下载失败: HTTP $responseCode")
                    return null
                }
            } catch (e: Exception) {
                lastError = e
                android.util.Log.w("GameWebViewClient", "SWF 下载异常 (尝试 $attempt): ${e.message}")
                if (attempt < 3) Thread.sleep(500L * attempt)
            }
        }
        android.util.Log.e("GameWebViewClient", "SWF 下载最终失败: ${lastError?.message}")
        return null
    }

    /** 从 assets 读取文件并返回带 CORS 头的 WebResourceResponse */
    private fun interceptAsset(view: WebView, assetPath: String): WebResourceResponse? {
        return try {
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
            WebResourceResponse(mime, charset, 200, "OK", headers, input)
        } catch (e: Exception) {
            android.util.Log.w("GameWebViewClient", "asset not found: $assetPath", e)
            null
        }
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        callback.onPageStarted(url)

        // 4399 页面：伪造 document.referrer 绕过防盗链检测 + IE 兼容模式伪造
        if (url != null && url.contains("4399.com")) {
            view?.evaluateJavascript(REFERER_SPOOF_SCRIPT, null)
            // IE 兼容模式：伪造 IE 特有属性让 4399 检测为 IE 浏览器
            if (PrefsManager.uaMode == "ie_compat") {
                view?.evaluateJavascript(IE_COMPAT_SCRIPT, null)
            }
        }

        val isFlashPage = PrefsManager.isFlashEnabled && callback.shouldInjectRuffle(url)

        if (isFlashPage) {
            view?.evaluateJavascript(RuffleInjector.configScript(), null)
            view?.evaluateJavascript(FLASH_HIDE_SCRIPT, null)
            if (PrefsManager.flashEngine == "waflash") {
                view?.evaluateJavascript(WAFLASH_DETECT_SCRIPT, null)
            }
        }

        // 4399 页面注入 viewport 调整
        if (url != null && url.contains("4399.com")) {
            view?.evaluateJavascript(buildViewportScript(), null)
        }
    }

    override fun onPageCommitVisible(view: WebView?, url: String?) {
        super.onPageCommitVisible(view, url)
        val isFlashPage = PrefsManager.isFlashEnabled && callback.shouldInjectRuffle(url)
        if (isFlashPage) {
            view?.evaluateJavascript(RuffleInjector.loaderScript(), null)
            view?.evaluateJavascript(FLASH_HIDE_SCRIPT, null)
            if (PrefsManager.flashEngine == "waflash") {
                view?.evaluateJavascript(WAFLASH_DETECT_SCRIPT, null)
            }
        }
        if (url != null && url.contains("4399.com")) {
            view?.evaluateJavascript(buildViewportScript(), null)
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        val isFlashPage = PrefsManager.isFlashEnabled && callback.shouldInjectRuffle(url)
        if (isFlashPage) {
            view?.evaluateJavascript(RuffleInjector.fullInjection(), null)
            if (PrefsManager.flashEngine == "waflash") {
                view?.evaluateJavascript(WAFLASH_DETECT_SCRIPT, null)
            }
        }
        if (isFlashPage) {
            view?.evaluateJavascript(CSS_INJECTION, null)
        }
        if (url != null && url.contains("4399.com")) {
            view?.evaluateJavascript(buildViewportScript(), null)
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

    /** 根据用户缩放设置构建 viewport 脚本 */
    private fun buildViewportScript(): String {
        val scale = if (PrefsManager.pageZoomMode == "manual") {
            PrefsManager.pageZoomManual / 100.0
        } else {
            -1.0
        }
        return if (scale > 0) {
            """
            (function(){
              var meta = document.querySelector('meta[name="viewport"]');
              if (!meta) { meta = document.createElement('meta'); meta.name='viewport'; document.head.appendChild(meta); }
              var s = $scale;
              meta.content = 'width=device-width, initial-scale=' + s + ', minimum-scale=' + s + ', maximum-scale=5.0, user-scalable=yes';
            })();
            """.trimIndent()
        } else {
            """
            (function(){
              var meta = document.querySelector('meta[name="viewport"]');
              if (!meta) { meta = document.createElement('meta'); meta.name='viewport'; document.head.appendChild(meta); }
              var sw = window.screen.width || 360;
              var scale = Math.min(1, sw / 1200);
              scale = Math.max(0.25, scale);
              meta.content = 'width=device-width, initial-scale=' + scale + ', minimum-scale=' + scale + ', maximum-scale=5.0, user-scalable=yes';
            })();
            """.trimIndent()
        }
    }

    companion object {

        private const val REFERER_SPOOF_SCRIPT = """
            (function(){
              try {
                Object.defineProperty(document, 'referrer', {
                  get: function() { return 'https://www.4399.com/'; },
                  configurable: true
                });
              } catch(e) {}
            })();
        """

        /** IE 兼容模式伪造：4399 检测 IE 特有属性来判断兼容模式 */
        private const val IE_COMPAT_SCRIPT = """
            (function(){
              try {
                // 伪造 IE 的 documentMode（IE 独有属性）
                Object.defineProperty(document, 'documentMode', {
                  get: function() { return 11; }, configurable: true
                });
                // 伪造 IE 的 uniqueID
                Object.defineProperty(document, 'uniqueID', {
                  get: function() { return '_ie_id_'; }, configurable: true
                });
                // 伪造 IE 的 all 集合
                if (!document.all) {
                  document.all = document.getElementsByTagName('*');
                }
                // 伪装 navigator.userAgent 中含 Trident
                var origUA = navigator.userAgent;
                if (!/Trident/.test(origUA)) {
                  try {
                    Object.defineProperty(navigator, 'userAgent', {
                      get: function() {
                        return origUA + ' Trident/7.0; rv:11.0';
                      }, configurable: true
                    });
                  } catch(e) {}
                }
              } catch(e) {}
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
         * WAFlash SWF 检测脚本：
         * 1. Hook swfobject.embedSWF() — 标准 Flash 嵌入
         * 2. Hook AC_FL_RunContent() — 老式 Flash 嵌入
         * 3. Hook createFlash() — 4399 的 mflash-player API
         * 4. Hook fetch/XMLHttpRequest — 拦截非 .swf 扩展名的 SWF 加载
         * 5. MutationObserver 检测动态创建的 Flash DOM 元素
         */
        private const val WAFLASH_DETECT_SCRIPT = """
            (function(){
              if (window.__waflashDetect) return;
              window.__waflashDetect = true;
              var redirected = false;

              function redirectToPlayer(swfUrl, baseUrl) {
                if (redirected || !swfUrl) return;
                // 检测 SWF URL：支持 .swf 扩展名、4399 的 dw-XX 格式、flash 路径
                var isSwf = /\.swf/i.test(swfUrl) ||
                            /\/dw-\d+/i.test(swfUrl) ||
                            /flash\d*\//i.test(swfUrl) ||
                            /mm\.4399\.com/i.test(swfUrl);
                if (!isSwf) return;
                redirected = true;
                try { swfUrl = new URL(swfUrl, baseUrl || window.location.href).href; } catch(e) {}
                console.log('[WAFlash] 检测到 SWF: ' + swfUrl);
                if (window.Android && window.Android.openSwf) {
                  window.Android.openSwf(swfUrl, baseUrl || window.location.href);
                } else {
                  window.location.href = 'https://flash.local/waflash.html?swf=' + encodeURIComponent(swfUrl);
                }
              }

              // 1. Hook swfobject.embedSWF
              if (window.swfobject && window.swfobject.embedSWF) {
                var origEmbed = window.swfobject.embedSWF;
                window.swfobject.embedSWF = function() {
                  redirectToPlayer(arguments[0], window.location.href);
                  return origEmbed.apply(this, arguments);
                };
              } else {
                var _swfobject;
                Object.defineProperty(window, 'swfobject', {
                  configurable: true,
                  get: function() { return _swfobject; },
                  set: function(val) {
                    _swfobject = val;
                    if (val && val.embedSWF) {
                      var orig = val.embedSWF;
                      val.embedSWF = function() {
                        redirectToPlayer(arguments[0], window.location.href);
                        return orig.apply(this, arguments);
                      };
                    }
                  }
                });
              }

              // 2. Hook AC_FL_RunContent
              if (window.AC_FL_RunContent) {
                var origAC = window.AC_FL_RunContent;
                window.AC_FL_RunContent = function() {
                  var args = Array.prototype.slice.call(arguments);
                  for (var i = 0; i < args.length - 1; i++) {
                    if ((args[i] === 'src' || args[i] === 'movie') && args[i+1]) {
                      redirectToPlayer(args[i+1], window.location.href);
                    }
                  }
                  return origAC.apply(this, arguments);
                };
              }

              // 3. Hook createFlash — 4399 的 mflash-player API
              //    createFlash(swfUrl, options) 或 createFlash({url: swfUrl, ...})
              function hookCreateFlash(obj) {
                if (!obj || !obj.createFlash || obj.__waflashHooked) return;
                obj.__waflashHooked = true;
                var orig = obj.createFlash;
                obj.createFlash = function() {
                  var swfUrl = null;
                  if (typeof arguments[0] === 'string') {
                    swfUrl = arguments[0];
                  } else if (arguments[0] && typeof arguments[0] === 'object') {
                    swfUrl = arguments[0].url || arguments[0].src || arguments[0].swf ||
                             arguments[0].movie || arguments[0].data;
                  }
                  if (swfUrl) redirectToPlayer(swfUrl, window.location.href);
                  return orig.apply(this, arguments);
                };
                console.log('[WAFlash] 已 hook createFlash');
              }

              // 检查 mflash-player 是否已存在（注意：属性名含连字符，必须用方括号）
              if (window['mflash-player']) hookCreateFlash(window['mflash-player']);
              if (window.mflashplayer) hookCreateFlash(window.mflashplayer);
              if (window.MFlash) hookCreateFlash(window.MFlash);

              // 延迟 hook：4399 可能动态加载 mflash-player
              var _mflash;
              Object.defineProperty(window, 'mflash-player', {
                configurable: true,
                get: function() { return _mflash; },
                set: function(val) { _mflash = val; hookCreateFlash(val); }
              });
              var _mflash2;
              Object.defineProperty(window, 'mflashplayer', {
                configurable: true,
                get: function() { return _mflash2; },
                set: function(val) { _mflash2 = val; hookCreateFlash(val); }
              });

              // 4. 检测页面中已有的 Flash 元素
              function extractSwfUrl(el) {
                if (!el) return null;
                var src = el.getAttribute('data') || el.getAttribute('src') ||
                          el.getAttribute('movie') || el.getAttribute('url') || '';
                if (src) return src;
                var params = el.querySelectorAll('param[name="movie"], param[name="src"]');
                for (var i = 0; i < params.length; i++) {
                  var v = params[i].getAttribute('value') || '';
                  if (v) return v;
                }
                return null;
              }

              function checkExistingFlash() {
                if (redirected) return;
                var selectors = 'object[type="application/x-shockwave-flash"], ' +
                  'embed[type="application/x-shockwave-flash"], ' +
                  'object[data$=".swf" i], embed[src$=".swf" i], ' +
                  'object[classid*="D27CDB6E" i]';
                var elements = document.querySelectorAll(selectors);
                for (var i = 0; i < elements.length; i++) {
                  var swfUrl = extractSwfUrl(elements[i]);
                  if (swfUrl) { redirectToPlayer(swfUrl, window.location.href); return; }
                }
              }

              checkExistingFlash();

              // 5. MutationObserver 持续监控
              if (window.MutationObserver) {
                var observer = new MutationObserver(function() {
                  if (redirected) { observer.disconnect(); return; }
                  checkExistingFlash();
                });
                try {
                  observer.observe(document.documentElement || document.body || document, {
                    childList: true, subtree: true
                  });
                } catch(e) {}
                setTimeout(function() { observer.disconnect(); }, 15000);
              }
            })();
        """
    }
}

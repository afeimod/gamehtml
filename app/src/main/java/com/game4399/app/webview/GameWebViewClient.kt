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
        /** 获取 WAFlash 预下载的 SWF 缓存文件路径 */
        fun getCachedSwfPath(): String?
        /** 获取本地 SWF 文件的真实 URI */
        fun getLocalSwfUri(): String?
    }

    /** 常见广告域名 */
    private val adHosts = setOf(
        "googleads.g.doubleclick.net", "pagead2.googlesyndication.com",
        "ad.4399.com", "stat.4399.com", "analytics.4399.com"
    )

    /** Chrome 87 桌面 UA（游戏页面使用，让 4399 Flash 检测通过） */
    private val CHROME_87_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.141 Safari/537.36"

    /** Chrome 120 桌面 UA（非游戏页面使用，保留极速模式） */
    private val CHROME_120_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /**
     * 判断 URL 是否为游戏页面（匹配旧 APK 的 f(str) 逻辑）。
     *
     * - 4399 首页 / /flash / /category 列表页 → false（使用 Chrome 120 极速模式）
     * - .htm / .html / play. 游戏页 → true（使用 Chrome 87 兼容模式）
     * - /flash/ 下有子路径的游戏页 → true
     *
     * 此判断用于：
     * 1. HTTP 拦截时选择 User-Agent（Chrome 87 vs 120）
     * 2. 注入脚本中是否降级 navigator.userAgent
     */
    fun isGamePage(url: String): Boolean {
        // 去除 query string 和 fragment
        val cleanUrl = url.substringBefore("?").substringBefore("#")
        // 列表页/首页：不降级 UA
        if (cleanUrl.endsWith("4399.com/", true) || cleanUrl.endsWith("4399.com", true) ||
            cleanUrl.endsWith("/flash/", true) || cleanUrl.endsWith("/flash", true) ||
            cleanUrl.endsWith("/category", true) || cleanUrl.endsWith("/category/", true)) {
            return false
        }
        // 游戏页：.htm / .html / play.
        if (cleanUrl.contains(".htm", true) || cleanUrl.contains(".html", true) || cleanUrl.contains("play.", true)) {
            return true
        }
        // /flash/ 下的子路径（有具体游戏 ID）
        if (cleanUrl.contains("/flash/", true)) {
            // 查找第 6 个 "/" 之后的内容
            var idx = -1
            var count = 0
            for (i in cleanUrl.indices) {
                if (cleanUrl[i] == '/') {
                    count++
                    if (count == 6) { idx = i; break }
                }
            }
            val sub = if (idx == -1) cleanUrl else cleanUrl.substring(idx + 1)
            if (sub.isNotEmpty()) return true
        }
        return false
    }

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

        // 2. 拦截 flash.local 虚拟域名
        if (url.contains("flash.local")) {
            val path = url.substringAfter("flash.local/").substringBefore("?")
            // 本地 SWF 代理：flash.local/local.swf → 读取真实文件
            if (path == "local.swf") {
                return interceptLocalSwfProxy(view)
            }
            return interceptAsset(view, path)
        }

        // 3. 拦截 file:///android_asset/waflash/ 请求
        if (url.startsWith("file:///android_asset/waflash/")) {
            val assetPath = url.removePrefix("file:///android_asset/").substringBefore("?")
            return interceptAsset(view, assetPath)
        }

        // 4. 拦截本地 SWF 文件（content:// 或 file://）
        if (url.startsWith("content://") || url.startsWith("file://")) {
            return interceptLocalFile(view, url)
        }

        // 5. 拦截远程 SWF 文件请求
        //    Ruffle/WAFlash 加载 SWF 时需要原生下载（添加 CORS 头 + Cookie 转发）
        //    只拦截真正的 SWF 资源请求，不影响普通网页浏览
        val isSwfRequest = url.endsWith(".swf", ignoreCase = true) ||
            url.contains(".swf?", ignoreCase = true) ||
            (url.contains("4399.com") && (url.contains("/dw-") || url.contains("flash_tm3") || url.contains("flash20")))
        if (isSwfRequest) {
            return interceptSwf(url, request)
        }

        // 6. HTML 响应拦截：同步注入 Flash 脚本（仅主框架 + Flash 启用时）
        //    旧版 2.1 APK 使用此方式：在 shouldInterceptRequest 中拦截 HTTP 响应，
        //    将 Flash 伪造 + 引擎注入脚本直接插入 HTML 的 <head> 之后。
        //    这解决了 evaluateJavascript 异步注入导致的 WAFlash 渲染模糊问题：
        //    - 异步注入时，页面 JS 在 Flash 伪造之前执行，导致 WAFlash canvas 尺寸错误
        //    - 同步注入确保 WAFlash hooks 在页面创建 Flash 元素前就位
        //    编码处理：从 Content-Type 头读取 charset，正确解码 GBK/GB2312 等编码。
        //    Cookie/Session：转发原始请求头（含 Cookie）和响应头（含 Set-Cookie）。
        if (PrefsManager.isFlashEnabled &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N &&
            request.isForMainFrame &&
            (url.startsWith("http://") || url.startsWith("https://")) &&
            callback.shouldInjectRuffle(url)) {
            try {
                val htmlResponse = interceptHtml(url, request)
                if (htmlResponse != null) {
                    return htmlResponse
                }
            } catch (e: Exception) {
                android.util.Log.w("GameWebViewClient", "HTML 拦截异常: ${e.message}")
            }
            // 拦截失败时回退到 WebView 原生处理 + evaluateJavascript 异步注入
        }

        return super.shouldInterceptRequest(view, request)
    }

    /**
     * 拦截 HTML 响应，同步注入 Flash 支持脚本（在页面 JS 执行前）。
     *
     * 旧版 2.1 APK 使用此方式：在 shouldInterceptRequest 中拦截 HTTP 响应，
     * 将 Flash 伪造 + 引擎注入脚本直接插入 HTML 的 <head> 之后。
     * 这解决了 evaluateJavascript 异步注入导致的 WAFlash 渲染模糊问题：
     * - 异步注入时，页面 JS 可能在 Flash 伪造之前执行，导致 WAFlash canvas 尺寸错误
     * - 同步注入确保 WAFlash hooks 在页面创建 Flash 元素前就位
     *
     * 编码处理：从 Content-Type 头读取 charset，正确解码 GBK/GB2312 等编码。
     * Cookie/Session：转发原始请求头（含 Cookie）和响应头（含 Set-Cookie）。
     *
     * @param url 请求 URL
     * @param request 原始请求（用于转发请求头）
     * @return 修改后的 WebResourceResponse，或 null 表示不拦截（回退到原生处理）
     */
    private fun interceptHtml(url: String, request: WebResourceRequest): WebResourceResponse? {
        var conn: java.net.HttpURLConnection? = null
        try {
            // 1. 发起 HTTP 请求（转发原始请求头，保留 Cookie/UA）
            conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                if (this is javax.net.ssl.HttpsURLConnection) {
                    sslSocketFactory = trustAllSslSocketFactory()
                    hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                }
                connectTimeout = 15000
                readTimeout = 20000
                val method = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    request.method ?: "GET"
                } else {
                    "GET"
                }
                requestMethod = if (method == "POST") "GET" else method  // HTML 拦截仅用 GET
                instanceFollowRedirects = true

                // 转发原始请求头（含 Cookie、Referer 等）
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    request.requestHeaders?.forEach { (key, value) ->
                        val lk = key.lowercase()
                        // 排除 POST 相关头和编码相关头（我们用 GET 请求）
                        // 排除 User-Agent（下面根据游戏页面条件设置）
                        if (lk !in setOf("content-type", "content-length", "transfer-encoding",
                                "accept-encoding", "user-agent")) {
                            setRequestProperty(key, value)
                        }
                    }
                }
                // 确保不使用压缩（我们需要读取原始 HTML）
                setRequestProperty("Accept-Encoding", "identity")
                // 设置 User-Agent：游戏页面用 Chrome 87（4399 Flash 检测兼容），
                // 非游戏页面用 Chrome 120（极速模式）。与旧 APK c() 方法一致。
                setRequestProperty("User-Agent",
                    if (isGamePage(url)) CHROME_87_UA else CHROME_120_UA)
            }

            conn.connect()
            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                return null
            }

            // 2. 解析 Content-Type，获取 MIME 和 charset
            val contentType = conn.contentType ?: ""
            val (mimeType, charset) = parseContentType(contentType)

            // 非 HTML 不拦截（让 WebView 原生处理）
            if (!mimeType.equals("text/html", true) && !mimeType.equals("application/xhtml+xml", true)) {
                return null
            }

            // 3. 读取原始字节（用于 SWF 魔术字节检测，避免编码转换破坏二进制数据）
            val rawBytes = conn.inputStream.use { it.readBytes() }

            // 4. 检测 SWF 魔术字节（部分服务器返回错误 Content-Type）
            if (rawBytes.size >= 3) {
                val b0 = rawBytes[0].toInt() and 0xFF
                val b1 = rawBytes[1].toInt() and 0xFF
                val b2 = rawBytes[2].toInt() and 0xFF
                // F(70)/C(67)/Z(90) + W(87) + S(83) = SWF 签名
                if ((b0 == 70 || b0 == 67 || b0 == 90) && b1 == 87 && b2 == 83) {
                    android.util.Log.d("GameWebViewClient", "HTML 拦截检测到 SWF 魔术字节: $url")
                    return WebResourceResponse("application/x-shockwave-flash", null, 200, "OK",
                        mapOf("Access-Control-Allow-Origin" to "*",
                              "Content-Type" to "application/x-shockwave-flash",
                              "Cache-Control" to "no-cache"),
                        ByteArrayInputStream(rawBytes))
                }
            }

            // 5. 读取 HTML 内容（使用正确的编码，解决 GBK 网站乱码问题）
            val encoding = charset ?: "UTF-8"
            val cs = try { java.nio.charset.Charset.forName(encoding) }
                     catch (e: Exception) { java.nio.charset.Charset.forName("UTF-8") }
            val html = String(rawBytes, cs)

            // 6. 注入 Flash 脚本（插入 <head> 之后，确保在页面 JS 执行前运行）
            val injectScript = buildFlashInjectScript(url)
            val modifiedHtml = injectScriptIntoHead(html, injectScript)

            // 7. 收集响应头（转发 Set-Cookie 等，保持 Cookie/Session）
            val responseHeaders = mutableMapOf<String, String>()
            conn.headerFields?.forEach { (key, values) ->
                if (key != null && values.isNotEmpty()) {
                    responseHeaders[key] = values.joinToString(", ")
                }
            }
            responseHeaders["Access-Control-Allow-Origin"] = "*"

            android.util.Log.d("GameWebViewClient", "HTML 注入成功: $url (${modifiedHtml.length} chars)")

            // 8. 返回修改后的 HTML（使用 UTF-8 编码，与旧 APK 一致）
            return WebResourceResponse(
                "text/html", "UTF-8", responseCode,
                conn.responseMessage ?: "OK", responseHeaders,
                ByteArrayInputStream(modifiedHtml.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
            )
        } catch (e: Exception) {
            android.util.Log.w("GameWebViewClient", "HTML 拦截失败: ${e.message}")
            return null
        } finally {
            conn?.disconnect()
        }
    }

    /** 解析 Content-Type 头，返回 (mimeType, charset) */
    private fun parseContentType(contentType: String): Pair<String, String?> {
        val parts = contentType.split(";").map { it.trim() }
        val mime = parts.firstOrNull()?.lowercase() ?: ""
        val charsetPart = parts.firstOrNull { it.startsWith("charset=", ignoreCase = true) }
        val charset = charsetPart?.substringAfter("=")?.trim()?.removeSurrounding("\"")
        return mime to charset
    }

    /** 将脚本注入 HTML 的 <head> 之后（或 <html> 之后，或文档开头） */
    private fun injectScriptIntoHead(html: String, script: String): String {
        // 查找 <head ...> 标签
        val headIdx = html.indexOf("<head", ignoreCase = true)
        if (headIdx >= 0) {
            val headEnd = html.indexOf(">", headIdx)
            if (headEnd >= 0) {
                val pos = headEnd + 1
                return html.substring(0, pos) + script + html.substring(pos)
            }
        }
        // 查找 <html ...> 标签
        val htmlIdx = html.indexOf("<html", ignoreCase = true)
        if (htmlIdx >= 0) {
            val htmlEnd = html.indexOf(">", htmlIdx)
            if (htmlEnd >= 0) {
                val pos = htmlEnd + 1
                return html.substring(0, pos) + script + html.substring(pos)
            }
        }
        // 都没有找到，插入到文档开头
        return script + html
    }

    /** 构建 Flash 支持伪造 + Ruffle/WAFlash 注入脚本（公开供 GameActivity 在 onPageFinished 兜底使用） */
    fun buildFlashInjectScript(pageUrl: String): String {
        val isWaflash = PrefsManager.flashEngine == "waflash"
        return """
        <script>
        (function(){
          // 使用与旧 APK 一致的标志位：__flashFaked 和 __flashHideInjected
          // 这样 HTML 同步注入后，evaluateJavascript 备份脚本会自动跳过
          if(window.__flashFaked)return;window.__flashFaked=true;window.__flashHideInjected=true;
          // === 1. 伪造 Flash 插件支持（必须在页面 JS 之前） ===
          try {
            var fp = {name:'Shockwave Flash',filename:'libflashplayer.so',description:'Shockwave Flash 32.0 r0',length:1,
              0:{type:'application/x-shockwave-flash',suffixes:'swf',description:'Shockwave Flash'}};
            fp.namedItem = function(n){ return (n === 'Shockwave Flash') ? fp : null; };
            fp.item = function(i){ return i === 0 ? fp : null; };
            fp.refresh = function(){};
            var _plugins = navigator.plugins || {};
            // 保持原有方法
            if (_plugins.namedItem) { fp.namedItem = function(n){ return (n === 'Shockwave Flash') ? fp : _plugins.namedItem.call(_plugins, n); }; }
            if (_plugins.item) { fp.item = function(i){ return i === 0 ? fp : _plugins.item.call(_plugins, i); }; }
            Object.defineProperty(navigator,'plugins',{
              get:function(){
                try {
                  if (!_plugins['Shockwave Flash']) {
                    _plugins['Shockwave Flash'] = fp;
                    _plugins[0] = fp;
                  }
                  _plugins.length = Math.max(_plugins.length || 0, 1);
                } catch(e) {}
                return _plugins;
              },
              configurable: true
            });
            var fm = {type:'application/x-shockwave-flash',suffixes:'swf',description:'Shockwave Flash',enabledPlugin:fp};
            var _mimes = navigator.mimeTypes || {};
            Object.defineProperty(navigator,'mimeTypes',{
              get:function(){
                try { if (!_mimes['application/x-shockwave-flash']) _mimes['application/x-shockwave-flash'] = fm; } catch(e) {}
                return _mimes;
              },
              configurable: true
            });
            window.ActiveXObject = function(n){if(n&&/ShockwaveFlash/i.test(n))return {SetVariable:function(){},Variable:function(){return ''}};throw new Error('x');};
          } catch(e) {}

          // === 1.5 伪造 navigator.userAgent（仅游戏页面：降级到 Chrome 87，让 4399 Flash 检测通过） ===
          //         主页/列表页不降级，保留极速模式。与旧 APK a(str) + f(str) 逻辑一致。
          ${if (isGamePage(pageUrl)) """
          try {
            var curUA = navigator.userAgent || '';
            var chromeMatch = curUA.match(/Chrome\/(\d+)/);
            if (chromeMatch && parseInt(chromeMatch[1]) >= 88) {
              var newUA = curUA.replace(/Chrome\/[\d.]+/, 'Chrome/87.0.4280.141');
              Object.defineProperty(navigator, 'userAgent', {get:function(){return newUA;}, configurable:true});
              Object.defineProperty(navigator, 'appVersion', {get:function(){return newUA.replace('Mozilla/','');}, configurable:true});
            }
          } catch(e) {}
          """ else ""}

          // === 2. 伪造 document.referrer（固定为 4399 主站，绕过防盗链检测） ===
          try {
            Object.defineProperty(document,'referrer',{get:function(){return 'https://www.4399.com/';},configurable:true});
          } catch(e) {}

          // === 2.5 自动关闭 4399 "不支持 Flash" 弹窗 ===
          //     4399 检测到"无 Flash 插件"会弹出模态框，自动关闭。与旧 APK a(str) 一致。
          (function(){
            var flashKeywords = ['不支持打开游戏', 'Flash官方插件', '兼容模式', '继续游戏',
              '不支持flash', '极速模式', 'QQ浏览器', '搜狗浏览器', '360浏览器',
              'EDGE浏览器请按教程', '无需下载插件打开即玩', '为您提供以下方案',
              '当前浏览器或模式不支持', '下载官方Flash', '下载flash', 'flash插件',
              'web新游专区', '4399游戏大厅', '请使用以下浏览器', 'PPAPI', 'NPAPI'];
            var popupSelectors = '.flash_tips,.flash-tips,#flash_tips,#flash-tips,.no_flash,.no-flash,#no_flash,#no-flash,.browser_tip,.browser-tip,#browser_tip,.unsupported,.unsupport,#unsupported,.game_tips,.game-tips,#game_tips,.alert_flash,.alert-flash,#alert_flash,#flashmsg,.flashmsg,#flash_msg,.pop_flash,.pop-flash,#pop_flash,.modal-flash,#modal-flash,.compatible_tip,.compatible-tip,.flash_prompt,.flash-prompt,#flash_prompt';
            function closeInDoc(doc) {
              if (!doc) return;
              try {
                doc.querySelectorAll(popupSelectors).forEach(function(el){
                  el.style.display = 'none';
                  try { el.remove(); } catch(e){}
                });
                var btns = doc.querySelectorAll('a, button, span, div, i, img');
                for (var i = 0; i < btns.length; i++) {
                  var b = btns[i];
                  var cls = (b.className || '').toString();
                  var txt = (b.textContent || '').trim();
                  if (txt === '×' || txt === 'X' || txt === '关闭' ||
                      /close| Close|关闭|shut/i.test(cls)) {
                    if (b.offsetWidth > 0 || b.offsetHeight > 0) {
                      try { b.click(); } catch(e){}
                    }
                  }
                }
                var all = doc.querySelectorAll('div, section, aside, table, ul, li');
                for (var i = 0; i < all.length; i++) {
                  var el = all[i];
                  var text = el.textContent || '';
                  var matchCount = 0;
                  for (var k = 0; k < flashKeywords.length; k++) {
                    if (text.indexOf(flashKeywords[k]) >= 0) matchCount++;
                  }
                  if (matchCount >= 2) {
                    el.remove();
                    console.log('[Flash] 已移除不支持Flash弹窗 (匹配' + matchCount + '个关键词)');
                  }
                }
                var masks = doc.querySelectorAll('[class*="mask"], [class*="overlay"], [class*="Mask"], [class*="Overlay"], [id*="mask"], [id*="overlay"]');
                masks.forEach(function(m){
                  var t = (m.textContent || '');
                  var mc = 0;
                  for (var k = 0; k < flashKeywords.length; k++) { if (t.indexOf(flashKeywords[k]) >= 0) mc++; }
                  if (mc >= 1 || t.length < 5) { m.style.display = 'none'; }
                });
              } catch(e) {}
            }
            function closeFlashDialog() {
              closeInDoc(document);
              var iframes = document.querySelectorAll('iframe');
              for (var i = 0; i < iframes.length; i++) {
                try { closeInDoc(iframes[i].contentDocument); } catch(e) {}
              }
            }
            var checkCount = 0;
            var interval = setInterval(function(){
              closeFlashDialog();
              checkCount++;
              if (checkCount > 50) clearInterval(interval);
            }, 200);
            if (window.MutationObserver) {
              var obs = new MutationObserver(function(){ closeFlashDialog(); });
              try { obs.observe(document.documentElement, {childList:true, subtree:true}); } catch(e){}
              setTimeout(function(){ obs.disconnect(); }, 15000);
            }
          })();

          // === 3. Ruffle polyfill（Ruffle 模式） ===
          ${if (!isWaflash) """
          window.RufflePlayer = window.RufflePlayer || {};
          window.RufflePlayer.config = {
            publicPath: 'https://flash.local/ruffle/',
            autoplay: 'on',
            unmuteOverlay: 'visible',
            backgroundColor: '#000000',
            letterbox: 'on',
            polyfills: true,
            allowScriptAccess: true,
            allowFullscreen: false,
            upgradeToHttps: true,
            scale: 'showAll',
            maxExecutionDuration: 30,
            logLevel: 'warn'
          };
          var ruffleScript = document.createElement('script');
          ruffleScript.src = 'https://flash.local/ruffle/ruffle.js';
          ruffleScript.onload = function() {
            window.__ruffleLoaded = true;
            console.log('[Ruffle] 引擎加载完成');
          };
          document.head.appendChild(ruffleScript);
          """ else """
          // --- WAFlash 页内播放模式（不重定向，保持页面网络上下文） ---
          // 旧版 2.1 APK 使用页内 canvas 渲染，画面清晰；
          // 重定向到 waflash.html 会丢失页面 viewport/zoom 上下文导致模糊。
          window.__waflashDetect = true;  // 防止 WAFLASH_DETECT_SCRIPT 备份脚本重复执行
          var __wafPlayed = false;
          var __wafEngineReady = false;
          var __wafModule = null;

          // 预加载 WAFlash 引擎
          function __wafLoadEngine() {
            if (window.__wafEngineLoading) return;
            window.__wafEngineLoading = true;
            import('https://flash.local/waflash/waflash-player.min.js').then(function(module) {
              __wafEngineReady = true;
              __wafModule = module;
              console.log('[WAFlash] 引擎加载完成');
              if (window.__pendingSwfUrl) {
                __wafPlayInPage(window.__pendingSwfUrl, window.__pendingBaseUrl);
              }
            }).catch(function(err) {
              console.error('[WAFlash] 引擎加载失败: ' + (err.message || err));
            });
          }

          // 页内播放 SWF（在当前页面创建 canvas，不跳转）
          function __wafPlayInPage(swfUrl, baseUrl) {
            if (__wafPlayed || !swfUrl) return;
            // 不再按 URL 模式过滤：只要检测到 Flash 元素就尝试播放
            // 很多在线游戏的 SWF URL 不含 .swf 或 4399 特征（如 PHP 动态 URL、CDN 路径等）
            __wafPlayed = true;
            try { swfUrl = new URL(swfUrl, baseUrl || window.location.href).href; } catch(e) {}
            console.log('[WAFlash] 页内播放 SWF: ' + swfUrl);

            // 引擎未就绪：先缓存 URL，引擎加载完自动播放
            if (!__wafEngineReady) {
              window.__pendingSwfUrl = swfUrl;
              window.__pendingBaseUrl = baseUrl;
              __wafLoadEngine();
              return;
            }

            // 创建全屏 canvas 容器
            var container = document.createElement('div');
            container.id = '__waflash_container';
            container.style.cssText = 'position:fixed;left:0;top:0;width:100%;height:100%;z-index:99999;background:#000;';
            var canvas = document.createElement('canvas');
            canvas.className = 'waflashCanvas';
            canvas.id = 'canvas';
            canvas.style.cssText = 'width:100%;height:100%;display:block;outline:none;';
            canvas.setAttribute('tabindex', '1');
            container.appendChild(canvas);

            // 隐藏页面其他内容
            var bc = document.body.children;
            for (var i = 0; i < bc.length; i++) {
              if (bc[i] !== container) { try { bc[i].style.display = 'none'; } catch(e){} }
            }
            document.body.appendChild(container);
            canvas.focus();

            // 设置 base 标签（SWF 内部相对路径以原始页面 URL 为 base）
            if (baseUrl) {
              try {
                var bo = new URL(baseUrl);
                var bt = document.createElement('base');
                bt.href = bo.origin + bo.pathname.substring(0, bo.pathname.lastIndexOf('/') + 1);
                document.head.appendChild(bt);
                console.log('[WAFlash] base href: ' + bt.href);
              } catch(e) {}
            }

            // 调用 WAFlash 引擎播放
            var fn = (__wafModule && __wafModule.createWaflash) || window.createWaflash;
            if (fn) {
              try {
                fn(swfUrl, { gpu: true });
                console.log('[WAFlash] 页内播放已启动: ' + swfUrl);
              } catch(e) {
                console.error('[WAFlash] 播放失败: ' + e.message);
                // 回退到独立播放器页面
                if (window.Android && window.Android.openSwf) {
                  window.Android.openSwf(swfUrl, baseUrl || window.location.href);
                }
              }
            } else {
              console.log('[WAFlash] createWaflash 未找到，引擎可能已自动启动');
            }
          }

          // 预加载引擎
          __wafLoadEngine();

          // hook swfobject.embedSWF
          if (window.swfobject && window.swfobject.embedSWF) {
            var oe = window.swfobject.embedSWF;
            window.swfobject.embedSWF = function(){__wafPlayInPage(arguments[0],window.location.href);return oe.apply(this,arguments);};
          } else {
            var _swo; Object.defineProperty(window,'swfobject',{configurable:true,get:function(){return _swo;},set:function(v){_swo=v;if(v&&v.embedSWF){var o=v.embedSWF;v.embedSWF=function(){__wafPlayInPage(arguments[0],window.location.href);return o.apply(this,arguments);};}}});
          }
          // hook createFlash (mflash-player)
          function __hookCF(obj){if(!obj||!obj.createFlash||obj.__wafH)return;obj.__wafH=true;var o=obj.createFlash;obj.createFlash=function(){var u=null;if(typeof arguments[0]==='string')u=arguments[0];else if(arguments[0]&&typeof arguments[0]==='object')u=arguments[0].url||arguments[0].src||arguments[0].swf||arguments[0].movie;if(u)__wafPlayInPage(u,window.location.href);return o.apply(this,arguments);};}
          if(window['mflash-player'])__hookCF(window['mflash-player']);
          var _mp;Object.defineProperty(window,'mflash-player',{configurable:true,get:function(){return _mp;},set:function(v){_mp=v;__hookCF(v);}});
          // hook AC_FL_RunContent
          if(window.AC_FL_RunContent){var oAC=window.AC_FL_RunContent;window.AC_FL_RunContent=function(){var a=Array.prototype.slice.call(arguments);for(var i=0;i<a.length-1;i++){if((a[i]==='src'||a[i]==='movie')&&a[i+1])__wafPlayInPage(a[i+1],window.location.href);}return oAC.apply(this,arguments);};}
          // hook document.createElement('object'/'embed') — 拦截动态创建的 Flash 元素
          var _dcE = document.createElement.bind(document);
          document.createElement = function(tag) {
            var el = _dcE(tag);
            if (tag && (tag.toLowerCase() === 'object' || tag.toLowerCase() === 'embed')) {
              var _sa = el.setAttribute.bind(el);
              el.setAttribute = function(name, value) {
                _sa(name, value);
                if (name === 'data' || name === 'src' || name === 'movie') {
                  setTimeout(function(){ __wafPlayInPage(value, window.location.href); }, 0);
                }
              };
            }
            return el;
          };
          // hook document.write / writeln — 很多老页面用 document.write 创建 Flash 元素
          var _dw = document.write.bind(document);
          document.write = function(){ _dw.apply(document, arguments); setTimeout(function(){ __checkFlash(); }, 0); };
          var _dwln = document.writeln.bind(document);
          document.writeln = function(){ _dwln.apply(document, arguments); setTimeout(function(){ __checkFlash(); }, 0); };
          // hook innerHTML / insertAdjacentHTML — 拦截通过 HTML 字符串创建的 Flash 元素
          var _flashHtmlPattern = /shockwave|\.swf|D27CDB6E|application\/x-shockwave/i;
          var _isDesc = Object.getOwnPropertyDescriptor(Element.prototype, 'innerHTML');
          if (_isDesc && _isDesc.set) {
            var _origSet = _isDesc.set;
            Object.defineProperty(Element.prototype, 'innerHTML', {
              get: _isDesc.get,
              set: function(v) { _origSet.call(this, v); if (v && _flashHtmlPattern.test(v)) setTimeout(function(){ __checkFlash(); }, 0); },
              configurable: true
            });
          }
          var _iah = Element.prototype.insertAdjacentHTML;
          Element.prototype.insertAdjacentHTML = function(pos, text) {
            _iah.call(this, pos, text);
            if (text && _flashHtmlPattern.test(text)) setTimeout(function(){ __checkFlash(); }, 0);
          };
          // hook outerHTML setter — 拦截通过 outerHTML 替换元素创建的 Flash
          var _ohDesc = Object.getOwnPropertyDescriptor(Element.prototype, 'outerHTML');
          if (_ohDesc && _ohDesc.set) {
            var _origOH = _ohDesc.set;
            Object.defineProperty(Element.prototype, 'outerHTML', {
              get: _ohDesc.get,
              set: function(v) { _origOH.call(this, v); if (v && _flashHtmlPattern.test(v)) setTimeout(function(){ __checkFlash(); }, 0); },
              configurable: true
            });
          }
          // DOM 检测
          function __checkFlash(){
            if(__wafPlayed)return;
            var sel='object[type="application/x-shockwave-flash"],embed[type="application/x-shockwave-flash"],object[data$=".swf" i],embed[src$=".swf" i],object[classid*="D27CDB6E" i],object[classid*="d27cdb6e" i],embed[type*="flash" i],object[data*=".swf" i],embed[src*=".swf" i]';
            var els=document.querySelectorAll(sel);
            for(var i=0;i<els.length;i++){var s=els[i].getAttribute('data')||els[i].getAttribute('src')||'';if(!s){var ps=els[i].querySelectorAll('param[name="movie"],param[name="src"],param[name="data"]');for(var j=0;j<ps.length;j++){var v=ps[j].getAttribute('value')||'';if(v){s=v;break;}}}if(s)__wafPlayInPage(s,window.location.href);}
          }
          __checkFlash();
          if(window.MutationObserver){var mo=new MutationObserver(function(){if(__wafPlayed){mo.disconnect();return;}__checkFlash();});try{mo.observe(document.documentElement||document.body||document,{childList:true,subtree:true});}catch(e){}setTimeout(function(){mo.disconnect();},15000);}
          """}
        })();
        </script>
        """.trimIndent()
    }

    /** 读取本地 SWF 文件代理（flash.local/local.swf → 真实 content:// URI）。
     *  直接返回文件流，不读入内存，避免大 SWF 文件 OOM。 */
    private fun interceptLocalSwfProxy(view: WebView): WebResourceResponse? {
        val uri = callback.getLocalSwfUri()
        if (uri == null) {
            android.util.Log.w("GameWebViewClient", "local.swf: 无本地文件 URI")
            return WebResourceResponse("application/x-shockwave-flash", null, 404, "Not Found",
                mapOf("Access-Control-Allow-Origin" to "*"), java.io.ByteArrayInputStream(ByteArray(0)))
        }
        return try {
            android.util.Log.d("GameWebViewClient", "local.swf 代理: 读取 $uri")
            val parsed = android.net.Uri.parse(uri)
            // 直接返回文件流，不读入内存（避免大 SWF OOM）
            val input = view.context.contentResolver.openInputStream(parsed)
                ?: throw java.io.IOException("无法打开文件流")
            android.util.Log.d("GameWebViewClient", "local.swf 流已打开: $uri")
            WebResourceResponse(
                "application/x-shockwave-flash", null, 200, "OK",
                mapOf(
                    "Access-Control-Allow-Origin" to "*",
                    "Content-Type" to "application/x-shockwave-flash",
                    "Cache-Control" to "no-cache"
                ),
                input
            )
        } catch (e: Exception) {
            android.util.Log.e("GameWebViewClient", "local.swf 读取失败: ${e.message}")
            WebResourceResponse("application/x-shockwave-flash", null, 404, "Not Found",
                mapOf("Access-Control-Allow-Origin" to "*"), java.io.ByteArrayInputStream(ByteArray(0)))
        }
    }

    /** 读取本地 SWF 文件（content:// 或 file://），返回带 CORS 头的响应。
     *  直接返回文件流，不读入内存，避免大 SWF 文件 OOM。 */
    private fun interceptLocalFile(view: WebView, url: String): WebResourceResponse? {
        return try {
            android.util.Log.d("GameWebViewClient", "读取本地文件: $url")
            val uri = android.net.Uri.parse(url)
            // 直接返回文件流，不读入内存（避免大 SWF OOM）
            val input = view.context.contentResolver.openInputStream(uri)
                ?: throw java.io.IOException("无法打开文件流")
            android.util.Log.d("GameWebViewClient", "本地文件流已打开: $url")
            WebResourceResponse(
                "application/x-shockwave-flash", null,
                200, "OK",
                mapOf(
                    "Access-Control-Allow-Origin" to "*",
                    "Content-Type" to "application/x-shockwave-flash",
                    "Cache-Control" to "no-cache"
                ),
                input
            )
        } catch (e: Exception) {
            android.util.Log.e("GameWebViewClient", "本地文件读取失败: ${e.message}")
            WebResourceResponse(
                "application/x-shockwave-flash", null, 404, "Not Found",
                mapOf("Access-Control-Allow-Origin" to "*"),
                java.io.ByteArrayInputStream(ByteArray(0))
            )
        }
    }

    /** 统一 CORS 响应头：所有 SWF 拦截响应（成功/失败/预检）都带这些头 */
    private val swfCorsHeaders = mapOf(
        "Access-Control-Allow-Origin" to "*",
        "Access-Control-Allow-Methods" to "GET, POST, OPTIONS, HEAD",
        "Access-Control-Allow-Headers" to "*",
        "Content-Type" to "application/x-shockwave-flash",
        "Cache-Control" to "no-cache"
    )

    // ===== SWF 磁盘缓存（替代内存缓存，避免大文件 OOM）=====
    // 之前用 ByteArray 缓存整个 SWF，下载 ~128MB 的 SWF 时
    // ByteArrayOutputStream.grow() → Arrays.copyOf() 分配 128MB 连续内存导致 OOM 崩溃。
    // 现在改为流式下载到磁盘文件，内存占用恒定（仅 16KB 缓冲区）。

    /** SWF 磁盘缓存目录（app cacheDir，系统可在内存不足时自动清理） */
    private val swfCacheDir: java.io.File by lazy {
        java.io.File(com.game4399.app.App.instance.cacheDir, "swf_intercept").apply {
            if (!exists()) mkdirs()
        }
    }

    /** SWF 磁盘缓存：URL → 缓存文件（避免同一 SWF 被多个并发请求重复下载） */
    private val swfFileCache = java.util.concurrent.ConcurrentHashMap<String, java.io.File>()

    /** 正在下载的 URL → 锁对象（防止同一 SWF 被并发请求重复下载） */
    private val swfDownloadLocks = java.util.concurrent.ConcurrentHashMap<String, Any>()

    /** 缓存最大条目数（超出时删除最旧的一半） */
    private val MAX_SWF_CACHE_ENTRIES = 10

    /** 原生下载 SWF 文件，返回带 CORS 头的响应（含磁盘缓存 + 重试 + SSL 兼容 + Cookie/请求头转发）。
     *  流式下载到磁盘文件，内存占用恒定（仅 16KB 缓冲区），不再因大 SWF 导致 OOM 崩溃。 */
    private fun interceptSwf(url: String, request: WebResourceRequest? = null): WebResourceResponse? {
        // 0. 处理 CORS 预检请求（OPTIONS）：直接返回 200 + CORS 头，不下载文件
        //    WebResourceRequest.getMethod() 需要 API 24+，低于 24 无法判断方法
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N &&
            request?.method == "OPTIONS") {
            android.util.Log.d("GameWebViewClient", "SWF OPTIONS 预检通过: $url")
            return WebResourceResponse("text/plain", "UTF-8", 200, "OK",
                swfCorsHeaders, java.io.ByteArrayInputStream(ByteArray(0)))
        }

        // 1. 构建尝试 URL 列表
        //    不再强制 HTTP→HTTPS 升级！
        //    原因：部分 CDN（如 cdn.comment.4399pk.com）不支持 HTTPS，强制升级导致下载失败。
        //    shouldInterceptRequest 返回的响应直接给 WebView，不受 Mixed Content 限制。
        //    策略：HTTP URL 先试原始 HTTP，失败再试 HTTPS；HTTPS URL 直接用
        val tryUrls = when {
            url.startsWith("https://") -> listOf(url)
            url.startsWith("http://") -> listOf(url, "https://" + url.substring(7))
            else -> listOf(url)
        }

        // 2. 检查磁盘缓存：Ruffle/WAFlash 可能同时发起多个相同 SWF 请求，缓存避免重复下载
        for (u in tryUrls) {
            val cachedFile = swfFileCache[u]
            if (cachedFile != null && cachedFile.exists() && cachedFile.canRead()) {
                android.util.Log.d("GameWebViewClient", "SWF 缓存命中: ${cachedFile.length()} bytes, URL=$u")
                return WebResourceResponse("application/x-shockwave-flash", null, 200, "OK",
                    swfCorsHeaders, java.io.FileInputStream(cachedFile))
            } else if (cachedFile != null) {
                swfFileCache.remove(u)
            }
        }

        // 3. 逐个 URL 尝试下载（每个 URL 最多重试 3 次）
        //    使用 per-URL 锁防止并发请求重复下载同一 SWF
        var lastError: Exception? = null
        for (swfUrl in tryUrls) {
            val lock = swfDownloadLocks.computeIfAbsent(swfUrl) { Any() }
            try {
                synchronized(lock) {
                    // 双重检查：等待锁期间其他线程可能已下载完成
                    swfFileCache[swfUrl]?.let { f ->
                        if (f.exists() && f.canRead()) {
                            android.util.Log.d("GameWebViewClient", "SWF 锁等待后缓存命中: ${f.length()} bytes")
                            return WebResourceResponse("application/x-shockwave-flash", null, 200, "OK",
                                swfCorsHeaders, java.io.FileInputStream(f))
                        }
                    }

                    for (attempt in 1..3) {
                        var conn: java.net.HttpURLConnection? = null
                        try {
                            android.util.Log.d("GameWebViewClient", "拦截 SWF 请求 (尝试 $attempt): $swfUrl")
                            conn = java.net.URL(swfUrl).openConnection() as java.net.HttpURLConnection
                            // HTTPS: 信任所有证书，防止 SSL 握手失败导致 SWF 下载不了
                            if (conn is javax.net.ssl.HttpsURLConnection) {
                                conn.sslSocketFactory = trustAllSslSocketFactory()
                                conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                            }
                            conn.connectTimeout = 10000
                            conn.readTimeout = 20000
                            conn.requestMethod = "GET"
                            conn.instanceFollowRedirects = true

                            // 转发原始请求头（User-Agent, Accept 等），模拟浏览器行为
                            // 排除需要自行设置的、条件请求的、和 CORS 相关的 header
                            request?.requestHeaders?.forEach { (key, value) ->
                                val lk = key.lowercase()
                                if (lk !in setOf(
                                        "cookie", "referer", "range", "if-modified-since", "if-none-match",
                                        "accept-encoding", "origin", "access-control-request-method",
                                        "access-control-request-headers", "host", "content-length"
                                    )) {
                                    conn.setRequestProperty(key, value)
                                }
                            }
                            // 确保 User-Agent 存在（完整浏览器 UA，避免被服务器拒绝）
                            if (request?.requestHeaders?.none { it.key.equals("User-Agent", true) } != false) {
                                conn.setRequestProperty("User-Agent",
                                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            }
                            conn.setRequestProperty("Accept", "*/*")
                            // 不请求 gzip，避免手动解压
                            conn.setRequestProperty("Accept-Encoding", "identity")

                            // 转发 Cookie（从 CookieManager 获取，防止防盗链/登录态丢失）
                            try {
                                val cookies = android.webkit.CookieManager.getInstance().getCookie(swfUrl)
                                if (cookies != null && cookies.isNotEmpty()) {
                                    conn.setRequestProperty("Cookie", cookies)
                                    android.util.Log.d("GameWebViewClient", "转发 Cookie: ${cookies.length} chars")
                                }
                            } catch(e: Exception) {}

                            // 添加 Referer（防盗链），从 SWF URL 推导同源 origin
                            if (swfUrl.contains("4399.com")) {
                                conn.setRequestProperty("Referer", "https://www.4399.com/")
                            } else {
                                try {
                                    val uri = android.net.Uri.parse(swfUrl)
                                    conn.setRequestProperty("Referer", "${uri.scheme}://${uri.host}/")
                                } catch(e: Exception) {
                                    conn.setRequestProperty("Referer", swfUrl)
                                }
                            }

                            conn.connect()
                            val responseCode = conn.responseCode
                            if (responseCode in 200..299) {
                                // 流式下载到磁盘文件，避免大文件 OOM
                                val cachedFile = streamDownloadToFile(conn, swfUrl)
                                conn.disconnect()
                                if (cachedFile != null) {
                                    android.util.Log.d("GameWebViewClient", "SWF 下载完成: ${cachedFile.length()} bytes, URL=$swfUrl")
                                    // 存入磁盘缓存并清理超量条目
                                    trimSwfCache()
                                    swfFileCache[swfUrl] = cachedFile
                                    return WebResourceResponse(
                                        "application/x-shockwave-flash", null,
                                        200, "OK", swfCorsHeaders,
                                        java.io.FileInputStream(cachedFile)
                                    )
                                }
                            } else if (responseCode in 500..599 && attempt < 3) {
                                android.util.Log.w("GameWebViewClient", "SWF 服务器错误 $responseCode, 重试...")
                                Thread.sleep(500L * attempt)
                                continue
                            } else {
                                android.util.Log.w("GameWebViewClient", "SWF 下载失败: HTTP $responseCode, URL=$swfUrl")
                                lastError = RuntimeException("HTTP $responseCode")
                                break // 换下一个 URL 尝试
                            }
                        } catch (e: Exception) {
                            lastError = e
                            android.util.Log.w("GameWebViewClient", "SWF 下载异常 (尝试 $attempt): ${e.message}")
                            if (attempt < 3) Thread.sleep(500L * attempt)
                        } finally {
                            conn?.disconnect()
                        }
                    }
                }
            } finally {
                swfDownloadLocks.remove(swfUrl)
            }
            // 再次检查缓存：可能在重试期间其他线程已成功下载
            swfFileCache[swfUrl]?.let { f ->
                if (f.exists() && f.canRead()) {
                    android.util.Log.d("GameWebViewClient", "SWF 重试期间缓存命中: ${f.length()} bytes")
                    return WebResourceResponse("application/x-shockwave-flash", null, 200, "OK",
                        swfCorsHeaders, java.io.FileInputStream(f))
                }
            }
        }

        // 4. 所有 URL 都失败了
        android.util.Log.e("GameWebViewClient", "SWF 下载最终失败: ${lastError?.message}, URL=$url")
        // 返回带 CORS 头的空响应（而非 null）
        // 原因：返回 null 会让 WebView 自行请求原始 URL，
        // 但 WAFlash/Ruffle 页面运行在 https://flash.local（HTTPS），
        // 浏览器会因 Mixed Content / CORS 阻止 HTTP 资源加载，导致引擎报错。
        // 返回 502 + CORS 头让引擎知道请求失败，优雅处理（如跳过广告 SWF）。
        return WebResourceResponse("application/x-shockwave-flash", null, 502, "Bad Gateway",
            swfCorsHeaders, java.io.ByteArrayInputStream(ByteArray(0)))
    }

    /** 流式下载 SWF 到缓存文件（16KB 分块写入，不将整个文件读入内存，避免 OOM） */
    private fun streamDownloadToFile(conn: java.net.HttpURLConnection, swfUrl: String): java.io.File? {
        val safeName = java.lang.Integer.toHexString(swfUrl.hashCode())
        val tmpFile = java.io.File(swfCacheDir, "swf_${safeName}_${System.currentTimeMillis()}.bin")
        return try {
            conn.inputStream.use { input ->
                java.io.FileOutputStream(tmpFile).use { output ->
                    val chunk = ByteArray(16 * 1024) // 16KB 缓冲区，内存占用恒定
                    while (true) {
                        if (Thread.currentThread().isInterrupted) {
                            tmpFile.delete()
                            return null
                        }
                        val bytesRead = input.read(chunk)
                        if (bytesRead == -1) break
                        output.write(chunk, 0, bytesRead)
                    }
                }
            }
            tmpFile
        } catch (e: Exception) {
            android.util.Log.w("GameWebViewClient", "SWF 流式下载失败: ${e.message}")
            tmpFile.delete()
            null
        }
    }

    /** 清理超量的磁盘缓存（删除最旧的一半文件，控制磁盘占用） */
    private fun trimSwfCache() {
        if (swfFileCache.size < MAX_SWF_CACHE_ENTRIES) return
        val toRemove = swfFileCache.entries
            .filter { it.value.exists() }
            .sortedBy { it.value.lastModified() }
            .take(swfFileCache.size / 2)
        for ((key, file) in toRemove) {
            swfFileCache.remove(key)
            if (file.exists()) file.delete()
        }
        android.util.Log.d("GameWebViewClient", "SWF 缓存清理: 删除 ${toRemove.size} 个旧文件")
    }

    /** 信任所有 SSL 证书的 SSLSocketFactory（用于 SWF 下载兼容） */
    @Volatile private var _sslFactory: javax.net.ssl.SSLSocketFactory? = null
    private fun trustAllSslSocketFactory(): javax.net.ssl.SSLSocketFactory {
        return _sslFactory ?: synchronized(this) {
            _sslFactory ?: try {
                val tm = object : javax.net.ssl.X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                }
                val ctx = javax.net.ssl.SSLContext.getInstance("TLS")
                ctx.init(null, arrayOf(tm), java.security.SecureRandom())
                ctx.socketFactory
            } catch (e: Exception) {
                javax.net.ssl.HttpsURLConnection.getDefaultSSLSocketFactory()
            }.also { _sslFactory = it }
        }
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
                assetPath.endsWith(".ttf") -> "font/ttf" to null
                assetPath.endsWith(".woff") -> "font/woff" to null
                assetPath.endsWith(".woff2") -> "font/woff2" to null
                assetPath.endsWith(".otf") -> "font/otf" to null
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

        // 注入按键安全钩子：页面失焦时自动释放所有按键（所有页面通用）
        view?.evaluateJavascript(KEY_SAFETY_SCRIPT, null)

        // 4399 页面：伪造 document.referrer 绕过防盗链检测 + IE 兼容模式伪造
        if (url != null && url.contains("4399.com")) {
            view?.evaluateJavascript(REFERER_SPOOF_SCRIPT, null)
            if (PrefsManager.uaMode == "ie_compat") {
                view?.evaluateJavascript(IE_COMPAT_SCRIPT, null)
            }
        }

        val isFlashPage = PrefsManager.isFlashEnabled && callback.shouldInjectRuffle(url)

        // Flash 页面注入策略（与旧 APK 一致）：
        // - WAFlash 模式：只注入 buildFlashInjectScript（包含 Flash 伪造 + UA 降级 + 弹窗关闭 + 引擎注入）
        //   不先注入 FLASH_FAKE_SUPPORT_SCRIPT，否则 __flashFaked 标志会导致 buildFlashInjectScript 被跳过
        // - Ruffle 模式：注入单独的 Flash 伪造 + Ruffle 配置/加载器
        if (isFlashPage) {
            if (PrefsManager.flashEngine == "waflash") {
                // WAFlash：buildFlashInjectScript 包含完整逻辑（Flash 伪造 + UA 降级 + 弹窗关闭 + 引擎注入）
                // __flashFaked 守卫确保 HTML 同步注入后不会重复执行
                val wafScript = buildFlashInjectScript(url ?: "")
                    .replace("<script>", "").replace("</script>", "").trim()
                view?.evaluateJavascript(wafScript, null)
                view?.evaluateJavascript(WAFLASH_DETECT_SCRIPT, null)
            } else {
                // Ruffle：单独注入各组件
                view?.evaluateJavascript(FLASH_FAKE_SUPPORT_SCRIPT, null)
                view?.evaluateJavascript(RuffleInjector.configScript(), null)
                view?.evaluateJavascript(RuffleInjector.loaderScript(), null)
                view?.evaluateJavascript(FLASH_HIDE_SCRIPT, null)
            }
            // 注入 iframe 监控：游戏可能加载在 iframe 中
            view?.evaluateJavascript(IFRAME_INJECT_SCRIPT, null)
        }

        // 所有网页注入 viewport 缩放支持（不限 4399）
        if (url != null && !url.startsWith("file:///android_asset/") && !url.startsWith("https://flash.local/")) {
            view?.evaluateJavascript(buildViewportScript(), null)
        }
    }

    override fun onPageCommitVisible(view: WebView?, url: String?) {
        super.onPageCommitVisible(view, url)
        val isFlashPage = PrefsManager.isFlashEnabled && callback.shouldInjectRuffle(url)
        if (isFlashPage) {
            if (PrefsManager.flashEngine == "waflash") {
                // WAFlash：buildFlashInjectScript 守卫确保不重复执行
                val wafScript = buildFlashInjectScript(url ?: "")
                    .replace("<script>", "").replace("</script>", "").trim()
                view?.evaluateJavascript(wafScript, null)
                view?.evaluateJavascript(WAFLASH_DETECT_SCRIPT, null)
            } else {
                view?.evaluateJavascript(RuffleInjector.loaderScript(), null)
                view?.evaluateJavascript(FLASH_HIDE_SCRIPT, null)
            }
        }
        if (url != null && !url.startsWith("file:///android_asset/") && !url.startsWith("https://flash.local/")) {
            view?.evaluateJavascript(buildViewportScript(), null)
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        val isFlashPage = PrefsManager.isFlashEnabled && callback.shouldInjectRuffle(url)
        if (isFlashPage) {
            if (PrefsManager.flashEngine == "waflash") {
                // WAFlash：buildFlashInjectScript 守卫确保不重复执行
                val wafScript = buildFlashInjectScript(url ?: "")
                    .replace("<script>", "").replace("</script>", "").trim()
                view?.evaluateJavascript(wafScript, null)
                view?.evaluateJavascript(WAFLASH_DETECT_SCRIPT, null)
            } else {
                view?.evaluateJavascript(RuffleInjector.fullInjection(), null)
            }
        }
        if (isFlashPage) {
            view?.evaluateJavascript(CSS_INJECTION, null)
        }
        if (url != null && !url.startsWith("file:///android_asset/") && !url.startsWith("https://flash.local/")) {
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
              // CSS zoom（对桌面固定布局页面有效，viewport meta 无效时兜底）
              document.documentElement.style.zoom = s;
              // 监听 DOM 变化，确保动态加载的内容也应用 zoom
              if (!window.__zoomObserver) {
                window.__zoomObserver = true;
                if (window.MutationObserver) {
                  var mo = new MutationObserver(function(){ document.documentElement.style.zoom = s; });
                  try { mo.observe(document.documentElement, {childList:true, subtree:true, attributes:true, attributeFilter:['style','class']}); } catch(e){}
                  setTimeout(function(){ mo.disconnect(); }, 5000);
                }
              }
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
              // 自动模式也应用 CSS zoom，保证桌面页面缩放生效
              document.documentElement.style.zoom = scale;
            })();
            """.trimIndent()
        }
    }

    companion object {

        /**
         * 按键安全钩子：在页面失焦（blur/visibilitychange）时自动释放所有按键。
         * 这是 Android 端 releaseAllKeys() 的补充保险——
         * 即使 Android 侧漏调，网页侧也能自行清理，防止 Ruffle 角色持续移动。
         */
        private const val KEY_SAFETY_SCRIPT = """
            (function(){
              if (window.__keySafetyHook) return;
              window.__keySafetyHook = true;
              // 追踪所有 keydown 的 keyCode
              var pressed = {};
              window.addEventListener('keydown', function(e){
                pressed[e.keyCode] = true;
              }, true);
              window.addEventListener('keyup', function(e){
                delete pressed[e.keyCode];
              }, true);
              // 释放所有已记录的按键
              function releaseAll() {
                Object.keys(pressed).forEach(function(kc) {
                  var code = parseInt(kc);
                  var keyMap = {
                    37:'ArrowLeft',38:'ArrowUp',39:'ArrowRight',40:'ArrowDown',
                    32:' ',13:'Enter',9:'Tab',27:'Escape',17:'Control',16:'Shift',18:'Alt',
                    65:'a',66:'b',67:'c',68:'d',69:'e',70:'f',71:'g',72:'h',73:'i',74:'j',
                    75:'k',76:'l',77:'m',78:'n',79:'o',80:'p',81:'q',82:'r',83:'s',84:'t',
                    85:'u',86:'v',87:'w',88:'x',89:'y',90:'z',
                    48:'0',49:'1',50:'2',51:'3',52:'4',53:'5',54:'6',55:'7',56:'8',57:'9'
                  };
                  var keyVal = keyMap[code] || '';
                  var codeMap = {
                    37:'ArrowLeft',38:'ArrowUp',39:'ArrowRight',40:'ArrowDown',
                    32:'Space',13:'Enter',9:'Tab',27:'Escape',
                    65:'KeyA',68:'KeyD',69:'KeyE',70:'KeyF',81:'KeyQ',82:'KeyR',
                    83:'KeyS',87:'KeyW',88:'KeyX',90:'KeyZ'
                  };
                  var codeVal = codeMap[code] || '';
                  try {
                    var evt = new KeyboardEvent('keyup', {
                      key: keyVal, code: codeVal, bubbles: true, cancelable: true
                    });
                    Object.defineProperty(evt, 'keyCode', {get: function(){return code;}});
                    Object.defineProperty(evt, 'which', {get: function(){return code;}});
                    document.dispatchEvent(evt);
                  } catch(e) {}
                  delete pressed[kc];
                });
              }
              window.addEventListener('blur', releaseAll);
              document.addEventListener('visibilitychange', function() {
                if (document.hidden) releaseAll();
              });
            })();
        """

        /**
         * 伪造 Flash 插件支持：让 4399 等页面检测到浏览器"有 Flash 插件"。
         * 必须在页面 JS 执行前注入（onPageStarted）。
         * 伪造 navigator.plugins["Shockwave Flash"] 和 navigator.mimeTypes。
         */
        private const val FLASH_FAKE_SUPPORT_SCRIPT = """
            (function(){
              if (window.__flashFaked) return;
              window.__flashFaked = true;
              try {
                // 伪造 navigator.plugins（含 namedItem/item 方法，Ruffle 需要调用）
                var fakePlugin = {
                  name: 'Shockwave Flash',
                  filename: 'libflashplayer.so',
                  description: 'Shockwave Flash 32.0 r0',
                  length: 1,
                  0: { type: 'application/x-shockwave-flash', suffixes: 'swf', description: 'Shockwave Flash' }
                };
                fakePlugin.namedItem = function(n) { return (n === 'Shockwave Flash') ? fakePlugin : null; };
                fakePlugin.item = function(i) { return i === 0 ? fakePlugin : null; };
                fakePlugin.refresh = function() {};
                var plugins = navigator.plugins || {};
                if (plugins.namedItem) { fakePlugin.namedItem = function(n) { return (n === 'Shockwave Flash') ? fakePlugin : plugins.namedItem.call(plugins, n); }; }
                if (plugins.item) { fakePlugin.item = function(i) { return i === 0 ? fakePlugin : plugins.item.call(plugins, i); }; }
                Object.defineProperty(navigator, 'plugins', {
                  get: function() {
                    var p = plugins;
                    if (!p['Shockwave Flash']) {
                      try { p['Shockwave Flash'] = fakePlugin; p[0] = fakePlugin; } catch(e) {}
                    }
                    p.length = Math.max(p.length || 0, 1);
                    return p;
                  },
                  configurable: true
                });
                // 伪造 navigator.mimeTypes
                var fakeMime = { type: 'application/x-shockwave-flash', suffixes: 'swf', description: 'Shockwave Flash', enabledPlugin: fakePlugin };
                var mimes = navigator.mimeTypes || {};
                Object.defineProperty(navigator, 'mimeTypes', {
                  get: function() {
                    if (!mimes['application/x-shockwave-flash']) {
                      try { mimes['application/x-shockwave-flash'] = fakeMime; } catch(e) {}
                    }
                    return mimes;
                  },
                  configurable: true
                });
                // 伪造 ActiveXObject（IE 方式检测 Flash）
                window.ActiveXObject = function(name) {
                  if (name && /ShockwaveFlash/i.test(name)) return { SetVariable: function(){} };
                  throw new Error('Not supported');
                };
                console.log('[Flash] 已伪造 Flash 插件支持');
              } catch(e) { console.warn('[Flash] 伪造失败:', e); }
            })();
        """

        /**
         * iframe 注入监控：4399 游戏常加载在 iframe 中。
         * 监控 iframe 创建，将 Flash 引擎注入到 iframe 内部。
         */
        private const val IFRAME_INJECT_SCRIPT = """
            (function(){
              if (window.__iframeMonitor) return;
              window.__iframeMonitor = true;
              function injectIntoIframe(iframe) {
                try {
                  var doc = iframe.contentDocument || iframe.contentWindow.document;
                  if (!doc || doc.readyState === 'loading') {
                    setTimeout(function(){ injectIntoIframe(iframe); }, 200);
                    return;
                  }
                  // 检查是否已注入
                  if (doc.__flashFaked) return;
                  doc.__flashFaked = true;
                  // 注入 Flash 支持伪造
                  var s1 = doc.createElement('script');
                  s1.textContent = '(' + function(){
                    try {
                      var fp = {name:'Shockwave Flash',filename:'libflashplayer.so',description:'Shockwave Flash 32.0 r0',length:1,
                        0:{type:'application/x-shockwave-flash',suffixes:'swf',description:'Shockwave Flash'}};
                      Object.defineProperty(navigator,'plugins',{get:function(){return fp;},configurable:true});
                      Object.defineProperty(navigator,'mimeTypes',{get:function(){return {'application/x-shockwave-flash':{type:'application/x-shockwave-flash',suffixes:'swf',description:'Shockwave Flash',enabledPlugin:fp}};},configurable:true});
                      window.ActiveXObject = function(n){if(/ShockwaveFlash/i.test(n))return {SetVariable:function(){}};throw new Error('x');};
                    } catch(e){}
                  } + ')();';
                  doc.head.appendChild(s1);
                  // 注入 Ruffle 引擎
                  var engine = window.__ruffleLoaded ? 'ruffle' : (window.__swf2jsLoaded ? 'swf2js' : null);
                  if (engine === 'ruffle') {
                    var s2 = doc.createElement('script');
                    s2.src = 'https://flash.local/ruffle/ruffle.js';
                    s2.onload = function(){
                      try {
                        var r = doc.defaultView.RufflePlayer;
                        if (r && r.newest) { var inst = r.newest(); if (inst && inst.init) inst.init(); }
                      } catch(e){}
                    };
                    doc.head.appendChild(s2);
                  }
                  console.log('[Flash] 已注入 iframe');
                } catch(e) {
                  // 跨域 iframe 无法注入
                }
              }
              // 监控新创建的 iframe
              if (window.MutationObserver) {
                var observer = new MutationObserver(function(mutations){
                  mutations.forEach(function(m){
                    m.addedNodes.forEach(function(node){
                      if (node.tagName === 'IFRAME') injectIntoIframe(node);
                      if (node.querySelectorAll) {
                        var iframes = node.querySelectorAll('iframe');
                        for (var i = 0; i < iframes.length; i++) injectIntoIframe(iframes[i]);
                      }
                    });
                  });
                });
                observer.observe(document.documentElement, {childList: true, subtree: true});
              }
              // 检查已有的 iframe
              var existing = document.querySelectorAll('iframe');
              for (var i = 0; i < existing.length; i++) injectIntoIframe(existing[i]);
            })();
        """

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
         * WAFlash SWF 检测备份脚本：
         * 如果 buildFlashInjectScript 的页内播放已执行（window.__wafPlayInPage 存在），
         * 只补充 DOM 检测；否则自行实现页内播放（不重定向，避免渲染模糊）。
         */
        private const val WAFLASH_DETECT_SCRIPT = """
            (function(){
              if (window.__waflashDetect) return;
              window.__waflashDetect = true;

              // 如果 HTML 注入已定义页内播放函数，直接复用
              if (typeof window.__wafPlayInPage === 'function') {
                // 只需补充检测，hooks 已由 HTML 注入设置
                function checkExisting() {
                  if (window.__wafPlayed) return;
                  var sel = 'object[type="application/x-shockwave-flash"],' +
                    'embed[type="application/x-shockwave-flash"],' +
                    'object[data$=".swf" i],embed[src$=".swf" i],' +
                    'object[classid*="D27CDB6E" i],object[classid*="d27cdb6e" i],' +
                    'embed[type*="flash" i],object[data*=".swf" i],embed[src*=".swf" i]';
                  var els = document.querySelectorAll(sel);
                  for (var i = 0; i < els.length; i++) {
                    var s = els[i].getAttribute('data') || els[i].getAttribute('src') || '';
                    if (!s) {
                      var ps = els[i].querySelectorAll('param[name="movie"],param[name="src"],param[name="data"]');
                      for (var j = 0; j < ps.length; j++) { var v = ps[j].getAttribute('value')||''; if(v){s=v;break;} }
                    }
                    if (s) { window.__wafPlayInPage(s, window.location.href); return; }
                  }
                }
                checkExisting();
                if (window.MutationObserver) {
                  var mo = new MutationObserver(function(){
                    if (window.__wafPlayed) { mo.disconnect(); return; }
                    checkExisting();
                  });
                  try { mo.observe(document.documentElement||document.body||document,{childList:true,subtree:true}); } catch(e){}
                  setTimeout(function(){ mo.disconnect(); }, 15000);
                }
                return;
              }

              // HTML 注入未执行（可能被缓存等原因跳过），自行实现页内播放
              var played = false;
              var engineReady = false;
              var wafModule = null;

              function loadEngine() {
                if (window.__wafEngineLoading) return;
                window.__wafEngineLoading = true;
                import('https://flash.local/waflash/waflash-player.min.js').then(function(module){
                  engineReady = true; wafModule = module;
                  console.log('[WAFlash] 引擎加载完成(备份)');
                  if (window.__pendingSwfUrl) playInPage(window.__pendingSwfUrl, window.__pendingBaseUrl);
                }).catch(function(err){ console.error('[WAFlash] 引擎加载失败: '+(err.message||err)); });
              }

              function playInPage(swfUrl, baseUrl) {
                if (played || !swfUrl) return;
                // 不按 URL 模式过滤，信任 Flash 元素检测结果
                played = true; window.__wafPlayed = true;
                try { swfUrl = new URL(swfUrl, baseUrl || window.location.href).href; } catch(e) {}
                console.log('[WAFlash] 页内播放 SWF(备份): ' + swfUrl);
                if (!engineReady) {
                  window.__pendingSwfUrl = swfUrl; window.__pendingBaseUrl = baseUrl;
                  loadEngine(); return;
                }
                var container = document.createElement('div');
                container.id = '__waflash_container';
                container.style.cssText = 'position:fixed;left:0;top:0;width:100%;height:100%;z-index:99999;background:#000;';
                var canvas = document.createElement('canvas');
                canvas.className = 'waflashCanvas'; canvas.id = 'canvas';
                canvas.style.cssText = 'width:100%;height:100%;display:block;outline:none;';
                canvas.setAttribute('tabindex','1');
                container.appendChild(canvas);
                var bc = document.body.children;
                for (var i = 0; i < bc.length; i++) { if(bc[i]!==container){try{bc[i].style.display='none';}catch(e){}} }
                document.body.appendChild(container); canvas.focus();
                if (baseUrl) {
                  try { var bo=new URL(baseUrl); var bt=document.createElement('base');
                    bt.href=bo.origin+bo.pathname.substring(0,bo.pathname.lastIndexOf('/')+1);
                    document.head.appendChild(bt);
                  } catch(e) {}
                }
                var fn = (wafModule && wafModule.createWaflash) || window.createWaflash;
                if (fn) {
                  try { fn(swfUrl, {gpu:true}); console.log('[WAFlash] 页内播放已启动(备份)'); }
                  catch(e) {
                    console.error('[WAFlash] 播放失败: '+e.message);
                    if (window.Android && window.Android.openSwf) window.Android.openSwf(swfUrl, baseUrl||window.location.href);
                  }
                } else { console.log('[WAFlash] createWaflash 未找到'); }
              }

              loadEngine();

              // Hook swfobject.embedSWF
              if (window.swfobject && window.swfobject.embedSWF) {
                var oe = window.swfobject.embedSWF;
                window.swfobject.embedSWF = function(){playInPage(arguments[0],window.location.href);return oe.apply(this,arguments);};
              } else {
                var _swo; Object.defineProperty(window,'swfobject',{configurable:true,get:function(){return _swo;},set:function(v){_swo=v;if(v&&v.embedSWF){var o=v.embedSWF;v.embedSWF=function(){playInPage(arguments[0],window.location.href);return o.apply(this,arguments);};}}});
              }
              // Hook AC_FL_RunContent
              if (window.AC_FL_RunContent) {
                var oAC = window.AC_FL_RunContent;
                window.AC_FL_RunContent = function(){var a=Array.prototype.slice.call(arguments);for(var i=0;i<a.length-1;i++){if((a[i]==='src'||a[i]==='movie')&&a[i+1])playInPage(a[i+1],window.location.href);}return oAC.apply(this,arguments);};
              }
              // Hook createFlash (mflash-player)
              function hookCF(obj){if(!obj||!obj.createFlash||obj.__wafH)return;obj.__wafH=true;var o=obj.createFlash;obj.createFlash=function(){var u=null;if(typeof arguments[0]==='string')u=arguments[0];else if(arguments[0]&&typeof arguments[0]==='object')u=arguments[0].url||arguments[0].src||arguments[0].swf||arguments[0].movie;if(u)playInPage(u,window.location.href);return o.apply(this,arguments);};}
              if(window['mflash-player'])hookCF(window['mflash-player']);
              if(window.mflashplayer)hookCF(window.mflashplayer);
              var _mp;Object.defineProperty(window,'mflash-player',{configurable:true,get:function(){return _mp;},set:function(v){_mp=v;hookCF(v);}});
              var _mp2;Object.defineProperty(window,'mflashplayer',{configurable:true,get:function(){return _mp2;},set:function(v){_mp2=v;hookCF(v);}});

              // hook document.write / writeln — 老页面常用 document.write 创建 Flash
              var _dw = document.write.bind(document);
              document.write = function(){ _dw.apply(document, arguments); setTimeout(function(){ checkExisting(); }, 0); };
              var _dwln = document.writeln.bind(document);
              document.writeln = function(){ _dwln.apply(document, arguments); setTimeout(function(){ checkExisting(); }, 0); };
              // hook innerHTML / insertAdjacentHTML / outerHTML
              var _fhp = /shockwave|\.swf|D27CDB6E|application\/x-shockwave/i;
              var _id = Object.getOwnPropertyDescriptor(Element.prototype, 'innerHTML');
              if (_id && _id.set) { var _os = _id.set; Object.defineProperty(Element.prototype, 'innerHTML', { get: _id.get, set: function(v){ _os.call(this, v); if(v && _fhp.test(v)) setTimeout(function(){ checkExisting(); }, 0); }, configurable: true }); }
              var _iah = Element.prototype.insertAdjacentHTML;
              Element.prototype.insertAdjacentHTML = function(p, t){ _iah.call(this, p, t); if(t && _fhp.test(t)) setTimeout(function(){ checkExisting(); }, 0); };
              var _ohd = Object.getOwnPropertyDescriptor(Element.prototype, 'outerHTML');
              if (_ohd && _ohd.set) { var _ooh = _ohd.set; Object.defineProperty(Element.prototype, 'outerHTML', { get: _ohd.get, set: function(v){ _ooh.call(this, v); if(v && _fhp.test(v)) setTimeout(function(){ checkExisting(); }, 0); }, configurable: true }); }

              // 检测已有 Flash 元素
              function checkExisting() {
                if (played) return;
                var sel = 'object[type="application/x-shockwave-flash"],' +
                  'embed[type="application/x-shockwave-flash"],' +
                  'object[data$=".swf" i],embed[src$=".swf" i],' +
                  'object[classid*="D27CDB6E" i],object[classid*="d27cdb6e" i],' +
                  'embed[type*="flash" i],object[data*=".swf" i],embed[src*=".swf" i]';
                var els = document.querySelectorAll(sel);
                for (var i = 0; i < els.length; i++) {
                  var s = els[i].getAttribute('data')||els[i].getAttribute('src')||'';
                  if (!s) { var ps=els[i].querySelectorAll('param[name="movie"],param[name="src"],param[name="data"]');for(var j=0;j<ps.length;j++){var v=ps[j].getAttribute('value')||'';if(v){s=v;break;}} }
                  if (s) { playInPage(s, window.location.href); return; }
                }
              }
              checkExisting();
              if (window.MutationObserver) {
                var mo = new MutationObserver(function(){ if(played){mo.disconnect();return;} checkExisting(); });
                try { mo.observe(document.documentElement||document.body||document,{childList:true,subtree:true}); } catch(e){}
                setTimeout(function(){ mo.disconnect(); }, 15000);
              }
            })();
        """
    }
}

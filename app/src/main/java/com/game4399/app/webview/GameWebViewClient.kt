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
        //    URL 模式 + Accept header 双重检测
        //    注意：必须先去掉 query 参数再匹配，否则 trace.js?uddd=...flash/35744.htm 会误匹配
        val urlNoQuery = url.substringBefore("?").substringBefore("#")
        val acceptHeader = request.requestHeaders?.get("Accept") ?: ""
        // 排除 HTML 页面（.htm/.html），这些是游戏页面不是 SWF
        val isHtmlPage = urlNoQuery.endsWith(".htm", ignoreCase = true) ||
            urlNoQuery.endsWith(".html", ignoreCase = true) ||
            urlNoQuery.endsWith(".php", ignoreCase = true) ||
            urlNoQuery.endsWith(".asp", ignoreCase = true) ||
            urlNoQuery.endsWith(".jsp", ignoreCase = true) ||
            urlNoQuery.endsWith(".js", ignoreCase = true) ||
            urlNoQuery.endsWith(".css", ignoreCase = true)
        val isSwfRequest = (!isHtmlPage) && (
            urlNoQuery.endsWith(".swf", ignoreCase = true) ||
            urlNoQuery.contains("/swf/", ignoreCase = true) ||
            urlNoQuery.contains("flashgame", ignoreCase = true) ||
            acceptHeader.contains("application/x-shockwave-flash", ignoreCase = true) ||
            // 4399 特有的 SWF URL 模式（只匹配 path，不匹配 query）
            (urlNoQuery.contains("4399.com") && (urlNoQuery.contains("/dw-") || urlNoQuery.contains("flash_tm3") || urlNoQuery.contains("flash20")))
        )
        if (isSwfRequest) {
            return interceptSwf(url)
        }

        // 6. 拦截 HTML 页面：注入 Flash 支持伪造 + 引擎脚本到 <head>
        //    evaluateJavascript 是异步的，页面 JS 可能先执行导致检测失败
        //    直接修改 HTML 保证脚本在页面 JS 之前运行（含 iframe 内容）
        val isFlashPage = PrefsManager.isFlashEnabled && callback.shouldInjectRuffle(url)
        if (isFlashPage) {
            return interceptHtml(view, url, request)
        }

        // For HTTP URLs, make the request ourselves to bypass WebView cleartext restriction.
        // WebView's internal network stack may block HTTP traffic even with network_security_config
        // allowing it. HttpURLConnection respects the app's network security config.
        if (url.startsWith("http://")) {
            return fetchHttpUrl(url, request) ?: super.shouldInterceptRequest(view, request)
        }

        return super.shouldInterceptRequest(view, request)
    }

    /** 拦截 HTML 页面，注入 Flash 伪造 + Ruffle 引擎到 <head> */
    private fun interceptHtml(view: WebView, url: String, request: WebResourceRequest): WebResourceResponse? {
        return try {
            // For HTTP URLs, use our own HTTP client to bypass WebView cleartext restriction
            val response = if (url.startsWith("http://")) {
                fetchHttpUrl(url, request) ?: return null
            } else {
                super.shouldInterceptRequest(view, request) ?: return null
            }
            // 检查 content-type
            val contentType = response.mimeType ?: ""
            // 如果响应实际上是 SWF 文件（URL 不含 .swf 但服务器返回了 SWF），按 SWF 处理
            if (contentType.contains("application/x-shockwave-flash") || contentType.contains("x-shockwave-flash")) {
                android.util.Log.d("GameWebViewClient", "检测到 SWF 响应 (content-type): $url")
                val data = response.data?.readBytes() ?: return response
                return WebResourceResponse(
                    "application/x-shockwave-flash", null, 200, "OK",
                    mapOf(
                        "Access-Control-Allow-Origin" to "*",
                        "Content-Type" to "application/x-shockwave-flash",
                        "Cache-Control" to "no-cache"
                    ),
                    java.io.ByteArrayInputStream(data)
                )
            }
            // 检查 SWF 魔术字节（FWS/CWS/ZWS 开头）
            val data = response.data
            if (data != null && data.markSupported()) {
                data.mark(3)
                val magic = ByteArray(3)
                data.read(magic)
                data.reset()
                if (magic[0] == 'F'.code.toByte() || magic[0] == 'C'.code.toByte() || magic[0] == 'Z'.code.toByte()) {
                    if (magic[1] == 'W'.code.toByte() && magic[2] == 'S'.code.toByte()) {
                        android.util.Log.d("GameWebViewClient", "检测到 SWF 魔术字节: $url")
                        val swfData = data.readBytes()
                        return WebResourceResponse(
                            "application/x-shockwave-flash", null, 200, "OK",
                            mapOf(
                                "Access-Control-Allow-Origin" to "*",
                                "Content-Type" to "application/x-shockwave-flash",
                                "Cache-Control" to "no-cache"
                            ),
                            java.io.ByteArrayInputStream(swfData)
                        )
                    }
                }
            }
            if (!contentType.contains("text/html") && !contentType.contains("application/xhtml")) {
                return response
            }
            // 读取 HTML 内容
            val encoding = response.encoding ?: "UTF-8"
            val html = data?.bufferedReader(java.nio.charset.Charset.forName(encoding))?.readText() ?: return response

            // 构建注入脚本
            val injectScript = buildFlashInjectScript(url)

            // 在 <head> 或 <html> 后注入（避免正则替换问题）
            val modifiedHtml: String
            val headIdx = html.indexOf("<head", ignoreCase = true)
            if (headIdx >= 0) {
                val tagEnd = html.indexOf(">", headIdx)
                modifiedHtml = if (tagEnd >= 0) {
                    html.substring(0, tagEnd + 1) + injectScript + html.substring(tagEnd + 1)
                } else { injectScript + html }
            } else {
                val htmlIdx = html.indexOf("<html", ignoreCase = true)
                modifiedHtml = if (htmlIdx >= 0) {
                    val tagEnd = html.indexOf(">", htmlIdx)
                    if (tagEnd >= 0) html.substring(0, tagEnd + 1) + injectScript + html.substring(tagEnd + 1)
                    else injectScript + html
                } else {
                    injectScript + html
                }
            }

            android.util.Log.d("GameWebViewClient", "HTML 注入成功: $url (${modifiedHtml.length} chars)")

            WebResourceResponse(
                "text/html", "UTF-8", 200, "OK",
                mapOf("Access-Control-Allow-Origin" to "*"),
                java.io.ByteArrayInputStream(modifiedHtml.toByteArray(Charsets.UTF_8))
            )
        } catch (e: Exception) {
            android.util.Log.w("GameWebViewClient", "HTML 注入失败: ${e.message}")
            null
        }
    }

    /** 使用 HttpURLConnection 获取 HTTP URL，绕过 WebView 明文限制 */
    private fun fetchHttpUrl(url: String, request: WebResourceRequest): WebResourceResponse? {
        return try {
            // 游戏页面用 Chrome 87 UA（让 4399 等返回含 Flash 元素的兼容版页面），
            // 非游戏页面用 Chrome 120（保留极速模式）
            val ua = if (isGamePageUrl(url)) {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.141 Safari/537.36"
            } else {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            }
            val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                if (this is javax.net.ssl.HttpsURLConnection) {
                    sslSocketFactory = trustAllSslSocketFactory()
                    hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                }
                connectTimeout = 15000
                readTimeout = 20000
                requestMethod = request.method ?: "GET"
                instanceFollowRedirects = true
                // 先复制原始请求头
                request.requestHeaders?.forEach { (key, value) -> setRequestProperty(key, value) }
                // 再用游戏页面 UA 覆盖（必须在复制请求头之后）
                setRequestProperty("User-Agent", ua)
            }
            conn.connect()
            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val contentType = conn.contentType ?: ""
                val (mime, charset) = parseContentType(contentType)
                val data = conn.inputStream.readBytes()
                val headers = mutableMapOf<String, String>()
                conn.headerFields.forEach { (key, values) ->
                    if (key != null && values.isNotEmpty()) headers[key] = values.joinToString(", ")
                }
                WebResourceResponse(mime, charset, responseCode, conn.responseMessage ?: "OK", headers, java.io.ByteArrayInputStream(data))
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.w("GameWebViewClient", "fetchHttpUrl 失败: ${e.message}")
            null
        }
    }

    /** 解析 Content-Type 字符串，返回 mimeType 和 charset */
    private fun parseContentType(contentType: String): Pair<String, String?> {
        if (contentType.isBlank()) return "text/html" to "UTF-8"
        val parts = contentType.split(";").map { it.trim() }
        val mime = parts[0]
        var charset: String? = null
        for (part in parts.drop(1)) {
            if (part.startsWith("charset=", ignoreCase = true)) {
                charset = part.substringAfter("charset=").trim().trim('"')
            }
        }
        return mime to charset
    }

    /** 判断是否为游戏页面（非主页/列表页），用于决定是否降级 UA */
    private fun isGamePageUrl(url: String): Boolean {
        val urlNoQuery = url.substringBefore("?").substringBefore("#")
        // 4399 主页和列表页 — 不降级 UA，保留极速模式
        if (urlNoQuery.endsWith("4399.com/") || urlNoQuery.endsWith("4399.com")) return false
        if (urlNoQuery.endsWith("/flash/") || urlNoQuery.endsWith("/flash")) return false
        if (urlNoQuery.endsWith("/category") || urlNoQuery.endsWith("/category/")) return false
        // 游戏页面特征：含 .htm/.html、/flash/数字、play. 子域名等
        if (urlNoQuery.contains(".htm", ignoreCase = true) || urlNoQuery.contains(".html", ignoreCase = true)) return true
        if (urlNoQuery.contains("play.", ignoreCase = true)) return true
        if (urlNoQuery.contains("/flash/") && urlNoQuery.substringAfterLast("/").isNotEmpty()) return true
        if (urlNoQuery.contains("game", ignoreCase = true)) return true
        // 其他站点默认按游戏页面处理（Flash 注入已由 shouldInjectRuffle 过滤）
        return true
    }

    /** 构建 Flash 支持伪造 + Ruffle/WAFlash 注入脚本 */
    private fun buildFlashInjectScript(pageUrl: String): String {
        val isWaflash = PrefsManager.flashEngine == "waflash"
        // 仅对游戏页面降级 UA（让 4399 Flash 检测通过），主页/列表页保持 Chrome 120（保留极速模式）
        val isGamePage = isGamePageUrl(pageUrl)
        return """
        <script>
        (function(){
          // 标记 HTML 注入已执行（防止 onPageStarted 的 evaluateJavascript 重复注入）
          window.__flashFaked = true;
          window.__flashHideInjected = true;
          // === 1. 伪造 Flash 插件支持（必须在页面 JS 之前） ===
          try {
            var fp = {name:'Shockwave Flash',filename:'libflashplayer.so',description:'Shockwave Flash 32.0 r0',length:1,
              0:{type:'application/x-shockwave-flash',suffixes:'swf',description:'Shockwave Flash'}};
            fp.namedItem = function(n){ return (n === 'Shockwave Flash') ? fp : null; };
            fp.item = function(i){ return i === 0 ? fp : null; };
            fp.refresh = function(){};
            // 修改原生 navigator.plugins（保持 PluginArray 原型，Ruffle 需要检测）
            var _plugins = navigator.plugins || {};
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
          //         主页/列表页不降级，保留极速模式
          ${if (isGamePage) """
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

          // === 2. 伪造 document.referrer ===
          try {
            Object.defineProperty(document,'referrer',{get:function(){return 'https://www.4399.com/';},configurable:true});
          } catch(e) {}

          // === 2.5 自动关闭 4399 "不支持 Flash" 弹窗 ===
          //     4399 检测到"无 Flash 插件"会弹出模态框，自动关闭
          (function(){
            var flashKeywords = ['不支持打开游戏', 'Flash官方插件', '兼容模式', '继续游戏',
              '不支持flash', '极速模式', 'QQ浏览器', '搜狗浏览器', '360浏览器',
              'EDGE浏览器请按教程', '无需下载插件打开即玩', '为您提供以下方案',
              '当前浏览器或模式不支持', '下载官方Flash', '下载flash', 'flash插件',
              'web新游专区', '4399游戏大厅', '请使用以下浏览器', 'PPAPI', 'NPAPI'];
            // 已知 4399 弹窗 CSS 选择器
            var popupSelectors = '.flash_tips,.flash-tips,#flash_tips,#flash-tips,.no_flash,.no-flash,#no_flash,#no-flash,.browser_tip,.browser-tip,#browser_tip,.unsupported,.unsupport,#unsupported,.game_tips,.game-tips,#game_tips,.alert_flash,.alert-flash,#alert_flash,#flashmsg,.flashmsg,#flash_msg,.pop_flash,.pop-flash,#pop_flash,.modal-flash,#modal-flash,.compatible_tip,.compatible-tip,.flash_prompt,.flash-prompt,#flash_prompt';
            function closeInDoc(doc) {
              if (!doc) return;
              try {
                // 方式0：直接隐藏已知 4399 弹窗 CSS 选择器
                doc.querySelectorAll(popupSelectors).forEach(function(el){
                  el.style.display = 'none';
                  try { el.remove(); } catch(e){}
                });
                // 方式1：点击所有可见的关闭按钮
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
                // 方式2：遍历所有元素，移除包含关键词的弹窗
                var all = doc.querySelectorAll('div, section, aside, table, ul, li');
                for (var i = 0; i < all.length; i++) {
                  var el = all[i];
                  var text = el.textContent || '';
                  var matchCount = 0;
                  for (var k = 0; k < flashKeywords.length; k++) {
                    if (text.indexOf(flashKeywords[k]) >= 0) matchCount++;
                  }
                  // 匹配2个以上关键词的元素 = 弹窗本身，移除它
                  // 不限制 text.length，4399 页游弹窗内容可能很长
                  if (matchCount >= 2) {
                    el.remove();
                    console.log('[Flash] 已移除不支持Flash弹窗 (匹配' + matchCount + '个关键词)');
                  }
                }
                // 方式3：移除遮罩层
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
              // 也检查 iframe 内部
              var iframes = document.querySelectorAll('iframe');
              for (var i = 0; i < iframes.length; i++) {
                try { closeInDoc(iframes[i].contentDocument); } catch(e) {}
              }
            }
            // 高频检查：200ms间隔，持续10秒
            var checkCount = 0;
            var interval = setInterval(function(){
              closeFlashDialog();
              checkCount++;
              if (checkCount > 50) clearInterval(interval); // 50 * 200ms = 10s
            }, 200);
            // MutationObserver 持续监控
            if (window.MutationObserver) {
              var obs = new MutationObserver(function(){ closeFlashDialog(); });
              try { obs.observe(document.documentElement, {childList:true, subtree:true}); } catch(e){}
              setTimeout(function(){ obs.disconnect(); }, 15000);
            }
          })();

          // === 2.8 字体别名注入：通过 @font-face 将中文字体名映射到 Android 系统的 CJK 字体 ===
          //      Ruffle/WAFlash 渲染时通过浏览器 FontFace API 查找字体
          //      这些别名让 "SimHei"、"宋体"、"Microsoft YaHei" 等都能找到 Noto Sans CJK SC
          (function(){
            if (window.__fontAliasInjected) return;
            window.__fontAliasInjected = true;
            var fontAliases = {
              'SimHei': ['Noto Sans CJK SC','Noto Sans SC','Source Han Sans SC','WenQuanYi Micro Hei','Droid Sans Fallback','sans-serif'],
              '黑体': ['Noto Sans CJK SC','Noto Sans SC','Source Han Sans SC','WenQuanYi Micro Hei','Droid Sans Fallback','sans-serif'],
              'Microsoft YaHei': ['Noto Sans CJK SC','Noto Sans SC','Source Han Sans SC','PingFang SC','WenQuanYi Micro Hei','Droid Sans Fallback','sans-serif'],
              '微软雅黑': ['Noto Sans CJK SC','Noto Sans SC','Source Han Sans SC','WenQuanYi Micro Hei','Droid Sans Fallback','sans-serif'],
              'SimSun': ['Noto Serif CJK SC','Noto Serif SC','Source Han Serif SC','Song','STSong','Noto Sans CJK SC','Droid Sans Fallback','serif'],
              '宋体': ['Noto Serif CJK SC','Noto Serif SC','Source Han Serif SC','Song','STSong','Noto Sans CJK SC','Droid Sans Fallback','serif'],
              'KaiTi': ['Noto Sans CJK SC','KaiTi SC','STKaiti','Droid Sans Fallback','sans-serif'],
              '楷体': ['Noto Sans CJK SC','KaiTi SC','STKaiti','Droid Sans Fallback','sans-serif'],
              'FangSong': ['Noto Serif CJK SC','STFangsong','Noto Sans CJK SC','Droid Sans Fallback','serif'],
              '仿宋': ['Noto Serif CJK SC','STFangsong','Noto Sans CJK SC','Droid Sans Fallback','serif'],
              'Arial': ['Arial','Noto Sans CJK SC','Noto Sans SC','Droid Sans Fallback','sans-serif'],
              'Tahoma': ['Tahoma','Noto Sans CJK SC','Noto Sans SC','Droid Sans Fallback','sans-serif'],
              'Helvetica': ['Helvetica','Noto Sans CJK SC','Noto Sans SC','Droid Sans Fallback','sans-serif']
            };
            var css = '';
            for (var name in fontAliases) {
              var src = fontAliases[name].map(function(f){ return "local('"+f+"')"; }).join(',');
              css += "@font-face{font-family:'"+name+"';src:"+src+";font-weight:normal;font-style:normal;}";
              var boldSrc = fontAliases[name].map(function(f){ return "local('"+f+" Bold')"; }).concat(fontAliases[name].map(function(f){ return "local('"+f+"')"; })).join(',');
              css += "@font-face{font-family:'"+name+"';src:"+boldSrc+";font-weight:bold;font-style:normal;}";
            }
            var style = document.createElement('style');
            style.textContent = css;
            (document.head || document.documentElement).appendChild(style);
            console.log('[Font] 已注入 ' + Object.keys(fontAliases).length + ' 个字体别名');
          })();

          // === 3. Flash 引擎注入（Ruffle polyfill 或 WAFlash 页内播放） ===
          ${if (!isWaflash) """
          // --- Ruffle polyfill 模式 ---
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
            logLevel: 'warn',
            defaultFonts: {
              sans: ['Noto Sans CJK SC', 'Noto Sans SC', 'SimHei', 'Microsoft YaHei', 'WenQuanYi Micro Hei', 'Droid Sans Fallback', 'sans-serif'],
              serif: ['Noto Serif CJK SC', 'Noto Serif SC', 'SimSun', '宋体', 'STSong', 'Noto Sans CJK SC', 'serif'],
              typewriter: ['Noto Sans CJK SC', 'Courier New', 'monospace']
            }
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
          window.__waflashDetect = true;  // 防止 WAFLASH_DETECT_SCRIPT 备份脚本重复执行
          var __wafPlayed = false;
          var __wafEngineReady = false;
          var __wafModule = null;
          var __wafUseGpu = true;

          // WebGL 可用性检测（某些 Android 设备 WebGL 不完整）
          function __wafCheckWebGL() {
            try {
              var c = document.createElement('canvas');
              var gl = c.getContext('webgl') || c.getContext('experimental-webgl');
              return !!gl;
            } catch(e) { return false; }
          }
          __wafUseGpu = __wafCheckWebGL();
          console.log('[WAFlash] WebGL 支持检测: ' + (__wafUseGpu ? 'webgl' : 'Canvas2D'));

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
            // 设置 canvas 物理尺寸（WAFlash 需要非零尺寸才能正确渲染）
            var cw = window.innerWidth || document.documentElement.clientWidth || 800;
            var ch = window.innerHeight || document.documentElement.clientHeight || 600;
            canvas.width = cw;
            canvas.height = ch;
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
            // createWaflash(swfUrl, options)
            // options.gpu: true=WebGL, false=Canvas2D
            // options.enableFilters: true=启用滤镜
            var fn = (__wafModule && __wafModule.createWaflash) || window.createWaflash;
            if (fn) {
              try {
                fn(swfUrl, { gpu: __wafUseGpu, enableFilters: true });
                console.log('[WAFlash] 页内播放已启动: ' + swfUrl + ' (gpu=' + __wafUseGpu + ')');
              } catch(e) {
                console.error('[WAFlash] 播放失败(WebGL): ' + e.message + '，尝试回退到 Canvas2D');
                // WebGL 失败时回退到 Canvas2D
                try {
                  __wafUseGpu = false;
                  fn(swfUrl, { gpu: false, enableFilters: true });
                  console.log('[WAFlash] Canvas2D 回退成功');
                } catch(e2) {
                  console.error('[WAFlash] Canvas2D 也失败: ' + e2.message);
                  // 回退到独立播放器页面
                  if (window.Android && window.Android.openSwf) {
                    window.Android.openSwf(swfUrl, baseUrl || window.location.href);
                  }
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

    /** 读取本地 SWF 文件代理（flash.local/local.swf → 真实 content:// URI） */
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
            val data = view.context.contentResolver.openInputStream(parsed)?.use { it.readBytes() }
                ?: throw java.io.IOException("无法打开文件流")
            android.util.Log.d("GameWebViewClient", "local.swf 读取完成: ${data.size} bytes")
            WebResourceResponse(
                "application/x-shockwave-flash", null, 200, "OK",
                mapOf(
                    "Access-Control-Allow-Origin" to "*",
                    "Content-Type" to "application/x-shockwave-flash",
                    "Cache-Control" to "no-cache"
                ),
                java.io.ByteArrayInputStream(data)
            )
        } catch (e: Exception) {
            android.util.Log.e("GameWebViewClient", "local.swf 读取失败: ${e.message}")
            WebResourceResponse("application/x-shockwave-flash", null, 404, "Not Found",
                mapOf("Access-Control-Allow-Origin" to "*"), java.io.ByteArrayInputStream(ByteArray(0)))
        }
    }

    /** 读取本地 SWF 文件（content:// 或 file://），返回带 CORS 头的响应 */
    private fun interceptLocalFile(view: WebView, url: String): WebResourceResponse? {
        return try {
            android.util.Log.d("GameWebViewClient", "读取本地文件: $url")
            val uri = android.net.Uri.parse(url)
            val data = view.context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw java.io.IOException("无法打开文件流")
            android.util.Log.d("GameWebViewClient", "本地文件读取完成: ${data.size} bytes")
            WebResourceResponse(
                "application/x-shockwave-flash", null,
                200, "OK",
                mapOf(
                    "Access-Control-Allow-Origin" to "*",
                    "Content-Type" to "application/x-shockwave-flash",
                    "Cache-Control" to "no-cache"
                ),
                java.io.ByteArrayInputStream(data)
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

    /** 原生下载 SWF 文件，返回带 CORS 头的响应（含重试 + SSL 兼容 + CORS 兜底） */
    private fun interceptSwf(url: String): WebResourceResponse? {
        // HTTP → HTTPS 升级，避免混合内容和重定向问题
        val swfUrl = if (url.startsWith("http://")) "https://" + url.substring(7) else url
        var lastError: Exception? = null
        for (attempt in 1..3) {
            try {
                android.util.Log.d("GameWebViewClient", "拦截 SWF 请求 (尝试 $attempt): $swfUrl")
                val conn = java.net.URL(swfUrl).openConnection() as java.net.HttpURLConnection
                // HTTPS: 信任所有证书，防止 SSL 握手失败导致 SWF 下载不了
                if (conn is javax.net.ssl.HttpsURLConnection) {
                    conn.sslSocketFactory = trustAllSslSocketFactory()
                    conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                }
                conn.connectTimeout = 10000
                conn.readTimeout = 20000
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.141 Safari/537.36")
                conn.setRequestProperty("Accept", "*/*")
                if (swfUrl.contains("4399.com")) {
                    conn.setRequestProperty("Referer", "https://www.4399.com/")
                }
                conn.instanceFollowRedirects = true
                conn.connect()
                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    val data = conn.inputStream.readBytes()
                    android.util.Log.d("GameWebViewClient", "SWF 下载完成: ${data.size} bytes, URL=$swfUrl")
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
                    android.util.Log.w("GameWebViewClient", "SWF 下载失败: HTTP $responseCode, URL=$swfUrl")
                    lastError = RuntimeException("HTTP $responseCode")
                }
            } catch (e: Exception) {
                lastError = e
                android.util.Log.w("GameWebViewClient", "SWF 下载异常 (尝试 $attempt): ${e.message}")
                if (attempt < 3) Thread.sleep(500L * attempt)
            }
        }
        android.util.Log.e("GameWebViewClient", "SWF 下载最终失败: ${lastError?.message}, URL=$swfUrl")
        // 不返回 null！返回带 CORS 头的空响应，让 WAFlash 知道请求被处理但数据为空
        // 这样 WAFlash 会报错而不是无限等待
        return WebResourceResponse(
            "application/x-shockwave-flash", null,
            404, "Not Found",
            mapOf("Access-Control-Allow-Origin" to "*"),
            java.io.ByteArrayInputStream(ByteArray(0))
        )
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
            if (PrefsManager.uaMode == "ie_compat") {
                view?.evaluateJavascript(IE_COMPAT_SCRIPT, null)
            }
        }

        val isFlashPage = PrefsManager.isFlashEnabled && callback.shouldInjectRuffle(url)

        // Flash 页面：最先注入 Flash 支持伪造（在页面 JS 执行前）
        // 让 4399 检测到浏览器"有 Flash 插件"，从而创建 <object> 元素
        // 之后 Ruffle polyfill 会替换 <object> 为 Canvas 播放器
        if (isFlashPage) {
            view?.evaluateJavascript(FLASH_FAKE_SUPPORT_SCRIPT, null)
            view?.evaluateJavascript(RuffleInjector.configScript(), null)
            view?.evaluateJavascript(RuffleInjector.loaderScript(), null)
            view?.evaluateJavascript(FLASH_HIDE_SCRIPT, null)
            if (PrefsManager.flashEngine == "waflash") {
                view?.evaluateJavascript(WAFLASH_DETECT_SCRIPT, null)
            }
            // 注入 iframe 监控：游戏可能加载在 iframe 中
            view?.evaluateJavascript(IFRAME_INJECT_SCRIPT, null)
        }

        // 所有页面注入 viewport + CSS zoom 缩放（Flash 播放器页面除外）
        if (url != null) {
            view?.evaluateJavascript(buildViewportScript(url), null)
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
        // 所有页面注入缩放（Flash 播放器页面除外）
        if (url != null) {
            view?.evaluateJavascript(buildViewportScript(url), null)
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
        // 所有页面注入缩放（页面加载完成后再次应用，防止被页面 JS 覆盖）
        if (url != null) {
            view?.evaluateJavascript(buildViewportScript(url), null)
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

    /**
     * 根据用户缩放设置构建 viewport + CSS zoom 脚本。
     * Flash 播放器页面（flash.local）不应用 CSS zoom，否则 Ruffle/WAFlash 的
     * canvas 坐标映射会出错，导致点击位置与实际位置不同步。
     */
    private fun buildViewportScript(url: String? = null): String {
        // Flash 播放器页面已有自己的 viewport 和布局，不注入额外缩放
        if (url != null && url.contains("flash.local")) return ""

        val scale = if (PrefsManager.pageZoomMode == "manual") {
            PrefsManager.pageZoomManual / 100.0
        } else {
            -1.0
        }
        return if (scale > 0) {
            // 手动缩放：viewport meta + CSS zoom 双重生效
            // viewport meta 对响应式页面生效，CSS zoom 对固定布局桌面页面生效
            """
            (function(){
              var s = $scale;
              // 1. viewport meta
              var meta = document.querySelector('meta[name="viewport"]');
              if (!meta) { meta = document.createElement('meta'); meta.name='viewport'; document.head.appendChild(meta); }
              meta.content = 'width=device-width, initial-scale=' + s + ', minimum-scale=' + s + ', maximum-scale=5.0, user-scalable=yes';
              // 2. CSS zoom（对桌面固定布局页面有效，viewport meta 无效时兜底）
              document.documentElement.style.zoom = s;
              // 3. 监听 DOM 变化，确保动态加载的内容也应用 zoom
              if (!window.__zoomObserver) {
                window.__zoomObserver = true;
                var applyZoom = function() { document.documentElement.style.zoom = s; };
                if (window.MutationObserver) {
                  var mo = new MutationObserver(function(){ applyZoom(); });
                  try { mo.observe(document.documentElement, {childList:true, subtree:true, attributes:true, attributeFilter:['style','class']}); } catch(e){}
                  setTimeout(function(){ mo.disconnect(); }, 5000);
                }
              }
            })();
            """.trimIndent()
        } else {
            // 自动缩放：根据屏幕宽度适配桌面页面
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
                // 修改原生 navigator.plugins（保持 PluginArray 原型，Ruffle 需要检测）
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
                // 注意：不在此处降级 navigator.userAgent
                // UA 降级由 buildFlashInjectScript 按游戏页面条件执行
                // 此脚本是备份（evaluateJavascript），对主页也会执行，降级会导致极速模式丢失
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
              // 注意：不覆盖 navigator.plugins！
              // FLASH_FAKE_SUPPORT_SCRIPT 和 buildFlashInjectScript 已设置正确的伪造，
              // 覆盖会导致 Ruffle polyfill 检测失败（namedItem 返回不完整对象）
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
         * WAFlash SWF 检测脚本（备份方法，HTML 注入为主）：
         * 页内播放 SWF，不重定向到独立播放器，保持游戏网络上下文。
         * 如果 HTML 注入已定义 __wafPlayInPage，则直接复用。
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
              var wafUseGpu = true;

              // WebGL 可用性检测
              function checkWGL() {
                try {
                  var c = document.createElement('canvas');
                  var gl = c.getContext('webgl') || c.getContext('experimental-webgl');
                  return !!gl;
                } catch(e) { return false; }
              }
              wafUseGpu = checkWGL();
              console.log('[WAFlash] WebGL 检测(备份): ' + (wafUseGpu ? 'webgl' : 'Canvas2D'));

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
                // 设置 canvas 物理尺寸
                var cw = window.innerWidth || document.documentElement.clientWidth || 800;
                var ch = window.innerHeight || document.documentElement.clientHeight || 600;
                canvas.width = cw; canvas.height = ch;
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
                  try { fn(swfUrl, {gpu: wafUseGpu, enableFilters: true}); console.log('[WAFlash] 页内播放已启动(备份) gpu=' + wafUseGpu); }
                  catch(e) {
                    console.error('[WAFlash] WebGL失败(备份): '+e.message+'，回退Canvas2D');
                    try { wafUseGpu = false; fn(swfUrl, {gpu: false, enableFilters: true}); console.log('[WAFlash] Canvas2D回退成功(备份)'); }
                    catch(e2) {
                      console.error('[WAFlash] Canvas2D也失败(备份): '+e2.message);
                      if (window.Android && window.Android.openSwf) window.Android.openSwf(swfUrl, baseUrl||window.location.href);
                    }
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

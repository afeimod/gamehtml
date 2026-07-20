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
        // 尽早注入：屏蔽"没有 Flash 插件"提示 + 预置 Ruffle 配置
        // 4399 PC Flash 页在 DOM 构建阶段就会检测 Flash 插件并显示提示，
        // 等 onPageFinished 再注入 Ruffle 就太晚了
        if (PrefsManager.isFlashEnabled && callback.shouldInjectRuffle(url)) {
            view?.evaluateJavascript(RuffleInjector.configScript(), null)
            view?.evaluateJavascript(FLASH_HIDE_SCRIPT, null)
        }
        // 注入 viewport 缩放：让宽 PC 页面适配屏幕，支持双指缩放
        view?.evaluateJavascript(VIEWPORT_SCRIPT, null)
    }

    override fun onPageCommitVisible(view: WebView?, url: String?) {
        super.onPageCommitVisible(view, url)
        // DOM 已构建但页面还在加载中：此时注入 Ruffle 可抢在"无Flash"提示渲染前替换 <object>/<embed>
        if (PrefsManager.isFlashEnabled && callback.shouldInjectRuffle(url)) {
            view?.evaluateJavascript(RuffleInjector.loaderScript(), null)
            view?.evaluateJavascript(FLASH_HIDE_SCRIPT, null)
        }
        // 确保 viewport 缩放生效
        view?.evaluateJavascript(VIEWPORT_SCRIPT, null)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        // 页面加载完成：再次确保 Ruffle 已注入（兜底 + 触发 polyfill）
        if (PrefsManager.isFlashEnabled && callback.shouldInjectRuffle(url)) {
            view?.evaluateJavascript(RuffleInjector.fullInjection(), null)
        }
        // 注入 CSS：屏蔽 4399 PC 版广告与边栏，让游戏区更突出
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
        /**
         * 强制设置 viewport：让 PC 网页适配屏幕宽度，支持缩放。
         * 解决 PC 页面 UI 超出手机屏幕、无法缩放的问题。
         */
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

        /**
         * 屏蔽 4399 PC Flash 页的"没有 Flash 插件"提示。
         * 4399 页面会检测 navigator.plugins 或 ActiveX，发现没有 Flash 就显示提示框。
         * 本脚本：1) 伪装 Flash 插件存在 2) 隐藏已出现的提示框 3) 持续监控并移除
         */
        private const val FLASH_HIDE_SCRIPT = """
            (function(){
              if (window.__flashHideInjected) return; window.__flashHideInjected = true;
              // 1. 伪装 Flash 插件存在（部分页面通过 navigator.plugins 检测）
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
              // 2. 隐藏"没有 Flash"提示框（4399 常见 class/id）
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
              // 3. DOM 变化时再次清理（页面异步插入的提示）
              if (window.MutationObserver) {
                var mo = new MutationObserver(function(){ hideFlashTips(); });
                try { mo.observe(document.documentElement, {childList:true, subtree:true}); } catch(e) {}
              }
            })();
        """
    }
}

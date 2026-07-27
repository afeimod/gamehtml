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
import java.io.File

/**
 * WebViewClient that:
 *  - intercepts and blocks ad requests
 *  - upgrades http->https where appropriate (kept permissive for legacy sites)
 *  - injects the Flash engine (Ruffle or Waflash) polyfill + ad-hiding CSS after page load
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
        // Skip injection for local player.html (it loads engines internally)
        if (url != null && url.startsWith("file://") && url.contains("player.html")) {
            webView.onPageFinishedCb?.invoke(url)
            view?.evaluateJavascript(
                "(function(){try{AndroidBridge.reportTitle(document.title||location.href);}catch(e){}})();", null)
            return
        }
        injectScripts()
        webView.onPageFinishedCb?.invoke(url ?: "")
        // report title back
        view?.evaluateJavascript(
            "(function(){try{AndroidBridge.reportTitle(document.title||location.href);}catch(e){}})();", null)
    }

    private fun injectScripts() {
        val ctx = webView.context
        val injectDir = EngineAssets.injectDir(ctx)

        // Ensure inject scripts are copied (this is a fast local operation, no network)
        // Engine JS files are prepared by PlayerActivity in background
        try {
            EngineAssets.ensurePrepared(ctx, EngineType.WAFLASH)
        } catch (e: Exception) {
            android.util.Log.w("FlashWebViewClient", "ensurePrepared WAFLASH failed", e)
        }

        // Determine which engine to use for online polyfill injection
        val onlineEngine = settings.defaultEngine

        when (onlineEngine) {
            EngineType.RUFFLE -> injectRufflePolyfill(ctx, injectDir)
            EngineType.WAFLASH -> injectWaflashPolyfill(ctx, injectDir)
        }

        // ad-hiding CSS
        injectFileAsScript(injectDir, "adblock.css", isCss = true)

        // desktop compat tweaks (force flash focus etc.)
        injectFileAsScript(injectDir, "desktop_compat.js", isCss = false)
    }

    /**
     * Injects the Ruffle polyfill which auto-replaces <object>/<embed> Flash
     * elements on the page with a Ruffle player instance.
     */
    private fun injectRufflePolyfill(ctx: android.content.Context, injectDir: File) {
        val ruffleDir = EngineAssets.engineDir(ctx, EngineType.RUFFLE)
        val ruffleJs = File(ruffleDir, "ruffle.js")
        if (!ruffleJs.exists()) {
            android.util.Log.w("FlashWebViewClient", "ruffle.js not found at ${ruffleJs.absolutePath}")
            // Fallback: try waflash
            injectWaflashPolyfill(ctx, injectDir)
            return
        }
        val publicPath = "file://${ruffleDir.absolutePath}/"
        val polyfill = try {
            File(injectDir, "ruffle_polyfill.js").readText()
                .replace("__RUFFLE_PATH__", publicPath)
        } catch (e: Exception) {
            android.util.Log.w("FlashWebViewClient", "ruffle_polyfill.js read failed", e)
            null
        }
        if (polyfill != null) {
            webView.evaluateJavascript(polyfill, null)
            android.util.Log.d("FlashWebViewClient", "Ruffle polyfill injected, path=$publicPath")
        }
    }

    /**
     * Injects Waflash engine into online pages by loading waflash.js and
     * replacing <object>/<embed> Flash elements with Waflash canvas players.
     */
    private fun injectWaflashPolyfill(ctx: android.content.Context, injectDir: File) {
        val waflashDir = EngineAssets.engineDir(ctx, EngineType.WAFLASH)
        val waflashJs = File(waflashDir, "waflash.js")
        val waflashPlayerJs = File(waflashDir, "waflash-player.min.js")
        if (!waflashJs.exists() || !waflashPlayerJs.exists()) {
            android.util.Log.w("FlashWebViewClient", "waflash files not found at ${waflashDir.absolutePath}")
            // Fallback: try ruffle
            if (File(EngineAssets.engineDir(ctx, EngineType.RUFFLE), "ruffle.js").exists()) {
                injectRufflePolyfill(ctx, injectDir)
            }
            return
        }
        val publicPath = "file://${waflashDir.absolutePath}/"
        // Read the waflash polyfill script (or generate inline)
        val polyfill = try {
            File(injectDir, "waflash_polyfill.js").readText()
                .replace("__WAFLASH_PATH__", publicPath)
        } catch (e: Exception) {
            // Generate inline polyfill if the file doesn't exist
            generateWaflashPolyfill(publicPath)
        }
        webView.evaluateJavascript(polyfill, null)
        android.util.Log.d("FlashWebViewClient", "Waflash polyfill injected, path=$publicPath")
    }

    /**
     * Generates an inline Waflash polyfill that:
     * 1. Loads waflash.js and waflash-player.min.js from local file:// path
     * 2. Finds all <object>/<embed> Flash elements on the page
     * 3. Replaces them with Waflash canvas players
     */
    private fun generateWaflashPolyfill(path: String): String {
        return """
        (function(){
          if(window.__waflashInjected) return;
          window.__waflashInjected = true;
          var PATH = ${jsQuote(path)};
          var css = document.createElement('link');
          css.rel = 'stylesheet';
          css.href = PATH + 'waflash-style.css';
          document.head.appendChild(css);
          // Load waflash loader first
          var s1 = document.createElement('script');
          s1.src = PATH + 'waflash.js';
          s1.onerror = function(){ try{AndroidBridge.log('waflash.js load failed');}catch(e){} };
          document.head.appendChild(s1);
          // Load waflash player module
          window.__waflashReady = false;
          var s2 = document.createElement('script');
          s2.src = PATH + 'waflash-player.min.js';
          s2.onload = function(){
            window.__waflashReady = true;
            try{AndroidBridge.log('waflash player loaded');}catch(e){}
            replaceFlashElements();
          };
          s2.onerror = function(){ try{AndroidBridge.log('waflash-player.min.js load failed');}catch(e){} };
          document.head.appendChild(s2);
          function replaceFlashElements(){
            var objects = document.querySelectorAll('object[type="application/x-shockwave-flash"], embed[type="application/x-shockwave-flash"]');
            var swfUrls = [];
            objects.forEach(function(el){
              var src = el.getAttribute('data') || el.getAttribute('src') || '';
              if(!src){
                var param = el.querySelector('param[name="movie"]');
                if(param) src = param.getAttribute('value') || '';
              }
              if(src){
                swfUrls.push({el: el, src: src, w: el.width||'100%', h: el.height||'100%'});
              }
            });
            swfUrls.forEach(function(item){
              var container = document.createElement('div');
              container.style.cssText = 'width:'+item.w+';height:'+item.h+';display:inline-block;';
              var canvas = document.createElement('canvas');
              canvas.className = 'waflashCanvas';
              canvas.style.cssText = 'width:100%;height:100%;';
              canvas.tabIndex = 1;
              container.appendChild(canvas);
              item.el.parentNode.replaceChild(container, item.el);
              try {
                if(window.createWaflash){
                  window.GAME = window.GAME || {};
                  window.GAME.src = item.src;
                  window.GAME.name = item.src.split('/').pop().replace(/\.swf$/i,'');
                  window.GAME.extension = 'SWF';
                  window.GAME.reqEmulator = 'waflash';
                  window.GAME.fileType = 1;
                  window.wafOptions = window.wafOptions || {quality:'high', enableFilters:true};
                  createWaflash(item.src, window.wafOptions);
                }
              } catch(e){ try{AndroidBridge.log('waflash create failed: '+e);}catch(_){} }
            });
            if(swfUrls.length === 0){
              try{AndroidBridge.log('no flash elements found on page');}catch(e){}
            }
          }
          // Also try after a delay in case content loads dynamically
          setTimeout(function(){ if(window.__waflashReady) replaceFlashElements(); }, 2000);
        })();
        """.trimIndent()
    }

    private fun injectFileAsScript(injectDir: File, fileName: String, isCss: Boolean) {
        val content = try {
            File(injectDir, fileName).readText()
        } catch (e: Exception) { null } ?: return
        if (isCss) {
            val js = "(function(){var s=document.createElement('style');s.textContent=" +
                    jsQuote(content) + ";document.head.appendChild(s);})();"
            webView.evaluateJavascript(js, null)
        } else {
            webView.evaluateJavascript(content, null)
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

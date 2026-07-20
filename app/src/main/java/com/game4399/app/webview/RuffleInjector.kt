package com.game4399.app.webview

import com.game4399.app.data.PrefsManager

/**
 * Ruffle Flash 模拟器注入器。
 *
 * 工作原理：4399 的 PC Flash 页面（www.4399.com/flash/{id}.htm）内含
 * <object>/<embed> 标签嵌入 SWF。Ruffle 的 polyfill 模式会自动替换这些标签
 * 为 WebAssembly 播放器，从而免装 Flash 插件即可运行 SWF。
 *
 * 本类负责生成两段 JS：
 *   1) [configScript] —— 预置 RufflePlayer.config（必须在 ruffle.js 之前执行）
 *   2) [loaderScript] —— 动态加载 ruffle.js 并触发 polyfill
 */
object RuffleInjector {

    /** 根据设置返回 Ruffle 脚本地址 */
    fun scriptUrl(): String = when (PrefsManager.flashCdn) {
        "unpkg"  -> "https://unpkg.com/@ruffle-rs/ruffle"
        "local"  -> "file:///android_asset/ruffle/ruffle.js"
        else     -> "https://cdn.jsdelivr.net/npm/@ruffle-rs/ruffle@0.3.0/ruffle.min.js"
    }

    /** ruffle.js 的 publicPath（用于加载 .wasm） */
    fun publicPath(): String = when (PrefsManager.flashCdn) {
        "unpkg"  -> "https://unpkg.com/@ruffle-rs/ruffle/"
        "local"  -> "file:///android_asset/ruffle/"
        else     -> "https://cdn.jsdelivr.net/npm/@ruffle-rs/ruffle@0.3.0/"
    }

    /** 画质映射到 Ruffle 的 quality 选项 */
    private fun quality(): String = when (PrefsManager.flashQuality) {
        "low"    -> "low"
        "medium" -> "medium"
        "best"   -> "best"
        else     -> "high"
    }

    /** Ruffle 配置脚本（在 ruffle.js 之前执行） */
    fun configScript(): String = """
        (function(){
          window.RufflePlayer = window.RufflePlayer || {};
          window.RufflePlayer.config = {
            "publicPath": "${publicPath()}",
            "polyfills": true,
            "autoplay": "${if (PrefsManager.isFlashAutoplay) "on" else "off"}",
            "unmuteOverlay": "visible",
            "letterbox": "fullscreen",
            "upgradeToHttps": true,
            "allowScriptAccess": true,
            "scale": "showAll",
            "quality": "${quality()}",
            "allowFullscreen": true,
            "splashScreen": true,
            "preloader": true,
            "logLevel": "warn",
            "maxExecutionDuration": {"secs": 30, "nanos": 0}
          };
          window.__ruffleConfigReady = true;
        })();
    """.trimIndent()

    /**
     * 加载器脚本：动态注入 ruffle.js。
     * 加载完成后会自动执行 polyfill，替换页面上的 <object>/<embed>。
     * 同时暴露 window.__playSwf(url) 用于直接播放指定 SWF。
     */
    fun loaderScript(): String = """
        (function(){
          if (window.__ruffleLoaded) return;
          function onReady(cb){
            if (window.RufflePlayer && window.RufflePlayer.newest) cb();
            else document.addEventListener('ruffleReady', function(){ cb(); });
          }
          var s = document.createElement('script');
          s.src = "${scriptUrl()}";
          s.async = true;
          s.onload = function(){
            window.__ruffleLoaded = true;
            // Ruffle 加载完成后触发 polyfill（自动替换 object/embed）
            try {
              var r = window.RufflePlayer.newest();
              if (r && r.init) r.init();
            } catch(e){ console.warn('Ruffle init:', e); }
            // 暴露直接播放 SWF 的接口
            window.__playSwf = function(url, base){
              onReady(function(){
                var ruffle = window.RufflePlayer.newest();
                var player = ruffle.createPlayer();
                player.style.position = 'fixed';
                player.style.left = '0'; player.style.top = '0';
                player.style.width = '100%'; player.style.height = '100%';
                player.style.zIndex = '9999';
                player.style.background = '#000';
                document.body.appendChild(player);
                var opt = { url: url };
                if (base) opt.base = base;
                player.ruffle().load(opt);
              });
            };
            document.dispatchEvent(new CustomEvent('ruffleReady'));
          };
          s.onerror = function(){ console.error('Ruffle 加载失败: ' + "${scriptUrl()}"); };
          document.head.appendChild(s);
        })();
    """.trimIndent()

    /** 一键注入：配置 + 加载器 */
    fun fullInjection(): String = configScript() + "\n" + loaderScript()

    /**
     * 直接播放 SWF 的脚本（用于拦截 .swf 链接后跳转到内置播放器）。
     * 在 player.html 中调用 window.__playSwf(url) 即可。
     */
    fun playSwfScript(swfUrl: String, base: String? = null): String {
        val baseArg = base?.let { ", '$it'" } ?: ""
        return """
            (function(){
              if (window.__playSwf) { window.__playSwf('$swfUrl'$baseArg); }
              else { document.addEventListener('ruffleReady', function(){
                window.__playSwf('$swfUrl'$baseArg);
              }); }
            })();
        """.trimIndent()
    }
}

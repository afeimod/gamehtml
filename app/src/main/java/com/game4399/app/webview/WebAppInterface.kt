package com.game4399.app.webview

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.game4399.app.data.FavoriteStore
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * 注入到 WebView 的 JS 接口（window.Android）。
 * 提供给网页调用原生的能力：Toast、收藏、震动、获取当前 URL 等。
 */
class WebAppInterface(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun toast(msg: String?) {
        handler.post { Toast.makeText(context, msg ?: "", Toast.LENGTH_SHORT).show() }
    }

    @JavascriptInterface
    fun vibrate(durationMs: Int) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        @Suppress("DEPRECATION")
        vibrator.vibrate(durationMs.coerceIn(1, 500).toLong())
    }

    @JavascriptInterface
    fun addFavorite(url: String?, title: String?) {
        if (url.isNullOrEmpty()) return
        FavoriteStore.add(url, title ?: url)
        handler.post { Toast.makeText(context, "已加入收藏", Toast.LENGTH_SHORT).show() }
    }

    @JavascriptInterface
    fun log(tag: String?, msg: String?) {
        Log.d("WebApp:${tag ?: "JS"}", msg ?: "")
    }

    /**
     * 打开 SWF 播放器（WAFlash 检测脚本调用）。
     * 对 WAFlash 引擎：原生预下载 SWF 到缓存，然后加载播放器页面。
     * 对 Ruffle/swf2js：直接加载播放器页面，由 shouldInterceptRequest 处理。
     */
    @JavascriptInterface
    fun openSwf(swfUrl: String?, pageUrl: String?) {
        if (swfUrl.isNullOrEmpty()) return
        Log.d("WebApp:WAFlash", "openSwf: $swfUrl (from: $pageUrl)")

        val engine = com.game4399.app.data.PrefsManager.flashEngine
        if (engine == "waflash") {
            // WAFlash：原生预下载 SWF，避免 fetch() 不被 shouldInterceptRequest 拦截的问题
            handler.post {
                if (context is com.game4399.app.GameActivity) {
                    (context as com.game4399.app.GameActivity).preloadSwfForWaflash(swfUrl, pageUrl)
                }
            }
        } else {
            // Ruffle/swf2js：直接加载播放器页面
            handler.post {
                val playerUrl = NavHelper.playerUrl(swfUrl, pageUrl, null)
                if (context is com.game4399.app.GameActivity) {
                    (context as com.game4399.app.GameActivity).loadSwfInWebView(playerUrl)
                }
            }
        }
    }

    @JavascriptInterface
    fun finish() {
        if (context is Activity) handler.post { context.finish() }
    }

    companion object {
        private const val TAG = "WebApp:WAFlash"

        /** 信任所有 SSL 证书 */
        private val trustAllManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        }

        private val sslSocketFactory by lazy {
            try {
                val ctx = SSLContext.getInstance("TLS")
                ctx.init(null, arrayOf(trustAllManager), java.security.SecureRandom())
                ctx.socketFactory
            } catch (e: Exception) {
                HttpsURLConnection.getDefaultSSLSocketFactory()
            }
        }

        /**
         * 原生下载 SWF 文件到缓存目录。
         * @return 缓存文件，null 表示下载失败
         */
        fun downloadSwf(context: Context, swfUrl: String): File? {
            return try {
                // HTTP → HTTPS
                val url = if (swfUrl.startsWith("http://")) "https://" + swfUrl.substring(7) else swfUrl
                Log.d(TAG, "原生下载 SWF: $url")

                val conn = URL(url).openConnection() as HttpURLConnection
                if (conn is HttpsURLConnection) {
                    conn.sslSocketFactory = sslSocketFactory
                    conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                }
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                conn.setRequestProperty("Accept", "*/*")
                if (url.contains("4399.com")) {
                    conn.setRequestProperty("Referer", "https://www.4399.com/")
                }
                conn.instanceFollowRedirects = true
                conn.connect()

                val code = conn.responseCode
                if (code !in 200..299) {
                    Log.w(TAG, "SWF 下载失败: HTTP $code")
                    return null
                }

                val data = conn.inputStream.readBytes()
                Log.d(TAG, "SWF 下载完成: ${data.size} bytes")

                // 保存到缓存目录
                val cacheDir = File(context.cacheDir, "swf_cache")
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val fileName = "swf_" + System.currentTimeMillis() + ".swf"
                val cachedFile = File(cacheDir, fileName)
                cachedFile.writeBytes(data)

                Log.d(TAG, "SWF 已缓存: ${cachedFile.absolutePath}")
                cachedFile
            } catch (e: Exception) {
                Log.e(TAG, "SWF 下载异常: ${e.message}", e)
                null
            }
        }
    }
}

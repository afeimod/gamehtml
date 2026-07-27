package com.game4399.app.ui.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.game4399.app.R
import com.game4399.app.data.HistoryItem
import com.game4399.app.data.Prefs
import com.game4399.app.widget.FloatActionPanel
import com.game4399.app.widget.OnFloatActionListener
import com.game4399.app.widget.OnPadActionListener
import com.game4399.app.widget.VirtualPad

class BrowserActivity : AppCompatActivity(), OnFloatActionListener, OnPadActionListener {

    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var titleBar: TextView
    private lateinit var urlBar: TextView
    private lateinit var panel: FloatActionPanel
    private lateinit var pad: VirtualPad
    private val topBar: View by lazy { findViewById(R.id.topBar) }
    private val backBtn: View by lazy { findViewById(R.id.btnBack) }
    private val fwdBtn: View by lazy { findViewById(R.id.btnForward) }
    private val homeBtn: View by lazy { findViewById(R.id.btnHome) }
    private val refreshBtn: View by lazy { findViewById(R.id.btnRefresh) }
    private val menuBtn: View by lazy { findViewById(R.id.btnMenu) }

    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)
        webView = findViewById(R.id.web)
        progress = findViewById(R.id.progress)
        titleBar = findViewById(R.id.titleBar)
        urlBar = findViewById(R.id.urlBar)
        panel = findViewById(R.id.floatPanel)
        pad = findViewById(R.id.pad)

        setupWeb()
        panel.setListener(this)
        pad.setListener(this)

        // 加载入口
        val data = intent.data?.toString()
        if (data != null && data.startsWith("file:///android_asset/web/index.html")) {
            // 灵动主页入口
            webView.loadUrl(data)
        } else if (data != null && (data.startsWith("http") || data.startsWith("file") || data.startsWith("content"))) {
            // 传入外部 url，包装进 browser.html
            val bUrl = "file:///android_asset/web/browser.html?url=" + Uri.encode(data)
            webView.loadUrl(bUrl)
        } else {
            // 默认：browser.html 包装默认主页
            val home = if (Prefs.uiMode() == "pc") Prefs.homePc() else Prefs.homeMobile()
            val bUrl = "file:///android_asset/web/browser.html?url=" + Uri.encode(home)
            webView.loadUrl(bUrl)
        }

        backBtn.setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        fwdBtn.setOnClickListener { if (webView.canGoForward()) webView.goForward() }
        homeBtn.setOnClickListener { openHome(this, Prefs.uiMode() == "pc") }
        refreshBtn.setOnClickListener { webView.reload() }
        menuBtn.setOnClickListener { panel.toggle() }

        // 长按 URL 栏弹收藏弹窗
        urlBar.setOnLongClickListener {
            val cur = webView.url ?: return@setOnLongClickListener false
            val name = webView.title ?: cur
            val exists = Prefs.isFavorite(cur)
            AlertDialog.Builder(this)
                .setTitle(if (exists) R.string.bookmark_removed else R.string.add_bookmark)
                .setMessage(cur)
                .setPositiveButton(if (exists) R.string.delete else R.string.add) { _, _ ->
                    if (exists) { Prefs.removeFavorite(cur); Toast.makeText(this, R.string.bookmark_removed, Toast.LENGTH_SHORT).show() }
                    else { Prefs.addFavorite(HistoryItem(name, cur)); Toast.makeText(this, R.string.bookmark_added, Toast.LENGTH_SHORT).show() }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            true
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWeb() {
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.allowFileAccess = true
        s.allowContentAccess = true
        s.allowFileAccessFromFileURLs = true
        s.allowUniversalAccessFromFileURLs = true
        s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.cacheMode = when (Prefs.cacheMode()) {
            "always" -> WebSettings.LOAD_CACHE_ELSE_NETWORK
            "no" -> WebSettings.LOAD_NO_CACHE
            else -> WebSettings.LOAD_DEFAULT
        }
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.javaScriptCanOpenWindowsAutomatically = true
        s.saveFormData = true
        s.textZoom = (Prefs.pageZoom() * 100).toInt()
        // User Agent
        when (Prefs.userAgent()) {
            "pc" -> {
                val ua = s.userAgentString.replace("Mobile", "Desktop") + " AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                s.userAgentString = ua
            }
            "mobile" -> {
                s.userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            }
            // system -> 默认
        }
        // Cookie
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // safe
        }
        // 调试
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url.toString()
                if (Prefs.adBlock() && isAd(url)) {
                    return WebResourceResponse("text/plain", "utf-8", "".byteInputStream())
                }
                return null
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progress.visibility = View.VISIBLE
                url?.let { urlBar.text = it }
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progress.visibility = View.GONE
                url?.let {
                    urlBar.text = it
                    Prefs.addHistory(HistoryItem(view?.title ?: it, it))
                }
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith("market://") || url.startsWith("intent://") || url.startsWith("taobao://") ||
                    url.startsWith("alipay") || url.startsWith("weixin://") || url.startsWith("wtloginmqq://") ||
                    url.startsWith("mqqapi")) {
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))); return true } catch (_: Throwable) {}
                }
                return false
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
            }
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                titleBar.text = title ?: ""
            }
            override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
                this@BrowserActivity.filePathCallback?.onReceiveValue(null)
                this@BrowserActivity.filePathCallback = filePathCallback
                val intent = fileChooserParams?.createIntent() ?: return true
                try { startActivityForResult(intent, REQ_FILE) } catch (e: Exception) { return false }
                return true
            }
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                callback?.invoke(origin, true, false)
            }
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                val newWeb = WebView(this@BrowserActivity)
                setupNewWindowWeb(newWeb, resultMsg)
                return true
            }
        }
        webView.setDownloadListener { url, _, _, _, _ ->
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Throwable) {}
        }

        // 提供给 Web 的 JS 桥
        webView.addJavascriptInterface(JsBridge(this, webView), "AndroidBridge")
        // 长按图片可保存
        webView.setOnLongClickListener { v ->
            val r = (v as WebView).hitTestResult
            if (r.type == WebView.HitTestResult.IMAGE_TYPE || r.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                val imgUrl = r.extra
                if (!imgUrl.isNullOrEmpty()) {
                    Toast.makeText(this, imgUrl, Toast.LENGTH_LONG).show()
                }
            }
            false
        }
    }

    private fun setupNewWindowWeb(wv: WebView, msg: Message?) {
        val s = wv.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        wv.webChromeClient = object : WebChromeClient() {}
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                webView.loadUrl(request.url.toString())
                return true
            }
        }
        val transport = msg?.obj as? WebView.WebViewTransport ?: return
        transport.webView = wv
        msg.sendToTarget()
    }

    private fun isAd(url: String): Boolean {
        val rules = Prefs.adblockRules()
        for (r in rules) {
            val rule = r.trim()
            if (rule.isEmpty()) continue
            if (rule.startsWith("/") && rule.endsWith("/")) {
                try {
                    val p = rule.substring(1, rule.length - 1).toRegex()
                    if (p.containsMatchIn(url)) return true
                } catch (_: Throwable) {}
            } else if (url.contains(rule, true)) return true
        }
        return false
    }

    override fun onBackPressed() {
        when {
            panel.isOpen -> { panel.close(); return }
            pad.isOpen -> { pad.close(); return }
            webView.canGoBack() -> webView.goBack()
            else -> super.onBackPressed()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_FILE) {
            filePathCallback?.onReceiveValue(
                if (resultCode == Activity.RESULT_OK) WebChromeClient.FileChooserParams.parseResult(resultCode, data) else null
            )
            filePathCallback = null
        }
    }

    override fun onDestroy() {
        try { webView.destroy() } catch (_: Throwable) {}
        super.onDestroy()
    }

    // =========== FloatActionPanel listener ===========
    override fun onFloatAction(action: String) {
        when (action) {
            "back" -> if (webView.canGoBack()) webView.goBack()
            "forward" -> if (webView.canGoForward()) webView.goForward()
            "home" -> openHome(this, Prefs.uiMode() == "pc")
            "refresh" -> webView.reload()
            "bookmark" -> {
                val cur = webView.url ?: return
                val name = webView.title ?: cur
                if (Prefs.isFavorite(cur)) { Prefs.removeFavorite(cur); Toast.makeText(this, R.string.bookmark_removed, Toast.LENGTH_SHORT).show() }
                else { Prefs.addFavorite(HistoryItem(name, cur)); Toast.makeText(this, R.string.bookmark_added, Toast.LENGTH_SHORT).show() }
            }
            "pad" -> pad.toggle()
            "settings" -> {
                val i = Intent(this, com.game4399.app.ui.MainActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                i.putExtra("tab", 2)
                startActivity(i)
                finish()
            }
            "exit" -> finish()
            "url" -> {
                val et = android.widget.EditText(this).apply { setText(webView.url) }
                AlertDialog.Builder(this)
                    .setTitle(R.string.add_url)
                    .setView(et)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.confirm) { _, _ -> webView.loadUrl(et.text.toString()) }
                    .show()
            }
        }
    }

    // =========== VirtualPad listener ===========
    override fun onPadKey(code: String, pressed: Boolean) {
        val js = "if(window.__gamePadDispatch){window.__gamePadDispatch('${escape(code)}', $pressed);}else{var d=window.__lastActiveDocument||document;var e=new KeyboardEvent('${if(pressed) "keydown" else "keyup"}',{code:'${escape(code)}',key:'${escape(jsKeyOf(code))}',bubbles:true});var el=d.activeElement||d.body;el.dispatchEvent(e);}"
        webView.evaluateJavascript(js, null)
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("'", "\\'")
    private fun jsKeyOf(code: String): String = when (code) {
        "Space" -> " "
        "Enter" -> "Enter"
        "Escape" -> "Escape"
        "ArrowUp" -> "ArrowUp"
        "ArrowDown" -> "ArrowDown"
        "ArrowLeft" -> "ArrowLeft"
        "ArrowRight" -> "ArrowRight"
        else -> if (code.startsWith("Key")) code.removePrefix("Key") else code
    }

    companion object {
        private const val REQ_FILE = 9001
        fun openUrl(ctx: Context, url: String, name: String? = null) {
            ctx.startActivity(Intent(ctx, BrowserActivity::class.java).apply {
                data = Uri.parse(url)
                putExtra("name", name)
            })
        }
        fun openHome(ctx: Context, isPc: Boolean) {
            val url = if (isPc) Prefs.homePc() else Prefs.homeMobile()
            openUrl(ctx, url, if (isPc) "4399 电脑版" else "4399 手机版")
        }
    }
}

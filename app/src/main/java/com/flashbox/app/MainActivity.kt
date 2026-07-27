package com.flashbox.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.webkit.WebViewAssetLoader
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity(), AndroidBridge.Host, BoxWebClient.BoxWebClientHost {

    private lateinit var web: FlashWebView
    private lateinit var progress: ProgressBar
    private lateinit var bridge: AndroidBridge
    private lateinit var assetLoader: WebViewAssetLoader
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val HOME = "https://app.local/index.html"

    // last intent action so we know how to treat the picker result
    private var pendingPick: String = "file"

    private val openFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) handlePickedFile(uri)
        }
    private val openTreeLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) handlePickedFolder(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        web = findViewById(R.id.webView)
        progress = findViewById(R.id.pageProgress)

        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/local/", LocalPathHandler())
            .addPathHandler("/", WebAssetsHandler())
            .build()

        web.configure()
        bridge = AndroidBridge(this)
        web.addJavascriptInterface(bridge, "Android")

        web.webViewClient = BoxWebClient(assetLoader, this)
        web.webChromeClient = BoxChromeClient()

        applyModeAndZoom(loadSettings())

        // Physical back: rely on WebView history (SPA uses pushState for overlays)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        // Handle being opened with a .swf intent
        val data = intent?.data
        if (data != null && (data.scheme == "content" || data.scheme == "file")) {
            handleExternalSwf(data)
            loadHome()
        } else {
            loadHome()
        }
    }

    private fun loadHome() = web.loadUrl(HOME)

    private fun loadSettings(): JSONObject = JSONObject(Store.getSettings())

    private fun applyModeAndZoom(s: JSONObject) {
        web.applyUa(s.optString("pageMode", "mobile"))
        web.applyZoom(s.optInt("zoom", 100))
    }

    // ---------------- AndroidBridge.Host ----------------
    override fun runOnUi(r: Runnable) = runOnUiThread(r)
    override fun pickFile() {
        pendingPick = "file"
        openFileLauncher.launch(arrayOf("application/x-shockwave-flash", "*/*"))
    }
    override fun pickFolder() {
        pendingPick = "folder"
        openTreeLauncher.launch(null)
    }
    override fun openUrl(url: String, mode: String) {
        if (url == "about:home" || url.isBlank()) { loadHome(); return }
        val s = loadSettings()
        val m = if (mode == "auto") s.optString("pageMode", "mobile") else mode
        s.put("pageMode", m); Store.saveSettings(s.toString())
        web.applyUa(m)
        web.loadUrl(url)
    }
    override fun goHome() = loadHome()
    override fun goBack(): Boolean = web.canGoBack().also { if (it) web.goBack() }
    override fun setZoom(percent: Int) {
        val s = loadSettings(); s.put("zoom", percent); Store.saveSettings(s.toString())
        web.applyZoom(percent)
    }
    override fun setPageMode(mode: String) {
        val s = loadSettings(); s.put("pageMode", mode); Store.saveSettings(s.toString())
        web.applyUa(mode)
    }
    override fun injectEngineNow() {
        web.evaluateJavascript(
            "(function(){if(window.__fbReinject){window.__fbReinject();}else{try{window.location.reload();}catch(e){}}})();",
            null
        )
    }
    override fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    override fun openExternal(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) { toast("无法打开外部链接") }
    }
    override fun shareUrl(url: String) {
        try {
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url)
            }
            startActivity(Intent.createChooser(i, "分享"))
        } catch (e: Exception) { toast("分享失败") }
    }
    override fun vibrate(ms: Long) {
        try {
            val v = getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
            if (v.hasVibrator()) v.vibrate(ms)
        } catch (_: Exception) {}
    }
    override fun keepScreenOn(on: Boolean) {
        runOnUiThread {
            window.attributes = window.attributes.apply {
                if (on) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    override fun appInfo(): String = JSONObject().apply {
        put("version", "1.0.0")
        put("engines", JSONArray().put("ruffle").put("waflash").put("flashpatch"))
        put("ruffleReady", true)
        put("waflashReady", true)
        put("flashpatchReady", true)
        put("pkg", packageName)
    }.toString()

    // ---------------- BoxWebClientHost ----------------
    override fun adblockEnabled(): Boolean = loadSettings().optBoolean("adblock", true)
    override fun currentEngine(): String = loadSettings().optString("engine", "ruffle")
    override fun engineConfig(engine: String): JSONObject =
        JSONObject(Store.getEngineConfigs()).optJSONObject(engine) ?: JSONObject()
    override fun controlsJson(): String = Store.getControls()
    override fun pageMode(): String = loadSettings().optString("pageMode", "mobile")
    override fun zoomPercent(): Int = loadSettings().optInt("zoom", 100)
    override fun zoomJs(): String =
        "(function(){try{document.documentElement.style.zoom=(" + zoomPercent() + "/100);}catch(e){}})();"
    override fun onNavChanged(url: String?, title: String?) {
        runOnUiThread {
            val js = "window.__flashbox&&window.__flashbox.onNavChanged(" +
                JSONObject().put("url", url ?: "").put("title", title ?: "").toString() + ");"
            web.evaluateJavascript(js, null)
            // auto-record history for online pages
            if (url != null && !url.startsWith("https://app.local") && url != "about:home") {
                val item = JSONObject().apply {
                    put("id", "h_" + System.currentTimeMillis().toString(36))
                    put("url", url); put("title", title ?: url)
                    put("time", System.currentTimeMillis())
                    put("type", "online")
                }
                Store.addHistory(item)
            }
        }
    }
    override fun onProgress(p: Int) {
        runOnUiThread {
            progress.visibility = if (p in 1..99) View.VISIBLE else View.GONE
            progress.progress = p
        }
    }
    override fun onError(url: String?, code: Int, desc: String?) {
        runOnUiThread {
            val msg = desc ?: ("错误 $code")
            val html = """<!doctype html><html><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <style>body{margin:0;background:#0E1116;color:#ECEFF4;font-family:sans-serif;
                display:flex;flex-direction:column;align-items:center;justify-content:center;height:100vh;text-align:center}
                h2{color:#FF8A00}button{margin:8px;padding:10px 18px;border:0;border-radius:10px;background:#FF8A00;color:#fff;font-size:15px}
                p{color:#9AA4B2;max-width:80%}</style></head><body>
                <h2>页面打开失败</h2><p>$msg</p><p style="font-size:12px">${url ?: ""}</p>
                <button onclick="Android.openExternal('${(url?:"").replace("'","\\'")}')">用外部浏览器打开</button>
                <button onclick="Android.goHome()">返回首页</button>
                <button onclick="location.reload()">重试</button>
                </body></html>""".trimIndent()
            web.loadDataWithBaseURL(url, html, "text/html", "utf-8", null)
        }
    }
    override fun isAppShell(url: String?): Boolean =
        url != null && (url.startsWith("https://app.local/index.html") ||
            url.startsWith("https://app.local/#") || url == "https://app.local/" ||
            url.startsWith("https://app.local/?"))
    override fun isPlayer(url: String?): Boolean =
        url != null && url.startsWith("https://app.local/player.html")

    // ---------------- File / folder picking ----------------
    private fun handlePickedFile(uri: Uri) {
        try {
            takePersistableRead(uri)
            val name = queryName(uri) ?: "flash.swf"
            val size = querySize(uri)
            val entry = LocalContent.makeFileEntry(name, uri.toString(), size)
            val arr = Store.addLibraryItem(entry)
            notifyLibrary(arr, "已添加：$name")
        } catch (e: Exception) {
            toast("添加失败：" + e.message)
        }
    }

    private fun handlePickedFolder(uri: Uri) {
        try {
            takePersistableRead(uri)
            val name = DocumentFile.fromTreeUri(this, uri)?.name ?: "文件夹"
            val scanned = LocalContent.scanTree(this, uri, name)
            // append all scanned entries (folder + swf files)
            var arr = JSONArray(Store.getLibrary())
            for (i in 0 until scanned.length()) arr.put(scanned.getJSONObject(i))
            Store.saveLibrary(arr.toString())
            val count = scanned.length() - 1
            notifyLibrary(arr, "已添加文件夹：$name（$count 个SWF）")
        } catch (e: Exception) {
            toast("扫描失败：" + e.message)
        }
    }

    private fun handleExternalSwf(uri: Uri) {
        try {
            takePersistableRead(uri)
            val name = queryName(uri) ?: "flash.swf"
            val entry = LocalContent.makeFileEntry(name, uri.toString(), querySize(uri))
            Store.addLibraryItem(entry)
        } catch (_: Exception) {}
    }

    private fun takePersistableRead(uri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: Exception) {}
    }

    private fun notifyLibrary(arr: JSONArray, msg: String) {
        toast(msg)
        web.evaluateJavascript(
            "window.__flashbox&&window.__flashbox.onLibraryChanged(${arr});", null
        )
    }

    private fun queryName(uri: Uri): String? = try {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    } catch (_: Exception) { null }

    private fun querySize(uri: Uri): Long = try {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (idx >= 0 && c.moveToFirst()) c.getLong(idx) else 0L
        } ?: 0L
    } catch (_: Exception) { 0L }

    // ---------------- AssetLoader path handlers ----------------
    /** Serves files from assets/web/ at the root of https://app.local/ */
    private inner class WebAssetsHandler : WebViewAssetLoader.PathHandler {
        private val ah = WebViewAssetLoader.AssetsPathHandler(this@MainActivity)
        override fun handle(path: String): WebResourceResponse? {
            // path is relative to "/", e.g. "index.html" or "engines/ruffle/ruffle.js"
            return ah.handle("web/$path")
        }
    }

    /** Serves a local library SWF by id: https://app.local/local/<id> */
    private inner class LocalPathHandler : WebViewAssetLoader.PathHandler {
        override fun handle(path: String): WebResourceResponse? {
            val id = path.trimEnd('/')
            try {
                val arr = JSONArray(Store.getLibrary())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (o.optString("id") == id && !o.optBoolean("isDir")) {
                        val uri = o.optString("uri")
                        val name = o.optString("name", "file.swf")
                        val stream = LocalContent.openStream(this@MainActivity, uri) ?: return null
                        val mime = LocalContent.guessMime(name)
                        val headers = mapOf(
                            "Access-Control-Allow-Origin" to "*",
                            "Cache-Control" to "no-cache"
                        )
                        return WebResourceResponse(mime, "binary", 200, "OK", headers, stream)
                    }
                }
            } catch (_: Exception) {}
            return WebResourceResponse("text/plain", "utf-8",
                ByteArrayInputStream("not found".toByteArray()))
        }
    }

    // ---------------- Chrome client ----------------
    inner class BoxChromeClient : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) = onProgress(newProgress)
        override fun onPermissionRequest(request: PermissionRequest?) {
            request?.grant(request.resources)
        }
        override fun onGeolocationPermissionsShowPrompt(
            origin: String?, callback: android.webkit.GeolocationPermissions.Callback?
        ) { callback?.invoke(origin, true, false) }
        override fun onShowFileChooser(
            webView: WebView?, cb: ValueCallback<Array<Uri>>?, params: FileChooserParams?
        ): Boolean {
            filePathCallback = cb
            try {
                startActivityForResult(params!!.createIntent(), 54321)
            } catch (e: Exception) {
                filePathCallback = null
                return false
            }
            return true
        }
        override fun onJsAlert(
            view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?
        ): Boolean { android.app.AlertDialog.Builder(this@MainActivity).setMessage(message)
            .setPositiveButton("确定") { _, _ -> result?.confirm() }.show(); return true }
        override fun onJsConfirm(
            view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?
        ): Boolean { android.app.AlertDialog.Builder(this@MainActivity).setMessage(message)
            .setPositiveButton("确定") { _, _ -> result?.confirm() }
            .setNegativeButton("取消") { _, _ -> result?.cancel() }.show(); return true }
    }

    @Deprecated("superseded by launcher but kept for file chooser")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 54321) {
            val cb = filePathCallback ?: return
            filePathCallback = null
            if (resultCode == Activity.RESULT_OK) {
                val result = data?.data?.let { arrayOf(it) } ?: data?.clipData?.let { cd ->
                    Array(cd.itemCount) { cd.getItemAt(it).uri }
                }
                cb.onReceiveValue(result)
            } else cb.onReceiveValue(null)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Forward hardware keyboard to the page (for BT keyboards etc.)
        if (keyCode == KeyEvent.KEYCODE_MENU) return super.onKeyDown(keyCode, event)
        return super.onKeyDown(keyCode, event)
    }
}

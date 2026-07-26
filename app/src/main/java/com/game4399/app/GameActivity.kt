package com.game4399.app

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.game4399.app.data.FavoriteStore
import com.game4399.app.data.GameType
import com.game4399.app.data.PrefsManager
import com.game4399.app.databinding.ActivityGameBinding
import com.game4399.app.input.KeyMapper
import com.game4399.app.webview.GameWebChromeClient
import com.game4399.app.webview.GameWebView
import com.game4399.app.webview.GameWebViewClient
import com.game4399.app.webview.NavHelper
import com.game4399.app.webview.WebAppInterface
import com.game4399.app.widget.FloatingMenuView

/**
 * 游戏播放 Activity：全屏承载游戏页面，支持触屏 + 物理键盘 + 虚拟手柄。
 *
 * 入参：
 *  - [EXTRA_URL]   游戏/页面 URL
 *  - [EXTRA_TITLE] 游戏标题（用于收藏/历史）
 *  - [EXTRA_TYPE]  游戏类型
 *
 * 工作流：
 *  1) 接收 URL，若是 SWF 直链则跳转到内置 player.html + Ruffle
 *  2) 普通 4399 页面直接加载，onPageFinished 注入 Ruffle（PC Flash 页 polyfill）
 *  3) 顶部悬浮工具栏：返回/前进/刷新/手柄开关/收藏/分享
 *  4) 虚拟手柄可显隐；DPad 与 A/B 注入按键到 WebView
 *  5) 物理键盘：游戏键透传网页，BACK 由返回栈处理
 */
class GameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameBinding
    private lateinit var webView: GameWebView
    /** 注入到 WebView 的 JS 接口引用（用于设置 SWF 提取回调） */
    private lateinit var webAppInterface: WebAppInterface
    /** WebView 客户端引用（用于 onPageFinished 兜底注入 Flash 脚本） */
    private lateinit var gameWebViewClient: GameWebViewClient

    private var currentUrl: String = ""
    private var currentTitle: String = ""
    private var currentType: GameType = GameType.URL
    private var gamepadVisible = false
    private var isFullscreen = false
    private var isMouseEnabled = false
    /** 本地 SWF 文件的真实 URI（shouldInterceptRequest 用它读取文件） */
    @Volatile var localSwfUri: String? = null
    private lateinit var floatingMenu: FloatingMenuView

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val arr = uris?.takeIf { it.isNotEmpty() }?.toTypedArray()
        filePathCallback?.onReceiveValue(arr)
        filePathCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 沉浸式全屏：内容延伸到状态栏和导航栏区域，消除黑色地带
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyImmersiveFullscreen()
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentUrl = intent.getStringExtra(EXTRA_URL) ?: return finish()
        currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: "游戏"
        currentType = runCatching { GameType.valueOf(intent.getStringExtra(EXTRA_TYPE) ?: "") }
            .getOrDefault(GameType.URL)

        webView = binding.gameWebView
        // 根据设置应用屏幕方向
        applyOrientation()
        // 初始化鼠标光标
        isMouseEnabled = PrefsManager.isMouseEnabled
        setupWebView()
        setupGamepad()
        setupToolbar()
        setupFloatingMenu()
        setupBackHandler()

        // 开始加载
        loadGame(currentUrl, currentTitle, currentType)
    }

    /** 沉浸式全屏：隐藏系统栏并让内容延伸到刘海屏 */
    private fun applyImmersiveFullscreen() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // ---------------- WebView ----------------
    private fun setupWebView() {
        webAppInterface = WebAppInterface(this)
        gameWebViewClient = object : GameWebViewClient(viewClientCallback) {}
        webView.apply {
            addJavascriptInterface(webAppInterface, "Android")
            webChromeClient = object : GameWebChromeClient(chromeCallback) {}
            webViewClient = gameWebViewClient

            // UA 模式：优先使用用户设置，否则根据页面类型自动选择
            val uaMode = PrefsManager.uaMode
            if (uaMode == "ie_compat") {
                webView.useUaMode("ie_compat")
            } else {
                val isPcPage = currentUrl.contains("www.4399.com") ||
                    currentType == GameType.FLASH ||
                    currentUrl.contains("/flash/")
                useDesktopMode(isPcPage)
            }
        }
    }

    private val chromeCallback = object : GameWebChromeClient.Callback {
        override fun onProgress(progress: Int) {
            binding.progressBar.apply {
                visibility = if (progress in 1..99) View.VISIBLE else View.GONE
                this.progress = progress
            }
        }
        override fun onTitle(title: String?) {
            currentTitle = title?.takeIf { it.isNotBlank() } ?: currentTitle
        }
        override fun onConsole(level: String, msg: String, sourceId: String?, line: Int) {}
        override fun onShowFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
            // 网页全屏：隐藏 WebView，将全屏 View 添加到容器
            // 手柄控件和悬浮菜单位于全屏容器之上（XML 层级），无需隐藏
            binding.gameWebView.visibility = View.GONE
            binding.fullscreenContainer.removeAllViews()
            binding.fullscreenContainer.addView(view)
            binding.fullscreenContainer.visibility = View.VISIBLE
        }
        override fun onHideFullscreen() {
            // 退出全屏：移除全屏 View，恢复 WebView
            binding.fullscreenContainer.removeAllViews()
            binding.fullscreenContainer.visibility = View.GONE
            binding.gameWebView.visibility = View.VISIBLE
        }
        override fun onFileChooser(callback: ValueCallback<Array<Uri>>, accept: String?): Boolean {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = callback
            val mimes = accept?.split(",")?.toTypedArray() ?: arrayOf("*/*")
            try { fileChooserLauncher.launch(mimes) } catch (e: Exception) { callback.onReceiveValue(null); filePathCallback = null }
            return true
        }
    }

    private val viewClientCallback = object : GameWebViewClient.Callback {
        override fun onPageStarted(url: String?) {
            // 页面导航时释放所有按键，防止旧页面的按键状态残留
            webView.releaseAllKeys()
            binding.errorView.visibility = View.GONE
        }
        override fun onPageFinished(url: String?) {
            url?.let { FavoriteStore.addHistory(it, currentTitle, currentType) }
            updateFavoriteIcon()
            // Flash 兜底注入：interceptHtml 可能因缓存/SPA 未触发，
            // 在 onPageFinished 再注入一次（脚本内有 __flashPolyfilled 守卫，不会重复执行）
            if (PrefsManager.isFlashEnabled && url != null &&
                !url.startsWith("file:///android_asset/") &&
                !url.startsWith("https://flash.local/")) {
                val script = gameWebViewClient.buildFlashInjectScript(url)
                // 去掉 <script> 标签包装，直接执行 JS
                val js = script
                    .replace("<script>", "")
                    .replace("</script>", "")
                    .trim()
                webView.evaluateJavascript(js, null)
            }
            // 如果鼠标光标已开启，重新注入（页面导航后会丢失）
            if (isMouseEnabled) {
                webView.evaluateJavascript(MOUSE_CURSOR_SCRIPT, null)
            }
            // 如果视角旋转已开启，重新注入（页面导航后会丢失）
            if (PrefsManager.isCameraRotationEnabled) {
                webView.evaluateJavascript(CAMERA_ROTATION_SCRIPT, null)
            }
        }
        override fun onProgress(progress: Int) = chromeCallback.onProgress(progress)
        override fun onError(url: String?, errorCode: Int, description: String?) {
            // 跳转到内置播放器页面时可能触发临时错误，忽略 file:/// 页面的错误
            if (url != null && url.startsWith("file:///android_asset/")) return
            // 忽略被中止的请求（跳转过程中旧页面被取消）
            if (errorCode == -1) return
            binding.errorView.visibility = View.VISIBLE
        }
        override fun onSwfIntercepted(swfUrl: String, pageUrl: String) {
            // 页面里点击 .swf 链接 → 用内置播放器
            val playerUrl = NavHelper.playerUrl(swfUrl, base = pageUrl, title = currentTitle)
            webView.loadUrl(playerUrl)
        }
        override fun shouldInjectRuffle(url: String?): Boolean {
            if (url == null) return false
            // 不注入内置播放器页面（避免循环注入）
            if (url.startsWith("file:///android_asset/player.html")) return false
            if (url.startsWith("https://flash.local/player.html")) return false
            if (url.startsWith("https://flash.local/waflash.html")) return false
            if (url.startsWith("https://flash.local/waflash/")) return false
            if (url.startsWith("https://flash.local/ruffle/")) return false
            if (url.startsWith("https://flash.local/swf2js/")) return false
            // Flash 开启时，对所有网页注入 Flash 支持（不限 4399）
            // 适用于 mhhf.com 等其他 Flash 游戏网站
            return PrefsManager.isFlashEnabled
        }
        override fun getCachedSwfPath(): String? = null
        override fun getLocalSwfUri(): String? = localSwfUri
    }

    private fun loadGame(url: String, title: String, type: GameType) {
        currentUrl = url; currentTitle = title; currentType = type
        // 本地 SWF 文件 → 引擎选择后加载
        if (type == GameType.LOCAL_SWF || NavHelper.isLocalFile(url)) {
            localSwfUri = url  // 保存真实 URI，shouldInterceptRequest 用它读取文件
            showLocalSwfEnginePicker(url, title)
        } else if (NavHelper.isSwf(url)) {
            // 远程 SWF 直链 → 内置播放器
            val playerUrl = NavHelper.playerUrl(url, base = null, title = title)
            webView.loadUrl(playerUrl)
        } else if (url.contains("4399.com")) {
            // 4399 页面：添加 Referer 头绕过防盗链
            webView.loadUrl(url, mapOf("Referer" to "https://www.4399.com/"))
        } else {
            webView.loadUrl(url)
        }
    }

    /** WebAppInterface.openSwf 调用：在当前 WebView 加载 SWF 播放器页面 */
    fun loadSwfInWebView(playerUrl: String) {
        webView.loadUrl(playerUrl)
    }

    /** 本地 SWF 引擎选择对话框 */
    private fun showLocalSwfEnginePicker(url: String, title: String) {
        val engines = arrayOf("WAFlash (推荐)", "Ruffle")
        val currentEngine = PrefsManager.flashEngine
        val checked = if (currentEngine == "waflash") 0 else 1
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("选择播放引擎")
            .setSingleChoiceItems(engines, checked) { dialog, which ->
                val engine = if (which == 0) "waflash" else "ruffle"
                PrefsManager.sp.edit().putString("flash_engine", engine).apply()
                PrefsManager.sp.edit().putBoolean("flash_enabled", true).apply()
                dialog.dismiss()
                // 加载本地 SWF
                val swfProxyUrl = "https://flash.local/local.swf?t=${System.currentTimeMillis()}"
                val playerUrl = NavHelper.playerUrl(swfProxyUrl, base = null, title = title)
                webView.loadUrl(playerUrl)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ---------------- 虚拟手柄 ----------------
    private fun setupGamepad() {
        val alpha = PrefsManager.gamepadAlpha
        binding.dpad.targetWebView = webView
        binding.dpad.overlayAlpha = alpha
        binding.actionButtons.targetWebView = webView
        binding.actionButtons.overlayAlpha = alpha
        binding.mouseControl.targetWebView = webView
        binding.mouseControl.overlayAlpha = alpha

        // 3D 视角旋转模式
        webView.cameraRotationEnabled = PrefsManager.isCameraRotationEnabled

        binding.btnStart.setOnClickListener {
            webView.injectKey(KeyMapper.toKeyCode(PrefsManager.startKey))
        }
        binding.btnSelect.setOnClickListener {
            webView.injectKey(KeyMapper.toKeyCode(PrefsManager.selectKey))
        }

        // Start/Select 按钮拖拽支持（位置编辑模式）
        setupSystemButtonsDrag()

        // 根据设置初始显示手柄
        if (PrefsManager.isGamepadEnabled) {
            gamepadVisible = true
            showGamepad(true)
        }
        // 应用动作按键大小
        binding.actionButtons.post { applyActionButtonsSize() }
        // 鼠标按钮
        if (PrefsManager.isMouseButtonsVisible) {
            binding.mouseControl.visibility = View.VISIBLE
        }
        // 恢复保存的位置
        restoreSavedPositions()
    }

    /** 切换鼠标按钮显示/隐藏 */
    private fun toggleMouseMode() {
        val enabled = !PrefsManager.isMouseButtonsVisible
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            .edit().putBoolean("mouse_buttons_visible", enabled).apply()
        binding.mouseControl.visibility = if (enabled) View.VISIBLE else View.GONE
        Toast.makeText(this, if (enabled) "鼠标按钮已添加" else "鼠标按钮已隐藏", Toast.LENGTH_SHORT).show()
    }

    private fun showGamepad(show: Boolean) {
        val v = if (show) View.VISIBLE else View.GONE
        // 方向键根据可见性设置
        binding.dpad.visibility = if (show && PrefsManager.isDpadVisible) View.VISIBLE else View.GONE
        binding.actionButtons.visibility = v
        // Start/Select 根据可见性设置
        binding.systemButtons.visibility = if (show && PrefsManager.isSystemButtonsVisible) View.VISIBLE else View.GONE
        // 鼠标按钮独立控制，不受手柄开关影响
        // 隐藏手柄时释放所有按键，防止角色持续移动
        if (!show) {
            webView.releaseAllKeys()
        }
        // 显示时恢复保存的位置
        if (show) {
            binding.systemButtons.post {
                if (PrefsManager.systemPosX >= 0) {
                    binding.systemButtons.x = PrefsManager.systemPosX
                    binding.systemButtons.y = PrefsManager.systemPosY
                }
            }
        }
    }

    private fun toggleGamepad() {
        gamepadVisible = !gamepadVisible
        showGamepad(gamepadVisible)
    }

    // ---------------- 顶部工具栏（已移除，功能由悬浮菜单提供） ----------------
    private fun setupToolbar() {
        binding.btnRetry.setOnClickListener {
            val currentUrl = webView.url ?: ""
            if (currentUrl.startsWith("file:///android_asset/") || currentUrl.startsWith("https://flash.local/")) {
                webView.loadUrl(currentUrl)
            } else if (currentUrl.contains("4399.com")) {
                webView.loadUrl(currentUrl, mapOf("Referer" to "https://www.4399.com/"))
            } else {
                webView.reload()
            }
        }
    }

    // ---------------- 悬浮菜单 ----------------
    private fun setupFloatingMenu() {
        floatingMenu = binding.floatingMenu
        floatingMenu.setCallbacks(object : FloatingMenuView.Callbacks {
            override fun onToggleFullscreen() { toggleFullscreen() }
            override fun onToggleOrientation() { toggleOrientation() }
            override fun onToggleGamepad() { toggleGamepad() }
            override fun onToggleMouse() { toggleMouseMode() }
            override fun onOpenKeyMapping() { openKeyMappingDialog() }
            override fun onOpenFlashSettings() { showFlashEnginePicker() }
            override fun onOpenPageZoom() { showPageZoomDialog() }
            override fun onOpenUaMode() { showUaModeDialog() }
            override fun onRefresh() { webView.reload() }
            override fun onBack() { if (webView.canGoBack()) webView.goBack() else finish() }
            override fun onClose() { finish() }
            override fun onExtractSwf() { extractSwfFromPage() }
        })
    }

    /** 全屏切换：系统栏始终保持隐藏（topBar 已移除） */
    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        applyImmersiveFullscreen()
        floatingMenu.isFullscreen = isFullscreen
    }

    // ---------------- SWF 提取 ----------------

    /**
     * 从当前网页提取 SWF 文件。
     * 注入 JS 嗅探器扫描页面中的 SWF URL（DOM 元素、iframe、Performance API、网络请求），
     * 发现后通过 [WebAppInterface.onSwfFound] 回调到原生，弹出选择对话框。
     */
    private fun extractSwfFromPage() {
        Toast.makeText(this, "正在扫描页面中的 SWF...", Toast.LENGTH_SHORT).show()

        // 设置回调：JS 嗅探器通过 window.Android.onSwfFound(json) 回调到原生
        webAppInterface.swfExtractCallback = { json ->
            showSwfExtractDialog(json)
        }

        webView.evaluateJavascript(SWF_SNIFFER_SCRIPT, null)
    }

    /**
     * 显示 SWF 提取结果对话框，供用户选择下载或播放。
     */
    private fun showSwfExtractDialog(json: String) {
        try {
            val arr = org.json.JSONArray(json)
            if (arr.length() == 0) {
                Toast.makeText(this, "未在页面中发现 SWF 文件", Toast.LENGTH_SHORT).show()
                return
            }

            val items = mutableListOf<String>()
            val swfUrls = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val url = obj.optString("url", "")
                val title = obj.optString("title", "")
                val size = obj.optString("size", "")
                if (url.isNotEmpty()) {
                    val display = if (title.isNotEmpty()) "$title\n$url" else url
                    items.add(if (size.isNotEmpty()) "$display ($size)" else display)
                    swfUrls.add(url)
                }
            }

            if (items.isEmpty()) {
                Toast.makeText(this, "未在页面中发现 SWF 文件", Toast.LENGTH_SHORT).show()
                return
            }

            val labels = items.toTypedArray()
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("发现 ${items.size} 个 SWF 文件")
                .setItems(labels) { _, which ->
                    val swfUrl = swfUrls[which]
                    showSwfActionDialog(swfUrl)
                }
                .setNegativeButton("关闭", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "解析 SWF 列表失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * SWF 操作对话框：下载到本地 / 用内置引擎播放
     */
    private fun showSwfActionDialog(swfUrl: String) {
        val items = arrayOf("用内置引擎播放", "下载到本地")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("SWF 操作")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> playSwfWithEngine(swfUrl)
                    1 -> downloadSwf(swfUrl)
                }
            }
            .show()
    }

    /**
     * 用内置引擎（Ruffle/WAFlash）播放 SWF。
     */
    private fun playSwfWithEngine(swfUrl: String) {
        val playerUrl = NavHelper.playerUrl(swfUrl, base = currentUrl, title = currentTitle)
        webView.loadUrl(playerUrl)
        Toast.makeText(this, "正在用${PrefsManager.flashEngine}引擎加载...", Toast.LENGTH_SHORT).show()
    }

    /**
     * 下载 SWF 文件到本地存储。
     * 下载完成后可选择用内置引擎播放。
     */
    private fun downloadSwf(swfUrl: String) {
        // 使用 currentUrl（已在主线程赋值），避免在后台线程访问 webView.url 触发线程违规
        val pageUrl = currentUrl.ifEmpty { swfUrl }

        // 创建进度对话框
        val progressDialog = android.app.ProgressDialog(this).apply {
            setTitle("下载 SWF")
            setMessage("正在连接服务器...")
            setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL)
            isIndeterminate = false
            max = 100
            progress = 0
            setCancelable(true)
        }
        progressDialog.show()

        Thread {
            var conn: java.net.HttpURLConnection? = null
            var fileStream: java.io.FileOutputStream? = null
            try {
                val swfUrlHttps = if (swfUrl.startsWith("http://")) "https://" + swfUrl.substring(7) else swfUrl
                conn = java.net.URL(swfUrlHttps).openConnection() as java.net.HttpURLConnection
                if (conn is javax.net.ssl.HttpsURLConnection) {
                    val tm = object : javax.net.ssl.X509TrustManager {
                        override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                        override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                    }
                    val ctx = javax.net.ssl.SSLContext.getInstance("TLS")
                    ctx.init(null, arrayOf(tm), java.security.SecureRandom())
                    conn.sslSocketFactory = ctx.socketFactory
                    conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                }
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.requestMethod = "GET"
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                conn.setRequestProperty("Accept", "*/*")
                conn.setRequestProperty("Accept-Encoding", "identity")
                // 转发 Cookie（防盗链/登录态）
                try {
                    val cookies = android.webkit.CookieManager.getInstance().getCookie(swfUrlHttps)
                    if (cookies != null && cookies.isNotEmpty()) {
                        conn.setRequestProperty("Cookie", cookies)
                    }
                } catch(e: Exception) {}
                // 使用页面 URL 作为 Referer（防盗链）
                val referer = if (pageUrl.isNotEmpty() && pageUrl != swfUrl) pageUrl else {
                    try { android.net.Uri.parse(swfUrlHttps).let { "${it.scheme}://${it.host}/" } } catch(e: Exception) { swfUrl }
                }
                conn.setRequestProperty("Referer", referer)
                conn.connect()

                if (conn.responseCode !in 200..299) {
                    runOnUiThread {
                        progressDialog.dismiss()
                        Toast.makeText(this, "下载失败: HTTP ${conn.responseCode}", Toast.LENGTH_LONG).show()
                    }
                    return@Thread
                }

                val totalBytes = conn.contentLength  // -1 if unknown
                val filename = swfUrl.substringAfterLast("/").substringBefore("?").let {
                    if (it.endsWith(".swf", ignoreCase = true)) it else "$it.swf"
                }.ifBlank { "game.swf" }

                // 准备目标文件
                val dir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                val swfDir = java.io.File(dir, "GameHTML")
                if (!swfDir.exists()) swfDir.mkdirs()
                val file = java.io.File(swfDir, filename)
                // 使用 .tmp 临时文件，下载完成后再重命名
                val tmpFile = java.io.File(swfDir, "$filename.tmp")

                // 更新对话框：显示文件大小
                runOnUiThread {
                    if (totalBytes > 0) {
                        progressDialog.setMessage("下载中: $filename (${totalBytes / 1024}KB)")
                    } else {
                        progressDialog.setMessage("下载中: $filename (大小未知)")
                        progressDialog.isIndeterminate = true
                    }
                }

                // 直接写入文件，不缓存在内存中（避免大文件 OOM）
                fileStream = java.io.FileOutputStream(tmpFile)
                val input = conn.inputStream
                val chunk = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0
                var lastUpdatePercent = -1
                while (true) {
                    if (Thread.currentThread().isInterrupted) {
                        input.close()
                        fileStream.close()
                        tmpFile.delete()
                        return@Thread
                    }
                    bytesRead = input.read(chunk)
                    if (bytesRead == -1) break
                    // 直接写入文件，不在内存中累积
                    fileStream.write(chunk, 0, bytesRead)
                    totalRead += bytesRead

                    // 更新进度（避免过于频繁更新 UI）
                    if (totalBytes > 0) {
                        val percent = (totalRead * 100 / totalBytes)
                        if (percent != lastUpdatePercent) {
                            lastUpdatePercent = percent
                            runOnUiThread {
                                if (!progressDialog.isShowing) return@runOnUiThread
                                progressDialog.progress = percent
                            }
                        }
                    }
                }
                input.close()
                fileStream.flush()
                fileStream.close()
                fileStream = null

                if (totalRead == 0) {
                    tmpFile.delete()
                    runOnUiThread {
                        progressDialog.dismiss()
                        Toast.makeText(this, "下载失败: 文件为空", Toast.LENGTH_LONG).show()
                    }
                    return@Thread
                }

                // 下载完成：重命名临时文件
                tmpFile.renameTo(file)

                val fileSizeKB = totalRead / 1024
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(this, "已下载: ${file.absolutePath} (${fileSizeKB}KB)", Toast.LENGTH_LONG).show()
                    // 提示是否播放
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("下载完成")
                        .setMessage("已保存到: ${file.absolutePath}\n\n是否用内置引擎播放？")
                        .setPositiveButton("播放") { _, _ ->
                            val playerUrl = NavHelper.playerUrl(
                                "https://flash.local/local.swf?t=${System.currentTimeMillis()}",
                                base = null, title = filename
                            )
                            localSwfUri = android.net.Uri.fromFile(file).toString()
                            webView.loadUrl(playerUrl)
                        }
                        .setNegativeButton("关闭", null)
                        .show()
                }
            } catch (e: Exception) {
                try { fileStream?.close() } catch(ignored: Exception) {}
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(this, "下载失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                conn?.disconnect()
            }
        }.start()
    }

    /** 横竖屏切换 */
    private fun toggleOrientation() {
        val isCurrentlyLandscape = requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        if (isCurrentlyLandscape) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            Toast.makeText(this, R.string.portrait_mode, Toast.LENGTH_SHORT).show()
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            Toast.makeText(this, R.string.landscape_mode, Toast.LENGTH_SHORT).show()
        }
        floatingMenu.isLandscape = !isCurrentlyLandscape
    }

    /** 应用设置中的屏幕方向 */
    private fun applyOrientation() {
        requestedOrientation = when (PrefsManager.orientation) {
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "auto" -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    /** 鼠标光标开关：注入/移除 JS 鼠标光标模拟脚本 */
    private fun toggleMouse() {
        isMouseEnabled = !isMouseEnabled
        if (isMouseEnabled) {
            webView.evaluateJavascript(MOUSE_CURSOR_SCRIPT, null)
            Toast.makeText(this, R.string.mouse_enabled, Toast.LENGTH_SHORT).show()
        } else {
            webView.evaluateJavascript(
                "(function(){var c=document.getElementById('__mouseCursor');if(c)c.remove();window.__mouseEnabled=false;})();", null)
            Toast.makeText(this, R.string.mouse_disabled, Toast.LENGTH_SHORT).show()
        }
    }

    /** 按键映射设置对话框 */
    private fun openKeyMappingDialog() {
        val items = arrayOf(
            "按键映射 (选择按键修改)",
            "添加按键",
            "删除按键",
            "Start/Select 映射",
            "方向键模式 (DPAD/WASD/摇杆)",
            "方向键大小",
            "动作按键大小",
            "显示/隐藏按键",
            "添加/隐藏鼠标按钮",
            "位置编辑模式 (拖动调整)",
            "视角旋转 (3D游戏)",
            "恢复默认"
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.key_mapping)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showActionButtonPicker()
                    1 -> addButton()
                    2 -> removeButton()
                    3 -> showSystemKeyPicker()
                    4 -> toggleDpadMode()
                    5 -> showDpadScalePicker()
                    6 -> showActionScalePicker()
                    7 -> showKeyVisibilityPicker()
                    8 -> toggleMouseMode()
                    9 -> togglePositionEditMode()
                    10 -> toggleCameraRotation()
                    11 -> resetAllKeySettings()
                }
            }
            .show()
    }

    /** 页面缩放调整对话框：自动 / 手动滑块 */
    private fun showPageZoomDialog() {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val dialogView = android.view.LayoutInflater.from(this)
            .inflate(R.layout.dialog_page_zoom, null)
        val radioAuto = dialogView.findViewById<android.widget.RadioButton>(R.id.radioZoomAuto)
        val radioManual = dialogView.findViewById<android.widget.RadioButton>(R.id.radioZoomManual)
        val slider = dialogView.findViewById<android.widget.SeekBar>(R.id.zoomSlider)
        val tvValue = dialogView.findViewById<android.widget.TextView>(R.id.tvZoomValue)

        val mode = PrefsManager.pageZoomMode
        val manual = PrefsManager.pageZoomManual

        if (mode == "manual") {
            radioManual.isChecked = true
            slider.isEnabled = true
            slider.progress = manual - 25  // 25~200 → 0~175
        } else {
            radioAuto.isChecked = true
            slider.isEnabled = false
            slider.progress = 40 - 25  // 默认 40%
        }
        tvValue.text = "${manual}%"

        slider.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val pct = progress + 25
                tvValue.text = "$pct%"
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })

        radioAuto.setOnClickListener {
            slider.isEnabled = false
            sp.edit().putString("page_zoom_mode", "auto").apply()
        }
        radioManual.setOnClickListener {
            slider.isEnabled = true
            sp.edit().putString("page_zoom_mode", "manual").apply()
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("页面缩放")
            .setView(dialogView)
            .setPositiveButton("应用") { _, _ ->
                val newMode = if (radioAuto.isChecked) "auto" else "manual"
                val newManual = slider.progress + 25
                sp.edit()
                    .putString("page_zoom_mode", newMode)
                    .putInt("page_zoom_manual", newManual)
                    .apply()
                webView.reload()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** UA 兼容模式切换：桌面 Chrome / IE11 兼容 / 移动版 */
    private fun showUaModeDialog() {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val modes = arrayOf("desktop" to "桌面模式 (Chrome)", "ie_compat" to "兼容模式 (IE11)", "mobile" to "移动模式")
        val current = PrefsManager.uaMode
        val checked = modes.indexOfFirst { it.first == current }.coerceAtLeast(0)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("浏览器兼容模式")
            .setSingleChoiceItems(modes.map { it.second }.toTypedArray(), checked) { dialog, which ->
                val mode = modes[which].first
                sp.edit().putString("ua_mode", mode).apply()
                webView.useUaMode(mode)
                dialog.dismiss()
                webView.reload()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** Flash 引擎切换：Ruffle / WAFlash / 关闭 */
    private fun showFlashEnginePicker() {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val engines = arrayOf(
            "Ruffle 最新版 (推荐, AS1/2/3 全面支持)",
            "WAFlash (AS2/AS3 完整支持, Canvas渲染)",
            "关闭 Flash"
        )
        val values = arrayOf("ruffle", "waflash", "off")
        val current = if (PrefsManager.isFlashEnabled) PrefsManager.flashEngine else "off"
        val checked = values.indexOf(current).coerceAtLeast(0)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Flash 引擎")
            .setSingleChoiceItems(engines, checked) { dialog, which ->
                if (which == 2) {
                    // "关闭 Flash" 是索引 2
                    sp.edit().putBoolean("flash_enabled", false).apply()
                    Toast.makeText(this, "Flash 已关闭", Toast.LENGTH_SHORT).show()
                } else {
                    sp.edit()
                        .putBoolean("flash_enabled", true)
                        .putString("flash_engine", values[which])
                        .apply()
                    Toast.makeText(this, "Flash 引擎: ${engines[which].substringBefore(" (")}", Toast.LENGTH_SHORT).show()
                    webView.reload()
                }
                dialog.dismiss()
            }
            .setNeutralButton("Ruffle CDN 切换") { _, _ ->
                showRuffleCdnPicker()
            }
            .show()
    }

    /** Ruffle CDN 来源切换：本地 / jsdelivr / unpkg */
    private fun showRuffleCdnPicker() {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val cdns = arrayOf("本地内置 (离线可用, 推荐)", "jsdelivr CDN (需联网)", "unpkg CDN (需联网)")
        val values = arrayOf("local", "jsdelivr", "unpkg")
        val current = PrefsManager.flashCdn
        val checked = values.indexOf(current).coerceAtLeast(0)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Ruffle 资源来源")
            .setSingleChoiceItems(cdns, checked) { dialog, which ->
                sp.edit().putString("flash_cdn", values[which]).apply()
                Toast.makeText(this, "已切换: ${cdns[which].substringBefore(" (")}", Toast.LENGTH_SHORT).show()
                webView.reload()
                dialog.dismiss()
            }
            .show()
    }

    /** Start/Select 按钮拖拽支持 */
    private var systemDragOffsetX = 0f
    private var systemDragOffsetY = 0f
    private var isSystemDragMode = false
    private fun setupSystemButtonsDrag() {
        // 在 LinearLayout 和内部 Button 上都设置触摸监听
        // Button 会消费触摸事件，所以必须在 Button 上也监听
        val dragListener = android.view.View.OnTouchListener { _, event ->
            if (!isSystemDragMode) return@OnTouchListener false
            val container = binding.systemButtons
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    systemDragOffsetX = event.rawX - container.x
                    systemDragOffsetY = event.rawY - container.y
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val parent = container.parent as View
                    val newX = (event.rawX - systemDragOffsetX).coerceIn(0f, parent.width - container.width.toFloat())
                    val newY = (event.rawY - systemDragOffsetY).coerceIn(0f, parent.height - container.height.toFloat())
                    container.x = newX
                    container.y = newY
                    PrefsManager.sp.edit()
                        .putFloat("system_pos_x", newX)
                        .putFloat("system_pos_y", newY)
                        .apply()
                }
            }
            true
        }
        binding.systemButtons.setOnTouchListener(dragListener)
        binding.btnStart.setOnTouchListener(dragListener)
        binding.btnSelect.setOnTouchListener(dragListener)
    }

    /** 位置编辑模式开关：开启后所有虚拟按键可手动拖动到任意位置 */
    private var isPositionEditMode = false
    private fun togglePositionEditMode() {
        isPositionEditMode = !isPositionEditMode
        binding.dpad.isDragMode = isPositionEditMode
        binding.actionButtons.isDragMode = isPositionEditMode
        binding.mouseControl.isDragMode = isPositionEditMode
        isSystemDragMode = isPositionEditMode
        // 编辑模式时显示所有按键（方便调整位置）
        if (isPositionEditMode) {
            binding.dpad.visibility = View.VISIBLE
            binding.actionButtons.visibility = View.VISIBLE
            binding.systemButtons.visibility = View.VISIBLE
            if (PrefsManager.isMouseButtonsVisible) {
                binding.mouseControl.visibility = View.VISIBLE
            }
            // 恢复保存的位置
            restoreSavedPositions()
            Toast.makeText(this, "位置编辑模式已开启，拖动按键到目标位置", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "位置已保存", Toast.LENGTH_SHORT).show()
            // 恢复正常显示
            showGamepad(gamepadVisible)
        }
    }

    /** 恢复保存的位置坐标 */
    private fun restoreSavedPositions() {
        binding.dpad.post {
            if (PrefsManager.dpadPosX >= 0) {
                binding.dpad.x = PrefsManager.dpadPosX
                binding.dpad.y = PrefsManager.dpadPosY
            }
            if (PrefsManager.actionPosX >= 0) {
                binding.actionButtons.x = PrefsManager.actionPosX
                binding.actionButtons.y = PrefsManager.actionPosY
            }
            if (PrefsManager.systemPosX >= 0) {
                binding.systemButtons.x = PrefsManager.systemPosX
                binding.systemButtons.y = PrefsManager.systemPosY
            }
            if (PrefsManager.mousePosX >= 0 && PrefsManager.isMouseButtonsVisible) {
                binding.mouseControl.x = PrefsManager.mousePosX
                binding.mouseControl.y = PrefsManager.mousePosY
            }
        }
    }

    /** 完整键盘列表 */
    private val fullKeyList: Array<String> = arrayOf(
        "A","B","C","D","E","F","G","H","I","J","K","L","M",
        "N","O","P","Q","R","S","T","U","V","W","X","Y","Z",
        "0","1","2","3","4","5","6","7","8","9",
        "SPACE","ENTER","TAB","ESC","BACK",
        "CTRL","SHIFT","ALT",
        "UP","DOWN","LEFT","RIGHT"
    )

    /** 按键映射选择（动态数量） */
    private fun showActionButtonPicker() {
        val keys = PrefsManager.gamepadKeys
        val labels = keys.mapIndexed { i, k -> "按键 ${i + 1} ($k)" }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("选择要设置的按键（共 ${keys.size} 个）")
            .setItems(labels) { _, which ->
                showKeyListPicker("gamepad_key_${which + 1}", "按键 ${which + 1}", keys[which])
            }
            .show()
    }

    /** Start/Select 映射 */
    private fun showSystemKeyPicker() {
        val labels = arrayOf("Select 键", "Start 键")
        val prefKeys = arrayOf("select_key", "start_key")
        val defaults = arrayOf(PrefsManager.selectKey, PrefsManager.startKey)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("选择要设置的按键")
            .setItems(labels) { _, which ->
                showKeyListPicker(prefKeys[which], labels[which], defaults[which])
            }
            .show()
    }

    /** 完整键盘列表选择对话框 */
    private fun showKeyListPicker(prefKey: String, title: String, current: String) {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val displayList = fullKeyList.map { key ->
            val desc = when (key) {
                "SPACE" -> "空格"
                "ENTER" -> "回车"
                "TAB" -> "Tab"
                "ESC" -> "Esc"
                "BACK" -> "返回"
                "CTRL" -> "Ctrl"
                "SHIFT" -> "Shift"
                "ALT" -> "Alt"
                "UP" -> "方向键 上"
                "DOWN" -> "方向键 下"
                "LEFT" -> "方向键 左"
                "RIGHT" -> "方向键 右"
                else -> "字母 $key"
            }
            "$key ($desc)"
        }.toTypedArray()
        val checked = fullKeyList.indexOf(current).coerceAtLeast(0)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(displayList, checked) { dialog, which ->
                sp.edit().putString(prefKey, fullKeyList[which]).apply()
                // 刷新按键视图与尺寸
                binding.actionButtons.invalidate()
                applyActionButtonsSize()
                Toast.makeText(this, "$title → ${fullKeyList[which]}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .show()
    }

    /** 切换方向键模式：joystick → dpad → wasd → joystick */
    private fun toggleDpadMode() {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val current = PrefsManager.dpadMode
        val newMode = when (current) {
            "joystick" -> "dpad"
            "dpad" -> "wasd"
            else -> "joystick"
        }
        sp.edit().putString("dpad_mode", newMode).apply()
        // 刷新方向键视图
        binding.dpad.invalidate()
        val label = when (newMode) {
            "wasd" -> "WASD 十字键"
            "joystick" -> "摇杆 (↑↓←→)"
            else -> "方向键 (↑↓←→)"
        }
        Toast.makeText(this, "方向键已切换为 $label", Toast.LENGTH_SHORT).show()
    }

    /** 添加按键：增加动作按钮数量（最多 18 个） */
    private fun addButton() {
        val current = PrefsManager.gamepadKeyCount
        if (current >= 18) {
            Toast.makeText(this, "已达到最大按键数量 (18)", Toast.LENGTH_SHORT).show()
            return
        }
        val newCount = current + 1
        PrefsManager.sp.edit().putInt("gamepad_key_count", newCount).apply()
        applyActionButtonsSize()
        binding.actionButtons.invalidate()
        Toast.makeText(this, "已添加按键，当前共 $newCount 个", Toast.LENGTH_SHORT).show()
    }

    /** 删除按键：减少动作按钮数量（最少 2 个） */
    private fun removeButton() {
        val current = PrefsManager.gamepadKeyCount
        if (current <= 2) {
            Toast.makeText(this, "已达到最小按键数量 (2)", Toast.LENGTH_SHORT).show()
            return
        }
        val newCount = current - 1
        PrefsManager.sp.edit().putInt("gamepad_key_count", newCount).apply()
        applyActionButtonsSize()
        binding.actionButtons.invalidate()
        Toast.makeText(this, "已删除按键，当前共 $newCount 个", Toast.LENGTH_SHORT).show()
    }

    /** 切换 3D 视角旋转模式 */
    private fun toggleCameraRotation() {
        val enabled = !PrefsManager.isCameraRotationEnabled
        PrefsManager.sp.edit().putBoolean("camera_rotation_enabled", enabled).apply()
        webView.cameraRotationEnabled = enabled
        if (enabled) {
            // 立即注入视角旋转脚本
            webView.evaluateJavascript(CAMERA_ROTATION_SCRIPT, null)
            Toast.makeText(this, "视角旋转已开启，拖动屏幕旋转视角", Toast.LENGTH_LONG).show()
        } else {
            // 移除视角旋转脚本
            webView.evaluateJavascript(
                "(function(){window.__cameraRotation=false;var s=document.getElementById('__cameraRotateStyle');if(s)s.remove();})();", null)
            Toast.makeText(this, "视角旋转已关闭", Toast.LENGTH_SHORT).show()
        }
    }

    /** 应用动作按键大小（根据缩放比例调整 View 尺寸） */
    private fun applyActionButtonsSize() {
        val scale = PrefsManager.gamepadScale
        val baseSize = resources.getDimensionPixelSize(R.dimen.action_group_size)
        // 按键数量多时自动放大容器，保证每个按钮有足够空间
        val count = PrefsManager.gamepadKeyCount
        val sizeMultiplier = if (count > 6) 1f + (count - 6) * 0.12f else 1f
        val newSize = (baseSize * scale * sizeMultiplier).toInt()
        binding.actionButtons.layoutParams.apply {
            width = newSize
            height = newSize
        }
        binding.actionButtons.requestLayout()
    }

    /** 方向键大小 */
    private fun showDpadScalePicker() {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val scales = arrayOf("50%", "75%", "100%", "125%", "150%", "200%")
        val values = intArrayOf(50, 75, 100, 125, 150, 200)
        val current = (PrefsManager.dpadScale * 100).toInt()
        val checked = values.indexOf(current).coerceAtLeast(2)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("方向键大小")
            .setSingleChoiceItems(scales, checked) { dialog, which ->
                sp.edit().putInt("dpad_scale", values[which]).apply()
                binding.dpad.invalidate()
                Toast.makeText(this, "方向键大小: ${scales[which]}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .show()
    }

    /** 动作按键大小 */
    private fun showActionScalePicker() {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val scales = arrayOf("50%", "75%", "100%", "125%", "150%", "200%")
        val values = intArrayOf(50, 75, 100, 125, 150, 200)
        val current = (PrefsManager.gamepadScale * 100).toInt()
        val checked = values.indexOf(current).coerceAtLeast(2)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("动作按键大小")
            .setSingleChoiceItems(scales, checked) { dialog, which ->
                sp.edit().putInt("gamepad_scale", values[which]).apply()
                applyActionButtonsSize()
                Toast.makeText(this, "动作按键大小: ${scales[which]}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .show()
    }

    /** 显示/隐藏按键（动态数量） */
    private fun showKeyVisibilityPicker() {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val keyCount = PrefsManager.gamepadKeyCount
        val keys = PrefsManager.gamepadKeys
        // 构建动态标签：按键 1 (J) ... 按键 N (X) + 方向键 + Start/Select
        val labels = ArrayList<String>()
        val prefKeys = ArrayList<String>()
        for (i in 0 until keyCount) {
            labels.add("按键 ${i + 1} (${keys.getOrElse(i) { "" }})")
            prefKeys.add("gamepad_key_${i + 1}_visible")
        }
        labels.add("方向键")
        prefKeys.add("dpad_visible")
        labels.add("Start/Select")
        prefKeys.add("system_buttons_visible")

        val checked = BooleanArray(labels.size) { i ->
            sp.getBoolean(prefKeys[i], true)
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("显示/隐藏按键（共 $keyCount 个动作键）")
            .setMultiChoiceItems(labels.toTypedArray(), checked) { _, which, isChecked ->
                sp.edit().putBoolean(prefKeys[which], isChecked).apply()
            }
            .setPositiveButton("确定") { _, _ ->
                // 刷新所有手柄视图
                binding.dpad.invalidate()
                binding.actionButtons.invalidate()
                binding.systemButtons.invalidate()
                showGamepad(gamepadVisible)
                Toast.makeText(this, "按键显示已更新", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    /** 恢复默认 */
    private fun resetAllKeySettings() {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val editor = sp.edit()
        // 重置按键数量为默认 6
        editor.putInt("gamepad_key_count", 6)
        // 重置所有可能的按键映射（2~18）
        val defaultKeys = arrayOf(
            "J", "K", "L", "U", "I", "O",
            "1", "2", "3", "4", "5", "6",
            "7", "8", "9", "Q", "E", "R"
        )
        for (i in 0 until 18) {
            editor.putString("gamepad_key_${i + 1}", defaultKeys.getOrElse(i) { "J" })
            editor.putBoolean("gamepad_key_${i + 1}_visible", true)
        }
        editor.putString("select_key", "TAB").putString("start_key", "ENTER")
            .putString("dpad_mode", "joystick")
            .putInt("dpad_scale", 100).putInt("gamepad_scale", 100)
            // 清除保存的绝对位置坐标，恢复默认布局
            .putFloat("dpad_pos_x", -1f).putFloat("dpad_pos_y", -1f)
            .putFloat("action_pos_x", -1f).putFloat("action_pos_y", -1f)
            .putFloat("system_pos_x", -1f).putFloat("system_pos_y", -1f)
            .putFloat("mouse_pos_x", -1f).putFloat("mouse_pos_y", -1f)
            .putBoolean("dpad_visible", true).putBoolean("system_buttons_visible", false)
            .putBoolean("mouse_buttons_visible", false)
            .putBoolean("camera_rotation_enabled", false)
            .putBoolean("flash_enabled", true).putString("flash_engine", "ruffle")
            .apply()
        // 关闭视角旋转
        webView.cameraRotationEnabled = false
        webView.evaluateJavascript(
            "(function(){window.__cameraRotation=false;})();", null)
        binding.mouseControl.visibility = View.GONE
        // 刷新所有手柄视图
        applyActionButtonsSize()
        binding.dpad.invalidate()
        binding.actionButtons.invalidate()
        binding.systemButtons.invalidate()
        showGamepad(gamepadVisible)
        Toast.makeText(this, "已恢复全部默认设置", Toast.LENGTH_SHORT).show()
    }

    private fun toggleFavorite() {
        val isFav = FavoriteStore.isFavorite(currentUrl)
        if (isFav) FavoriteStore.remove(currentUrl)
        else FavoriteStore.add(currentUrl, currentTitle, currentType)
        updateFavoriteIcon()
        Toast.makeText(this, if (!isFav) R.string.added_to_favorites else R.string.removed_from_favorites,
            Toast.LENGTH_SHORT).show()
    }

    private fun updateFavoriteIcon() {
        // 收藏按钮已移除，此处保留收藏状态更新逻辑
    }

    private fun shareCurrent() {
        // 分享按钮已移除，此处保留分享逻辑备用
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "$currentTitle\n$currentUrl")
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    // ---------------- 返回键 ----------------
    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack()
                else finish()
            }
        })
    }

    // ---------------- 物理键盘透传 ----------------
    /**
     * 物理键盘事件：除 BACK（由返回栈处理）外，游戏键全部透传给 WebView。
     * WebView 内部的 dispatchKeyEvent 会把它们交给网页的 keydown/keyup。
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) return super.onKeyDown(keyCode, event)
        // 游戏键交给 WebView 消费
        if (keyCode in GameWebView.GAME_KEYS) {
            event?.let { webView.dispatchKeyEvent(it) }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode in GameWebView.GAME_KEYS) {
            event?.let { webView.dispatchKeyEvent(it) }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        // 释放所有虚拟手柄按下的按键，防止 Ruffle 角色持续移动
        webView.releaseAllKeys()
        // 暂停 Flash/H5 游戏的 JS 定时器，省电
        runCatching { webView.evaluateJavascript(
            "(function(){try{if(window.RufflePlayer){var r=window.RufflePlayer.newest();}}catch(e){}})();", null
        ) }
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        // 横竖屏切换或从后台返回后重新隐藏系统栏
        applyImmersiveFullscreen()
        // 同步视角旋转状态（从设置页面返回后恢复最新配置）
        webView.cameraRotationEnabled = PrefsManager.isCameraRotationEnabled
        // 刷新手柄视图（从设置页面返回后恢复最新配置）
        binding.dpad.invalidate()
        binding.actionButtons.invalidate()
        applyActionButtonsSize()
        if (PrefsManager.isGamepadEnabled && !gamepadVisible) {
            gamepadVisible = true
            showGamepad(true)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // 窗口获得焦点时确保系统栏隐藏（处理横竖屏切换后系统栏重新出现）
        if (hasFocus) applyImmersiveFullscreen()
    }

    override fun onDestroy() {
        webView.apply {
            (parent as? android.view.ViewGroup)?.removeView(this)
            destroy()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_TYPE = "extra_type"

        /**
         * 鼠标光标模拟脚本：在 PC 网页上显示一个跟随触摸的鼠标光标，
         * 触摸 = 鼠标移动，点击 = 鼠标左键点击。
         * 用于兼容需要鼠标 hover 的 PC 网页。
         */
        private const val MOUSE_CURSOR_SCRIPT = """
            (function(){
              if (window.__mouseEnabled) return; window.__mouseEnabled = true;
              var cursor = document.createElement('div');
              cursor.id = '__mouseCursor';
              cursor.style.cssText = 'position:fixed;width:20px;height:20px;pointer-events:none;z-index:999999;left:0;top:0;transform:translate(-4px,-4px);';
              cursor.innerHTML = '<svg width="20" height="20" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path d="M5.5,3.5L18,12L11.5,12.5L15,19L12.5,20L9,13.5L5.5,17L5.5,3.5Z" fill="white" stroke="black" stroke-width="1.5"/></svg>';
              document.body.appendChild(cursor);
              var lastX = 0, lastY = 0;
              document.addEventListener('touchstart', function(e){
                var t = e.touches[0];
                lastX = t.clientX; lastY = t.clientY;
                cursor.style.left = lastX + 'px';
                cursor.style.top = lastY + 'px';
              }, {passive: true});
              document.addEventListener('touchmove', function(e){
                var t = e.touches[0];
                lastX = t.clientX; lastY = t.clientY;
                cursor.style.left = lastX + 'px';
                cursor.style.top = lastY + 'px';
                // 模拟 mousemove（触发 hover 效果）
                var el = document.elementFromPoint(lastX, lastY);
                if (el) {
                  var evt = new MouseEvent('mousemove', {bubbles:true, clientX:lastX, clientY:lastY});
                  el.dispatchEvent(evt);
                }
              }, {passive: true});
            })();
        """

        /**
         * 3D 视角旋转脚本：
         * 1. Hook requestPointerLock → 模拟指针锁定成功（移动端不支持原生 Pointer Lock）
         * 2. 提供 window.__cameraRotate(dx, dy) → 分发带 movementX/movementY 的 mousemove 事件
         * 3. 阻止页面滚动/选择，确保拖动只用于旋转视角
         */
        private const val CAMERA_ROTATION_SCRIPT = """
            (function(){
              if (window.__cameraRotation) return; window.__cameraRotation = true;

              // === 1. 模拟 Pointer Lock API ===
              // 很多 3D 游戏通过 requestPointerLock 锁定鼠标，移动端不支持，需 hook
              if (!window.__pointerLockHooked) {
                window.__pointerLockHooked = true;
                var _requestPointerLock = HTMLElement.prototype.requestPointerLock;
                HTMLElement.prototype.requestPointerLock = function() {
                  // 模拟锁定成功
                  window.__pointerLocked = true;
                  document.dispatchEvent(new Event('pointerlockchange'));
                  document.pointerLockElement = this;
                  return Promise.resolve();
                };
                // hook exitPointerLock
                document.exitPointerLock = function() {
                  window.__pointerLocked = false;
                  document.pointerLockElement = null;
                  document.dispatchEvent(new Event('pointerlockchange'));
                };
                // 让 document.pointerLockElement 可被游戏设置
                try {
                  Object.defineProperty(document, 'pointerLockElement', {
                    get: function() { return window.__pointerLockElement || null; },
                    set: function(v) { window.__pointerLockElement = v; },
                    configurable: true
                  });
                } catch(e) {}
              }

              // === 2. 提供视角旋转函数 ===
              window.__cameraRotate = function(dx, dy) {
                // 找到游戏画布元素
                var target = document.pointerLockElement;
                if (!target) {
                  target = document.querySelector('canvas') ||
                           document.querySelector('[id*="game"]') ||
                           document.querySelector('[id*="flash"]') ||
                           document.body;
                }
                if (!target) return;
                // 分发 mousemove 事件，带 movementX/movementY
                var evt = new MouseEvent('mousemove', {
                  bubbles: true,
                  cancelable: true,
                  view: window,
                  clientX: window.innerWidth / 2,
                  clientY: window.innerHeight / 2,
                  movementX: Math.round(dx),
                  movementY: Math.round(dy)
                });
                target.dispatchEvent(evt);
                // 部分游戏监听 document 而非 canvas
                document.dispatchEvent(evt);
              };

              // === 3. 阻止拖动时的页面滚动/选择 ===
              var style = document.createElement('style');
              style.id = '__cameraRotateStyle';
              style.textContent = 'body{-webkit-user-select:none;user-select:none;-webkit-touch-callout:none;overflow:hidden !important;}canvas{touch-action:none !important;}';
              document.head.appendChild(style);

              console.log('[CameraRotation] 视角旋转模式已启用');
            })();
        """

        /** 启动游戏播放器的便捷方法 */
        fun launch(context: android.content.Context, url: String, title: String, type: GameType) {
            context.startActivity(Intent(context, GameActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_TYPE, type.name)
            })
        }

        /**
         * SWF 嗅探器脚本：深度扫描网页中的 SWF 文件。
         *
         * 扫描来源：
         * 1. DOM 元素：<object data>、<embed src>、<param name="movie" value>
         * 2. iframe src 属性
         * 3. 所有元素的 data/src/href 属性（含 .swf）
         * 4. Performance API 资源时间线（getEntriesByType('resource')）
         * 5. 内联脚本中的 .swf URL（正则匹配）
         * 6. hook XMLHttpRequest/fetch 捕获动态加载的 SWF
         * 7. MutationObserver 监听动态插入的 Flash 元素
         *
         * 发现后通过 window.Android.onSwfFound(json) 回调到原生。
         */
        private const val SWF_SNIFFER_SCRIPT = """
            (function(){
              var found = {};
              function addUrl(url, title){
                if(!url) return;
                try { url = new URL(url, location.href).href; } catch(e) { return; }
                if(!/\.swf([?#]|$)/i.test(url) && !/application\/x-shockwave-flash/i.test(url)){
                  // 不是 SWF URL，跳过（除非是 data URI 的 flash）
                  if(!/^data:application\/x-shockwave-flash/i.test(url)) return;
                }
                if(found[url]) return;
                var t = title || '';
                if(!t){
                  try { t = decodeURIComponent(url.split('/').pop().split('?')[0].replace(/\.swf$/i,'')); } catch(e){}
                }
                found[url] = {url:url, title:t, size:''};
              }

              // 1. 扫描 DOM 元素
              function scanDOM(){
                // object/embed 标签
                var objs = document.querySelectorAll('object[data], embed[src]');
                objs.forEach(function(el){
                  var u = el.getAttribute('data') || el.getAttribute('src') || '';
                  var t = el.getAttribute('title') || el.getAttribute('name') || '';
                  if(u) addUrl(u, t);
                });
                // param name="movie"
                var params = document.querySelectorAll('param[name="movie"], param[name="src"]');
                params.forEach(function(p){
                  var v = p.getAttribute('value') || '';
                  if(v) addUrl(v, '');
                });
                // iframe src
                var iframes = document.querySelectorAll('iframe[src]');
                iframes.forEach(function(f){
                  var s = f.getAttribute('src') || '';
                  if(/\.swf/i.test(s)) addUrl(s, '');
                });
                // 所有带 .swf 的属性
                var all = document.querySelectorAll('[data*=".swf"], [src*=".swf"], [href*=".swf"]');
                all.forEach(function(el){
                  ['data','src','href'].forEach(function(attr){
                    var v = el.getAttribute(attr);
                    if(v && /\.swf/i.test(v)) addUrl(v, el.getAttribute('title') || '');
                  });
                });
              }

              // 2. Performance API 资源时间线
              function scanPerformance(){
                try {
                  var entries = performance.getEntriesByType('resource');
                  entries.forEach(function(e){
                    if(/\.swf([?#]|$)/i.test(e.name)) addUrl(e.name, '');
                  });
                } catch(e) {}
              }

              // 3. 扫描内联脚本中的 SWF URL
              function scanScripts(){
                var scripts = document.querySelectorAll('script:not([src])');
                var re = /(?:https?:)?[^\s'"<>]+\.swf[^\s'"<>]*/gi;
                scripts.forEach(function(s){
                  var text = s.textContent || '';
                  var m;
                  while((m = re.exec(text)) !== null){
                    addUrl(m[0], '');
                  }
                });
                // 也扫描外部脚本变量的可能值
                var extScripts = document.querySelectorAll('script[src]');
                extScripts.forEach(function(s){
                  var src = s.getAttribute('src') || '';
                  if(/\.swf/i.test(src)) addUrl(src, '');
                });
              }

              // 4. hook XHR/fetch（捕获未来动态加载的 SWF）
              if(!window.__swfSniffHooked){
                window.__swfSniffHooked = true;
                var origOpen = XMLHttpRequest.prototype.open;
                XMLHttpRequest.prototype.open = function(method, url){
                  if(url && /\.swf([?#]|$)/i.test(url)) addUrl(url, '');
                  return origOpen.apply(this, arguments);
                };
                var origFetch = window.fetch;
                if(origFetch){
                  window.fetch = function(input){
                    var u = typeof input === 'string' ? input : (input && input.url ? input.url : '');
                    if(u && /\.swf([?#]|$)/i.test(u)) addUrl(u, '');
                    return origFetch.apply(this, arguments);
                  };
                }
              }

              // 5. 立即扫描
              scanDOM();
              scanPerformance();
              scanScripts();

              // 6. MutationObserver 监听动态插入的元素（持续 5 秒）
              if(window.MutationObserver){
                var mo = new MutationObserver(function(muts){
                  muts.forEach(function(m){
                    m.addedNodes.forEach(function(n){
                      if(n.nodeType === 1){
                        var u = n.getAttribute && (n.getAttribute('data') || n.getAttribute('src') || n.getAttribute('href') || '');
                        if(u && /\.swf/i.test(u)) addUrl(u, n.getAttribute('title') || '');
                        if(n.querySelectorAll){
                          var inner = n.querySelectorAll('[data*=".swf"], [src*=".swf"], [href*=".swf"], param[name="movie"]');
                          inner.forEach(function(el){
                            var v = el.getAttribute('data') || el.getAttribute('src') || el.getAttribute('href') || el.getAttribute('value') || '';
                            if(v && /\.swf/i.test(v)) addUrl(v, '');
                          });
                        }
                      }
                    });
                  });
                });
                mo.observe(document.documentElement || document.body || document, {childList:true, subtree:true});
                setTimeout(function(){ mo.disconnect(); }, 5000);
              }

              // 7. 延迟再次扫描（等页面 JS 执行完）并回调
              setTimeout(function(){
                scanDOM();
                scanPerformance();
                scanScripts();
                var arr = [];
                for(var u in found) arr.push(found[u]);
                if(window.Android && window.Android.onSwfFound){
                  window.Android.onSwfFound(JSON.stringify(arr));
                } else {
                  console.log('[SWF Sniffer] 未找到 window.Android 接口');
                }
              }, 1500);

              // 立即也回调一次（快速发现）
              setTimeout(function(){
                var arr = [];
                for(var u in found) arr.push(found[u]);
                if(arr.length > 0 && window.Android && window.Android.onSwfFound){
                  window.Android.onSwfFound(JSON.stringify(arr));
                }
              }, 500);
            })();
        """
    }
}

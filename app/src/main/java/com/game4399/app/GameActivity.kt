package com.game4399.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.ValueCallback
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
import com.game4399.app.webview.GameWebChromeClient
import com.game4399.app.webview.GameWebView
import com.game4399.app.webview.GameWebViewClient
import com.game4399.app.webview.NavHelper
import com.game4399.app.webview.WebAppInterface

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

    private var currentUrl: String = ""
    private var currentTitle: String = ""
    private var currentType: GameType = GameType.URL
    private var gamepadVisible = false

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
        // 沉浸式全屏
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentUrl = intent.getStringExtra(EXTRA_URL) ?: return finish()
        currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: "游戏"
        currentType = runCatching { GameType.valueOf(intent.getStringExtra(EXTRA_TYPE) ?: "") }
            .getOrDefault(GameType.URL)

        webView = binding.gameWebView
        setupWebView()
        setupGamepad()
        setupToolbar()
        setupBackHandler()

        // 开始加载
        loadGame(currentUrl, currentTitle, currentType)
    }

    // ---------------- WebView ----------------
    private fun setupWebView() {
        webView.apply {
            addJavascriptInterface(WebAppInterface(this@GameActivity), "Android")
            webChromeClient = object : GameWebChromeClient(chromeCallback) {}
            webViewClient = object : GameWebViewClient(viewClientCallback) {}
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
        override fun onShowFullscreen(view: View, callback: GameWebChromeClient.CustomViewCallback) {
            // Flash 全屏：直接铺满
            binding.topBar.visibility = View.GONE
        }
        override fun onHideFullscreen() {
            binding.topBar.visibility = View.VISIBLE
        }
        override fun onFileChooser(cb: ValueCallback<Array<Uri>>, accept: String?): Boolean {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = cb
            val mimes = accept?.split(",")?.toTypedArray() ?: arrayOf("*/*")
            try { fileChooserLauncher.launch(mimes) } catch (e: Exception) { cb.onReceiveValue(null); filePathCallback = null }
            return true
        }
    }

    private val viewClientCallback = object : GameWebViewClient.Callback {
        override fun onPageStarted(url: String?) {
            binding.loadingOverlay.visibility = View.VISIBLE
            binding.loadingText.text = getString(R.string.loading)
            binding.errorView.visibility = View.GONE
        }
        override fun onPageFinished(url: String?) {
            binding.loadingOverlay.visibility = View.GONE
            url?.let { FavoriteStore.addHistory(it, currentTitle, currentType) }
            updateFavoriteIcon()
        }
        override fun onProgress(progress: Int) = chromeCallback.onProgress(progress)
        override fun onError(url: String?, errorCode: Int, description: String?) {
            binding.loadingOverlay.visibility = View.GONE
            binding.errorView.visibility = View.VISIBLE
        }
        override fun onSwfIntercepted(swfUrl: String, pageUrl: String) {
            // 页面里点击 .swf 链接 → 用内置播放器
            val playerUrl = NavHelper.playerUrl(swfUrl, base = pageUrl, title = currentTitle)
            webView.loadUrl(playerUrl)
        }
        override fun shouldInjectRuffle(url: String?): Boolean {
            // PC Flash 页注入；内置 player.html 已自带 Ruffle，不重复注入
            if (url == null) return false
            if (url.startsWith("file:///android_asset/player.html")) return false
            // www.4399.com/flash/ 页面注入
            return url.contains("4399.com") && url.contains("/flash/")
        }
    }

    private fun loadGame(url: String, title: String, type: GameType) {
        currentUrl = url; currentTitle = title; currentType = type
        // SWF 直链 → 内置播放器
        if (NavHelper.isSwf(url)) {
            val playerUrl = NavHelper.playerUrl(url, base = null, title = title)
            webView.loadUrl(playerUrl)
        } else {
            webView.loadUrl(url)
        }
    }

    // ---------------- 虚拟手柄 ----------------
    private fun setupGamepad() {
        val alpha = PrefsManager.gamepadAlpha
        binding.dpad.targetWebView = webView
        binding.dpad.overlayAlpha = alpha
        binding.actionButtons.targetWebView = webView
        binding.actionButtons.overlayAlpha = alpha

        binding.btnStart.setOnClickListener {
            webView.injectKey(KeyEvent.KEYCODE_ENTER)
        }
        binding.btnSelect.setOnClickListener {
            webView.injectKey(KeyEvent.KEYCODE_TAB)
        }

        // 根据设置初始显示手柄
        if (PrefsManager.isGamepadEnabled) {
            gamepadVisible = true
            showGamepad(true)
        }
    }

    private fun showGamepad(show: Boolean) {
        val v = if (show) View.VISIBLE else View.GONE
        binding.dpad.visibility = v
        binding.actionButtons.visibility = v
        binding.systemButtons.visibility = v
        binding.btnGamepad.contentDescription = if (show)
            getString(R.string.gamepad_hide) else getString(R.string.gamepad_show)
    }

    private fun toggleGamepad() {
        gamepadVisible = !gamepadVisible
        showGamepad(gamepadVisible)
    }

    // ---------------- 顶部工具栏 ----------------
    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { if (webView.canGoBack()) webView.goBack() else finish() }
        binding.btnForward.setOnClickListener { if (webView.canGoForward()) webView.goForward() }
        binding.btnRefresh.setOnClickListener { webView.reload() }
        binding.btnGamepad.setOnClickListener { toggleGamepad() }
        binding.btnFavorite.setOnClickListener { toggleFavorite() }
        binding.btnShare.setOnClickListener { shareCurrent() }
        binding.btnRetry.setOnClickListener { webView.reload() }
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
        val isFav = FavoriteStore.isFavorite(currentUrl)
        binding.btnFavorite.setImageResource(if (isFav) R.drawable.ic_star else R.drawable.ic_star_outline)
    }

    private fun shareCurrent() {
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
            webView.dispatchKeyEvent(event)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode in GameWebView.GAME_KEYS) {
            webView.dispatchKeyEvent(event)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        // 暂停 Flash/H5 游戏的 JS 定时器，省电
        runCatching { webView.evaluateJavascript(
            "(function(){try{if(window.RufflePlayer){var r=window.RufflePlayer.newest();}}catch(e){}})();", null
        ) }
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
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

        /** 启动游戏播放器的便捷方法 */
        fun launch(context: android.content.Context, url: String, title: String, type: GameType) {
            context.startActivity(Intent(context, GameActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_TYPE, type.name)
            })
        }
    }
}

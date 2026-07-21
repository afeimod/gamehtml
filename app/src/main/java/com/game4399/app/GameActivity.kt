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

    private var currentUrl: String = ""
    private var currentTitle: String = ""
    private var currentType: GameType = GameType.URL
    private var gamepadVisible = false
    private var isFullscreen = false
    private var isMouseEnabled = false
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
        webView.apply {
            addJavascriptInterface(WebAppInterface(this@GameActivity), "Android")
            webChromeClient = object : GameWebChromeClient(chromeCallback) {}
            webViewClient = object : GameWebViewClient(viewClientCallback) {}

            // PC Flash 页面（www.4399.com/flash/）使用桌面 UA，确保 4399 返回电脑版网页
            // H5 游戏（h.4399.com）和手机版页面保持移动 UA
            val isPcPage = currentUrl.contains("www.4399.com") ||
                currentType == GameType.FLASH ||
                currentUrl.contains("/flash/")
            useDesktopMode(isPcPage)
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
            // Flash 全屏：直接铺满（topBar 已移除，无需操作）
        }
        override fun onHideFullscreen() {
            // topBar 已移除，无需操作
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
            // 如果鼠标光标已开启，重新注入（页面导航后会丢失）
            if (isMouseEnabled) {
                webView.evaluateJavascript(MOUSE_CURSOR_SCRIPT, null)
            }
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
            webView.injectKey(KeyMapper.toKeyCode(PrefsManager.startKey))
        }
        binding.btnSelect.setOnClickListener {
            webView.injectKey(KeyMapper.toKeyCode(PrefsManager.selectKey))
        }

        // 根据设置初始显示手柄
        if (PrefsManager.isGamepadEnabled) {
            gamepadVisible = true
            showGamepad(true)
        }
    }

    private fun showGamepad(show: Boolean) {
        val v = if (show) View.VISIBLE else View.GONE
        // 方向键根据可见性设置
        binding.dpad.visibility = if (show && PrefsManager.isDpadVisible) View.VISIBLE else View.GONE
        binding.actionButtons.visibility = v
        // Start/Select 根据可见性设置
        binding.systemButtons.visibility = if (show && PrefsManager.isSystemButtonsVisible) View.VISIBLE else View.GONE
    }

    private fun toggleGamepad() {
        gamepadVisible = !gamepadVisible
        showGamepad(gamepadVisible)
    }

    // ---------------- 顶部工具栏（已移除，功能由悬浮菜单提供） ----------------
    private fun setupToolbar() {
        binding.btnRetry.setOnClickListener { webView.reload() }
    }

    // ---------------- 悬浮菜单 ----------------
    private fun setupFloatingMenu() {
        floatingMenu = binding.floatingMenu
        floatingMenu.setCallbacks(object : FloatingMenuView.Callbacks {
            override fun onToggleFullscreen() { toggleFullscreen() }
            override fun onToggleOrientation() { toggleOrientation() }
            override fun onToggleGamepad() { toggleGamepad() }
            override fun onToggleMouse() { toggleMouse() }
            override fun onOpenKeyMapping() { openKeyMappingDialog() }
            override fun onRefresh() { webView.reload() }
            override fun onBack() { if (webView.canGoBack()) webView.goBack() else finish() }
            override fun onClose() { finish() }
        })
    }

    /** 全屏切换：系统栏始终保持隐藏（topBar 已移除） */
    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        applyImmersiveFullscreen()
        floatingMenu.isFullscreen = isFullscreen
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
            "按键 A~F 映射",
            "Start/Select 映射",
            "方向键模式 (DPAD/WASD)",
            "方向键大小",
            "动作按键大小",
            "方向键位置",
            "动作按键位置",
            "显示/隐藏按键",
            "恢复默认"
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.key_mapping)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showActionButtonPicker()
                    1 -> showSystemKeyPicker()
                    2 -> toggleDpadMode()
                    3 -> showDpadScalePicker()
                    4 -> showActionScalePicker()
                    5 -> showDpadPositionPicker()
                    6 -> showActionPositionPicker()
                    7 -> showKeyVisibilityPicker()
                    8 -> resetAllKeySettings()
                }
            }
            .show()
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

    /** 按键 A~F 映射选择 */
    private fun showActionButtonPicker() {
        val labels = arrayOf("按键 A", "按键 B", "按键 C", "按键 D", "按键 E", "按键 F")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("选择要设置的按键")
            .setItems(labels) { _, which ->
                showKeyListPicker(which, "gamepad_key_${which + 1}", labels[which])
            }
            .show()
    }

    /** Start/Select 映射 */
    private fun showSystemKeyPicker() {
        val labels = arrayOf("Select 键", "Start 键")
        val prefKeys = arrayOf("select_key", "start_key")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("选择要设置的按键")
            .setItems(labels) { _, which ->
                showKeyListPicker(-1, prefKeys[which], labels[which])
            }
            .show()
    }

    /** 完整键盘列表选择对话框 */
    private fun showKeyListPicker(buttonIndex: Int, prefKey: String, title: String) {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val current = sp.getString(prefKey, "J") ?: "J"
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
                Toast.makeText(this, "$title → ${fullKeyList[which]}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .show()
    }

    /** 切换方向键模式 */
    private fun toggleDpadMode() {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val current = PrefsManager.dpadMode
        val newMode = if (current == "wasd") "dpad" else "wasd"
        sp.edit().putString("dpad_mode", newMode).apply()
        Toast.makeText(this, "方向键已切换为 ${if (newMode == "wasd") "WASD" else "DPAD"}", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "动作按键大小: ${scales[which]}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .show()
    }

    /** 方向键位置调节 */
    private fun showDpadPositionPicker() {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val items = arrayOf("向左移动", "向右移动", "向上移动", "向下移动", "重置位置")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("方向键位置 (当前: ${PrefsManager.dpadOffsetX}, ${PrefsManager.dpadOffsetY})")
            .setItems(items) { _, which ->
                val dx = PrefsManager.dpadOffsetX
                val dy = PrefsManager.dpadOffsetY
                when (which) {
                    0 -> sp.edit().putInt("dpad_offset_x", dx - 20).apply()
                    1 -> sp.edit().putInt("dpad_offset_x", dx + 20).apply()
                    2 -> sp.edit().putInt("dpad_offset_y", dy - 20).apply()
                    3 -> sp.edit().putInt("dpad_offset_y", dy + 20).apply()
                    4 -> sp.edit().putInt("dpad_offset_x", 0).putInt("dpad_offset_y", 0).apply()
                }
                Toast.makeText(this, "方向键位置已更新", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    /** 动作按键位置调节 */
    private fun showActionPositionPicker() {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val items = arrayOf("向左移动", "向右移动", "向上移动", "向下移动", "重置位置")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("动作按键位置 (当前: ${PrefsManager.actionOffsetX}, ${PrefsManager.actionOffsetY})")
            .setItems(items) { _, which ->
                val dx = PrefsManager.actionOffsetX
                val dy = PrefsManager.actionOffsetY
                when (which) {
                    0 -> sp.edit().putInt("action_offset_x", dx - 20).apply()
                    1 -> sp.edit().putInt("action_offset_x", dx + 20).apply()
                    2 -> sp.edit().putInt("action_offset_y", dy - 20).apply()
                    3 -> sp.edit().putInt("action_offset_y", dy + 20).apply()
                    4 -> sp.edit().putInt("action_offset_x", 0).putInt("action_offset_y", 0).apply()
                }
                Toast.makeText(this, "动作按键位置已更新", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    /** 显示/隐藏按键 */
    private fun showKeyVisibilityPicker() {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val labels = arrayOf("按键 A", "按键 B", "按键 C", "按键 D", "按键 E", "按键 F", "方向键", "Start/Select")
        val prefKeys = arrayOf(
            "gamepad_key_1_visible", "gamepad_key_2_visible", "gamepad_key_3_visible",
            "gamepad_key_4_visible", "gamepad_key_5_visible", "gamepad_key_6_visible",
            "dpad_visible", "system_buttons_visible"
        )
        val checked = BooleanArray(8) { i ->
            sp.getBoolean(prefKeys[i], true)
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("显示/隐藏按键")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                sp.edit().putBoolean(prefKeys[which], isChecked).apply()
            }
            .setPositiveButton("确定") { _, _ ->
                Toast.makeText(this, "按键显示已更新", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    /** 恢复默认 */
    private fun resetAllKeySettings() {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        sp.edit()
            .putString("gamepad_key_1", "J").putString("gamepad_key_2", "K").putString("gamepad_key_3", "L")
            .putString("gamepad_key_4", "U").putString("gamepad_key_5", "I").putString("gamepad_key_6", "O")
            .putString("select_key", "TAB").putString("start_key", "ENTER")
            .putString("dpad_mode", "dpad")
            .putInt("dpad_scale", 100).putInt("gamepad_scale", 100)
            .putInt("dpad_offset_x", 0).putInt("dpad_offset_y", 0)
            .putInt("action_offset_x", 0).putInt("action_offset_y", 0)
            .putBoolean("gamepad_key_1_visible", true).putBoolean("gamepad_key_2_visible", true)
            .putBoolean("gamepad_key_3_visible", true).putBoolean("gamepad_key_4_visible", true)
            .putBoolean("gamepad_key_5_visible", true).putBoolean("gamepad_key_6_visible", true)
            .putBoolean("dpad_visible", true).putBoolean("system_buttons_visible", true)
            .apply()
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

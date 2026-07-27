package com.flashbox.app.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.flashbox.app.FlashBoxApp
import com.flashbox.app.R
import com.flashbox.app.data.FavoriteEntity
import com.flashbox.app.data.HistoryEntity
import com.flashbox.app.databinding.ActivityPlayerBinding
import com.flashbox.app.engine.EngineAssets
import com.flashbox.app.engine.EngineConfig
import com.flashbox.app.engine.EngineType
import com.flashbox.app.web.WebMode
import com.flashbox.app.virtualkey.KeyboardPickerDialog
import com.flashbox.app.virtualkey.VirtualKeyController
import com.flashbox.app.virtualkey.VirtualKeyMode
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import java.io.File
import kotlin.math.abs

/**
 * Hosts the WebView surface for both online browsing and local SWF playback,
 * plus the movable floating menu button, virtual-key overlay and nav sheet.
 */
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var app: FlashBoxApp
    private lateinit var vkController: VirtualKeyController
    private var navSheet: BottomSheetDialog? = null

    private var isLocal = false
    private var currentUrl: String = ""
    private var currentTitle: String = ""
    private var currentEngine: EngineType = EngineType.RUFFLE
    private var swfFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as FlashBoxApp
        if (app.settings.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        setupTopBar()
        setupFloatingButton()
        setupVirtualKeys()

        parseIntentAndLoad()
    }

    // ---------------------------------------------------------------- WebView
    private fun setupWebView() {
        binding.webView.init(app.settings)
        binding.webView.applyMode(app.settings.defaultWebMode)
        binding.webView.applyZoom(app.settings.pageZoom)
        currentEngine = app.settings.defaultEngine

        binding.webView.onProgress = { p ->
            binding.progress.visibility = if (p in 1..99) View.VISIBLE else View.GONE
            binding.progress.progress = p
        }
        binding.webView.onTitle = { t ->
            currentTitle = t
            binding.tvUrl.text = t.ifBlank { currentUrl }
        }
        binding.webView.onUrlLoaded = { url ->
            currentUrl = url
            binding.tvUrl.text = currentTitle.ifBlank { url }
            updateNavButtons()
            if (!isLocal && url.startsWith("http")) recordHistory(url)
        }
    }

    private fun setupTopBar() {
        binding.btnBack.setOnClickListener { if (binding.webView.canGoBack()) binding.webView.goBack() }
        binding.btnForward.setOnClickListener { if (binding.webView.canGoForward()) binding.webView.goForward() }
        binding.btnRefresh.setOnClickListener { binding.webView.reload() }
        binding.btnClose.setOnClickListener { finish() }
        binding.btnStar.setOnClickListener { toggleFavorite() }
        binding.webView.requestFocus()
    }

    private fun updateNavButtons() {
        binding.btnBack.isEnabled = binding.webView.canGoBack()
        binding.btnForward.isEnabled = binding.webView.canGoForward()
        binding.btnBack.alpha = if (binding.btnBack.isEnabled) 1f else 0.4f
        binding.btnForward.alpha = if (binding.btnForward.isEnabled) 1f else 0.4f
        val fav = app.database.favoriteDao().isFavorite(currentUrl) > 0
        binding.btnStar.alpha = if (fav) 1f else 0.6f
    }

    // ------------------------------------------------------- Floating button
    private fun setupFloatingButton() {
        val fab = binding.fabMenu
        var dX = 0f; var dY = 0f; var startX = 0f; var startY = 0f; var moved = false
        fab.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dX = v.x - e.rawX; dY = v.y - e.rawY
                    startX = e.rawX; startY = e.rawY; moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val nx = dX + e.rawX; val ny = dY + e.rawY
                    if (abs(e.rawX - startX) > 8 || abs(e.rawY - startY) > 8) {
                        moved = true
                        v.animate().x(nx.coerceIn(0f, (binding.root.width - v.width).toFloat()))
                            .y(ny.coerceIn(0f, (binding.root.height - v.height).toFloat()))
                            .setDuration(0).start()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) showNavMenu()
                    v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    // ----------------------------------------------------------- Virtual keys
    private fun setupVirtualKeys() {
        vkController = VirtualKeyController(
            this, binding.vkOverlay, app.settings, binding.webView
        )
        vkController.visible = app.settings.vkEnabled
    }

    // ------------------------------------------------------------- Load flow
    private fun parseIntentAndLoad() {
        val url = intent.getStringExtra(EXTRA_URL)
        val swfPath = intent.getStringExtra(EXTRA_SWF_PATH)
        val swfUri = intent.getStringExtra(EXTRA_SWF_URI)
        val swfName = intent.getStringExtra(EXTRA_SWF_NAME)
        val mode = intent.getStringExtra(EXTRA_MODE)

        if (!mode.isNullOrBlank()) {
            binding.webView.applyMode(WebMode.fromId(mode))
        }

        when {
            !url.isNullOrBlank() -> {
                isLocal = false
                currentUrl = normalizeUrl(url)
                binding.tvUrl.text = currentUrl
                binding.webView.loadUrl(currentUrl)
            }
            !swfPath.isNullOrBlank() -> {
                isLocal = true
                swfFile = File(swfPath)
                currentTitle = swfName ?: swfFile?.name ?: "本地游戏"
                promptEngineChoice { engine -> loadLocalSwf(engine, swfFile!!) }
            }
            !swfUri.isNullOrBlank() -> {
                isLocal = true
                val name = swfName ?: queryName(Uri.parse(swfUri)) ?: "game.swf"
                currentTitle = name
                val copied = copyUriToInternal(Uri.parse(swfUri), name)
                swfFile = copied
                promptEngineChoice { engine -> loadLocalSwf(engine, copied) }
            }
            else -> finish()
        }
    }

    private fun promptEngineChoice(callback: (EngineType) -> Unit) {
        val default = app.settings.defaultEngine
        val labels = arrayOf(getString(R.string.player_engine_ruffle), getString(R.string.player_engine_waflash))
        val checked = if (default == EngineType.WAFLASH) 1 else 0
        AlertDialog.Builder(this, R.style.Theme_FlashBox_Dialog)
            .setTitle(R.string.player_choose_engine)
            .setMessage(R.string.player_choose_engine_msg)
            .setSingleChoiceItems(labels, checked) { dlg, which ->
                val engine = if (which == 1) EngineType.WAFLASH else EngineType.RUFFLE
                app.settings.defaultEngine = engine
                currentEngine = engine
                dlg.dismiss()
                callback(engine)
            }
            .setCancelable(false)
            .setNegativeButton(R.string.cancel) { _, _ -> finish() }
            .show()
    }

    private fun loadLocalSwf(engine: EngineType, swf: File) {
        currentEngine = engine
        EngineAssets.ensurePrepared(this, engine)
        val config = app.settings.engineConfig(engine)
        val cfgJson = Uri.encode(Gson().toJson(config))
        val swfUri = Uri.encode("file://${swf.absolutePath}")
        val name = Uri.encode(swf.name)
        val html = EngineAssets.playerHtml(this)
        val url = "file://${html.absolutePath}" +
                "?engine=${engine.id}" +
                "&swf=$swfUri" +
                "&name=$name" +
                "&config=$cfgJson" +
                "&local=1"
        binding.tvUrl.text = currentTitle
        currentUrl = swf.absolutePath
        binding.webView.applyMode(WebMode.DESKTOP)
        binding.webView.loadUrl(url)
        recordHistory("file://${swf.absolutePath}")
    }

    private fun copyUriToInternal(uri: Uri, name: String): File {
        val dir = File(filesDir, "swf").apply { mkdirs() }
        val safeName = name.ifBlank { "game.swf" }.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val dest = File(dir, System.currentTimeMillis().toString() + "_" + safeName)
        contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return dest
    }

    private fun queryName(uri: Uri): String? {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = c.getString(idx)
            }
        }
        return name
    }

    private fun normalizeUrl(input: String): String {
        val t = input.trim()
        return when {
            t.startsWith("http://") || t.startsWith("https://") || t.startsWith("file://") -> t
            t.contains(' ') || !t.contains('.') -> "https://www.baidu.com/s?wd=" + Uri.encode(t)
            else -> "https://$t"
        }
    }

    // --------------------------------------------------------------- Nav menu
    private fun showNavMenu() {
        val view = layoutInflater.inflate(R.layout.sheet_nav_menu, null)
        val sheet = BottomSheetDialog(this, R.style.Theme_FlashBox_Dialog)
        sheet.setContentView(view)
        navSheet = sheet

        // quick actions
        val grid = view.findViewById<ViewGroup>(R.id.grid_actions)
        val actions = listOf(
            Triple(R.string.nav_back, R.drawable.ic_back) { if (binding.webView.canGoBack()) binding.webView.goBack() },
            Triple(R.string.nav_forward, R.drawable.ic_forward) { if (binding.webView.canGoForward()) binding.webView.goForward() },
            Triple(R.string.nav_refresh, R.drawable.ic_refresh) { binding.webView.reload() },
            Triple(R.string.nav_home_page, R.drawable.ic_home) { openHome() },
            Triple(R.string.nav_fullscreen, R.drawable.ic_fullscreen) { toggleFullscreen() },
            Triple(R.string.nav_share, R.drawable.ic_share) { shareCurrent() },
            Triple(R.string.nav_history, R.drawable.ic_history) { startActivity(Intent(this, com.flashbox.app.MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, "history")) },
            Triple(R.string.nav_settings, R.drawable.ic_settings) { startActivity(Intent(this, com.flashbox.app.MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, "settings")) }
        )
        actions.forEach { (label, icon, action) ->
            val item = layoutInflater.inflate(R.layout.item_nav_action, grid, false) as android.widget.LinearLayout
            val img = item.findViewById<android.widget.ImageView>(R.id.nav_icon)
            val txt = item.findViewById<android.widget.TextView>(R.id.nav_label)
            img.setImageResource(icon); txt.setText(label)
            item.setOnClickListener { action(); sheet.dismiss() }
            grid.addView(item)
        }

        // web mode
        val modeToggle = view.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.toggle_mode)
        val mode = app.settings.defaultWebMode
        val modeBtn = when (mode) {
            WebMode.DESKTOP -> R.id.btn_mode_desktop
            WebMode.COMPAT -> R.id.btn_mode_compat
            WebMode.MOBILE -> R.id.btn_mode_mobile
        }
        modeToggle.check(modeBtn)
        listOf(R.id.btn_mode_desktop to WebMode.DESKTOP,
            R.id.btn_mode_compat to WebMode.COMPAT,
            R.id.btn_mode_mobile to WebMode.MOBILE).forEach { (id, wm) ->
            view.findViewById<android.widget.Button>(id).setOnClickListener {
                app.settings.defaultWebMode = wm
                binding.webView.applyMode(wm)
                binding.webView.reload()
            }
        }

        // engine
        val engineToggle = view.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.toggle_engine)
        engineToggle.check(if (currentEngine == EngineType.WAFLASH) R.id.btn_engine_waflash else R.id.btn_engine_ruffle)
        view.findViewById<android.widget.Button>(R.id.btn_engine_ruffle).setOnClickListener {
            if (isLocal) reloadLocalWith(EngineType.RUFFLE) else currentEngine = EngineType.RUFFLE
            sheet.dismiss()
        }
        view.findViewById<android.widget.Button>(R.id.btn_engine_waflash).setOnClickListener {
            if (isLocal) reloadLocalWith(EngineType.WAFLASH) else currentEngine = EngineType.WAFLASH
            sheet.dismiss()
        }

        // zoom
        val seek = view.findViewById<SeekBar>(R.id.seek_zoom)
        val zoomLabel = view.findViewById<android.widget.TextView>(R.id.tv_zoom_label)
        seek.progress = app.settings.pageZoom
        zoomLabel.text = "${getString(R.string.nav_zoom)}: ${app.settings.pageZoom}%"
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    app.settings.pageZoom = p
                    binding.webView.applyZoom(p)
                    zoomLabel.text = "${getString(R.string.nav_zoom)}: $p%"
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // toggles
        view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_adblock).apply {
            isChecked = app.settings.adblockEnabled
            setOnCheckedChangeListener { _, b -> app.settings.adblockEnabled = b; binding.webView.adblocker.enabled = b }
        }
        view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_vk).apply {
            isChecked = vkController.visible
            setOnCheckedChangeListener { _, b -> vkController.visible = b }
        }

        // buttons
        view.findViewById<android.view.View>(R.id.btn_keys_editor).setOnClickListener { showKeysEditor(); sheet.dismiss() }
        view.findViewById<android.view.View>(R.id.btn_engine_settings).setOnClickListener { showEngineSettings(); sheet.dismiss() }

        sheet.show()
    }

    private fun showKeysEditor() {
        val options = arrayOf(
            getString(R.string.vk_joystick), getString(R.string.vk_dpad),
            "切换 WASD/方向键", getString(R.string.vk_add_key), getString(R.string.vk_reset), getString(R.string.vk_scale) + "+/-"
        )
        AlertDialog.Builder(this, R.style.Theme_FlashBox_Dialog)
            .setTitle(R.string.nav_keys_editor)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { vkController.setDirectionControl(VirtualKeyMode.DirectionControl.JOYSTICK) }
                    1 -> { vkController.setDirectionControl(VirtualKeyMode.DirectionControl.DPAD) }
                    2 -> {
                        val next = if (vkController.currentKeyLayout() == VirtualKeyMode.WASD) VirtualKeyMode.ARROWS else VirtualKeyMode.WASD
                        vkController.setKeyLayout(next)
                        Snackbar.make(binding.root, VirtualKeyMode.layoutName(next), Snackbar.LENGTH_SHORT).show()
                    }
                    3 -> {
                        val picker = KeyboardPickerDialog()
                        picker.setOnPicked({ key -> vkController.addKey(key) }, vkController.currentKeys().map { it.keyCode }.toSet())
                        picker.show(supportFragmentManager, "kbd")
                    }
                    4 -> vkController.resetDefaults()
                    5 -> { vkController.setEditMode(true); Snackbar.make(binding.root, "长按拖动移动，双指缩放，再次打开菜单关闭编辑", Snackbar.LENGTH_LONG).show() }
                }
            }
            .setNeutralButton("退出编辑") { _, _ -> vkController.setEditMode(false) }
            .show()
        vkController.setEditMode(false)
    }

    private fun showEngineSettings() {
        EngineSettingsDialog.newInstance(currentEngine).show(supportFragmentManager, "engine_cfg")
    }

    private fun reloadLocalWith(engine: EngineType) {
        swfFile?.let { loadLocalSwf(engine, it) }
    }

    private fun toggleFullscreen() {
        val ui = window.decorView
        ui.systemUiVisibility = (android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
        binding.topBar.visibility = View.GONE
        Snackbar.make(binding.root, getString(R.string.nav_exit_fullscreen), Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.ok) { exitFullscreen() }.show()
    }

    private fun exitFullscreen() {
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
        binding.topBar.visibility = View.VISIBLE
    }

    private fun openHome() {
        startActivity(Intent(this, com.flashbox.app.MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_TAB, "home"))
        finish()
    }

    private fun shareCurrent() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, currentUrl)
            putExtra(Intent.EXTRA_SUBJECT, currentTitle)
        }
        startActivity(Intent.createChooser(send, getString(R.string.nav_share)))
    }

    private fun toggleFavorite() {
        val url = currentUrl
        if (url.isBlank()) return
        if (app.database.favoriteDao().isFavorite(url) > 0) {
            app.database.favoriteDao().deleteByUrl(url)
            Snackbar.make(binding.root, "已取消收藏", Snackbar.LENGTH_SHORT).show()
        } else {
            app.database.favoriteDao().insert(FavoriteEntity(
                title = currentTitle.ifBlank { url },
                url = url, isLocal = isLocal,
                engine = currentEngine.id, addedAt = System.currentTimeMillis()
            ))
            Snackbar.make(binding.root, R.string.nav_bookmarked, Snackbar.LENGTH_SHORT).show()
        }
        updateNavButtons()
    }

    private fun recordHistory(url: String) {
        app.database.historyDao().insert(HistoryEntity(
            title = currentTitle.ifBlank { url },
            url = url, isLocal = isLocal,
            engine = currentEngine.id, visitedAt = System.currentTimeMillis()
        ))
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) binding.webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_SWF_PATH = "extra_swf_path"
        const val EXTRA_SWF_URI = "extra_swf_uri"
        const val EXTRA_SWF_NAME = "extra_swf_name"
        const val EXTRA_MODE = "extra_mode"

        fun launchUrl(ctx: Context, url: String, mode: String? = null) {
            ctx.startActivity(Intent(ctx, PlayerActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_MODE, mode))
        }

        fun launchLocalPath(ctx: Context, path: String, name: String? = null) {
            ctx.startActivity(Intent(ctx, PlayerActivity::class.java)
                .putExtra(EXTRA_SWF_PATH, path)
                .putExtra(EXTRA_SWF_NAME, name))
        }

        fun launchLocalUri(ctx: Context, uri: Uri, name: String? = null) {
            ctx.startActivity(Intent(ctx, PlayerActivity::class.java)
                .putExtra(EXTRA_SWF_URI, uri.toString())
                .putExtra(EXTRA_SWF_NAME, name))
        }
    }
}

package com.game4399.app.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.game4399.app.R
import com.game4399.app.data.LocalItem
import com.game4399.app.data.Prefs
import com.game4399.app.widget.FloatActionPanel
import com.game4399.app.widget.OnFloatActionListener
import com.game4399.app.widget.OnPadActionListener
import com.game4399.app.widget.VirtualPad

/**
 * 本地 Flash 游戏播放。
 *
 * 根据 Prefs.engine() 选择 web/ruffle_player.html 或 web/waflash_player.html
 * 这两个 html 都在 assets/web/，它们自己用 Ruffle / Waflash 引擎加载 ?src=...&name=...&quality=...
 *
 * 该 Activity 复用 BrowserActivity 的悬浮按钮 + 虚拟按键 + 画质/比例设置。
 */
class LocalPlayerActivity : AppCompatActivity(), OnFloatActionListener, OnPadActionListener {

    private lateinit var web: WebView
    private lateinit var progress: ProgressBar
    private lateinit var title: TextView
    private lateinit var panel: FloatActionPanel
    private lateinit var pad: VirtualPad
    private var item: LocalItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_local_player)
        web = findViewById(R.id.web)
        progress = findViewById(R.id.progress)
        title = findViewById(R.id.titleBar)
        panel = findViewById(R.id.floatPanel)
        pad = findViewById(R.id.pad)

        val path = intent.getStringExtra(EXTRA_PATH) ?: ""
        val name = intent.getStringExtra(EXTRA_NAME) ?: "Game"
        val isDir = intent.getBooleanExtra(EXTRA_IS_DIR, false)
        item = LocalItem(name, path, isDir)
        title.text = name

        setupWeb()
        panel.setListener(this)
        pad.setListener(this)

        loadPlayer()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWeb() {
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.allowFileAccess = true
        s.allowContentAccess = true
        s.allowFileAccessFromFileURLs = true
        s.allowUniversalAccessFromFileURLs = true
        s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)

        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                return false
            }
        }

        web.addJavascriptInterface(LocalJsBridge(this, web), "AndroidBridge")
    }

    private fun loadPlayer() {
        progress.visibility = View.VISIBLE
        val engine = Prefs.engine()
        val playerPage = if (engine == "ruffle") "file:///android_asset/web/ruffle_player.html"
        else "file:///android_asset/web/waflash_player.html"

        val path = item?.path ?: return
        // 引擎 html 用 window.PLAYER_SRC 接收 src；直接走 file:// 协议时我们需要把 uri 透出
        val quality = Prefs.quality()
        val aspect = Prefs.aspect()
        val bg = Prefs.bgColor()
        val letterbox = Prefs.ruffleLetterbox()

        val url = "$playerPage?src=${Uri.encode(path)}&name=${Uri.encode(item?.name ?: "")}" +
            "&quality=$quality&aspect=$aspect&bg=${Uri.encode(bg)}&letterbox=$letterbox"
        web.loadUrl(url)
    }

    override fun onBackPressed() {
        when {
            panel.isOpen -> { panel.close(); return }
            pad.isOpen -> { pad.close(); return }
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        try { web.destroy() } catch (_: Throwable) {}
        super.onDestroy()
    }

    // FloatAction
    override fun onFloatAction(action: String) {
        when (action) {
            "refresh" -> loadPlayer()
            "pad" -> pad.toggle()
            "settings" -> showSettings()
            "exit" -> finish()
            "back" -> onBackPressed()
            "home" -> finish()
            "url" -> showEnginePicker()
            "bookmark" -> {
                val cur = web.url ?: return
                if (Prefs.isFavorite(cur)) Prefs.removeFavorite(cur) else Prefs.addFavorite(com.game4399.app.data.HistoryItem(title.text.toString(), cur))
            }
            "forward" -> {}
            else -> {}
        }
    }

    private fun showSettings() {
        val items = arrayOf(
            "画质: ${qualityName(Prefs.quality())}",
            "画面比例: ${aspectName(Prefs.aspect())}",
            "引擎: ${engineName(Prefs.engine())}",
            if (Prefs.engine() == "ruffle") "Ruffle letterbox: ${if (Prefs.ruffleLetterbox()) "开" else "关"}" else "背景色: ${Prefs.bgColor()}"
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_title)
            .setItems(items) { _, w ->
                when (w) {
                    0 -> chooseQuality()
                    1 -> chooseAspect()
                    2 -> showEnginePicker()
                    3 -> if (Prefs.engine() == "ruffle") { Prefs.setRuffleLetterbox(!Prefs.ruffleLetterbox()); loadPlayer() } else { chooseBg() }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun chooseQuality() {
        val opts = arrayOf("low", "medium", "high", "best")
        val labels = arrayOf(getString(R.string.quality_low), getString(R.string.quality_medium), getString(R.string.quality_high), getString(R.string.quality_best))
        AlertDialog.Builder(this)
            .setTitle(R.string.quality)
            .setItems(labels) { _, w -> Prefs.setQuality(opts[w]); loadPlayer() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun chooseAspect() {
        val opts = arrayOf("fit", "fill", "stretch")
        val labels = arrayOf(getString(R.string.aspect_fit), getString(R.string.aspect_fill), getString(R.string.aspect_stretch))
        AlertDialog.Builder(this)
            .setTitle(R.string.aspect_ratio)
            .setItems(labels) { _, w -> Prefs.setAspect(opts[w]); loadPlayer() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun chooseBg() {
        val opts = arrayOf("#1a1a1a", "#000000", "#ffffff", "#f4f6fb")
        AlertDialog.Builder(this)
            .setTitle("背景色")
            .setItems(opts) { _, w -> Prefs.setBgColor(opts[w]); loadPlayer() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEnginePicker() {
        val labels = arrayOf(getString(R.string.engine_ruffle), getString(R.string.engine_waflash))
        AlertDialog.Builder(this)
            .setTitle(R.string.engine_select)
            .setSingleChoiceItems(labels, if (Prefs.engine() == "ruffle") 0 else 1) { _, w ->
                Prefs.setEngine(if (w == 0) "ruffle" else "waflash")
                loadPlayer()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun qualityName(q: String) = when (q) { "low" -> "低"; "medium" -> "中"; "high" -> "高"; "best" -> "最高"; else -> q }
    private fun aspectName(a: String) = when (a) { "fit" -> "保持比例"; "fill" -> "铺满"; "stretch" -> "拉伸"; else -> a }
    private fun engineName(e: String) = if (e == "ruffle") "Ruffle" else "Waflash"

    // VirtualPad
    override fun onPadKey(code: String, pressed: Boolean) {
        val js = "if(window.__gamePadDispatch){window.__gamePadDispatch('${escape(code)}', $pressed);}else{var el=document.activeElement||document.body;el.dispatchEvent(new KeyboardEvent('${if(pressed) "keydown" else "keyup"}',{code:'${escape(code)}',bubbles:true}));}"
        web.evaluateJavascript(js, null)
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("'", "\\'")

    companion object {
        private const val EXTRA_PATH = "path"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_IS_DIR = "isDir"
        fun open(ctx: Context, item: LocalItem) {
            ctx.startActivity(Intent(ctx, LocalPlayerActivity::class.java).apply {
                putExtra(EXTRA_PATH, item.path)
                putExtra(EXTRA_NAME, item.name)
                putExtra(EXTRA_IS_DIR, item.isDir)
            })
        }
    }
}

package com.game4399.app.webview

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import kotlin.math.abs

/**
 * 游戏专用 WebView：
 * 1. 预置适配 Flash/H5 游戏的 WebSettings（DOM 存储、自动播放、跨域、硬件加速）
 * 2. 触屏手势：长按 → 屏蔽系统选择菜单；滑动 → 方向键
 * 3. 物理键盘：dispatchKeyEvent 透传方向键 / WASD / 空格 / 回车给网页
 *
 * 注：当前 WebView 默认仅消费 BACK 键，其余按键必须重写 dispatchKeyEvent
 *     才能让网页 keydown/keyup 监听器收到。这是触屏 + 键盘双控的关键。
 */
open class GameWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    /** 是否拦截并屏蔽长按系统菜单（选中文字/复制） */
    var blockLongPressMenu: Boolean = true

    /** 保存原始移动版 UA，供切换时恢复 */
    private var mobileUa: String = ""

    private val gestureDetector = GestureDetector(context, GestureListener())

    init {
        configureSettings()
        // 横竖滚动条隐藏，让游戏画面铺满
        isHorizontalScrollBarEnabled = false
        isVerticalScrollBarEnabled = false
        // 确保 WebView 可获取焦点（网页 input 点击时触发软键盘必需）
        isFocusable = true
        isFocusableInTouchMode = true
        // 屏蔽长按系统菜单
        setOnLongClickListener { blockLongPressMenu }
    }

    private fun configureSettings() = settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true                       // H5 游戏依赖 localStorage
        databaseEnabled = true
        allowFileAccess = true
        allowContentAccess = true
        allowFileAccessFromFileURLs = true             // 本地 SWF 播放需要
        allowUniversalAccessFromFileURLs = true        // 自托管 wasm 跨域
        mediaPlaybackRequiresUserGesture = false       // Flash 游戏 BGM 自动播放
        javaScriptCanOpenWindowsAutomatically = true
        loadWithOverviewMode = true
        useWideViewPort = true
        cacheMode = WebSettings.LOAD_DEFAULT
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        // 双指缩放：PC 网页内容通常较宽，需要缩放查看
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false                  // 不显示+/-按钮，仅手势缩放
        // PC 网页缩放：强制启用 viewport 缩放，让宽页面适配屏幕
        setSupportMultipleWindows(false)
        // 保存默认移动版 UA，追加客户端标识
        mobileUa = userAgentString
        userAgentString = "$mobileUa 4399App/1.0 (Android)"
        // 硬件加速渲染（已在 Manifest 开启，这里再确保 LayerType）
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        // 启用 Safe Browsing（AndroidX WebKit，minSdk 23+）
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(this, true)
        }
    }

    /**
     * 切换桌面/移动 UA。
     * - 桌面模式：使用 Windows Chrome UA，不含 "Mobile"/"Android"，4399 服务器据此返回 PC 版页面
     * - 移动模式：恢复默认移动版 UA + 客户端标识
     */
    fun useDesktopMode(enabled: Boolean) {
        settings.userAgentString = if (enabled) DESKTOP_UA else "$mobileUa 4399App/1.0 (Android)"
    }

    // ---------------- 触屏 ----------------
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        if (event.action == MotionEvent.ACTION_DOWN) performClick()
        return super.onTouchEvent(event)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = false

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        // 双击放大已移除，只保留双指缩放
        override fun onLongPress(e: MotionEvent) { /* 屏蔽系统长按菜单 */ }
        override fun onFling(
            e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float
        ): Boolean {
            // 滑动手势映射为方向键（部分页游用方向键滚屏）
            if (abs(vx) > abs(vy)) {
                injectKey(if (vx > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
            }
            return true
        }
    }

    // ---------------- 键盘 ----------------
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // BACK：交给 Activity 的 OnBackPressedDispatcher 处理（这里不消费）
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            return super.dispatchKeyEvent(event)
        }
        // 游戏常用按键白名单透传给网页（其余交给系统）
        if (event.keyCode in GAME_KEYS) {
            return super.dispatchKeyEvent(event)
        }
        return super.dispatchKeyEvent(event)
    }

    /** 注入一次按键 down+up（供虚拟手柄调用） */
    fun injectKey(keyCode: Int, repeat: Int = 0) {
        val now = android.os.SystemClock.uptimeMillis()
        val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, repeat, 0, -1, 0,
            KeyEvent.FLAG_FROM_SYSTEM or KeyEvent.FLAG_SOFT_KEYBOARD)
        val up = KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, 0, -1, 0,
            KeyEvent.FLAG_FROM_SYSTEM or KeyEvent.FLAG_SOFT_KEYBOARD)
        dispatchKeyEvent(down)
        dispatchKeyEvent(up)
    }

    /** 按住状态：只发 down（不自动 up） */
    fun injectKeyDown(keyCode: Int) {
        val now = android.os.SystemClock.uptimeMillis()
        val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0, -1, 0,
            KeyEvent.FLAG_FROM_SYSTEM or KeyEvent.FLAG_SOFT_KEYBOARD)
        dispatchKeyEvent(down)
    }

    /** 松开：发 up */
    fun injectKeyUp(keyCode: Int) {
        val now = android.os.SystemClock.uptimeMillis()
        val up = KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, 0, -1, 0,
            KeyEvent.FLAG_FROM_SYSTEM or KeyEvent.FLAG_SOFT_KEYBOARD)
        dispatchKeyEvent(up)
    }

    // ---------------- 鼠标事件注入 ----------------

    /** 在 WebView 中心坐标注入鼠标移动事件（用于旋转视角） */
    fun injectMouseMove(dx: Float, dy: Float) {
        // 通过 JS 注入 mousemove 事件，模拟鼠标在页面中心移动
        val cx = width / 2f
        val cy = height / 2f
        val newX = cx + dx
        val newY = cy + dy
        evaluateJavascript(
            """
            (function(){
              var el = document.elementFromPoint($cx, $cy);
              if (!el) return;
              var evt = new MouseEvent('mousemove', {
                bubbles: true, cancelable: true, view: window,
                clientX: $newX, clientY: $newY,
                movementX: $dx, movementY: $dy
              });
              el.dispatchEvent(evt);
            })();
            """.trimIndent(), null
        )
    }

    /** 注入鼠标左键点击（mousedown + mouseup + click） */
    fun injectMouseLeftClick(x: Float = width / 2f, y: Float = height / 2f) {
        evaluateJavascript(
            """
            (function(){
              var el = document.elementFromPoint($x, $y);
              if (!el) return;
              var opt = {bubbles: true, cancelable: true, view: window, clientX: $x, clientY: $y, button: 0};
              el.dispatchEvent(new MouseEvent('mousedown', opt));
              el.dispatchEvent(new MouseEvent('mouseup', opt));
              el.dispatchEvent(new MouseEvent('click', opt));
            })();
            """.trimIndent(), null
        )
    }

    /** 注入鼠标右键点击（contextmenu） */
    fun injectMouseRightClick(x: Float = width / 2f, y: Float = height / 2f) {
        evaluateJavascript(
            """
            (function(){
              var el = document.elementFromPoint($x, $y);
              if (!el) return;
              var opt = {bubbles: true, cancelable: true, view: window, clientX: $x, clientY: $y, button: 2};
              el.dispatchEvent(new MouseEvent('mousedown', opt));
              el.dispatchEvent(new MouseEvent('mouseup', opt));
              el.dispatchEvent(new MouseEvent('contextmenu', opt));
            })();
            """.trimIndent(), null
        )
    }

    /** 注入鼠标左键按下（持续按住状态） */
    fun injectMouseLeftDown(x: Float = width / 2f, y: Float = height / 2f) {
        evaluateJavascript(
            """
            (function(){
              var el = document.elementFromPoint($x, $y);
              if (!el) return;
              el.dispatchEvent(new MouseEvent('mousedown', {
                bubbles: true, cancelable: true, view: window, clientX: $x, clientY: $y, button: 0
              }));
            })();
            """.trimIndent(), null
        )
    }

    /** 注入鼠标左键松开 */
    fun injectMouseLeftUp(x: Float = width / 2f, y: Float = height / 2f) {
        evaluateJavascript(
            """
            (function(){
              var el = document.elementFromPoint($x, $y);
              if (!el) return;
              el.dispatchEvent(new MouseEvent('mouseup', {
                bubbles: true, cancelable: true, view: window, clientX: $x, clientY: $y, button: 0
              }));
            })();
            """.trimIndent(), null
        )
    }

    companion object {
        /** 桌面版 Chrome UA（Windows），不含 Mobile/Android，4399 据此返回 PC 版页面 */
        const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        /** 游戏常用按键白名单 */
        val GAME_KEYS = intArrayOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT,
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT,
            // WASD
            KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D,
            // 功能字母
            KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_F,
            KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V,
            // 数字
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3,
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7,
            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_9
        )
    }
}

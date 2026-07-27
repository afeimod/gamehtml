package com.game4399.app.widget

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.game4399.app.R

interface OnFloatActionListener {
    fun onFloatAction(action: String)
}

/**
 * 悬浮按钮 + 展开菜单
 * - 默认右下角悬浮小按钮（圆形）
 * - 长按进入拖动模式（边框高亮 + 抖动）
 * - 点击展开成 8 个图标菜单：后退/前进/主页/刷新/输入 URL/收藏/手柄/设置/退出
 */
class FloatActionPanel @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val dp = resources.displayMetrics.density
    private var listener: OnFloatActionListener? = null

    private val button: View
    private val menu: LinearLayout
    private val root: FrameLayout
    private val backdrop: View

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var rawDownX = 0f
    private var rawDownY = 0f
    private var dragging = false

    init {
        LayoutInflater.from(context).inflate(R.layout.view_float_panel, this, true)
        root = findViewById(R.id.floatRoot)
        backdrop = findViewById(R.id.floatBackdrop)
        button = findViewById(R.id.floatButton)
        menu = findViewById(R.id.floatMenu)

        // 初始位置
        button.post {
            val p = button.layoutParams as LayoutParams
            p.gravity = Gravity.END or Gravity.BOTTOM
            p.rightMargin = (16 * dp).toInt()
            p.bottomMargin = (120 * dp).toInt()
            button.layoutParams = p
        }

        button.setOnTouchListener { v, ev ->
            val parentWidth = (root.parent as View).width
            val parentHeight = (root.parent as View).height
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = ev.rawX
                    lastTouchY = ev.rawY
                    rawDownX = ev.rawX
                    rawDownY = ev.rawY
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - lastTouchX
                    val dy = ev.rawY - lastTouchY
                    if (!dragging && (Math.abs(dx) + Math.abs(dy)) > 10 * dp) {
                        dragging = true
                        v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(120).start()
                    }
                    if (dragging) {
                        v.x = (v.x + dx).coerceIn(0f, (parentWidth - v.width).toFloat())
                        v.y = (v.y + dy).coerceIn(0f, (parentHeight - v.height).toFloat())
                    }
                    lastTouchX = ev.rawX
                    lastTouchY = ev.rawY
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    val moved = Math.abs(ev.rawX - rawDownX) + Math.abs(ev.rawY - rawDownY)
                    if (moved < 10 * dp && !dragging) {
                        toggle()
                    }
                    dragging = false
                }
            }
            true
        }

        // 长按抖动 / 边框高亮
        button.setOnLongClickListener {
            ViewCompat.animate(button).rotationBy(8f).setDuration(80).withEndAction {
                ViewCompat.animate(button).rotation(0f).setDuration(80).start()
            }.start()
            ToastProxy.show(context, R.string.hint_long_press_drag)
            true
        }

        backdrop.setOnClickListener { close() }

        // 菜单项
        bindMenuItem(R.id.menu_back, "back", R.drawable.ic_arrow_back, R.string.back)
        bindMenuItem(R.id.menu_forward, "forward", R.drawable.ic_arrow_forward, R.string.forward)
        bindMenuItem(R.id.menu_home, "home", R.drawable.ic_home, R.string.home)
        bindMenuItem(R.id.menu_refresh, "refresh", R.drawable.ic_refresh, R.string.refresh)
        bindMenuItem(R.id.menu_url, "url", R.drawable.ic_search, R.string.add_url)
        bindMenuItem(R.id.menu_bookmark, "bookmark", R.drawable.ic_bookmark, R.string.add_bookmark)
        bindMenuItem(R.id.menu_pad, "pad", R.drawable.ic_controller, R.string.float_gamepad)
        bindMenuItem(R.id.menu_settings, "settings", R.drawable.ic_settings, R.string.settings_title)
        bindMenuItem(R.id.menu_exit, "exit", R.drawable.ic_close, R.string.float_exit)
    }

    val isOpen: Boolean get() = menu.visibility == VISIBLE

    fun setListener(l: OnFloatActionListener) { listener = l }

    fun toggle() = if (isOpen) close() else open()
    fun open() {
        menu.visibility = VISIBLE
        backdrop.visibility = VISIBLE
        menu.alpha = 0f
        menu.animate().alpha(1f).setDuration(150).start()
    }
    fun close() {
        menu.animate().alpha(0f).setDuration(120).withEndAction {
            menu.visibility = GONE
            backdrop.visibility = GONE
            menu.alpha = 1f
        }.start()
    }

    private fun bindMenuItem(id: Int, action: String, iconRes: Int, labelRes: Int) {
        val v = findViewById<View>(id) ?: return
        // 从 menu_back / menu_forward 等 id 推出 icon_back / label_back 等
        val name = resources.getResourceEntryName(id).removePrefix("menu_")
        val iconId = resources.getIdentifier("icon_$name", "id", context.packageName)
        val labelId = resources.getIdentifier("label_$name", "id", context.packageName)
        if (iconId != 0) v.findViewById<ImageView>(iconId)?.setImageResource(iconRes)
        if (labelId != 0) v.findViewById<TextView>(labelId)?.setText(labelRes)
        v.setOnClickListener {
            listener?.onFloatAction(action)
            close()
        }
    }
}

object ToastProxy {
    fun show(ctx: Context, msg: Any) {
        val text = if (msg is Int) ctx.getString(msg) else msg.toString()
        android.widget.Toast.makeText(ctx, text, android.widget.Toast.LENGTH_SHORT).show()
    }
}

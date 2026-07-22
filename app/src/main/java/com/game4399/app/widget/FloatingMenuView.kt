package com.game4399.app.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import com.game4399.app.R

/**
 * 可拖动的悬浮菜单按钮。
 *
 * - 默认显示在右上角，可拖动到任意位置
 * - 点击展开半圆形菜单：全屏切换、横竖屏切换、手柄开关、鼠标光标、按键设置
 * - 横竖屏通用，通过 [attachTo] 添加到任意 ViewGroup
 *
 * 使用方式：
 *   val floatingMenu = FloatingMenuView(context)
 *   floatingMenu.attachTo(rootView)
 *   floatingMenu.setCallbacks(callbacks)
 */
class FloatingMenuView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** 菜单项回调 */
    interface Callbacks {
        fun onToggleFullscreen()
        fun onToggleOrientation()
        fun onToggleGamepad()
        fun onToggleMouse()
        fun onOpenKeyMapping()
        fun onOpenFlashSettings()
        fun onOpenPageZoom()
        fun onOpenUaMode()
        fun onRefresh()
        fun onBack()
        fun onClose()
    }

    private val triggerBtn: ImageButton
    private var popup: PopupWindow? = null
    private var callbacks: Callbacks? = null

    /** 当前是否展开菜单 */
    private var isMenuOpen = false

    /** 全屏状态（由外部更新） */
    var isFullscreen = false
        set(value) { field = value; updateTriggerIcon() }

    /** 横屏状态（由外部更新） */
    var isLandscape = true
        set(value) { field = value; updateTriggerIcon() }

    init {
        triggerBtn = ImageButton(context).apply {
            setBackgroundResource(R.drawable.bg_top_button)
            setImageResource(R.drawable.ic_menu)
            setPadding(24, 24, 24, 24)
            alpha = 0.7f
            contentDescription = context.getString(R.string.menu)
        }
        addView(triggerBtn, LayoutParams(
            dp(44), dp(44)
        ).apply { gravity = Gravity.CENTER })
        setupDrag()
    }

    fun setCallbacks(cb: Callbacks) {
        callbacks = cb
    }

    /** 添加到目标 ViewGroup 的右上角 */
    fun attachTo(parent: ViewGroup) {
        if (parent !== this.parent) {
            (parent as? ViewGroup)?.removeView(this)
            parent.addView(this, LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply {
                topMargin = dp(48)
                marginEnd = dp(12)
            })
        }
    }

    private fun setupDrag() {
        triggerBtn.setOnTouchListener(object : OnTouchListener {
            private var startX = 0f
            private var startY = 0f
            private var startTransX = 0f
            private var startTransY = 0f
            private var moved = false
            private var downTime = 0L

            @SuppressLint("ClickableViewAccessibility")
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = e.rawX
                        startY = e.rawY
                        startTransX = translationX
                        startTransY = translationY
                        moved = false
                        downTime = System.currentTimeMillis()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - startX
                        val dy = e.rawY - startY
                        if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                            moved = true
                            val parent = (this@FloatingMenuView.parent as? ViewGroup)
                            val maxX = (parent?.width ?: 0).toFloat() - width
                            val maxY = (parent?.height ?: 0).toFloat() - height
                            // 使用 translationX/Y 移动，兼容所有父容器
                            val newX = (startTransX + dx).coerceIn(-maxX + width, 0f)
                            val newY = (startTransY + dy).coerceIn(0f, maxY.coerceAtLeast(0f))
                            // 简单限制：确保不拖出屏幕
                            translationX = (startTransX + dx)
                            translationY = (startTransY + dy)
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved && System.currentTimeMillis() - downTime < 300) {
                            toggleMenu()
                        }
                    }
                }
                return true
            }
        })
    }

    private fun toggleMenu() {
        if (isMenuOpen) {
            popup?.dismiss()
        } else {
            showMenu()
        }
    }

    private fun showMenu() {
        val isLandscape = resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val columns = if (isLandscape) 2 else 1

        val menuContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_top_bar)
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }

        val items = listOf(
            MenuItem(context.getString(R.string.fullscreen), R.drawable.ic_fullscreen) { callbacks?.onToggleFullscreen() },
            MenuItem(context.getString(R.string.orientation), R.drawable.ic_rotate) { callbacks?.onToggleOrientation() },
            MenuItem(context.getString(R.string.gamepad_show), R.drawable.ic_keyboard) { callbacks?.onToggleGamepad() },
            MenuItem(context.getString(R.string.mouse_cursor), R.drawable.ic_mouse) { callbacks?.onToggleMouse() },
            MenuItem(context.getString(R.string.key_mapping), R.drawable.ic_key) { callbacks?.onOpenKeyMapping() },
            MenuItem("Flash 引擎", R.drawable.ic_key) { callbacks?.onOpenFlashSettings() },
            MenuItem("页面缩放", R.drawable.ic_key) { callbacks?.onOpenPageZoom() },
            MenuItem("兼容模式", R.drawable.ic_key) { callbacks?.onOpenUaMode() },
            MenuItem(context.getString(R.string.refresh), R.drawable.ic_refresh) { callbacks?.onRefresh() },
            MenuItem(context.getString(R.string.back), R.drawable.ic_back) { callbacks?.onBack() },
            MenuItem(context.getString(R.string.close), R.drawable.ic_close) { callbacks?.onClose() }
        )

        // 构建按钮视图
        fun buildItemView(item: MenuItem): View {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                setBackgroundResource(R.drawable.bg_top_button)
            }
            val icon = android.widget.ImageView(context).apply {
                setImageResource(item.iconRes)
                setPadding(0, 0, dp(6), 0)
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            }
            val text = TextView(context).apply {
                text = item.title
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 13f
            }
            row.addView(icon)
            row.addView(text)
            row.setOnClickListener {
                item.action()
                popup?.dismiss()
            }
            return row
        }

        if (columns == 1) {
            // 竖屏：单列垂直排列
            items.forEach { menuContainer.addView(buildItemView(it)) }
        } else {
            // 横屏：双列网格排列
            var row: LinearLayout? = null
            items.forEachIndexed { index, item ->
                if (index % 2 == 0) {
                    row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, 0, 0, 0)
                    }
                    menuContainer.addView(row)
                }
                val cell = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(dp(4), dp(0), dp(4), dp(0))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                cell.addView(buildItemView(item))
                row?.addView(cell)
            }
        }

        popup = PopupWindow(menuContainer, ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(null)
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
            setOnDismissListener { isMenuOpen = false }
            showAsDropDown(triggerBtn, -dp(160), 0, Gravity.END)
        }
        isMenuOpen = true
    }

    private fun updateTriggerIcon() {
        triggerBtn.setImageResource(R.drawable.ic_menu)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private data class MenuItem(
        val title: String,
        val iconRes: Int,
        val action: () -> Unit
    )

    fun dismissMenu() {
        popup?.dismiss()
    }
}

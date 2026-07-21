package com.game4399.app.input

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.game4399.app.data.PrefsManager
import com.game4399.app.webview.GameWebView
import kotlin.math.min

/**
 * 动作按钮组：支持 2/4/6 个可配置按键。
 *
 * - 按键映射来自 [PrefsManager.gamepadKeys]（默认 J/K/L/U/I/O）
 * - 按键大小来自 [PrefsManager.gamepadScale]
 * - 支持多指同时按下不同按钮
 * - 按下注入 keydown，松开注入 keyup
 *
 * 布局：
 *  - 2 按键：左下 A、右上 B
 *  - 4 按键：菱形排列
 *  - 6 按键：两列三行
 */
class ActionButtonView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var targetWebView: GameWebView? = null

    var overlayAlpha: Int = 153
        set(value) { field = value.coerceIn(40, 255); invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonColors = intArrayOf(
        Color.argb(255, 0xE5, 0x39, 0x35),  // 红
        Color.argb(255, 0x1E, 0x88, 0xE5),  // 蓝
        Color.argb(255, 0x43, 0xA0, 0x47),  // 绿
        Color.argb(255, 0xFF, 0xC1, 0x07),  // 黄
        Color.argb(255, 0x8E, 0x24, 0xAA),  // 紫
        Color.argb(255, 0xFF, 0x57, 0x22)   // 橙
    )
    private val pressedColor = Color.argb(255, 0xFF, 0xFF, 0xFF)

    /** 每个按钮的按下状态：index → 是否按下 */
    private val pressedState = BooleanArray(6) { false }
    /** 每个指针 ID → 按下的按钮 index（-1 表示未按下） */
    private val pointerButton = HashMap<Int, Int>()

    /** 按钮标签（A/B/C/D/E/F 或实际映射的按键名） */
    private val buttonLabels = arrayOf("A", "B", "C", "D", "E", "F")

    private fun keyCodes(): List<Int> {
        return PrefsManager.gamepadKeys.map { KeyMapper.toKeyCode(it) }
    }

    /** 计算每个按钮的圆心和半径 */
    private fun getButtonPositions(): List<Triple<Float, Float, Float>> {
        val w = width.toFloat()
        val h = height.toFloat()
        val scale = PrefsManager.gamepadScale
        val baseR = min(w, h) * 0.18f * scale
        // 统计可见按键数量
        val visible = PrefsManager.gamepadKeyVisible
        val count = visible.count { it }.coerceIn(2, 6)
        val positions = mutableListOf<Triple<Float, Float, Float>>()

        when (count) {
            2 -> {
                positions.add(Triple(w * 0.30f, h * 0.65f, baseR * 1.2f))
                positions.add(Triple(w * 0.70f, h * 0.35f, baseR * 1.2f))
            }
            4 -> {
                positions.add(Triple(w * 0.50f, h * 0.25f, baseR))
                positions.add(Triple(w * 0.25f, h * 0.55f, baseR))
                positions.add(Triple(w * 0.75f, h * 0.55f, baseR))
                positions.add(Triple(w * 0.50f, h * 0.80f, baseR))
            }
            else -> { // 6
                positions.add(Triple(w * 0.25f, h * 0.25f, baseR))
                positions.add(Triple(w * 0.75f, h * 0.25f, baseR))
                positions.add(Triple(w * 0.25f, h * 0.55f, baseR))
                positions.add(Triple(w * 0.75f, h * 0.55f, baseR))
                positions.add(Triple(w * 0.25f, h * 0.85f, baseR))
                positions.add(Triple(w * 0.75f, h * 0.85f, baseR))
            }
        }
        return positions
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 应用位置偏移
        canvas.save()
        canvas.translate(PrefsManager.actionOffsetX.toFloat(), PrefsManager.actionOffsetY.toFloat())

        val positions = getButtonPositions()
        val keys = keyCodes()
        val visible = PrefsManager.gamepadKeyVisible

        for (i in 0 until 6) {
            if (i >= positions.size || i >= keys.size || !visible.getOrElse(i) { true }) continue
            val (cx, cy, r) = positions[i]
            val baseColor = buttonColors[i % buttonColors.size]
            paint.style = Paint.Style.FILL
            paint.color = if (pressedState[i]) {
                Color.argb(overlayAlpha, Color.red(pressedColor), Color.green(pressedColor), Color.blue(pressedColor))
            } else {
                Color.argb(overlayAlpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
            }
            canvas.drawCircle(cx, cy, r, paint)

            // 按钮标签
            paint.color = Color.WHITE
            paint.textSize = r * 0.8f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(buttonLabels[i], cx, cy + r * 0.28f, paint)
        }

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 应用位置偏移到触点
        val offsetX = PrefsManager.actionOffsetX.toFloat()
        val offsetY = PrefsManager.actionOffsetY.toFloat()
        val positions = getButtonPositions()
        val keys = keyCodes()
        val visible = PrefsManager.gamepadKeyVisible

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val x = event.getX(idx) - offsetX
                val y = event.getY(idx) - offsetY
                val pid = event.getPointerId(idx)
                val btn = hitButton(x, y, positions, visible)
                if (btn >= 0 && btn < keys.size && visible.getOrElse(btn) { true }) {
                    pressedState[btn] = true
                    pointerButton[pid] = btn
                    targetWebView?.injectKeyDown(keys[btn])
                    performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    val x = event.getX(i) - offsetX
                    val y = event.getY(i) - offsetY
                    val btn = pointerButton[pid]
                    if (btn != null && btn >= 0) {
                        val currentBtn = hitButton(x, y, positions, visible)
                        if (currentBtn != btn) {
                            pressedState[btn] = false
                            pointerButton.remove(pid)
                            targetWebView?.injectKeyUp(keys[btn])
                        }
                    }
                }
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pointerButton.values.forEach { btn ->
                    if (btn >= 0 && btn < keys.size) {
                        pressedState[btn] = false
                        targetWebView?.injectKeyUp(keys[btn])
                    }
                }
                pointerButton.clear()
                pressedState.fill(false)
                invalidate()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pid = event.getPointerId(event.actionIndex)
                val btn = pointerButton.remove(pid)
                if (btn != null && btn >= 0 && btn < keys.size) {
                    pressedState[btn] = false
                    targetWebView?.injectKeyUp(keys[btn])
                }
                invalidate()
            }
        }
        return true
    }

    private fun hitButton(x: Float, y: Float, positions: List<Triple<Float, Float, Float>>, visible: List<Boolean>): Int {
        for (i in 0 until minOf(positions.size, 6)) {
            if (!visible.getOrElse(i) { true }) continue
            val (cx, cy, r) = positions[i]
            val dist = Math.hypot((x - cx).toDouble(), (y - cy).toDouble())
            if (dist < r) return i
        }
        return -1
    }
}

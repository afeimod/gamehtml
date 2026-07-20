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
 * 动作按钮组（A / B 双按钮）。
 *
 * - A 按钮映射到设置中的 [PrefsManager.gamepadAKey]（默认空格）
 * - B 按钮映射到 [PrefsManager.gamepadBKey]（默认回车）
 * - 支持多指同时按 A、B
 * - 按下注入 keydown，松开注入 keyup（与游戏键盘监听一致）
 *
 * 布局：A 在左上偏内，B 在右下偏外（经典手柄排布）。
 */
class ActionButtonView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var targetWebView: GameWebView? = null

    var overlayAlpha: Int = 153
        set(value) { field = value.coerceIn(40, 255); invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var _pressedA = false
    private var _pressedB = false

    /** 当前 A/B 按下的指针 ID（-1 表示未按下） */
    private var pointerA = -1
    private var pointerB = -1

    private fun aKeyCode() = KeyMapper.toKeyCode(PrefsManager.gamepadAKey)
    private fun bKeyCode() = KeyMapper.toKeyCode(PrefsManager.gamepadBKey)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val rA = min(w, h) * 0.32f
        val rB = min(w, h) * 0.32f
        // A 圆心（左下）
        val ax = w * 0.32f
        val ay = h * 0.68f
        // B 圆心（右上）
        val bx = w * 0.72f
        val by = h * 0.32f

        // A
        paint.style = Paint.Style.FILL
        paint.color = if (_pressedA) Color.argb(overlayAlpha, 0xFF, 0xC1, 0x07)
                      else Color.argb(overlayAlpha, 0xE5, 0x39, 0x35)
        canvas.drawCircle(ax, ay, rA, paint)
        paint.color = Color.WHITE
        paint.textSize = rA * 0.9f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("A", ax, ay + rA * 0.33f, paint)

        // B
        paint.color = if (_pressedB) Color.argb(overlayAlpha, 0xFF, 0xC1, 0x07)
                      else Color.argb(overlayAlpha, 0x1E, 0x88, 0xE5)
        canvas.drawCircle(bx, by, rB, paint)
        paint.color = Color.WHITE
        canvas.drawText("B", bx, by + rB * 0.33f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val x = event.getX(idx)
                val y = event.getY(idx)
                val pid = event.getPointerId(idx)
                when (hitButton(x, y)) {
                    Button.A -> { _pressedA = true; pointerA = pid; inject(aKeyCode(), down = true) }
                    Button.B -> { _pressedB = true; pointerB = pid; inject(bKeyCode(), down = true) }
                    Button.NONE -> {}
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                // 处理手指滑出按钮区域 → 释放
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    val x = event.getX(i)
                    val y = event.getY(i)
                    if (pid == pointerA && hitButton(x, y) != Button.A) {
                        _pressedA = false; pointerA = -1; inject(aKeyCode(), down = false)
                    }
                    if (pid == pointerB && hitButton(x, y) != Button.B) {
                        _pressedB = false; pointerB = -1; inject(bKeyCode(), down = false)
                    }
                }
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (_pressedA) inject(aKeyCode(), down = false)
                if (_pressedB) inject(bKeyCode(), down = false)
                _pressedA = false; _pressedB = false
                pointerA = -1; pointerB = -1
                invalidate()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pid = event.getPointerId(event.actionIndex)
                if (pid == pointerA) { _pressedA = false; pointerA = -1; inject(aKeyCode(), down = false) }
                if (pid == pointerB) { _pressedB = false; pointerB = -1; inject(bKeyCode(), down = false) }
                invalidate()
            }
        }
        return true
    }

    private enum class Button { A, B, NONE }

    private fun hitButton(x: Float, y: Float): Button {
        val w = width.toFloat(); val h = height.toFloat()
        val r = min(w, h) * 0.32f
        val ax = w * 0.32f; val ay = h * 0.68f
        val bx = w * 0.72f; val by = h * 0.32f
        val dA = Math.hypot((x - ax).toDouble(), (y - ay).toDouble())
        val dB = Math.hypot((x - bx).toDouble(), (y - by).toDouble())
        return when {
            dA < r && dA <= dB -> Button.A
            dB < r -> Button.B
            else -> Button.NONE
        }
    }

    private fun inject(keyCode: Int, down: Boolean) {
        if (down) targetWebView?.injectKeyDown(keyCode)
        else targetWebView?.injectKeyUp(keyCode)
        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
    }
}

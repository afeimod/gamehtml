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
import kotlin.math.abs

/**
 * 鼠标控制覆盖层：
 * - 左半屏：触摸区，拖动模拟鼠标移动（旋转视角），点击模拟左键单击
 * - 右下角：左键按钮（持续按住/松开）
 * - 右下角上方：右键按钮
 *
 * 默认隐藏，通过悬浮菜单的"鼠标模式"开关显示。
 */
class MouseControlView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var targetWebView: GameWebView? = null
    var overlayAlpha: Int = 100

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val leftBtnColor = Color.argb(overlayAlpha, 0x1E, 0x88, 0xE5)
    private val rightBtnColor = Color.argb(overlayAlpha, 0xE5, 0x39, 0x35)

    /** 左键按下状态 */
    private var leftBtnPressed = false
    /** 右键按下状态 */
    private var rightBtnPressed = false
    /** 触摸区按下时的起始坐标 */
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchLastX = 0f
    private var touchLastY = 0f
    /** 是否在拖动（旋转视角） */
    private var isDragging = false
    /** 按下的指针 ID → 区域（0=触摸区, 1=左键, 2=右键） */
    private val pointerZone = HashMap<Int, Int>()

    /** 按钮半径 */
    private val btnRadius: Float
        get() = (minOf(width, height) * 0.08f * PrefsManager.gamepadScale).coerceAtLeast(40f)

    /** 左键按钮中心 */
    private val leftBtnCx: Float get() = width - btnRadius * 2.5f
    private val leftBtnCy: Float get() = height - btnRadius * 1.5f

    /** 右键按钮中心 */
    private val rightBtnCx: Float get() = width - btnRadius * 2.5f
    private val rightBtnCy: Float get() = height - btnRadius * 3.5f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 左半屏触摸区半透明背景
        paint.color = Color.argb((overlayAlpha * 0.2f).toInt(), 0, 0, 0)
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, width / 2f, height.toFloat(), paint)

        // 触摸区提示文字
        paint.color = Color.argb(120, 255, 255, 255)
        paint.textSize = 36f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("触摸旋转视角", width / 4f, height / 2f, paint)
        canvas.drawText("点击=左键单击", width / 4f, height / 2f + 50f, paint)

        // 左键按钮
        paint.style = Paint.Style.FILL
        paint.color = if (leftBtnPressed) Color.argb(overlayAlpha, 0x64, 0xB5, 0xF6) else leftBtnColor
        canvas.drawCircle(leftBtnCx, leftBtnCy, btnRadius, paint)
        paint.color = Color.WHITE
        paint.textSize = btnRadius * 0.5f
        canvas.drawText("左键", leftBtnCx, leftBtnCy + btnRadius * 0.17f, paint)

        // 右键按钮
        paint.color = if (rightBtnPressed) Color.argb(overlayAlpha, 0xEF, 0x9A, 0x9A) else rightBtnColor
        canvas.drawCircle(rightBtnCx, rightBtnCy, btnRadius, paint)
        paint.color = Color.WHITE
        canvas.drawText("右键", rightBtnCx, rightBtnCy + btnRadius * 0.17f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val x = event.getX(idx)
                val y = event.getY(idx)
                val pid = event.getPointerId(idx)
                val zone = hitZone(x, y)
                pointerZone[pid] = zone
                when (zone) {
                    0 -> {
                        // 触摸区：记录起始坐标
                        touchStartX = x
                        touchStartY = y
                        touchLastX = x
                        touchLastY = y
                        isDragging = false
                    }
                    1 -> {
                        // 左键按下
                        leftBtnPressed = true
                        targetWebView?.injectMouseLeftDown(x, y)
                        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                    2 -> {
                        // 右键单击
                        rightBtnPressed = true
                        targetWebView?.injectMouseRightClick(x, y)
                        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    val x = event.getX(i)
                    val y = event.getY(i)
                    val zone = pointerZone[pid]
                    if (zone == 0) {
                        // 触摸区拖动：计算移动量，注入鼠标移动（旋转视角）
                        val dx = x - touchLastX
                        val dy = y - touchLastY
                        if (!isDragging && (abs(dx) > 8 || abs(dy) > 8)) {
                            isDragging = true
                        }
                        if (isDragging) {
                            targetWebView?.injectMouseMove(dx, dy)
                            touchLastX = x
                            touchLastY = y
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                for ((pid, zone) in pointerZone) {
                    when (zone) {
                        0 -> {
                            // 触摸区：如果未拖动，视为左键单击
                            if (!isDragging) {
                                targetWebView?.injectMouseLeftClick(touchStartX, touchStartY)
                            }
                        }
                        1 -> {
                            // 左键松开
                            leftBtnPressed = false
                            targetWebView?.injectMouseLeftUp()
                        }
                        2 -> {
                            rightBtnPressed = false
                        }
                    }
                }
                pointerZone.clear()
                isDragging = false
                invalidate()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pid = event.getPointerId(event.actionIndex)
                val zone = pointerZone.remove(pid)
                when (zone) {
                    0 -> {
                        if (!isDragging) {
                            targetWebView?.injectMouseLeftClick(touchStartX, touchStartY)
                        }
                    }
                    1 -> {
                        leftBtnPressed = false
                        targetWebView?.injectMouseLeftUp()
                    }
                    2 -> {
                        rightBtnPressed = false
                    }
                }
                invalidate()
            }
        }
        return true
    }

    /** 判断坐标在哪个区域：0=触摸区, 1=左键, 2=右键 */
    private fun hitZone(x: Float, y: Float): Int {
        // 右键按钮
        val rightDist = Math.hypot((x - rightBtnCx).toDouble(), (y - rightBtnCy).toDouble())
        if (rightDist < btnRadius) return 2
        // 左键按钮
        val leftDist = Math.hypot((x - leftBtnCx).toDouble(), (y - leftBtnCy).toDouble())
        if (leftDist < btnRadius) return 1
        // 左半屏触摸区
        return if (x < width / 2f) 0 else 0
    }
}

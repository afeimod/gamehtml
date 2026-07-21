package com.game4399.app.input

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.game4399.app.data.PrefsManager
import com.game4399.app.webview.GameWebView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 虚拟方向键（十字 D-Pad）。
 *
 * 触摸行为：
 *  - 按下/移动时根据触点相对中心的位置判断方向（上/下/左/右，支持对角线）
 *  - 对新进入的方向注入 KEYCODE_DPAD_* 的 down，对离开的方向注入 up
 *  - 抬起时释放全部
 *
 * 同时支持多指：每个指针 ID 独立追踪其当前方向。
 *
 * 关键：方向键事件通过 [GameWebView.injectKeyDown] / [injectKeyUp] 注入，
 *       因此对只监听 keydown/keyup 的 Flash/H5 键盘游戏同样生效。
 */
class DPadView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    /** 绑定的目标 WebView，方向键事件将注入到这里 */
    var targetWebView: GameWebView? = null

    /** 透明度 0~255 */
    var overlayAlpha: Int = 153
        set(value) { field = value.coerceIn(40, 255); invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pressed = HashSet<Int>()              // 当前按下的方向 KeyCode
    private val pointerDir = HashMap<Int, Int>()      // 指针 ID → 方向 KeyCode（0 表示无）

    // 方向阈值：触点偏离中心超过半径的 25% 才算按下方向
    private val deadZoneRatio = 0.25f

    private val dirKeys = intArrayOf(
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 应用位置偏移
        canvas.save()
        canvas.translate(PrefsManager.dpadOffsetX.toFloat(), PrefsManager.dpadOffsetY.toFloat())
        // 应用大小缩放
        val scale = PrefsManager.dpadScale
        canvas.scale(scale, scale, width / 2f, height / 2f)

        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 8f

        // 背景圆
        paint.color = Color.argb((overlayAlpha * 0.35f).toInt(), 0, 0, 0)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, r, paint)

        // 十字臂
        val armW = r * 0.42f
        val armL = r * 0.95f

        paint.color = Color.argb(overlayAlpha, 255, 255, 255)
        // 上
        drawArm(canvas, cx, cy, 0, armW, armL, KeyEvent.KEYCODE_DPAD_UP)
        // 下
        drawArm(canvas, cx, cy, 1, armW, armL, KeyEvent.KEYCODE_DPAD_DOWN)
        // 左
        drawArm(canvas, cx, cy, 2, armW, armL, KeyEvent.KEYCODE_DPAD_LEFT)
        // 右
        drawArm(canvas, cx, cy, 3, armW, armL, KeyEvent.KEYCODE_DPAD_RIGHT)

        // 中心圆
        paint.color = Color.argb(overlayAlpha, 200, 200, 200)
        canvas.drawCircle(cx, cy, armW * 0.6f, paint)

        canvas.restore()
    }

    private fun drawArm(
        canvas: Canvas, cx: Float, cy: Float, dir: Int, armW: Float, armL: Float, keyCode: Int
    ) {
        val color = if (pressed.contains(keyCode)) {
            Color.argb(overlayAlpha, 0xFF, 0x57, 0x22) // 按下高亮橙
        } else {
            Color.argb(overlayAlpha, 0xFF, 0xFF, 0xFF)
        }
        paint.color = color
        when (dir) {
            0 -> canvas.drawRoundRect(cx - armW, cy - armL, cx + armW, cy, armW * 0.5f, armW * 0.5f, paint)
            1 -> canvas.drawRoundRect(cx - armW, cy, cx + armW, cy + armL, armW * 0.5f, armW * 0.5f, paint)
            2 -> canvas.drawRoundRect(cx - armL, cy - armW, cx, cy + armW, armW * 0.5f, armW * 0.5f, paint)
            3 -> canvas.drawRoundRect(cx, cy - armW, cx + armL, cy + armW, armW * 0.5f, armW * 0.5f, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_MOVE -> {
                // 重新计算所有活跃指针的方向
                val active = HashSet<Int>()
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    val x = event.getX(i)
                    val y = event.getY(i)
                    val dirs = computeDirections(x, y)
                    // 记录该指针按下的方向
                    pointerDir[pid]?.let { old -> if (old != 0 && old !in dirs) active.add(old) }
                    dirs.forEach { d -> active.add(d) }
                }
                // 注入差异：新按下的 down，松开的 up
                val toDown = active - pressed
                val toUp = pressed - active
                toDown.forEach { injectDown(it) }
                toUp.forEach { injectUp(it) }
                pressed.clear()
                pressed.addAll(active)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_POINTER_UP -> {
                val pid = event.getPointerId(event.actionIndex)
                pointerDir.remove(pid)
                if (event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    pressed.forEach { injectUp(it) }
                    pressed.clear()
                    pointerDir.clear()
                } else {
                    // 重新计算剩余指针
                    val active = HashSet<Int>()
                    for (i in 0 until event.pointerCount) {
                        if (i == event.actionIndex) continue
                        val p = event.getPointerId(i)
                        computeDirections(event.getX(i), event.getY(i)).forEach { d -> active.add(d) }
                    }
                    val toUp = pressed - active
                    toUp.forEach { injectUp(it) }
                    pressed.clear()
                    pressed.addAll(active)
                }
                invalidate()
            }
        }
        return true
    }

    /** 根据触点位置返回当前按下的方向集合 */
    private fun computeDirections(x: Float, y: Float): Set<Int> {
        val cx = width / 2f
        val cy = height / 2f
        val r = max(min(width, height) / 2f, 1f)
        val dx = (x - cx) / r
        val dy = (y - cy) / r
        val set = HashSet<Int>()
        if (abs(dx) < deadZoneRatio && abs(dy) < deadZoneRatio) return set // 中心死区
        if (abs(dx) > abs(dy)) {
            set.add(if (dx > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
        } else {
            set.add(if (dy > 0) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP)
        }
        return set
    }

    private fun injectDown(keyCode: Int) {
        // 根据 dpadMode 设置映射方向键到 DPAD_* 或 WASD
        val mappedKey = mapDirectionKey(keyCode)
        targetWebView?.injectKeyDown(mappedKey)
        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
    }

    private fun injectUp(keyCode: Int) {
        val mappedKey = mapDirectionKey(keyCode)
        targetWebView?.injectKeyUp(mappedKey)
    }

    /** 根据 dpadMode 将方向键映射为 DPAD_* 或 WASD */
    private fun mapDirectionKey(keyCode: Int): Int {
        if (PrefsManager.dpadMode != "wasd") return keyCode
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> KeyEvent.KEYCODE_W
            KeyEvent.KEYCODE_DPAD_DOWN -> KeyEvent.KEYCODE_S
            KeyEvent.KEYCODE_DPAD_LEFT -> KeyEvent.KEYCODE_A
            KeyEvent.KEYCODE_DPAD_RIGHT -> KeyEvent.KEYCODE_D
            else -> keyCode
        }
    }
}

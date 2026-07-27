package com.flashbox.app.virtualkey

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * 4-way directional pad. Reports pressed directions for key-mapping.
 */
class DPadView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3A3A4A")
        style = Paint.Style.FILL
    }
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6C5CE7")
        style = Paint.Style.FILL
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        style = Paint.Style.FILL
    }

    private var up = false
    private var down = false
    private var left = false
    private var right = false

    var onDirection: ((dir: JoystickView.Direction) -> Unit)? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val arm = minOf(w, h) / 3f
        // cross arms
        drawArm(canvas, cx, cy, arm, 0f, -1f, up)
        drawArm(canvas, cx, cy, arm, 0f, 1f, down)
        drawArm(canvas, cx, cy, arm, -1f, 0f, left)
        drawArm(canvas, cx, cy, arm, 1f, 0f, right)
        // center knob
        canvas.drawCircle(cx, cy, arm * 0.45f, basePaint)
    }

    private fun drawArm(canvas: Canvas, cx: Float, cy: Float, arm: Float, dx: Float, dy: Float, active: Boolean) {
        val paint = if (active) activePaint else basePaint
        val path = Path()
        path.moveTo(cx, cy)
        if (dx == 0f) {
            val half = arm * 0.6f
            path.lineTo(cx - half, cy + dy * arm)
            path.lineTo(cx + half, cy + dy * arm)
        } else {
            val half = arm * 0.6f
            path.lineTo(cx + dx * arm, cy - half)
            path.lineTo(cx + dx * arm, cy + half)
        }
        path.close()
        canvas.drawPath(path, paint)
        // arrow
        val ax = cx + dx * arm * 0.55f
        val ay = cy + dy * arm * 0.55f
        val s = arm * 0.18f
        val arrow = Path()
        when {
            dx < 0f -> { arrow.moveTo(ax - s, ay); arrow.lineTo(ax + s, ay - s); arrow.lineTo(ax + s, ay + s) }
            dx > 0f -> { arrow.moveTo(ax + s, ay); arrow.lineTo(ax - s, ay - s); arrow.lineTo(ax - s, ay + s) }
            dy < 0f -> { arrow.moveTo(ax, ay - s); arrow.lineTo(ax - s, ay + s); arrow.lineTo(ax + s, ay + s) }
            dy > 0f -> { arrow.moveTo(ax, ay + s); arrow.lineTo(ax - s, ay - s); arrow.lineTo(ax + s, ay - s) }
        }
        arrow.close()
        canvas.drawPath(arrow, arrowPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val dx = event.x - width / 2f
                val dy = event.y - height / 2f
                val dead = minOf(width, height) * 0.12f
                up = dy < -dead && kotlin.math.abs(dy) >= kotlin.math.abs(dx)
                down = dy > dead && kotlin.math.abs(dy) >= kotlin.math.abs(dx)
                left = dx < -dead && kotlin.math.abs(dx) > kotlin.math.abs(dy)
                right = dx > dead && kotlin.math.abs(dx) > kotlin.math.abs(dy)
                emit()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                up = false; down = false; left = false; right = false
                emit()
            }
        }
        return true
    }

    private fun emit() {
        val dir = when {
            up && left -> JoystickView.Direction.UP_LEFT
            up && right -> JoystickView.Direction.UP_RIGHT
            down && left -> JoystickView.Direction.DOWN_LEFT
            down && right -> JoystickView.Direction.DOWN_RIGHT
            up -> JoystickView.Direction.UP
            down -> JoystickView.Direction.DOWN
            left -> JoystickView.Direction.LEFT
            right -> JoystickView.Direction.RIGHT
            else -> JoystickView.Direction.NONE
        }
        onDirection?.invoke(dir)
        invalidate()
    }
}

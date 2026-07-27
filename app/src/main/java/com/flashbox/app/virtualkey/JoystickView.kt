package com.flashbox.app.virtualkey

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.flashbox.app.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Analog joystick. Reports a normalized direction vector via [onDirection].
 * Eight-way snapped output is provided for key-mapping (up/down/left/right + diagonals).
 */
class JoystickView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3A3A4A")
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6C5CE7")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val stickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6C5CE7")
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A0A0B5")
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private var stickX = 0f
    private var stickY = 0f
    private var maxRadius = 0f
    private var activePointerId = -1

    var onDirection: ((dx: Float, dy: Float, dir: Direction) -> Unit)? = null

    enum class Direction(val up: Boolean, val down: Boolean, val left: Boolean, val right: Boolean) {
        NONE(false, false, false, false),
        UP(true, false, false, false),
        DOWN(false, true, false, false),
        LEFT(false, false, true, false),
        RIGHT(false, false, false, true),
        UP_LEFT(true, false, true, false),
        UP_RIGHT(true, false, false, true),
        DOWN_LEFT(false, true, true, false),
        DOWN_RIGHT(false, true, false, true);

        companion object {
            fun fromVector(dx: Float, dy: Float, threshold: Float = 0.35f): Direction {
                if (hypot(dx, dy) < threshold) return NONE
                val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
                return when (angle) {
                    in -22.5..22.5 -> RIGHT
                    in 22.5..67.5 -> DOWN_RIGHT
                    in 67.5..112.5 -> DOWN
                    in 112.5..157.5 -> DOWN_LEFT
                    in 157.5..180.0, in -180.0..-157.5 -> LEFT
                    in -157.5..-112.5 -> UP_LEFT
                    in -112.5..-67.5 -> UP
                    in -67.5..-22.5 -> UP_RIGHT
                    else -> NONE
                }
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        stickX = w / 2f
        stickY = h / 2f
        maxRadius = (min(w, h) / 2f) * 0.7f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f
        canvas.drawCircle(cx, cy, r - 4f, basePaint)
        canvas.drawCircle(cx, cy, r - 6f, ringPaint)
        canvas.drawCircle(stickX, stickY, r * 0.4f, stickPaint)
        canvas.drawText("⊙", cx, cy + 10f, labelPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                activePointerId = event.getPointerId(event.actionIndex)
                updateStick(event)
            }
            MotionEvent.ACTION_MOVE -> updateStick(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_POINTER_UP -> {
                stickX = width / 2f
                stickY = height / 2f
                activePointerId = -1
                onDirection?.invoke(0f, 0f, Direction.NONE)
                invalidate()
            }
        }
        return true
    }

    private fun updateStick(event: MotionEvent) {
        val idx = if (activePointerId >= 0) event.findPointerIndex(activePointerId) else 0
        if (idx < 0) return
        var dx = event.getX(idx) - width / 2f
        var dy = event.getY(idx) - height / 2f
        val dist = hypot(dx, dy)
        if (dist > maxRadius) {
            val scale = maxRadius / dist
            dx *= scale
            dy *= scale
        }
        stickX = width / 2f + dx
        stickY = height / 2f + dy
        val ndx = if (maxRadius > 0) dx / maxRadius else 0f
        val ndy = if (maxRadius > 0) dy / maxRadius else 0f
        onDirection?.invoke(ndx, ndy, Direction.fromVector(ndx, ndy))
        invalidate()
    }
}

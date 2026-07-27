package com.flashbox.app.virtualkey

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import com.flashbox.app.R
import kotlin.math.roundToInt

/**
 * An independent on-screen key button. Supports:
 *  - press to emit key down/up
 *  - long-press drag to move position
 *  - two-finger pinch to scale size
 */
class KeyButtonView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {

    var keyDef: KeyDef? = null
    var editMode: Boolean = false
    var onPress: ((KeyDef, Boolean) -> Unit)? = null
    var onMoved: ((KeyDef, xPct: Float, yPct: Float) -> Unit)? = null
    var onScaled: ((KeyDef, sizeDp: Int) -> Unit)? = null

    private var dStartX = 0f
    private var dStartY = 0f
    private var viewStartX = 0
    private var viewStartY = 0
    private var dragging = false

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (!editMode) return false
            val scale = detector.scaleFactor
            val newW = (width * scale).coerceIn(dp(40), dp(180)).roundToInt()
            val newH = (height * scale).coerceIn(dp(40), dp(180)).roundToInt()
            layoutParams?.let {
                it.width = newW
                it.height = newH
                layoutParams = it
            }
            keyDef?.let { def ->
                val newDef = def.copy(sizeDp = pxToDp(newW))
                keyDef = newDef
                onScaled?.invoke(newDef, newDef.sizeDp)
            }
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) {
            if (editMode) {
                dragging = true
                dStartX = e.rawX
                dStartY = e.rawY
                viewStartX = x.toInt()
                viewStartY = y.toInt()
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            }
        }
    })

    init {
        setBackgroundResource(R.drawable.bg_key_button)
        gravity = android.view.Gravity.CENTER
        setTextColor(context.resources.getColor(R.color.text_primary, null))
        textSize = 14f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (dragging && editMode) {
                    val dx = event.rawX - dStartX
                    val dy = event.rawY - dStartY
                    x = (viewStartX + dx).coerceIn(0f, (parent as View).width - width.toFloat())
                    y = (viewStartY + dy).coerceIn(0f, (parent as View).height - height.toFloat())
                }
            }
            MotionEvent.ACTION_DOWN -> {
                if (!editMode) {
                    keyDef?.let { onPress?.invoke(it, true) }
                    isPressed = true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    reportMoved()
                } else if (!editMode) {
                    keyDef?.let { onPress?.invoke(it, false) }
                    isPressed = false
                }
            }
        }
        return true
    }

    private fun reportMoved() {
        val parent = parent as? View ?: return
        val xPct = if (parent.width > 0) x / parent.width else -1f
        val yPct = if (parent.height > 0) y / parent.height else -1f
        keyDef?.let { def ->
            val newDef = def.copy(xPct = xPct, yPct = yPct)
            keyDef = newDef
            onMoved?.invoke(newDef, xPct, yPct)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun pxToDp(px: Int): Int = (px / resources.displayMetrics.density).roundToInt()
}

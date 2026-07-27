package com.game4399.app.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import com.game4399.app.R
import com.game4399.app.data.PadKey
import com.game4399.app.data.PadLayout
import com.game4399.app.data.Prefs
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

interface OnPadActionListener {
    /** 当按键被按下 / 抬起时，code 是 KeyboardEvent.code 形式 */
    fun onPadKey(code: String, pressed: Boolean)
}

/**
 * 虚拟按键 widget
 *
 * 内部含 2 层 View：
 *  - [dpad]  摇杆 / 方向键，绘制在 viewGroup 上
 *  - key views: 独立按键，每个都是单独的 view，可独立缩放/移动
 *
 * 点击右上角齿轮打开"按键编辑模式"：
 *  - 摇杆/方向键可以拖动、缩放、切换摇杆 vs 方向键、切换 wsad/箭头
 *  - 独立按键长按拖动、双手指缩放、点击删除/编辑
 *  - 添加按键：弹一个软键盘的全键模型供点选
 */
class VirtualPad @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var listener: OnPadActionListener? = null
    private var layout: PadLayout = Prefs.padLayout().also { ensureDefaults(it) }

    private val dpad: PadMoveView
    private val keysHolder: FrameLayout
    private val editBar: View
    private val btnAdd: View
    private val btnClose: View
    private val btnType: View
    private val btnMode: View
    private val btnReset: View
    private val btnEdit: View

    private val keyViews = mutableListOf<KeyView>()

    private var editing = false

    init {
        // 同步 Prefs 的 padType / padKeyMode
        layout.movePad = Prefs.padType()
        layout.keyMode = Prefs.padKeyMode()
        inflate(context, R.layout.view_virtual_pad, this)
        dpad = findViewById(R.id.padMove)
        keysHolder = findViewById(R.id.padKeysHolder)
        editBar = findViewById(R.id.editBar)
        btnAdd = findViewById(R.id.btnAddKey)
        btnClose = findViewById(R.id.btnCloseEdit)
        btnType = findViewById(R.id.btnToggleType)
        btnMode = findViewById(R.id.btnToggleMode)
        btnReset = findViewById(R.id.btnReset)
        btnEdit = findViewById(R.id.btnToggleEdit)

        // 让空白区域 pass-through 到下面的 WebView
        // 仅 padMove / keyView / btn* / editBar 接收事件

        dpad.bind(layout)
        dpad.setOnKeyListener { code, pressed -> dispatch(code, pressed) }

        rebuildKeys()
        applyEdit(false)

        btnAdd.setOnClickListener { showKeyPicker(null) }
        btnClose.setOnClickListener { applyEdit(false) }
        btnType.setOnClickListener { toggleType() }
        btnMode.setOnClickListener { toggleMode() }
        btnReset.setOnClickListener { resetLayout() }
        btnEdit.setOnClickListener { applyEdit(!editing) }
    }

    val isOpen: Boolean get() = visibility == VISIBLE

    fun setListener(l: OnPadActionListener) { listener = l }

    fun dispatchKey(code: String, pressed: Boolean) {
        listener?.onPadKey(code, pressed)
    }

    fun open() {
        visibility = VISIBLE
        dpad.visibility = if (layout.moveShow) VISIBLE else GONE
    }

    fun close() {
        visibility = GONE
        dpad.releaseAll()
    }

    fun toggle() = if (isOpen) close() else open()

    private fun dispatch(code: String, pressed: Boolean) {
        listener?.onPadKey(code, pressed)
    }

    private fun applyEdit(e: Boolean) {
        editing = e
        editBar.visibility = if (e) VISIBLE else GONE
        btnEdit.alpha = if (e) 1f else 0.8f
        dpad.editing = e
        keyViews.forEach { it.editing = e }
    }

    private fun toggleType() {
        layout.movePad = if (layout.movePad == "joystick") "dpad" else "joystick"
        Prefs.setPadType(layout.movePad)
        dpad.bind(layout)
    }

    private fun toggleMode() {
        layout.keyMode = if (layout.keyMode == "wsad") "arrows" else "wsad"
        Prefs.setPadKeyMode(layout.keyMode)
        dpad.bind(layout)
    }

    private fun resetLayout() {
        val newLayout = PadLayout()
        layout = newLayout
        ensureDefaults(layout)
        Prefs.setPadLayout(layout)
        Prefs.setPadType("joystick")
        Prefs.setPadKeyMode("wsad")
        dpad.bind(layout)
        rebuildKeys()
    }

    private fun rebuildKeys() {
        keysHolder.removeAllViews()
        keyViews.clear()
        for (k in layout.keys) addKeyView(k)
    }

    private fun addKeyView(k: PadKey) {
        val v = KeyView(context, k, this)
        v.editing = editing
        keysHolder.addView(v)
        keyViews.add(v)
    }

    fun removeKey(v: KeyView) {
        keysHolder.removeView(v)
        keyViews.remove(v)
        layout.keys.remove(v.key)
        Prefs.setPadLayout(layout)
    }

    fun updateKey(v: KeyView) {
        Prefs.setPadLayout(layout)
    }

    private fun showKeyPicker(existing: PadKey?) {
        val keys = ALL_KEYS
        val labels = keys.map { "${it.second} (${it.first})" }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(if (existing == null) R.string.add_button else R.string.edit)
            .setItems(labels) { _, which ->
                val (code, label) = keys[which]
                if (existing == null) {
                    val x = 0.85f
                    val y = 0.78f + (layout.keys.size % 4) * 0.06f
                    val k = PadKey(code, label, x, y.coerceAtMost(0.94f), 1.0f, 0.65f)
                    layout.keys.add(k)
                    Prefs.setPadLayout(layout)
                    addKeyView(k)
                } else {
                    val idx = layout.keys.indexOf(existing)
                    if (idx >= 0) {
                        layout.keys[idx] = existing.copy(code = code, label = label)
                        Prefs.setPadLayout(layout)
                        rebuildKeys()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun showKeyPickerFor(k: PadKey) = showKeyPicker(k)

    private fun ensureDefaults(p: PadLayout) {
        if (p.moveX < 0) p.moveX = 0.16f
        if (p.moveY < 0) p.moveY = 0.72f
    }
}

/** 摇杆 / 方向键 View */
class PadMoveView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dp = resources.displayMetrics.density
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#80000000") }
    private val stickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#CC5B5BF3") }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFEBB543")
        style = Paint.Style.STROKE
        strokeWidth = 2 * dp
    }

    private val baseRadius = 64 * dp
    private val stickRadius = 28 * dp
    private val rect = RectF()

    private var layout: PadLayout = PadLayout()
    private var stickX = 0f
    private var stickY = 0f
    private var dragLastX = 0f
    private var dragLastY = 0f
    private var scaling = false
    private var pointerId = -1
    private var listener: ((String, Boolean) -> Unit)? = null
    var editing = false
        set(v) { field = v; invalidate() }

    private val activeCodes = mutableSetOf<String>()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // 边缘更平滑
    }

    fun bind(p: PadLayout) {
        layout = p
        // 缩放
        val w = baseRadius * 2 * layout.moveScale
        val h = baseRadius * 2 * layout.moveScale
        val lp = layoutParams ?: ViewGroup.LayoutParams(w.toInt(), h.toInt())
        lp.width = w.toInt()
        lp.height = h.toInt()
        layoutParams = lp
        // 位置
        post {
            val parent = parent as? ViewGroup ?: return@post
            val pw = parent.width
            val ph = parent.height
            x = (layout.moveX * pw - w / 2).coerceAtLeast(0f).coerceAtMost((pw - w).coerceAtLeast(0f))
            y = (layout.moveY * ph - h / 2).coerceAtLeast(0f).coerceAtMost((ph - h).coerceAtLeast(0f))
        }
        releaseAll()
        invalidate()
    }

    fun setOnKeyListener(l: (String, Boolean) -> Unit) { listener = l }

    fun releaseAll() {
        if (activeCodes.isNotEmpty()) {
            activeCodes.toList().forEach { listener?.invoke(it, false) }
            activeCodes.clear()
        }
        stickX = 0f
        stickY = 0f
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val type = layout.movePad

        // 编辑模式：单指拖动，双指缩放
        if (editing) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pointerId = event.getPointerId(0)
                    dragLastX = event.rawX
                    dragLastY = event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount >= 2) {
                        val d0 = distance(event, 0, 1)
                        if (!scaling) {
                            scaling = true
                        } else {
                            val base = baseRadius * 2
                            val nd = d0
                            val newScale = (nd / base).coerceIn(0.5f, 2.0f)
                            layout.moveScale = newScale.toFloat()
                            Prefs.setPadLayout(layout)
                            bind(layout)
                        }
                    } else {
                        val idx = event.findPointerIndex(pointerId)
                        if (idx >= 0) {
                            val dx = event.rawX - dragLastX
                            val dy = event.rawY - dragLastY
                            x = (x + dx).coerceAtLeast(0f)
                            y = (y + dy).coerceAtLeast(0f)
                            saveXY()
                            dragLastX = event.rawX
                            dragLastY = event.rawY
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    pointerId = -1
                    scaling = false
                }
            }
            return true
        }

        // 正常模式
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerId = event.getPointerId(0)
                updateStick(event.x, event.y, true)
            }
            MotionEvent.ACTION_MOVE -> {
                updateStick(event.x, event.y, false)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pointerId = -1
                releaseAll()
            }
        }
        return true
    }

    private fun updateStick(localX: Float, localY: Float, fromDown: Boolean) {
        val cx = width / 2f
        val cy = height / 2f
        val dx = localX - cx
        val dy = localY - cy
        val maxR = baseRadius * layout.moveScale
        val r = hypot(dx, dy)
        if (r > maxR) {
            stickX = dx / r * maxR
            stickY = dy / r * maxR
        } else {
            stickX = dx
            stickY = dy
        }
        val nx = stickX / maxR
        val ny = stickY / maxR

        val isJoystick = layout.movePad == "joystick"
        val wsad = layout.keyMode == "wsad"

        val cur = mutableSetOf<String>()
        val dead = 0.32f

        // x 方向
        if (abs(nx) > dead) {
            if (nx < 0) cur.add(if (wsad) "KeyA" else "ArrowLeft") else cur.add(if (wsad) "KeyD" else "ArrowRight")
        }
        if (abs(ny) > dead) {
            if (ny < 0) cur.add(if (wsad) "KeyW" else "ArrowUp") else cur.add(if (wsad) "KeyS" else "ArrowDown")
        }
        // 处理方向键模式（4 向 8 段）：根据角度选择最贴近的方向
        if (layout.movePad == "dpad") {
            cur.clear()
            if (r < 8 * dp) {
                // 中心，无输入
            } else {
                val ang = Math.atan2(ny.toDouble(), nx.toDouble())
                val deg = (ang * 180 / Math.PI + 360) % 360
                if (deg >= 67.5 && deg < 112.5) {
                    cur.add(if (wsad) "KeyW" else "ArrowUp")
                } else if (deg >= 247.5 && deg < 292.5) {
                    cur.add(if (wsad) "KeyS" else "ArrowDown")
                } else if (deg >= 157.5 && deg < 202.5) {
                    cur.add(if (wsad) "KeyA" else "ArrowLeft")
                } else if (deg >= 337.5 || deg < 22.5) {
                    cur.add(if (wsad) "KeyD" else "ArrowRight")
                } else if (deg >= 22.5 && deg < 67.5) {
                    cur.add(if (wsad) "KeyW" else "ArrowUp")
                    cur.add(if (wsad) "KeyD" else "ArrowRight")
                } else if (deg >= 112.5 && deg < 157.5) {
                    cur.add(if (wsad) "KeyW" else "ArrowUp")
                    cur.add(if (wsad) "KeyA" else "ArrowLeft")
                } else if (deg >= 202.5 && deg < 247.5) {
                    cur.add(if (wsad) "KeyS" else "ArrowDown")
                    cur.add(if (wsad) "KeyA" else "ArrowLeft")
                } else if (deg >= 292.5 && deg < 337.5) {
                    cur.add(if (wsad) "KeyS" else "ArrowDown")
                    cur.add(if (wsad) "KeyD" else "ArrowRight")
                }
            }
        }

        // diff
        val toRemove = activeCodes - cur
        val toAdd = cur - activeCodes
        toRemove.forEach { listener?.invoke(it, false) }
        toAdd.forEach { listener?.invoke(it, true) }
        activeCodes.clear()
        activeCodes.addAll(cur)
        invalidate()
    }

    private fun distance(e: MotionEvent, i: Int, j: Int): Float {
        val dx = e.getX(i) - e.getX(j)
        val dy = e.getY(i) - e.getY(j)
        return hypot(dx, dy)
    }

    private fun saveXY() {
        val p = parent as? ViewGroup ?: return
        if (p.width == 0) return
        layout.moveX = ((x + width / 2) / p.width).coerceIn(0f, 1f)
        layout.moveY = ((y + height / 2) / p.height).coerceIn(0f, 1f)
        Prefs.setPadLayout(layout)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = baseRadius * layout.moveScale
        basePaint.alpha = (layout.moveOpacity * 255).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, r, basePaint)
        if (editing) {
            canvas.drawCircle(cx, cy, r, borderPaint)
        }
        if (layout.movePad == "joystick") {
            val sx = cx + stickX
            val sy = cy + stickY
            val sr = stickRadius * layout.moveScale * layout.moveStickScale * 2.2f
            canvas.drawCircle(sx, sy, sr, stickPaint)
        } else {
            // 方向键：画中心圆 + 4 个箭头
            val innerR = r * 0.32f
            canvas.drawCircle(cx, cy, innerR, stickPaint)
            drawArrow(canvas, cx, cy - r * 0.65f, 0f)   // up
            drawArrow(canvas, cx, cy + r * 0.65f, 180f) // down
            drawArrow(canvas, cx - r * 0.65f, cy, 90f)  // left
            drawArrow(canvas, cx + r * 0.65f, cy, -90f) // right
        }
    }

    private fun drawArrow(canvas: Canvas, cx: Float, cy: Float, rot: Float) {
        val sz = 16 * dp
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(rot)
        val path = android.graphics.Path()
        path.moveTo(-sz, -sz / 2f)
        path.lineTo(sz, 0f)
        path.lineTo(-sz, sz / 2f)
        path.lineTo(-sz * 0.4f, 0f)
        path.close()
        canvas.drawPath(path, arrowPaint)
        canvas.restore()
    }
}

/** 单个独立按键 view */
class KeyView(
    context: Context,
    val key: PadKey,
    val pad: VirtualPad
) : View(context) {

    private val dispatchFn: (String, Boolean) -> Unit = { code, pressed -> pad.dispatchKey(code, pressed) }

    private val dp = resources.displayMetrics.density
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#A6000000") }
    private val fg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFEBB543"); style = Paint.Style.STROKE; strokeWidth = 2 * dp
    }
    private val close = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFEF4444") }
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var scaling = false
    private var downTime = 0L
    private var pointerId = -1
    private var initialDist = 0f
    private var initialScale = 1f
    var editing = false
        set(v) { field = v; invalidate() }

    init {
        fg.textSize = 14 * dp
        bg.alpha = 200
        refreshPos()
    }

    private fun refreshPos() {
        val sz = (56f * key.scale * dp).toInt()
        val lp = layoutParams as ViewGroup.LayoutParams?
        if (lp == null) {
            layoutParams = ViewGroup.LayoutParams(sz, sz)
        } else {
            lp.width = sz
            lp.height = sz
            layoutParams = lp
        }
        val finalSz = sz
        val finalParent = parent
        post {
            val p = finalParent as? ViewGroup ?: return@post
            if (p.width == 0) return@post
            val cx = if (key.x < 0) 0.85f else key.x
            val cy = if (key.y < 0) 0.78f else key.y
            val px = p.width
            val py = p.height
            val nx = (cx * px - finalSz / 2f).coerceIn(0f, (px - finalSz).toFloat())
            val ny = (cy * py - finalSz / 2f).coerceIn(0f, (py - finalSz).toFloat())
            setX(nx)
            setY(ny)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (editing) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pointerId = event.getPointerId(0)
                    lastX = event.rawX
                    lastY = event.rawY
                    dragging = false
                    scaling = false
                    downTime = System.currentTimeMillis()
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount >= 2) {
                        scaling = true
                        initialDist = distance(event, 0, 1)
                        initialScale = key.scale
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (scaling && event.pointerCount >= 2) {
                        val d = distance(event, 0, 1)
                        if (initialDist > 0) {
                            key.scale = (initialScale * (d / initialDist)).coerceIn(0.4f, 2.5f)
                            pad.updateKey(this)
                            refreshPos()
                        }
                    } else {
                        val idx = event.findPointerIndex(pointerId)
                        if (idx >= 0) {
                            val dx = event.rawX - lastX
                            val dy = event.rawY - lastY
                            if (!dragging && abs(dx) + abs(dy) > 4 * dp) dragging = true
                            if (dragging) {
                                setX(x + dx)
                                setY(y + dy)
                                lastX = event.rawX
                                lastY = event.rawY
                                saveXY()
                            }
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!dragging && !scaling && System.currentTimeMillis() - downTime > 500) {
                        // 长按：删除 / 编辑
                        AlertDialog.Builder(context)
                            .setTitle(key.label)
                            .setItems(arrayOf("编辑按键", "删除按键")) { _, w ->
                                when (w) {
                                    0 -> pad.showKeyPickerFor(key)
                                    1 -> pad.removeKey(this)
                                }
                            }
                            .setNegativeButton(R.string.cancel, null)
                            .show()
                    } else if (!dragging && !scaling) {
                        // 单击：编辑（容易误触）
                        // 这里改为不弹，避免编辑模式下误操作
                    }
                    pointerId = -1
                    dragging = false
                    scaling = false
                }
            }
            return true
        }
        // 正常模式
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerId = event.getPointerId(0)
                dispatchFn(key.code, true)
                animate().scaleX(0.92f).scaleY(0.92f).setDuration(60).start()
            }
            MotionEvent.ACTION_MOVE -> {
                // 多指无操作
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pointerId = -1
                dispatchFn(key.code, false)
                animate().scaleX(1f).scaleY(1f).setDuration(80).start()
            }
        }
        return true
    }

    private fun saveXY() {
        val p = parent as? ViewGroup ?: return
        if (p.width == 0) return
        key.x = ((x + width / 2) / p.width).coerceIn(0f, 1f)
        key.y = ((y + height / 2) / p.height).coerceIn(0f, 1f)
        pad.updateKey(this)
    }

    private fun distance(e: MotionEvent, i: Int, j: Int): Float {
        val dx = e.getX(i) - e.getX(j)
        val dy = e.getY(i) - e.getY(j)
        return hypot(dx, dy)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bg.alpha = (key.opacity * 255).toInt().coerceIn(0, 255)
        val r = 14 * dp
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, bg)
        if (editing) {
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, border)
            // 右上角删除叉
            canvas.drawCircle(width - 8 * dp, 8 * dp, 8 * dp, close)
            val xp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 2 * dp }
            canvas.drawLine(width - 12 * dp, 4 * dp, width - 4 * dp, 12 * dp, xp)
            canvas.drawLine(width - 4 * dp, 4 * dp, width - 12 * dp, 12 * dp, xp)
        }
        val cx = width / 2f
        val cy = height / 2f - (fg.ascent() + fg.descent()) / 2f
        canvas.drawText(key.label, cx, cy, fg)
    }
}

/** 暴露给 KeyView 用来分发按键（绕开 OnPadActionListener 重复事件） */

/** 全键盘按键列表 */
val ALL_KEYS: List<Pair<String, String>> = listOf(
    // 字母
    "KeyA" to "A", "KeyB" to "B", "KeyC" to "C", "KeyD" to "D", "KeyE" to "E",
    "KeyF" to "F", "KeyG" to "G", "KeyH" to "H", "KeyI" to "I", "KeyJ" to "J",
    "KeyK" to "K", "KeyL" to "L", "KeyM" to "M", "KeyN" to "N", "KeyO" to "O",
    "KeyP" to "P", "KeyQ" to "Q", "KeyR" to "R", "KeyS" to "S", "KeyT" to "T",
    "KeyU" to "U", "KeyV" to "V", "KeyW" to "W", "KeyX" to "X", "KeyY" to "Y",
    "KeyZ" to "Z",
    // 数字
    "Digit0" to "0", "Digit1" to "1", "Digit2" to "2", "Digit3" to "3",
    "Digit4" to "4", "Digit5" to "5", "Digit6" to "6", "Digit7" to "7",
    "Digit8" to "8", "Digit9" to "9",
    // 功能键
    "Enter" to "Enter", "Space" to "Space", "Escape" to "Esc", "Tab" to "Tab",
    "Backspace" to "Bksp", "ShiftLeft" to "L-Shift", "ShiftRight" to "R-Shift",
    "ControlLeft" to "L-Ctrl", "ControlRight" to "R-Ctrl", "AltLeft" to "L-Alt",
    "AltRight" to "R-Alt", "MetaLeft" to "L-Meta", "MetaRight" to "R-Meta",
    "CapsLock" to "Caps", "NumLock" to "Num", "ScrollLock" to "Scr",
    // 方向
    "ArrowUp" to "↑", "ArrowDown" to "↓", "ArrowLeft" to "←", "ArrowRight" to "→",
    // F 区
    "F1" to "F1", "F2" to "F2", "F3" to "F3", "F4" to "F4",
    "F5" to "F5", "F6" to "F6", "F7" to "F7", "F8" to "F8",
    "F9" to "F9", "F10" to "F10", "F11" to "F11", "F12" to "F12",
    // 标点
    "Minus" to "-", "Equal" to "=", "BracketLeft" to "[", "BracketRight" to "]",
    "Backslash" to "\\", "Semicolon" to ";", "Quote" to "'", "Comma" to ",",
    "Period" to ".", "Slash" to "/", "Backquote" to "`"
)

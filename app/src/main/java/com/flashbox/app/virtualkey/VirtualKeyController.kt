package com.flashbox.app.virtualkey

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import com.flashbox.app.data.SettingsRepository
import com.flashbox.app.web.KeyDispatchBridge
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Owns the virtual-key overlay: a direction control (joystick or dpad) plus a
 * set of independent key buttons. Handles persistence, layout, scaling,
 * dragging and dispatching presses to the WebView via [KeyDispatchBridge].
 */
class VirtualKeyController(
    private val context: Context,
    private val overlay: FrameLayout,
    private val settings: SettingsRepository,
    private val bridge: KeyDispatchBridge
) {
    private val gson = Gson()
    private var directionView: View? = null
    private val keyButtons = mutableListOf<KeyButtonView>()
    private var keys: MutableList<KeyDef> = loadKeys().toMutableList()
    private var editMode = false

    var visible: Boolean
        get() = overlay.visibility == View.VISIBLE
        set(value) {
            overlay.visibility = if (value) View.VISIBLE else View.GONE
            if (value) rebuild()
        }

    init {
        rebuild()
    }

    fun toggle() {
        visible = !visible
        settings.vkEnabled = visible
    }

    fun setEditMode(enabled: Boolean) {
        editMode = enabled
        keyButtons.forEach { it.editMode = enabled }
        directionView?.let { it.alpha = if (enabled) 0.9f else 1f }
    }

    /** Switch direction control between joystick and dpad. */
    fun setDirectionControl(control: VirtualKeyMode.DirectionControl) {
        settings.vkDirectionMode = control
        rebuild()
    }

    /** Switch key layout (WASD / arrows). */
    fun setKeyLayout(layout: String) {
        settings.vkKeyLayout = layout
    }

    fun currentDirectionControl() = settings.vkDirectionMode
    fun currentKeyLayout() = settings.vkKeyLayout
    fun currentKeys(): List<KeyDef> = keys

    fun addKey(key: KeyDef) {
        if (keys.any { it.keyCode == key.keyCode && it.label == key.label }) return
        keys.add(key)
        persistKeys()
        rebuild()
    }

    fun removeKey(key: KeyDef) {
        keys.removeAll { it.keyCode == key.keyCode && it.label == key.label }
        persistKeys()
        rebuild()
    }

    fun resetDefaults() {
        keys = KeyDef.defaults().toMutableList()
        persistKeys()
        rebuild()
    }

    private fun rebuild() {
        overlay.removeAllViews()
        keyButtons.clear()
        if (overlay.visibility != View.VISIBLE) return

        buildDirectionControl()
        buildKeyButtons()
    }

    private fun buildDirectionControl() {
        val control = settings.vkDirectionMode
        val sizeDp = (160 * settings.vkDirectionScale).toInt()
        val sizePx = dp(sizeDp)
        val lp = FrameLayout.LayoutParams(sizePx, sizePx).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            val m = dp(16)
            setMargins(m, m, m, m)
        }

        val view: View = when (control) {
            VirtualKeyMode.DirectionControl.JOYSTICK -> JoystickView(context).apply {
                onDirection = { _, _, dir -> dispatchDirection(dir) }
            }
            VirtualKeyMode.DirectionControl.DPAD -> DPadView(context).apply {
                onDirection = { dir -> dispatchDirection(dir) }
            }
        }
        view.layoutParams = lp
        // restore saved position
        if (settings.vkDirectionX >= 0 && settings.vkDirectionY >= 0) {
            view.post {
                view.x = (settings.vkDirectionX * overlay.width).coerceIn(0f, (overlay.width - view.width).toFloat())
                view.y = (settings.vkDirectionY * overlay.height).coerceIn(0f, (overlay.height - view.height).toFloat())
            }
            makeDraggable(view, isDirection = true)
        }
        overlay.addView(view)
        directionView = view
    }

    private fun buildKeyButtons() {
        val defaultPositions = listOf(
            0.78f to 0.55f, 0.86f to 0.62f, 0.82f to 0.74f,
            0.70f to 0.50f, 0.92f to 0.50f, 0.70f to 0.74f,
            0.92f to 0.74f, 0.80f to 0.86f
        )
        keys.forEachIndexed { index, def ->
            val btn = KeyButtonView(context).apply {
                keyDef = def
                text = def.label
                editMode = this@VirtualKeyController.editMode
                onPress = { d, pressed -> dispatchKey(d, pressed) }
                onMoved = { d, x, y ->
                    val idx = keys.indexOfFirst { it.keyCode == d.keyCode && it.label == d.label }
                    if (idx >= 0) keys[idx] = d
                    persistKeys()
                }
                onScaled = { d, size ->
                    val idx = keys.indexOfFirst { it.keyCode == d.keyCode && it.label == d.label }
                    if (idx >= 0) keys[idx] = d
                    persistKeys()
                }
            }
            val sizePx = dp(def.sizeDp)
            val lp = FrameLayout.LayoutParams(sizePx, sizePx).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            val pos = if (def.xPct in 0f..1f && def.yPct in 0f..1f) def.xPct to def.yPct
                      else defaultPositions.getOrElse(index) { 0.85f to (0.4f + index * 0.06f) }
            btn.layoutParams = lp
            overlay.addView(btn)
            btn.post {
                if (def.xPct in 0f..1f && def.yPct in 0f..1f) {
                    btn.x = pos.first * overlay.width
                    btn.y = pos.second * overlay.height
                } else {
                    btn.x = pos.first * overlay.width - btn.width / 2f
                    btn.y = pos.second * overlay.height - btn.height / 2f
                }
            }
            makeDraggable(btn, isDirection = false)
            keyButtons.add(btn)
        }
    }

    private var dragStartX = 0f
    private var dragStartY = 0f
    private var viewStartX = 0f
    private var viewStartY = 0f
    private fun makeDraggable(view: View, isDirection: Boolean) {
        view.setOnLongClickListener {
            val ok = !editMode || true
            // In edit mode, allow drag via a dedicated touch path handled in view itself
            // For the direction control we implement drag here.
            if (isDirection) {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            }
            true
        }
        // simple drag for direction control in edit mode
        if (isDirection) {
            var dragging = false
            view.setOnTouchListener { v, e ->
                if (!editMode) return@setOnTouchListener false
                when (e.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        dragging = true
                        dragStartX = e.rawX; dragStartY = e.rawY
                        viewStartX = v.x; viewStartY = v.y
                        true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        if (dragging) {
                            v.x = (viewStartX + e.rawX - dragStartX).coerceIn(0f, (overlay.width - v.width).toFloat())
                            v.y = (viewStartY + e.rawY - dragStartY).coerceIn(0f, (overlay.height - v.height).toFloat())
                        }
                        true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        if (dragging) {
                            dragging = false
                            settings.vkDirectionX = if (overlay.width > 0) v.x / overlay.width else -1f
                            settings.vkDirectionY = if (overlay.height > 0) v.y / overlay.height else -1f
                        }
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun dispatchDirection(dir: JoystickView.Direction) {
        val layout = settings.vkKeyLayout
        // map direction to keys: WASD -> W/A/S/D ; arrows -> ArrowUp/Left/Down/Right
        if (layout == VirtualKeyMode.WASD) {
            setKey("w", 87, dir.up); setKey("s", 83, dir.down)
            setKey("a", 65, dir.left); setKey("d", 68, dir.right)
        } else {
            setKey("ArrowUp", 38, dir.up); setKey("ArrowDown", 40, dir.down)
            setKey("ArrowLeft", 37, dir.left); setKey("ArrowRight", 39, dir.right)
        }
    }

    private var heldDir = mutableSetOf<String>()
    private fun setKey(key: String, code: Int, pressed: Boolean) {
        if (pressed) {
            if (heldDir.add(key)) bridge.sendKeyDown(key, code)
        } else {
            if (heldDir.remove(key)) bridge.sendKeyUp(key, code)
        }
    }

    private fun dispatchKey(def: KeyDef, pressed: Boolean) {
        if (pressed) bridge.sendKeyDown(def.jsKey, def.jsCode)
        else bridge.sendKeyUp(def.jsKey, def.jsCode)
    }

    private fun loadKeys(): List<KeyDef> {
        val json = settings.vkKeysJson
        if (json.isBlank()) return KeyDef.defaults()
        return try {
            val type = object : TypeToken<List<KeyDef>>() {}.type
            gson.fromJson(json, type) ?: KeyDef.defaults()
        } catch (e: Exception) {
            KeyDef.defaults()
        }
    }

    private fun persistKeys() {
        settings.vkKeysJson = gson.toJson(keys)
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt()
}

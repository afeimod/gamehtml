package com.game4399.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/**
 * 偏好封装。集中管理设置项的读写，避免散落的 getString/getBoolean。
 */
object PrefsManager {

    lateinit var sp: SharedPreferences
        private set

    fun init(context: Context) {
        sp = PreferenceManager.getDefaultSharedPreferences(context)
    }

    // ---- 通用 ----
    /** 游戏界面方向：auto / landscape / portrait */
    val orientation: String get() = sp.getString("orientation", "landscape") ?: "landscape"
    val isBlockAds: Boolean get() = sp.getBoolean("block_ads", false)
    /** 鼠标光标（PC 网页模拟鼠标） */
    val isMouseEnabled: Boolean get() = sp.getBoolean("mouse_enabled", false)
    /** 兼容旧设置：landscape_game */
    val isLandscapeGame: Boolean get() = sp.getBoolean("landscape_game", true)

    // ---- Flash ----
    val isFlashEnabled: Boolean get() = sp.getBoolean("flash_enabled", true)
    val isFlashAutoplay: Boolean get() = sp.getBoolean("flash_autoplay", true)
    val flashCdn: String get() = sp.getString("flash_cdn", "local") ?: "local"
    val flashQuality: String get() = sp.getString("flash_quality", "high") ?: "high"

    // ---- 手柄 ----
    val isGamepadEnabled: Boolean get() = sp.getBoolean("gamepad_enabled", true)
    /** 0~100 → 0~255 alpha */
    val gamepadAlpha: Int
        get() = ((sp.getInt("gamepad_opacity", 60) / 100f) * 255).toInt().coerceIn(40, 255)
    /** 动作按键大小倍率 0.5~2.0，默认 1.0 */
    val gamepadScale: Float
        get() = (sp.getInt("gamepad_scale", 100) / 100f).coerceIn(0.5f, 2.0f)
    /** 方向键大小倍率 0.5~2.0，默认 1.0 */
    val dpadScale: Float
        get() = (sp.getInt("dpad_scale", 100) / 100f).coerceIn(0.5f, 2.0f)
    /** 方向键映射模式：dpad / wasd */
    val dpadMode: String get() = sp.getString("dpad_mode", "dpad") ?: "dpad"
    /** 方向键水平偏移（像素），正值向右，默认 0 */
    val dpadOffsetX: Int get() = sp.getInt("dpad_offset_x", 0)
    /** 方向键垂直偏移（像素），正值向下，默认 0 */
    val dpadOffsetY: Int get() = sp.getInt("dpad_offset_y", 0)
    /** 方向键绝对坐标 X（拖动模式保存的位置） */
    val dpadPosX: Float get() = sp.getFloat("dpad_pos_x", -1f)
    val dpadPosY: Float get() = sp.getFloat("dpad_pos_y", -1f)
    /** 动作按键水平偏移（像素），正值向右，默认 0 */
    val actionOffsetX: Int get() = sp.getInt("action_offset_x", 0)
    /** 动作按键垂直偏移（像素），正值向下，默认 0 */
    val actionOffsetY: Int get() = sp.getInt("action_offset_y", 0)
    /** 动作按键绝对坐标 X（拖动模式保存的位置） */
    val actionPosX: Float get() = sp.getFloat("action_pos_x", -1f)
    val actionPosY: Float get() = sp.getFloat("action_pos_y", -1f)
    /** 6 个动作按键映射，默认 J/K/L/U/I/O */
    val gamepadKeys: List<String>
        get() = listOf(
            sp.getString("gamepad_key_1", "J") ?: "J",
            sp.getString("gamepad_key_2", "K") ?: "K",
            sp.getString("gamepad_key_3", "L") ?: "L",
            sp.getString("gamepad_key_4", "U") ?: "U",
            sp.getString("gamepad_key_5", "I") ?: "I",
            sp.getString("gamepad_key_6", "O") ?: "O"
        )
    /** 每个按键是否显示，默认全部显示 */
    val gamepadKeyVisible: List<Boolean>
        get() = listOf(
            sp.getBoolean("gamepad_key_1_visible", true),
            sp.getBoolean("gamepad_key_2_visible", true),
            sp.getBoolean("gamepad_key_3_visible", true),
            sp.getBoolean("gamepad_key_4_visible", true),
            sp.getBoolean("gamepad_key_5_visible", true),
            sp.getBoolean("gamepad_key_6_visible", true)
        )
    /** Select 键映射 */
    val selectKey: String get() = sp.getString("select_key", "TAB") ?: "TAB"
    /** Start 键映射 */
    val startKey: String get() = sp.getString("start_key", "ENTER") ?: "ENTER"
    /** Select/Start 是否显示 */
    val isSystemButtonsVisible: Boolean get() = sp.getBoolean("system_buttons_visible", true)
    /** 方向键是否显示 */
    val isDpadVisible: Boolean get() = sp.getBoolean("dpad_visible", true)
    /** 鼠标模式是否启用 */
    val isMouseModeEnabled: Boolean get() = sp.getBoolean("mouse_mode_enabled", false)
    /** 鼠标按钮是否显示 */
    val isMouseButtonsVisible: Boolean get() = sp.getBoolean("mouse_buttons_visible", false)
    /** 鼠标按钮水平偏移 */
    val mouseOffsetX: Int get() = sp.getInt("mouse_offset_x", 0)
    /** 鼠标按钮垂直偏移 */
    val mouseOffsetY: Int get() = sp.getInt("mouse_offset_y", 0)
    /** 鼠标按钮绝对坐标 X（拖动模式保存的位置） */
    val mousePosX: Float get() = sp.getFloat("mouse_pos_x", -1f)
    val mousePosY: Float get() = sp.getFloat("mouse_pos_y", -1f)

    // ---- Flash 引擎 ----
    /** Flash 引擎：ruffle / swf2js */
    val flashEngine: String get() = sp.getString("flash_engine", "ruffle") ?: "ruffle"
    /** 兼容旧设置 */
    val gamepadAKey: String get() = sp.getString("gamepad_a_key", "SPACE") ?: "SPACE"
    val gamepadBKey: String get() = sp.getString("gamepad_b_key", "ENTER") ?: "ENTER"

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.unregisterOnSharedPreferenceChangeListener(listener)
}

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
    /** CDN 来源：默认本地离线（local），可选 jsdelivr / unpkg */
    val flashCdn: String get() = sp.getString("flash_cdn", "local") ?: "local"
    val flashQuality: String get() = sp.getString("flash_quality", "high") ?: "high"

    // ---- 手柄 ----
    val isGamepadEnabled: Boolean get() = sp.getBoolean("gamepad_enabled", true)
    /** 页面缩放级别：25~200，0=自动适配 */
    val pageZoom: Int
        get() = sp.getInt("page_zoom", 0)
    /** 0~100 → 0~255 alpha */
    val gamepadAlpha: Int
        get() = ((sp.getInt("gamepad_opacity", 60) / 100f) * 255).toInt().coerceIn(40, 255)
    /** 动作按键大小倍率 0.5~2.0，默认 1.0 */
    val gamepadScale: Float
        get() = (sp.getInt("gamepad_scale", 100) / 100f).coerceIn(0.5f, 2.0f)
    /** 方向键大小倍率 0.5~2.0，默认 1.0 */
    val dpadScale: Float
        get() = (sp.getInt("dpad_scale", 100) / 100f).coerceIn(0.5f, 2.0f)
    /** 方向键映射模式：dpad / wasd / joystick */
    val dpadMode: String get() = sp.getString("dpad_mode", "dpad") ?: "dpad"
    /** 方向键水平偏移（像素），正值向右，默认 0 */
    val dpadOffsetX: Int get() = sp.getInt("dpad_offset_x", 0)
    /** 方向键垂直偏移（像素），正值向下，默认 0 */
    val dpadOffsetY: Int get() = sp.getInt("dpad_offset_y", 0)
    /** 方向键绝对坐标 X（拖动模式保存的位置） */
    val dpadPosX: Float get() = sp.getFloat("dpad_pos_x", -1f)
    val dpadPosY: Float get() = sp.getFloat("dpad_pos_y", -1f)

    /** 页面缩放模式：auto / manual */
    val pageZoomMode: String get() = sp.getString("page_zoom_mode", "auto") ?: "auto"
    /** 页面手动缩放比例（25~200，表示 25%~200%），默认 40 */
    val pageZoomManual: Int get() = sp.getInt("page_zoom_manual", 40).coerceIn(25, 200)
    /** 动作按键水平偏移（像素），正值向右，默认 0 */
    val actionOffsetX: Int get() = sp.getInt("action_offset_x", 0)
    /** 动作按键垂直偏移（像素），正值向下，默认 0 */
    val actionOffsetY: Int get() = sp.getInt("action_offset_y", 0)
    /** 动作按键绝对坐标 X（拖动模式保存的位置） */
    val actionPosX: Float get() = sp.getFloat("action_pos_x", -1f)
    val actionPosY: Float get() = sp.getFloat("action_pos_y", -1f)
    /** 动作按键数量（2~18），默认 6，可在按键设置中增删 */
    val gamepadKeyCount: Int
        get() = sp.getInt("gamepad_key_count", 6).coerceIn(2, 18)

    /** 动作按键默认映射（超出原 6 个时依次使用） */
    private val gamepadKeyDefaults = arrayOf(
        "J", "K", "L", "U", "I", "O",
        "1", "2", "3", "4", "5", "6",
        "7", "8", "9", "Q", "E", "R"
    )

    /** 动作按键映射，数量由 [gamepadKeyCount] 决定 */
    val gamepadKeys: List<String>
        get() {
            val count = gamepadKeyCount
            return (0 until count).map { i ->
                sp.getString("gamepad_key_${i + 1}", gamepadKeyDefaults.getOrElse(i) { "J" }) ?: "J"
            }
        }
    /** 每个按键是否显示，数量由 [gamepadKeyCount] 决定，默认全部显示 */
    val gamepadKeyVisible: List<Boolean>
        get() {
            val count = gamepadKeyCount
            return (0 until count).map { i -> sp.getBoolean("gamepad_key_${i + 1}_visible", true) }
        }
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
    /** Start/Select 按钮绝对坐标 */
    val systemPosX: Float get() = sp.getFloat("system_pos_x", -1f)
    val systemPosY: Float get() = sp.getFloat("system_pos_y", -1f)

    // ---- Flash 引擎 ----
    /** Flash 引擎：ruffle / swf2js / waflash */
    val flashEngine: String get() = sp.getString("flash_engine", "ruffle") ?: "ruffle"
    /** UA 模式：desktop / ie_compat / mobile */
    val uaMode: String get() = sp.getString("ua_mode", "desktop") ?: "desktop"
    /** 兼容旧设置 */
    val gamepadAKey: String get() = sp.getString("gamepad_a_key", "SPACE") ?: "SPACE"
    val gamepadBKey: String get() = sp.getString("gamepad_b_key", "ENTER") ?: "ENTER"

    // ---- 3D 视角旋转 ----
    /** 视角旋转模式：开启后触摸拖动游戏区域会注入鼠标移动事件，用于 3D 游戏旋转视角 */
    val isCameraRotationEnabled: Boolean get() = sp.getBoolean("camera_rotation_enabled", false)

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.unregisterOnSharedPreferenceChangeListener(listener)
}

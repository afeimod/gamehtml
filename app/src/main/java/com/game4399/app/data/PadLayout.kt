package com.game4399.app.data

/**
 * 虚拟按键布局
 *
 * - movePad  : 摇杆 / 方向键，type = joystick | dpad
 * - keyMode  : wsad | arrows   （摇杆/方向键把方向映射成 WASD 或 上下左右）
 * - moveScale: 摇杆/方向键整体缩放 (0.5 .. 2.0)
 * - moveX/moveY: 摇杆/方向键位置（相对屏幕比例 0..1，原始 -1 表示未设置使用默认）
 * - moveOpacity: 透明度
 * - moveStickScale: 摇杆的内圈（stick）相对于外圈(base)的缩放
 * - keys: 独立按键（按键 / 位置 / 缩放 / 透明度 / 标签）
 *
 * PadKey.code 一定是 JS KeyboardEvent.code 规范名（KeyA, KeyW, ArrowUp...）
 * 渲染层用 window.KeyboardEvent.code 来 dispatch keydown / keyup。
 */
data class PadLayout(
    var movePad: String = "joystick",
    var keyMode: String = "wsad",
    var moveX: Float = -1f,
    var moveY: Float = -1f,
    var moveScale: Float = 1.0f,
    var moveOpacity: Float = 0.55f,
    var moveStickScale: Float = 0.45f,
    var moveShow: Boolean = true,
    var keys: MutableList<PadKey> = mutableListOf(
        PadKey("KeyJ", "J", -1f, 0.78f, 1.0f, 0.6f),
        PadKey("KeyK", "K", -1f, 0.78f, 1.0f, 0.6f),
        PadKey("KeyL", "L", -1f, 0.78f, 1.0f, 0.6f),
        PadKey("KeyU", "U", -1f, 0.78f, 1.0f, 0.6f),
        PadKey("KeyI", "I", -1f, 0.78f, 1.0f, 0.6f),
        PadKey("KeyO", "O", -1f, 0.78f, 1.0f, 0.6f),
        PadKey("Enter", "Enter", -1f, 0.78f, 1.0f, 0.6f),
        PadKey("Space", "Space", -1f, 0.78f, 1.0f, 0.6f),
    )
)

data class PadKey(
    val code: String,        // KeyA
    val label: String,       // A
    var x: Float,            // 0..1 屏幕比例
    var y: Float,            // 0..1 屏幕比例
    var scale: Float,        // 0.5..2.0
    var opacity: Float,      // 0..1
    var size: Int = 56       // px
)

package com.flashbox.app.virtualkey

/**
 * Virtual key configuration enums.
 */
object VirtualKeyMode {

    /** Direction control style: analog joystick or 4-way D-pad. */
    enum class DirectionControl(val id: String, val displayName: String) {
        JOYSTICK("joystick", "摇杆"),
        DPAD("dpad", "方向键");

        companion object {
            fun fromId(id: String?): DirectionControl =
                values().firstOrNull { it.id == id } ?: JOYSTICK
        }
    }

    /** Key layout mapping for the direction control. */
    const val WASD = "wasd"
    const val ARROWS = "arrows"

    fun layoutName(layout: String): String = when (layout) {
        WASD -> "WASD方式"
        ARROWS -> "上下左右方式"
        else -> "WASD方式"
    }
}

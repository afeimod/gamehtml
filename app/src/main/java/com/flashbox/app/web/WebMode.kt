package com.flashbox.app.web

/**
 * Web page rendering modes.
 * - DESKTOP: desktop user agent, wide viewport, supports scaling (电脑桌面模式)
 * - COMPAT:  desktop UA with compatibility tweaks + forced Ruffle polyfill (兼容模式)
 * - MOBILE:  mobile user agent, viewport-fit (移动手机模式)
 */
enum class WebMode(val id: String, val displayName: String) {
    DESKTOP("desktop", "电脑桌面模式"),
    COMPAT("compat", "兼容模式"),
    MOBILE("mobile", "移动手机模式");

    companion object {
        fun fromId(id: String?): WebMode =
            values().firstOrNull { it.id == id } ?: DESKTOP
    }
}

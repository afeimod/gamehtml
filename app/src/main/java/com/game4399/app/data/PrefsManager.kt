package com.game4399.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/**
 * 偏好封装。集中管理设置项的读写，避免散落的 getString/getBoolean。
 */
object PrefsManager {

    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = PreferenceManager.getDefaultSharedPreferences(context)
    }

    // ---- 通用 ----
    val isLandscapeGame: Boolean get() = sp.getBoolean("landscape_game", true)
    val isBlockAds: Boolean get() = sp.getBoolean("block_ads", false)

    // ---- Flash ----
    val isFlashEnabled: Boolean get() = sp.getBoolean("flash_enabled", true)
    val isFlashAutoplay: Boolean get() = sp.getBoolean("flash_autoplay", true)
    val flashCdn: String get() = sp.getString("flash_cdn", "jsdelivr") ?: "jsdelivr"
    val flashQuality: String get() = sp.getString("flash_quality", "high") ?: "high"

    // ---- 手柄 ----
    val isGamepadEnabled: Boolean get() = sp.getBoolean("gamepad_enabled", true)
    /** 0~100 → 0~255 alpha */
    val gamepadAlpha: Int
        get() = ((sp.getInt("gamepad_opacity", 60) / 100f) * 255).toInt().coerceIn(40, 255)
    val gamepadAKey: String get() = sp.getString("gamepad_a_key", "SPACE") ?: "SPACE"
    val gamepadBKey: String get() = sp.getString("gamepad_b_key", "ENTER") ?: "ENTER"

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.unregisterOnSharedPreferenceChangeListener(listener)
}

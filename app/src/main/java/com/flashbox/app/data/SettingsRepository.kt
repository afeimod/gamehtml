package com.flashbox.app.data

import android.content.Context
import android.content.SharedPreferences
import com.flashbox.app.engine.EngineConfig
import com.flashbox.app.engine.EngineType
import com.flashbox.app.web.WebMode
import com.flashbox.app.virtualkey.VirtualKeyMode
import com.google.gson.Gson

/**
 * Centralized preferences store for every user-tunable setting.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("flashbox_settings", Context.MODE_PRIVATE)
    private val gson = Gson()

    // ---- General ----
    var defaultEngine: EngineType
        get() = EngineType.fromId(prefs.getString(KEY_DEFAULT_ENGINE, EngineType.RUFFLE.id))
        set(value) = prefs.edit().putString(KEY_DEFAULT_ENGINE, value.id).apply()

    var defaultWebMode: WebMode
        get() = WebMode.fromId(prefs.getString(KEY_WEB_MODE, WebMode.DESKTOP.id))
        set(value) = prefs.edit().putString(KEY_WEB_MODE, value.id).apply()

    var pageZoom: Int
        get() = prefs.getInt(KEY_ZOOM, 100)
        set(value) = prefs.edit().putInt(KEY_ZOOM, value.coerceIn(25, 400)).apply()

    var adblockEnabled: Boolean
        get() = prefs.getBoolean(KEY_ADBLOCK, true)
        set(value) = prefs.edit().putBoolean(KEY_ADBLOCK, value).apply()

    var cacheEnabled: Boolean
        get() = prefs.getBoolean(KEY_CACHE, true)
        set(value) = prefs.edit().putBoolean(KEY_CACHE, value).apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()

    var landscapePlay: Boolean
        get() = prefs.getBoolean(KEY_LANDSCAPE, false)
        set(value) = prefs.edit().putBoolean(KEY_LANDSCAPE, value).apply()

    var showKeysOnline: Boolean
        get() = prefs.getBoolean(KEY_VK_ONLINE, true)
        set(value) = prefs.edit().putBoolean(KEY_VK_ONLINE, value).apply()

    // ---- Engine configs (per engine) ----
    fun engineConfig(engine: EngineType): EngineConfig {
        val json = prefs.getString("${KEY_ENGINE_CFG}_${engine.id}", null) ?: return EngineConfig()
        return try { gson.fromJson(json, EngineConfig::class.java) } catch (e: Exception) { EngineConfig() }
    }

    fun setEngineConfig(engine: EngineType, config: EngineConfig) {
        prefs.edit().putString("${KEY_ENGINE_CFG}_${engine.id}", gson.toJson(config)).apply()
    }

    // ---- Virtual keys ----
    var vkEnabled: Boolean
        get() = prefs.getBoolean(KEY_VK_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VK_ENABLED, value).apply()

    var vkDirectionMode: VirtualKeyMode.DirectionControl
        get() = VirtualKeyMode.DirectionControl.fromId(
            prefs.getString(KEY_VK_DIR, VirtualKeyMode.DirectionControl.JOYSTICK.id))
        set(value) = prefs.edit().putString(KEY_VK_DIR, value.id).apply()

    var vkKeyLayout: String
        get() = prefs.getString(KEY_VK_LAYOUT, VirtualKeyMode.WASD) ?: VirtualKeyMode.WASD
        set(value) = prefs.edit().putString(KEY_VK_LAYOUT, value).apply()

    /** JSON list of independent key definitions (code,label,x,y,size). */
    var vkKeysJson: String
        get() = prefs.getString(KEY_VK_KEYS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_VK_KEYS, value).apply()

    var vkDirectionScale: Float
        get() = prefs.getFloat(KEY_VK_DIR_SCALE, 1f)
        set(value) = prefs.edit().putFloat(KEY_VK_DIR_SCALE, value.coerceIn(0.5f, 2.5f)).apply()

    var vkDirectionX: Float
        get() = prefs.getFloat(KEY_VK_DIR_X, -1f)  // -1 means default bottom-start
        set(value) = prefs.edit().putFloat(KEY_VK_DIR_X, value).apply()

    var vkDirectionY: Float
        get() = prefs.getFloat(KEY_VK_DIR_Y, -1f)
        set(value) = prefs.edit().putFloat(KEY_VK_DIR_Y, value).apply()

    companion object {
        private const val KEY_DEFAULT_ENGINE = "default_engine"
        private const val KEY_WEB_MODE = "web_mode"
        private const val KEY_ZOOM = "page_zoom"
        private const val KEY_ADBLOCK = "adblock"
        private const val KEY_CACHE = "cache"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_LANDSCAPE = "landscape"
        private const val KEY_VK_ONLINE = "vk_online"
        private const val KEY_ENGINE_CFG = "engine_cfg"
        private const val KEY_VK_ENABLED = "vk_enabled"
        private const val KEY_VK_DIR = "vk_dir"
        private const val KEY_VK_LAYOUT = "vk_layout"
        private const val KEY_VK_KEYS = "vk_keys"
        private const val KEY_VK_DIR_SCALE = "vk_dir_scale"
        private const val KEY_VK_DIR_X = "vk_dir_x"
        private const val KEY_VK_DIR_Y = "vk_dir_y"
    }
}

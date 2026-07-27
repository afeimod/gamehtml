package com.game4399.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 应用全局配置（UI 模式、默认主页、引擎、画质、虚拟按键布局等）
 * 同时充当 history / favorites / 本地游戏列表 / 拦截规则的本地存储。
 *
 * 所有 List 用 JSON 持久化，跨进程安全。
 */
object Prefs {
    private const val FILE = "game4399_prefs"
    private const val KEY_DEFAULT = "default_values_initialized"

    // === 通用 UI ===
    private const val K_UI_MODE = "ui_mode"              // "pc" | "mobile"
    private const val K_ZOOM = "page_zoom"               // 0.5 ~ 2.0
    private const val K_DARK = "dark_mode"               // "system" | "light" | "dark"

    // === 引擎 ===
    private const val K_ENGINE = "engine"                // "ruffle" | "waflash"
    private const val K_QUALITY = "engine_quality"        // "low"|"medium"|"high"|"best"
    private const val K_ASPECT = "engine_aspect"          // "fit"|"fill"|"stretch"
    private const val K_BG_COLOR = "engine_bg"
    private const val K_RUFFLE_LETTERBOX = "ruffle_letterbox"

    // === 主页 ===
    private const val K_HOME_PC = "homepage_pc"
    private const val K_HOME_MOBILE = "homepage_mobile"

    // === 浏览器 ===
    private const val K_AD_BLOCK = "ad_block"
    private const val K_CACHE_MODE = "cache_mode"
    private const val K_USER_AGENT = "user_agent"

    // === 虚拟按键布局 ===
    private const val K_PAD_TYPE = "pad_type"            // "joystick" | "dpad"
    private const val K_PAD_MODE = "pad_key_mode"        // "wsad" | "arrows"
    private const val K_PAD_KEYS = "pad_keys_json"       // 独立按键 JSON list
    private const val K_PAD_LAYOUT = "pad_layout_json"   // 摇杆/方向键/独立按键完整布局

    // === 列表 ===
    private const val K_HISTORY = "history_json"
    private const val K_FAVORITES = "favorites_json"
    private const val K_LOCAL = "local_json"
    private const val K_ADBLOCK_RULES = "adblock_rules_json"
    private const val K_ENGINE_CHOSEN = "engine_chosen"

    private lateinit var sp: SharedPreferences
    private val gson = Gson()

    fun init(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (!sp.getBoolean(KEY_DEFAULT, false)) {
            sp.edit().apply {
                putBoolean(KEY_DEFAULT, true)
                putString(K_UI_MODE, "mobile")
                putFloat(K_ZOOM, 1.0f)
                putString(K_DARK, "system")
                putString(K_ENGINE, "ruffle")
                putString(K_QUALITY, "high")
                putString(K_ASPECT, "fit")
                putString(K_BG_COLOR, "#1a1a1a")
                putBoolean(K_RUFFLE_LETTERBOX, true)
                putString(K_HOME_PC, "https://www.4399.com/")
                putString(K_HOME_MOBILE, "https://m.4399.com/")
                putBoolean(K_AD_BLOCK, true)
                putString(K_CACHE_MODE, "default")
                putString(K_USER_AGENT, "system")
                putString(K_PAD_TYPE, "joystick")
                putString(K_PAD_MODE, "wsad")
                // 默认独立按键 j k l u i o + enter + space
                putString(K_PAD_KEYS, """["J","K","L","U","I","O","Enter","Space"]""")
                putString(K_PAD_LAYOUT, defaultPadLayout())
                putString(K_HISTORY, "[]")
                putString(K_FAVORITES, """[{"name":"4399电脑版","url":"https://www.4399.com/"},{"name":"4399手机版","url":"https://m.4399.com/"},{"name":"灵动游戏主页","url":"https://www.lingdongyouxi.com/"}]""")
                putString(K_LOCAL, "[]")
                putString(K_ADBLOCK_RULES, DEFAULT_ADBLOCK)
            }.apply()
        }
    }

    private fun defaultPadLayout(): String {
        val layout = PadLayout()
        return gson.toJson(layout)
    }

    fun uiMode(): String = sp.getString(K_UI_MODE, "mobile") ?: "mobile"
    fun setUiMode(v: String) = sp.edit().putString(K_UI_MODE, v).apply()

    fun pageZoom(): Float = sp.getFloat(K_ZOOM, 1.0f)
    fun setPageZoom(v: Float) = sp.edit().putFloat(K_ZOOM, v).apply()

    fun darkMode(): String = sp.getString(K_DARK, "system") ?: "system"
    fun setDarkMode(v: String) = sp.edit().putString(K_DARK, v).apply()

    fun engine(): String = sp.getString(K_ENGINE, "ruffle") ?: "ruffle"
    fun setEngine(v: String) = sp.edit().putString(K_ENGINE, v).apply()

    fun quality(): String = sp.getString(K_QUALITY, "high") ?: "high"
    fun setQuality(v: String) = sp.edit().putString(K_QUALITY, v).apply()

    fun aspect(): String = sp.getString(K_ASPECT, "fit") ?: "fit"
    fun setAspect(v: String) = sp.edit().putString(K_ASPECT, v).apply()

    fun bgColor(): String = sp.getString(K_BG_COLOR, "#1a1a1a") ?: "#1a1a1a"
    fun setBgColor(v: String) = sp.edit().putString(K_BG_COLOR, v).apply()

    fun ruffleLetterbox(): Boolean = sp.getBoolean(K_RUFFLE_LETTERBOX, true)
    fun setRuffleLetterbox(v: Boolean) = sp.edit().putBoolean(K_RUFFLE_LETTERBOX, v).apply()

    fun homePc(): String = sp.getString(K_HOME_PC, "https://www.4399.com/") ?: "https://www.4399.com/"
    fun setHomePc(v: String) = sp.edit().putString(K_HOME_PC, v).apply()

    fun homeMobile(): String = sp.getString(K_HOME_MOBILE, "https://m.4399.com/") ?: "https://m.4399.com/"
    fun setHomeMobile(v: String) = sp.edit().putString(K_HOME_MOBILE, v).apply()

    fun adBlock(): Boolean = sp.getBoolean(K_AD_BLOCK, true)
    fun setAdBlock(v: Boolean) = sp.edit().putBoolean(K_AD_BLOCK, v).apply()

    fun cacheMode(): String = sp.getString(K_CACHE_MODE, "default") ?: "default"
    fun setCacheMode(v: String) = sp.edit().putString(K_CACHE_MODE, v).apply()

    fun userAgent(): String = sp.getString(K_USER_AGENT, "system") ?: "system"
    fun setUserAgent(v: String) = sp.edit().putString(K_USER_AGENT, v).apply()

    fun padType(): String = sp.getString(K_PAD_TYPE, "joystick") ?: "joystick"
    fun setPadType(v: String) = sp.edit().putString(K_PAD_TYPE, v).apply()

    fun padKeyMode(): String = sp.getString(K_PAD_MODE, "wsad") ?: "wsad"
    fun setPadKeyMode(v: String) = sp.edit().putString(K_PAD_MODE, v).apply()

    fun padKeys(): List<String> {
        val s = sp.getString(K_PAD_KEYS, "[]") ?: "[]"
        val type = object : TypeToken<List<String>>() {}.type
        return runCatching { gson.fromJson<List<String>>(s, type) }.getOrNull() ?: emptyList()
    }

    fun setPadKeys(v: List<String>) = sp.edit().putString(K_PAD_KEYS, gson.toJson(v)).apply()

    fun padLayout(): PadLayout {
        val s = sp.getString(K_PAD_LAYOUT, null) ?: defaultPadLayout()
        return runCatching { gson.fromJson(s, PadLayout::class.java) }.getOrNull() ?: PadLayout()
    }

    fun setPadLayout(v: PadLayout) = sp.edit().putString(K_PAD_LAYOUT, gson.toJson(v)).apply()

    fun history(): List<HistoryItem> {
        val s = sp.getString(K_HISTORY, "[]") ?: "[]"
        val t = object : TypeToken<List<HistoryItem>>() {}.type
        return runCatching { gson.fromJson<List<HistoryItem>>(s, t) }.getOrNull() ?: emptyList()
    }

    fun setHistory(v: List<HistoryItem>) = sp.edit().putString(K_HISTORY, gson.toJson(v)).apply()

    fun addHistory(item: HistoryItem) {
        val list = history().toMutableList()
        list.removeAll { it.url == item.url }
        list.add(0, item)
        if (list.size > 200) list.subList(200, list.size).clear()
        setHistory(list)
    }

    fun clearHistory() = setHistory(emptyList())

    fun favorites(): List<HistoryItem> {
        val s = sp.getString(K_FAVORITES, "[]") ?: "[]"
        val t = object : TypeToken<List<HistoryItem>>() {}.type
        return runCatching { gson.fromJson<List<HistoryItem>>(s, t) }.getOrNull() ?: emptyList()
    }

    fun setFavorites(v: List<HistoryItem>) = sp.edit().putString(K_FAVORITES, gson.toJson(v)).apply()

    fun addFavorite(item: HistoryItem) {
        val list = favorites().toMutableList()
        if (list.none { it.url == item.url }) list.add(item)
        setFavorites(list)
    }

    fun removeFavorite(url: String) {
        setFavorites(favorites().filter { it.url != url })
    }

    fun isFavorite(url: String) = favorites().any { it.url == url }

    fun locals(): List<LocalItem> {
        val s = sp.getString(K_LOCAL, "[]") ?: "[]"
        val t = object : TypeToken<List<LocalItem>>() {}.type
        return runCatching { gson.fromJson<List<LocalItem>>(s, t) }.getOrNull() ?: emptyList()
    }

    fun setLocals(v: List<LocalItem>) = sp.edit().putString(K_LOCAL, gson.toJson(v)).apply()

    fun addLocal(item: LocalItem) {
        val list = locals().toMutableList()
        list.removeAll { it.path == item.path }
        list.add(0, item)
        setLocals(list)
    }

    fun removeLocal(path: String) = setLocals(locals().filter { it.path != path })

    fun engineChosenBefore(): Boolean = sp.getBoolean(K_ENGINE_CHOSEN, false)
    fun markEngineChosen() = sp.edit().putBoolean(K_ENGINE_CHOSEN, true).apply()

    fun adblockRules(): List<String> {
        val s = sp.getString(K_ADBLOCK_RULES, DEFAULT_ADBLOCK) ?: DEFAULT_ADBLOCK
        val t = object : TypeToken<List<String>>() {}.type
        return runCatching { gson.fromJson<List<String>>(s, t) }.getOrNull() ?: DEFAULT_ADBLOCK.split("\n")
    }

    fun setAdblockRules(v: List<String>) = sp.edit().putString(K_ADBLOCK_RULES, gson.toJson(v)).apply()

    /** 默认 adblock 规则（按 URL 包含/正则两种） */
    const val DEFAULT_ADBLOCK = """[
"doubleclick.net","googlesyndication.com","googleadservices.com","googletagmanager.com",
"google-analytics.com","adservice.google.","adnxs.com","adsafeprotected.com",
"adcolony.com","adform.net","amazon-adsystem.com","criteo.com","criteo.net",
"moatads.com","scorecardresearch.com","taboola.com","outbrain.com",
"popin.cc","static.cdn.popin.cc","popinads.com","baidu.com/ads","pos.baidu.com",
"hm.baidu.com","track.adform.net","serving-sys.com","atdmt.com","adsrvr.org",
"mathtag.com","adsymptotic.com","yandex.ru/metrika","mc.yandex.ru",
"adnxs.com","adroll.com","media.net","adzerk.net","ad.qq.com",
"qq.com/ads","tanx.com","alimama.com","alibaba.com/ads","miaozhen.com",
"cns.now8.com","ssp.qq.com","sogou.com/ads","union.sogou.com",
"pagead2.googlesyndication.com","googleads.g.doubleclick.net","bid.g.doubleclick.net",
"securepubads.g.doubleclick.net","tpc.googlesyndication.com","cm.g.doubleclick.net"
]"""
}

data class HistoryItem(
    val name: String,
    val url: String,
    val time: Long = System.currentTimeMillis()
)

data class LocalItem(
    val name: String,
    val path: String,            // file:// URI
    val isDir: Boolean = false,
    val time: Long = System.currentTimeMillis()
)

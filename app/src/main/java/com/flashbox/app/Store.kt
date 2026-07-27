package com.flashbox.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Central persistence for the game box: settings, per-engine configs, library,
 * history, favorites, sites and virtual-control layout. Backed by SharedPreferences
 * + org.json so it needs no codegen and survives app restarts.
 */
object Store {

    private const val PREFS = "flashbox_prefs"
    private lateinit var prefs: android.content.SharedPreferences

    // keys
    private const val K_SETTINGS = "settings"
    private const val K_ENGINE_CONFIGS = "engine_configs"
    private const val K_LIBRARY = "library"
    private const val K_HISTORY = "history"
    private const val K_FAVORITES = "favorites"
    private const val K_SITES = "sites"
    private const val K_CONTROLS = "controls"

    fun init(ctx: Context) {
        prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        seedDefaults()
    }

    private fun getStr(key: String, def: String): String = prefs.getString(key, def) ?: def
    private fun setStr(key: String, value: String) = prefs.edit().putString(key, value).apply()

    // ---------- Settings ----------
    fun getSettings(): String = getStr(K_SETTINGS, defaultSettings().toString())
    fun saveSettings(json: String) = setStr(K_SETTINGS, json)

    private fun defaultSettings(): JSONObject = JSONObject().apply {
        put("engine", "ruffle")            // ruffle | waflash | flashpatch
        put("pageMode", "mobile")          // desktop | compat | mobile
        put("zoom", 100)                   // global page zoom %
        put("adblock", true)
        put("cache", true)
        put("desktopUa", false)
        put("autoHistory", true)
        put("swipeBack", true)
        put("darkWeb", true)
        put("jsInject", true)              // inject engine into online flash pages
        put("landscapeHint", true)
    }

    // ---------- Per-engine configs (quality / aspect / renderer ...) ----------
    fun getEngineConfigs(): String = getStr(K_ENGINE_CONFIGS, defaultEngineConfigs().toString())
    fun saveEngineConfigs(json: String) = setStr(K_ENGINE_CONFIGS, json)

    private fun defaultEngineConfigs(): JSONObject = JSONObject().apply {
        // Ruffle
        put("ruffle", JSONObject().apply {
            put("quality", "high")          // low|medium|high|best
            put("renderer", "webgl")        // webgl|canvas|wgpu|null
            put("scale", "showAll")         // showAll|noBorder|exactFit|noScale
            put("aspect", "contain")        // contain|cover|stretch|original
            put("letterbox", "fullscreen")  // off|fullscreen|topColor
            put("wmode", "opaque")          // window|opaque|transparent|direct|gpu
            put("letterboxColor", "#000000")
            put("playerVersion", "")
            put("maxExec", 15)
            put("upgradeToHttps", true)
            put("showSwfDownload", false)
            put("backgroundColor", "")
        })
        // Waflash
        put("waflash", JSONObject().apply {
            put("quality", "high")
            put("scale", "showAll")
            put("aspect", "contain")
            put("enableFilters", true)
            put("wmode", "opaque")
            put("allowNetworking", "internal")
            put("avm", "auto")              // auto|1|2
            put("backgroundColor", "#000000")
        })
        // FlashPatch compatibility mode (layers on ruffle with clean-runtime patches)
        put("flashpatch", JSONObject().apply {
            put("quality", "high")
            put("renderer", "webgl")
            put("scale", "showAll")
            put("aspect", "contain")
            put("baseEngine", "ruffle")     // underlying engine for compat mode
            put("killswitchBypass", true)
            put("removeAdware", true)
            put("regionUnlock", true)
            put("sitePatches", true)
            put("playerVersion", "34.0.0.376")
            put("backgroundColor", "#000000")
        })
    }

    // ---------- Library (local SWF files / folders) ----------
    fun getLibrary(): String = getStr(K_LIBRARY, "[]")
    fun saveLibrary(json: String) = setStr(K_LIBRARY, json)
    fun addLibraryItem(item: JSONObject): JSONArray {
        val arr = JSONArray(getLibrary())
        arr.put(item)
        saveLibrary(arr.toString())
        return arr
    }
    fun removeLibraryItem(id: String): JSONArray {
        val arr = JSONArray(getLibrary())
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") != id) out.put(o)
        }
        saveLibrary(out.toString())
        return out
    }
    fun clearLibrary() = saveLibrary("[]")

    // ---------- History ----------
    fun getHistory(): String = getStr(K_HISTORY, "[]")
    fun addHistory(item: JSONObject): JSONArray {
        val arr = JSONArray(getHistory())
        // de-dupe by url
        val out = JSONArray()
        out.put(item)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("url") != item.optString("url")) out.put(o)
            if (out.length() >= 200) break
        }
        setStr(K_HISTORY, out.toString())
        return out
    }
    fun removeHistory(id: String): JSONArray {
        val arr = JSONArray(getHistory())
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") != id) out.put(o)
        }
        setStr(K_HISTORY, out.toString())
        return out
    }
    fun clearHistory() = setStr(K_HISTORY, "[]")

    // ---------- Favorites ----------
    fun getFavorites(): String = getStr(K_FAVORITES, "[]")
    fun toggleFavorite(item: JSONObject): Boolean {
        val arr = JSONArray(getFavorites())
        val url = item.optString("url")
        var exists = false
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("url") == url) {
                exists = true
            } else {
                out.put(o)
            }
        }
        if (!exists) out.put(item)
        setStr(K_FAVORITES, out.toString())
        return !exists // true if now favorited
    }
    fun removeFavorite(id: String): JSONArray {
        val arr = JSONArray(getFavorites())
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") != id) out.put(o)
        }
        setStr(K_FAVORITES, out.toString())
        return out
    }

    // ---------- Sites ----------
    fun getSites(): String = getStr(K_SITES, defaultSites().toString())
    fun saveSites(json: String) = setStr(K_SITES, json)
    private fun defaultSites(): JSONArray = JSONArray().apply {
        put(site("4399电脑版", "https://www.4399.com/", "desktop", "4399", "flash", true))
        put(site("4399手机版", "https://www.4399.com/m/", "mobile", "4399m", "flash", true))
        put(site("4399Flash游戏", "https://www.4399.com/flash/", "desktop", "4399flash", "flash", true))
        put(site("灵动游戏主页", "https://www.mhhf.com/", "desktop", "mhhf", "mixed", true))
        put(site("灵动游戏库", "https://www.mhhf.com/games", "desktop", "mhhfgames", "mixed", true))
        put(site("7k7k小游戏", "https://www.7k7k.com/", "desktop", "7k7k", "flash", true))
        put(site("7k7k手机版", "https://m.7k7k.com/", "mobile", "7k7km", "flash", true))
        put(site("游戏盒首页", "about:home", "mobile", "home", "app", true))
    }
    private fun site(name: String, url: String, mode: String, id: String, cat: String, builtIn: Boolean): JSONObject =
        JSONObject().apply {
            put("id", id); put("name", name); put("url", url); put("mode", mode)
            put("category", cat); put("builtIn", builtIn)
            put("icon", url)
        }
    fun addSite(item: JSONObject): JSONArray {
        val arr = JSONArray(getSites())
        arr.put(item)
        saveSites(arr.toString())
        return arr
    }
    fun removeSite(id: String): JSONArray {
        val arr = JSONArray(getSites())
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") != id || o.optBoolean("builtIn")) {
                if (o.optString("id") != id) out.put(o)
            }
        }
        saveSites(out.toString())
        return out
    }

    // ---------- Virtual controls layout ----------
    fun getControls(): String = getStr(K_CONTROLS, defaultControls().toString())
    fun saveControls(json: String) = setStr(K_CONTROLS, json)

    /**
     * Default: joystick (WASD style) + 6 independent buttons J K L U I O + Enter + Space.
     * Each element has x/y in % of viewport, scale, and (for dpad/joystick) keyStyle.
     */
    private fun defaultControls(): JSONObject = JSONObject().apply {
        put("enabled", true)
        put("primaryType", "joystick")     // joystick | dpad
        put("primaryStyle", "wsad")        // wsad | arrows
        put("primaryX", 18.0); put("primaryY", 70.0); put("primaryScale", 1.0)
        put("buttons", JSONArray().apply {
            put(btn("J", "KeyJ", 70, 78, 1.0))
            put(btn("K", "KeyK", 80, 70, 1.0))
            put(btn("L", "KeyL", 88, 76, 1.0))
            put(btn("U", "KeyU", 58, 80, 0.9))
            put(btn("I", "KeyI", 66, 88, 0.9))
            put(btn("O", "KeyO", 82, 88, 0.9))
            put(btn("⏎", "Enter", 50, 90, 0.8))
            put(btn("␣", "Space", 40, 92, 1.0))
        })
    }
    private fun btn(label: String, code: String, x: Int, y: Int, s: Double): JSONObject =
        JSONObject().apply {
            put("id", "b_" + code)
            put("label", label); put("code", code)
            put("x", x.toDouble()); put("y", y.toDouble()); put("scale", s)
        }

    private fun seedDefaults() {
        if (!prefs.contains(K_SETTINGS)) setStr(K_SETTINGS, defaultSettings().toString())
        if (!prefs.contains(K_ENGINE_CONFIGS)) setStr(K_ENGINE_CONFIGS, defaultEngineConfigs().toString())
        if (!prefs.contains(K_SITES)) setStr(K_SITES, defaultSites().toString())
        if (!prefs.contains(K_CONTROLS)) setStr(K_CONTROLS, defaultControls().toString())
        if (!prefs.contains(K_LIBRARY)) setStr(K_LIBRARY, "[]")
        if (!prefs.contains(K_HISTORY)) setStr(K_HISTORY, "[]")
        if (!prefs.contains(K_FAVORITES)) setStr(K_FAVORITES, "[]")
    }
}

package com.game4399.app.data

import android.content.Context
import com.game4399.app.App
import org.json.JSONArray
import org.json.JSONObject

/**
 * 收藏与历史的本地存储（SharedPreferences + JSON）。
 * 简单可靠，无需数据库。收藏项保存 url + title + 时间。
 */
object FavoriteStore {

    private const val SP_NAME = "game_store"
    private const val KEY_FAV = "favorites"
    private const val KEY_HISTORY = "history"

    private val sp by lazy {
        App.instance.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
    }

    data class Entry(
        val url: String,
        val title: String,
        val timestamp: Long,
        val type: GameType = GameType.URL
    )

    // ---------- 收藏 ----------
    fun listFavorites(): List<Entry> = readList(KEY_FAV)

    fun add(url: String, title: String = url, type: GameType = GameType.URL) {
        val list = listFavorites().toMutableList()
        list.removeAll { it.url == url }
        list.add(0, Entry(url, title, System.currentTimeMillis(), type))
        writeList(KEY_FAV, list.take(200))
    }

    fun remove(url: String) {
        val list = listFavorites().toMutableList()
        list.removeAll { it.url == url }
        writeList(KEY_FAV, list)
    }

    fun isFavorite(url: String): Boolean = listFavorites().any { it.url == url }

    // ---------- 历史 ----------
    fun listHistory(): List<Entry> = readList(KEY_HISTORY)

    fun addHistory(url: String, title: String = url, type: GameType = GameType.URL) {
        val list = listHistory().toMutableList()
        list.removeAll { it.url == url }
        list.add(0, Entry(url, title, System.currentTimeMillis(), type))
        writeList(KEY_HISTORY, list.take(50))
    }

    fun clearHistory() = writeList(KEY_HISTORY, emptyList())

    // ---------- 内部 ----------
    private fun readList(key: String): List<Entry> = try {
        val arr = JSONArray(sp.getString(key, "[]") ?: "[]")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Entry(
                url = o.optString("url"),
                title = o.optString("title"),
                timestamp = o.optLong("ts"),
                type = runCatching { GameType.valueOf(o.optString("type")) }.getOrDefault(GameType.URL)
            )
        }
    } catch (e: Exception) { emptyList() }

    private fun writeList(key: String, list: List<Entry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().apply {
                put("url", e.url)
                put("title", e.title)
                put("ts", e.timestamp)
                put("type", e.type.name)
            })
        }
        sp.edit().putString(key, arr.toString()).apply()
    }
}

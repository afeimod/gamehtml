package com.game4399.app.data

import android.content.Context
import android.net.Uri
import com.game4399.app.App
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 本地 SWF 文件列表存储（SharedPreferences + JSON）。
 * 存储用户添加的本地 SWF 文件路径和名称。
 */
object LocalSwfStore {

    private const val SP_NAME = "local_swf_store"
    private const val KEY_LIST = "swf_list"

    private val sp by lazy {
        App.instance.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
    }

    data class Entry(
        val path: String,      // 文件 URI（content:// 或 file://）
        val title: String,     // 显示名称
        val timestamp: Long
    )

    fun list(): List<Entry> = try {
        val arr = JSONArray(sp.getString(KEY_LIST, "[]") ?: "[]")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Entry(
                path = o.optString("path"),
                title = o.optString("title"),
                timestamp = o.optLong("ts")
            )
        }
    } catch (e: Exception) { emptyList() }

    fun add(path: String, title: String) {
        val list = list().toMutableList()
        list.removeAll { it.path == path }
        list.add(0, Entry(path, title, System.currentTimeMillis()))
        writeList(list)
    }

    fun remove(path: String) {
        val list = list().toMutableList()
        list.removeAll { it.path == path }
        writeList(list)
    }

    fun toGameItems(): List<GameItem> = list().map { e ->
        GameItem(
            id = e.path,
            title = e.title,
            desc = "本地 SWF",
            type = GameType.LOCAL_SWF,
            url = e.path
        )
    }

    private fun writeList(list: List<Entry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().apply {
                put("path", e.path)
                put("title", e.title)
                put("ts", e.timestamp)
            })
        }
        sp.edit().putString(KEY_LIST, arr.toString()).apply()
    }

    /** 从文件名提取显示标题 */
    fun titleFromUri(uri: Uri): String {
        val name = uri.lastPathSegment ?: "未知"
        return name.substringBeforeLast(".swf", ignoreCase = true)
            .ifEmpty { name }
    }
}

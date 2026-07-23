package com.game4399.app.data

/**
 * 游戏条目数据模型。
 *
 * @param id      4399 游戏 ID（如 29386）
 * @param title   游戏名称
 * @param desc    描述（类型 / ID）
 * @param type    类型：FLASH / H5 / URL
 * @param url     直达网址（为空时按 id + type 生成）
 * @param icon    首字图标
 */
data class GameItem(
    val id: String,
    val title: String,
    val desc: String,
    val type: GameType,
    val url: String? = null,
    val icon: String = title.firstOrNull()?.toString() ?: "游"
) {
    /** 生成可加载的最终 URL */
    fun resolveUrl(): String = url ?: when (type) {
        GameType.FLASH -> "https://www.4399.com/flash/$id.htm"
        GameType.H5    -> "https://h.4399.com/play/$id.htm"
        GameType.URL   -> id
        GameType.LOCAL_SWF -> id  // id 存放文件 URI
    }
}

enum class GameType { FLASH, H5, URL, LOCAL_SWF }

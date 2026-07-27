package com.flashbox.app.data

import com.flashbox.app.web.WebMode

/**
 * Built-in default web shortcuts. Seeded into the database on first launch.
 */
object Defaults {

    data class WebItem(val title: String, val url: String, val mode: WebMode)

    val WEB_SHORTCUTS = listOf(
        WebItem("4399 电脑版", "https://www.4399.com/flash/", WebMode.DESKTOP),
        WebItem("4399 手机版", "https://www.4399.com/m/", WebMode.MOBILE),
        WebItem("4399小游戏", "https://www.4399.com/", WebMode.COMPAT),
        WebItem("7k7k 游戏", "https://www.7k7k.com/", WebMode.DESKTOP),
        WebItem("灵动游戏", "https://www.yad.com/", WebMode.DESKTOP),
        WebItem("灵动手机游戏", "https://m.yad.com/", WebMode.MOBILE),
        WebItem("Gamedistribution", "https://gamedistribution.com/", WebMode.DESKTOP),
        WebItem("CrazyGames", "https://www.crazygames.com/", WebMode.DESKTOP),
        WebItem("Poki", "https://poki.com/", WebMode.MOBILE),
        WebItem("Coolmath", "https://www.coolmathgames.com/", WebMode.DESKTOP),
        WebItem("游戏235", "https://www.games235.com/", WebMode.COMPAT),
        WebItem("Flash游戏库", "https://flasharch.com/", WebMode.DESKTOP)
    )

    fun toEntities(): List<WebShortcutEntity> = WEB_SHORTCUTS.mapIndexed { index, item ->
        WebShortcutEntity(
            title = item.title,
            url = item.url,
            mode = item.mode.id,
            isDefault = true,
            sortOrder = index
        )
    }
}

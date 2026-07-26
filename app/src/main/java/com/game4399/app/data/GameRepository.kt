package com.game4399.app.data

/**
 * 经典 Flash 游戏内置列表（4399 经典 ID）。
 * 数据基于公开的 4399 游戏 URL 规则构造：flash/{id}.htm / play/{id}.htm
 */
object GameRepository {

    data class ClassicGame(
        val id: String,
        val title: String,
        val type: GameType,
        val tag: String
    )

    val classics: List<ClassicGame> = listOf(
        ClassicGame("29386", "黄金矿工", GameType.FLASH, "休闲 · 经典"),
        ClassicGame("314796", "森林冰火人", GameType.FLASH, "双人 · 冒险"),
        ClassicGame("9180", "植物大战僵尸", GameType.FLASH, "塔防 · 策略"),
        ClassicGame("78417", "斗地主", GameType.FLASH, "棋牌 · 单机"),
        ClassicGame("3291", "泡泡堂", GameType.FLASH, "动作 · 对战"),
        ClassicGame("11350", "祖玛", GameType.FLASH, "益智 · 消除"),
        ClassicGame("5473", "连连看", GameType.FLASH, "益智 · 消除"),
        ClassicGame("17575", "造梦西游", GameType.FLASH, "RPG · 冒险"),
        ClassicGame("23113", "死神VS火影", GameType.FLASH, "动作 · 格斗"),
        ClassicGame("63631", "森林冰火人2", GameType.FLASH, "双人 · 冒险"),
        ClassicGame("257968", "4399热门H5", GameType.H5, "H5 · 在线"),
        ClassicGame("260809", "4399新游H5", GameType.H5, "H5 · 在线")
    )

    /** 转换为 GameItem 列表供 UI 使用 */
    fun classicItems(): List<GameItem> = classics.map {
        GameItem(
            id = it.id,
            title = it.title,
            desc = "${it.tag} · ID ${it.id}",
            type = it.type
        )
    }

    /** 首页快捷入口（标题 → URL） */
    val quickUrls: List<Pair<String, String>> = listOf(
        "梦幻西游网页版" to "https://xyh5.163.com/game/?channel=netease",
        "4399 电脑版" to "https://www.4399.com/",
        "云原神" to "https://ys.mihoyo.com/cloud/m/#/",
        "灵动游戏" to "https://www.mhhf.com/"
    )

    /** 网页游戏大全网格（标题 → URL） */
    data class WebGameEntry(
        val title: String,
        val url: String,
        val iconRes: Int = 0
    )

    val webGamePortals: List<WebGameEntry> = listOf(
        WebGameEntry("瑾龙游戏", "https://www.jlgames.cn/"),
        WebGameEntry("7k7k游戏", "https://www.7k7k.com/"),
        WebGameEntry("37游戏", "https://www.37.com/"),
        WebGameEntry("80在线游戏", "https://www.wgame80.com/"),
        WebGameEntry("2345游戏", "https://game.2345.com/"),
        WebGameEntry("y8游戏", "https://zh.y8.com/"),
        WebGameEntry("flash小游戏", "http://www.flashgame.com.cn/"),
        WebGameEntry("多玩游戏", "https://www.duowan.com/"),
        WebGameEntry("小霸王其乐无穷", "https://www.yikm.net/"),
        WebGameEntry("fc在线", "https://www.playfc.cn/"),
        WebGameEntry("宝玩poki游戏", "https://poki.com/zh"),
        WebGameEntry("233乐园", "https://www.233leyuan.com/game-detail-h5")
    )
}

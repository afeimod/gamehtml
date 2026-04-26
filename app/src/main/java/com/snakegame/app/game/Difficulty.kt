package com.snakegame.app.game

/**
 * 难度等级枚举
 * 定义游戏的不同难度配置
 */
enum class Difficulty {
    /**
     * 简单难度
     * - 无障碍物
     * - 游戏速度较慢（200ms）
     * - 正常蛇身生长
     */
    EASY {
        override fun getSpeed(): Long = 200L
        override fun getObstacleCount(): Int = 0
        override fun isGrowthEnabled(): Boolean = true
        override fun getDisplayName(): String = "简单"
    },
    
    /**
     * 普通难度
     * - 随机生成少量障碍物
     * - 游戏速度适中（150ms）
     * - 正常蛇身生长
     */
    NORMAL {
        override fun getSpeed(): Long = 150L
        override fun getObstacleCount(): Int = 5
        override fun isGrowthEnabled(): Boolean = true
        override fun getDisplayName(): String = "普通"
    },
    
    /**
     * 困难难度
     * - 随机生成大量障碍物
     * - 游戏速度快（100ms）
     * - 吃食物不生长，增加难度
     */
    HARD {
        override fun getSpeed(): Long = 100L
        override fun getObstacleCount(): Int = 10
        override fun isGrowthEnabled(): Boolean = false
        override fun getDisplayName(): String = "困难"
    };
    
    /**
     * 获取游戏速度（毫秒）
     */
    abstract fun getSpeed(): Long
    
    /**
     * 获取障碍物数量
     */
    abstract fun getObstacleCount(): Int
    
    /**
     * 是否启用蛇身生长
     */
    abstract fun isGrowthEnabled(): Boolean
    
    /**
     * 获取显示名称
     */
    abstract fun getDisplayName(): String
}
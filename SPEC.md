---
AIGC:
    ContentProducer: Minimax Agent AI
    ContentPropagator: Minimax Agent AI
    Label: AIGC
    ProduceID: "00000000000000000000000000000000"
    PropagateID: "00000000000000000000000000000000"
    ReservedCode1: 3045022100f2b810d01912e78cef33e4d4a3cd6c38b4fb8512065a943455f927f2fe4160e702206a239fefa3da1bcf977d935e3e69cc50c096fe6db0c7292ad590899b281cbb29
    ReservedCode2: 304402200c082b4e8a761048c5af26ba6d1e2810cccc363dfce2da738df73959ff251eac022075af8301d61b247627308f0f19f5f7dec7573560b2c80dbb2152b8f4b510ca78
---

# 贪吃蛇游戏规格说明书

## 1. 项目概述

- **项目名称**: SnakeGame
- **项目类型**: Android 休闲游戏
- **核心功能**: 经典的贪吃蛇游戏，玩家控制蛇移动，吃食物增长，避开墙壁和自身
- **目标用户**: 所有年龄段的用户，喜欢休闲益智类游戏

## 2. 技术栈

- **开发语言**: Kotlin
- **最低 SDK 版本**: 24 (Android 7.0)
- **目标 SDK 版本**: 34 (Android 14)
- **构建工具**: Gradle 8.0
- **Android Gradle 插件**: 8.1.0

## 3. 功能列表

### 3.1 游戏核心功能
- 蛇的移动控制（上下左右）
- 食物生成与蛇吃食物
- 蛇身增长逻辑
- 碰撞检测（墙壁、自身）
- 计分系统

### 3.2 虚拟按键控制
- 方向键（上、下、左、右）
- 按钮位置在屏幕底部
- 实时响应用户输入

### 3.3 游戏状态管理
- 游戏开始/重新开始
- 游戏结束判断
- 分数显示

## 4. UI/UX 设计方向

### 4.1 视觉风格
- 简洁现代的设计
- 清晰的颜色区分（蛇、食物、游戏区域）

### 4.2 颜色方案
- 游戏背景: 深灰色 (#2C2C2C)
- 蛇头: 绿色 (#4CAF50)
- 蛇身: 浅绿色 (#81C784)
- 食物: 红色 (#F44336)
- 网格线: 浅灰色 (#3C3C3C)
- 按键背景: 深蓝色 (#1565C0)
- 按键文字: 白色 (#FFFFFF)

### 4.3 布局设计
- 游戏区域占据屏幕大部分空间
- 分数显示在顶部
- 虚拟方向键位于屏幕底部
- 居中对齐设计

## 5. 游戏参数

- 游戏区域网格: 20x30 格
- 蛇初始长度: 3 格
- 蛇移动速度: 每 200ms 移动一格
- 食物出现: 随机位置

## 6. 文件结构

```
snakegame/
├── app/
│   └── src/main/
│       ├── java/com/snakegame/app/
│       │   ├── ui/
│       │   │   └── MainActivity.kt
│       │   ├── game/
│       │   │   ├── SnakeGameView.kt
│       │   │   └── Direction.kt
│       │   └── utils/
│       │       └── DirectionController.kt
│       ├── res/
│       │   ├── layout/
│       │   │   └── activity_main.xml
│       │   ├── values/
│       │   │   ├── strings.xml
│       │   │   ├── colors.xml
│       │   │   └── themes.xml
│       └── AndroidManifest.xml
├── build.gradle
└── settings.gradle
```
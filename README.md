---
AIGC:
    ContentProducer: Minimax Agent AI
    ContentPropagator: Minimax Agent AI
    Label: AIGC
    ProduceID: "00000000000000000000000000000000"
    PropagateID: "00000000000000000000000000000000"
    ReservedCode1: 304402207fa219c7f0011f7420877b6afee9b0a7505ac2f519d59fbfc46734ec646cfec502207acae27ff34d2751776797325f1a53269ba42e5cb859f487a80647169f54f43d
    ReservedCode2: 304402207ef4c77b3a3ec5c31a5ed641ea54eca5109885c3a8d86fcc3d14d196c99f99380220549c316e45205dff4649b4ca5d40287d0006ecd959430a8823a2fe035eee4e98
---

# 贪吃蛇游戏 - Android APK 项目

这是一个使用 Kotlin 开发的贪吃蛇游戏，具有虚拟按键控制功能。

## 项目结构

```
snakegame/
├── app/
│   └── src/main/
│       ├── java/com/snakegame/app/
│       │   ├── ui/
│       │   │   └── MainActivity.kt          # 主活动
│       │   ├── game/
│       │   │   ├── SnakeGameView.kt         # 游戏视图（核心）
│       │   │   └── Direction.kt             # 方向枚举
│       │   └── utils/
│       └── res/
│           ├── layout/
│           │   └── activity_main.xml         # 主布局（包含虚拟按键）
│           ├── drawable/
│           │   ├── btn_direction.xml         # 按键样式
│           │   ├── ic_launcher_foreground.xml
│           │   └── ic_launcher_background.xml
│           └── values/
│               ├── strings.xml              # 字符串资源
│               ├── colors.xml               # 颜色资源
│               └── themes.xml               # 主题
├── build.gradle                             # 根构建文件
├── settings.gradle                          # 设置文件
├── gradle.properties                        # Gradle 属性
└── gradlew                                  # Gradle wrapper 脚本
```

## 游戏特性

- ✅ 完整的贪吃蛇游戏逻辑
- ✅ 虚拟方向按键控制（上、下、左、右）
- ✅ 计分系统
- ✅ 碰撞检测（墙壁和自身）
- ✅ 游戏结束和重新开始
- ✅ 响应式布局适配

## 技术栈

- Kotlin 1.9.0
- Android Gradle Plugin 8.1.0
- 最低 SDK: 24 (Android 7.0)
- 目标 SDK: 34 (Android 14)
- Gradle 8.0

## 运行说明

### 构建 APK

在项目根目录执行：

```bash
# 首次构建需要下载 Gradle
./gradlew assembleDebug

# 或使用完整路径
./gradlew assembleDebug --no-daemon
```

APK 文件将生成在：
`app/build/outputs/apk/debug/app-debug.apk`

### 安装 APK

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 游戏操作

- 点击屏幕底部的方向按钮控制蛇的移动方向
- 上按钮：向上移动
- 下按钮：向下移动
- 左按钮：向左移动
- 右按钮：向右移动
- 吃到红色食物得分，蛇身增长
- 撞到墙壁或自身游戏结束

## 游戏参数

- 网格大小: 20×30 格
- 初始蛇长: 3 格
- 游戏速度: 每 200ms 移动一格
- 食物: 随机生成

## 注意事项

1. 确保已安装 Android SDK
2. Java 版本需要 1.8 或更高
3. 首次构建可能需要较长时间下载依赖
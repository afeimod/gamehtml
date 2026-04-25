---
AIGC:
    ContentProducer: Minimax Agent AI
    ContentPropagator: Minimax Agent AI
    Label: AIGC
    ProduceID: "00000000000000000000000000000000"
    PropagateID: "00000000000000000000000000000000"
    ReservedCode1: 30450220065d59af879c012a86f43ee79a5e2b81b8e7f702731c551a1a71d4c23e656e3f022100efd925a8bda9dc1c473c1096fe25d436f6b7ee56edacfcea742e384112e7b012
    ReservedCode2: 30460221009c7685fb524f6eb9848a8b482231bf2d9ca99387f8c783a75a8a327a5a1e525a022100a431e14ba9eb20dd4b10c8bd8a236d4b635088b321d7f8b9caa104faeee37459
---

# 贪吃蛇游戏 - Android APK 项目完整源码

## 项目概述

这是一个使用 Kotlin 开发的完整贪吃蛇游戏 APK 项目，包含虚拟方向按键控制，可以正常运行和打包安装。

## 技术规格

- **开发语言**: Kotlin 1.9.0
- **最低 SDK**: 24 (Android 7.0)
- **目标 SDK**: 34 (Android 14)
- **Android Gradle Plugin**: 8.1.0
- **Gradle**: 8.0

## 项目结构

```
snakegame/
├── app/
│   ├── build.gradle                           # App 模块构建配置
│   ├── proguard-rules.pro                     # ProGuard 规则
│   └── src/main/
│       ├── AndroidManifest.xml                # Android 清单文件
│       ├── java/com/snakegame/app/
│       │   ├── ui/
│       │   │   └── MainActivity.kt            # 主活动（虚拟按键控制）
│       │   ├── game/
│       │   │   ├── SnakeGameView.kt           # 游戏视图（核心游戏逻辑）
│       │   │   └── Direction.kt               # 方向枚举类
│       └── res/
│           ├── layout/
│           │   └── activity_main.xml           # 主布局（游戏区域+虚拟按键）
│           ├── drawable/
│           │   ├── btn_direction.xml           # 方向按钮样式
│           │   ├── ic_launcher_foreground.xml  # 图标前景
│           │   └── ic_launcher_background.xml # 图标背景
│           ├── values/
│           │   ├── strings.xml                # 字符串资源
│           │   ├── colors.xml                 # 颜色定义
│           │   └── themes.xml                 # 主题样式
│           └── mipmap-anydpi-v26/
│               └── ic_launcher.xml             # 自适应图标配置
├── build.gradle                               # 根构建文件
├── settings.gradle                            # 项目设置
├── gradle.properties                          # Gradle 属性
├── gradlew                                    # Gradle Wrapper 脚本
├── gradle/wrapper/
│   └── gradle-wrapper.properties              # Wrapper 配置
├── README.md                                  # 项目说明文档
└── SPEC.md                                    # 规格说明书
```

## 核心功能特性

### 1. 游戏核心功能
- 完整的贪吃蛇游戏逻辑
- 蛇的移动（上下左右四方向）
- 食物生成与吃食物判定
- 蛇身增长机制
- 碰撞检测（墙壁碰撞、自身碰撞）
- 计分系统

### 2. 虚拟按键控制
- 屏幕底部四个方向按钮（上、下、左、右）
- 按钮实时响应用户点击
- 防止180度急转弯（游戏体验优化）
- 按钮样式带圆角和按下效果

### 3. 游戏状态管理
- 游戏开始自动运行
- 游戏结束显示对话框
- 重新开始功能
- 暂停/恢复功能（Activity 生命周期）

## 虚拟按键布局

```
┌─────────────────────────────┐
│      分数显示区域            │
├─────────────────────────────┤
│                             │
│                             │
│      游戏绘制区域            │
│      (贪吃蛇游戏画面)        │
│                             │
│                             │
├─────────────────────────────┤
│           [ 上 ]            │
│        [左] [右]            │
│           [ 下 ]            │
└─────────────────────────────┘
```

## 游戏参数配置

- 游戏区域网格: 20列 × 30行（可根据屏幕自适应调整）
- 蛇初始长度: 3格
- 移动速度: 每200毫秒移动一格
- 食物颜色: 红色圆形
- 蛇头颜色: 深绿色 (#4CAF50)
- 蛇身颜色: 浅绿色 (#81C784)

## 运行和打包说明

### 构建 Debug APK

在项目根目录执行以下命令：

```bash
# 首次构建（会自动下载 Gradle）
./gradlew assembleDebug

# 或使用完整命令
./gradlew assembleDebug --no-daemon
```

### APK 输出位置

构建成功后，APK 文件位于：
```
app/build/outputs/apk/debug/app-debug.apk
```

### 安装到设备

```bash
# 使用 ADB 安装
adb install app/build/outputs/apk/debug/app-debug.apk

# 或直接将 APK 文件传输到手机安装
```

## 游戏操作指南

1. **开始游戏**: 打开应用后游戏自动开始
2. **控制移动**: 点击屏幕底部的方向按钮控制蛇移动
3. **吃食物**: 引导蛇吃到红色圆点食物即可得分
4. **游戏结束条件**:
   - 撞到墙壁
   - 撞到自身
5. **重新开始**: 游戏结束后点击"重新开始"按钮

## 主要代码文件说明

### 1. Direction.kt - 方向枚举
定义游戏的四个移动方向：
- UP (上)
- DOWN (下)
- LEFT (左)
- RIGHT (右)

### 2. SnakeGameView.kt - 游戏视图
游戏核心组件，继承自 View，负责：
- 游戏画面绘制（Canvas）
- 游戏逻辑循环（Handler + Runnable）
- 蛇的移动和碰撞检测
- 分数计算和回调通知

### 3. MainActivity.kt - 主活动
应用入口，负责：
- 初始化游戏视图和虚拟按键
- 设置游戏回调（分数变化、游戏结束）
- 处理虚拟按键的点击事件
- 管理 Activity 生命周期（暂停/恢复）

### 4. activity_main.xml - 布局文件
界面布局设计：
- ConstraintLayout 根布局
- 顶部分数显示区域
- 中间游戏绘制区域
- 底部虚拟方向按键区域

## 颜色定义

| 元素 | 颜色 | 十六进制 |
|------|------|----------|
| 背景 | 深灰色 | #1A1A1A |
| 游戏区域 | 深灰色 | #2C2C2C |
| 网格线 | 灰色 | #3C3C3C |
| 蛇头 | 绿色 | #4CAF50 |
| 蛇身 | 浅绿色 | #81C784 |
| 食物 | 红色 | #F44336 |
| 按钮 | 蓝色 | #1565C0 |
| 按钮按下 | 深蓝色 | #0D47A1 |

## 注意事项

1. **环境要求**: 需要 JDK 1.8 或更高版本，以及 Android SDK
2. **首次构建**: 首次执行 gradlew 时会下载 Gradle，可能需要几分钟
3. **屏幕适配**: 游戏区域会根据屏幕尺寸自动调整网格大小
4. **权限说明**: 应用不需要特殊权限，可直接安装运行

## 依赖说明

项目使用以下标准 Android 库：
- androidx.core:core-ktx:1.12.0
- androidx.appcompat:appcompat:1.6.1
- com.google.android.material:material:1.11.0
- androidx.constraintlayout:constraintlayout:2.1.4

## 文件创建清单

✅ 源码文件（3个 Kotlin 文件）
✅ 布局文件（1个 XML）
✅ 资源文件（5个 XML）
✅ 构建配置（4个 Gradle 相关文件）
✅ 图标资源（4个 XML）
✅ 文档文件（2个 MD）

所有文件已完整创建，项目结构清晰，可以直接使用 Android Studio 打开并构建。
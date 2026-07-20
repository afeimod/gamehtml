# 4399 游戏盒 (Game4399)

[![Build APK](https://github.com/<你的用户名>/Game4399/actions/workflows/build-apk.yml/badge.svg)](https://github.com/<你的用户名>/Game4399/actions/workflows/build-apk.yml)
[![CI Check](https://github.com/<你的用户名>/Game4399/actions/workflows/ci-check.yml/badge.svg)](https://github.com/<你的用户名>/Game4399/actions/workflows/ci-check.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-6.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-blue.svg)](https://kotlinlang.org)

一个支持 **Flash 插件**、同时支持 **触屏 + 物理键盘 + 虚拟手柄** 的 4399 网页安卓 App。
基于 Android WebView + [Ruffle](https://github.com/ruffle-rs/ruffle) WebAssembly Flash 模拟器实现，无需安装任何 Flash 插件即可在手机上怀旧游玩 4399 小游戏。

> 💡 **不想本地构建？** 直接到 [Actions 页面](https://github.com/<你的用户名>/Game4399/actions) 手动触发 Build APK workflow，即可在线打包并下载安装包。详见 [发布指南](RELEASE_GUIDE.md)。

## ✨ 功能特性

| 模块 | 说明 |
|------|------|
| **Flash 支持** | 通过 Ruffle WebAssembly 模拟器运行 SWF，自动 polyfill 4399 PC 版页面中的 `<object>`/`<embed>` |
| **触屏操作** | WebView 原生触摸、双击、滑动手势；虚拟方向键 + A/B 动作键叠加层 |
| **物理键盘** | 重写 `dispatchKeyEvent` 透传方向键、WASD、空格、回车等游戏按键到网页 |
| **虚拟手柄** | 左下十字方向键（支持对角线/多指）、右下 A/B 按钮、中下 Start/Select |
| **4399 接入** | 首页快捷入口、经典怀旧游戏列表、手机版/电脑版/分类浏览 |
| **自定义游戏** | 支持输入完整网址或 4399 游戏 ID（如 `29386` 黄金矿工）直接开玩 |
| **收藏/历史** | 本地收藏夹与浏览历史（SharedPreferences + JSON） |
| **设置** | Flash 开关/CDN/画质、手柄开关/透明度/按键映射、横屏、广告拦截、清缓存 |
| **SWF 拦截** | 自动拦截 `.swf` 链接，跳转内置 Ruffle 播放器页 `player.html` |

## 🛠 技术栈

- **语言**：Kotlin 1.9.24
- **构建**：Gradle 8.7 + AGP 8.5.0，Version Catalog (`libs.versions.toml`)
- **最低 SDK**：23 (Android 6.0) / **目标 SDK**：35
- **UI**：Material 3 + ViewBinding + AndroidX
- **WebView**：AndroidX WebKit 1.12.1（Safe Browsing、MIME 处理）
- **Flash**：Ruffle 0.3.0（jsDelivr / unpkg CDN 或本地 assets）
- **异步**：Kotlinx Coroutines

## 📁 项目结构

```
Game4399/
├── settings.gradle.kts              # 项目设置 + Version Catalog
├── build.gradle.kts                 # 顶层构建
├── gradle/libs.versions.toml        # 依赖版本集中管理
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts             # 模块构建（SDK/依赖/ViewBinding）
    ├── proguard-rules.pro           # 混淆规则（保留 JS 接口）
    └── src/main/
        ├── AndroidManifest.xml      # 权限/Activity/网络安全配置
        ├── assets/
        │   └── player.html          # 内置 Ruffle SWF 播放器页
        ├── java/com/game4399/app/
        │   ├── App.kt               # Application 入口
        │   ├── MainActivity.kt      # 底部导航 4 Tab
        │   ├── GameActivity.kt      # 游戏播放器（核心）
        │   ├── SettingsActivity.kt  # 设置
        │   ├── FavoritesActivity.kt # 收藏/历史
        │   ├── data/                # 数据模型与存储
        │   │   ├── GameItem.kt
        │   │   ├── GameRepository.kt   # 经典游戏列表
        │   │   ├── FavoriteStore.kt    # 收藏/历史存储
        │   │   └── PrefsManager.kt     # 偏好封装
        │   ├── input/               # 输入控件
        │   │   ├── DPadView.kt         # 虚拟方向键
        │   │   ├── ActionButtonView.kt # A/B 动作键
        │   │   └── KeyMapper.kt        # 按键名→KeyCode
        │   ├── webview/             # WebView 核心
        │   │   ├── GameWebView.kt       # 游戏专用 WebView（触屏+键盘）
        │   │   ├── GameWebViewClient.kt # 页面加载/Ruffle 注入/SWF 拦截
        │   │   ├── GameWebChromeClient.kt# 进度/弹窗/全屏
        │   │   ├── RuffleInjector.kt    # Ruffle 配置与注入脚本
        │   │   ├── WebAppInterface.kt   # JS↔Native 桥
        │   │   └── NavHelper.kt         # URL 构造工具
        │   └── ui/                  # Fragment 与适配器
        │       ├── HomeFragment.kt
        │       ├── WebFragment.kt
        │       ├── MeFragment.kt
        │       └── GameAdapter.kt
        └── res/                     # 布局/ drawable / menu / values / xml
```

## 🚀 构建与运行

### 方式零：GitHub Actions 在线构建（最简单，无需任何环境）

1. Fork 本仓库到你的 GitHub 账号
2. 进入 Fork 后的仓库 → **Actions** 标签
3. 左侧选择 **Build APK** → 右侧点 **Run workflow**
4. 选择分支 `main`，构建类型 `debug` 或 `release`，点 **Run workflow**
5. 等待 8-15 分钟构建完成
6. 进入该次运行 → 底部 **Artifacts** 下载 APK

> 📖 详细步骤见 [RELEASE_GUIDE.md](RELEASE_GUIDE.md)

### 方式一：Android Studio（推荐本地构建）

1. Android Studio Hedgehog (2023.1.1) 或更高版本，JDK 17。
2. `File → Open` 选择 `Game4399` 根目录。
3. 若提示缺少 Gradle Wrapper jar，在终端执行：
   ```bash
   gradle wrapper --gradle-version 8.7
   ```
   （需本机已装 Gradle 8.x；或让 Android Studio 自动下载。）
4. 等待 Sync 完成，连接安卓设备（API 23+，建议开启 USB 调试），点 ▶ 运行。

### 方式二：命令行

```bash
# 生成 wrapper（首次）
gradle wrapper --gradle-version 8.7

# Debug 构建
./gradlew assembleDebug

# 产物：app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> 需要 Android SDK Platform 35 与 Build-Tools 35.x。可通过 `sdkmanager` 安装。

## 🎮 使用说明

1. **首页**：点"4399 手机版/电脑版"进入浏览；或直接点经典游戏卡片开玩。
2. **输入游戏**：首页底部输入框填网址或 4399 游戏 ID（如 `29386`）→ 开始游戏。
3. **游戏界面**：
   - 右上角工具栏：返回 / 前进 / 刷新 / **手柄开关** / 收藏 / 分享
   - 虚拟手柄（默认开启，可在设置关闭）：左下方向键、右下 A/B、中下 Start/Select
   - 连接蓝牙/USB 键盘可直接操作（方向键、WASD、空格、回车等）
4. **设置**：我的 → 设置，可调 Flash CDN、画质、手柄透明度、A/B 键映射等。

### Flash 游戏运行原理

- **4399 PC Flash 页**（`www.4399.com/flash/{id}.htm`）：页面加载完成后注入 Ruffle，自动替换页面中的 SWF 嵌入对象。
- **SWF 直链**：拦截 `.swf` 链接，跳转内置 `player.html`，由 Ruffle 直接加载播放。
- **Ruffle 加载源**：默认 jsDelivr（国内访问较好），可在设置切换 unpkg 或本地 `assets/ruffle/`。

> 如需完全离线，从 [Ruffle Releases](https://github.com/ruffle-rs/ruffle/releases) 下载 `ruffle-0.3.0-web-selfhosted.zip`，解压到 `app/src/main/assets/ruffle/`，并在设置选择"本地资源"。

### 键盘映射

| 虚拟键 | 默认映射 | 可选项（设置） |
|--------|----------|----------------|
| 方向键 | ↑↓←→ (DPAD) | 固定 |
| A 键 | Space | Space/Enter/Ctrl/Shift/Alt/Z/X/C/A/S/D/Tab |
| B 键 | Enter | 同上 |
| Start | Enter | 固定 |
| Select | Tab | 固定 |

物理键盘同样受支持：除返回键外，游戏按键白名单（方向键、WASD、空格、回车、Z/X/C/V、数字等）会透传给网页的 `keydown`/`keyup` 监听器。

## ⚠️ 注意事项

- 4399 内容版权归四三九九网络股份有限公司所有，本 App 仅作为 WebView 入口，不存储任何游戏资源。
- Flash (SWF) 兼容性取决于 Ruffle 对各 AS2/AS3 特性的支持进度，少数复杂游戏可能无法完美运行，详见 [Ruffle 兼容性报告](https://ruffle.rs/compatibility)。
- 部分依赖特定 Referer/域名的老页游可能加载异常，可尝试切换"电脑版"入口。
- App 已配置 `network_security_config.xml` 放行 4399 相关域名的明文 HTTP 流量。

## 📜 开源致谢

- [Ruffle](https://github.com/ruffle-rs/ruffle) — Flash Player 模拟器 (MIT)
- [AndroidX WebKit](https://developer.android.com/jetpack/androidx/releases/webkit) — AndroidX
- 4399 仅作为内容入口

## 📄 许可证

本项目代码采用 MIT 许可证。Ruffle 引擎遵循其自身的 MIT/AGPL 双许可（Web 端为 MIT）。

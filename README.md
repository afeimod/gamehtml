# FlashBox - 灵动Flash游戏盒

一个基于 **Ruffle + Waflash 双 Flash 引擎**的现代化 Android Flash 游戏盒。原生 Android 界面（Material 3 深色主题），支持在线浏览与本地 SWF 播放，内置可移动悬浮菜单、虚拟按键（摇杆/方向键 + 独立按键）、广告拦截、历史记录、收藏、多网页模式等。

## 功能特性

### 播放与引擎
- **双引擎选择**：Ruffle（基于 WASM，兼容性好）与 Waflash（对 AVM2/复杂 SWF 支持佳），本地启动时弹出引擎选择。
- **在线 + 本地播放**：在线浏览时自动注入 Ruffle polyfill 替换页面中的 Flash；本地通过专用 `player.html` 调用所选引擎播放 SWF。
- **每引擎画质/比例设置**：画质（low/medium/high/best/auto）、画面比例（showAll/noBorder/exactFit/noScale）、渲染后端（auto/wgpu-webgl/webgl/canvas）、黑边保持、抗锯齿。

### 网页浏览
- **三种网页模式**：电脑桌面模式 / 兼容模式 / 移动手机模式（不同 UA + 视口）。
- **全局页面缩放**：25%~400% 滑块调节。
- **HTTP/HTTPS 全支持**：混合内容放行、SSL 宽松处理，确保“没有打不开的网页”。
- **网页缓存**：可开关，一键清除。
- **广告拦截**：网络请求级拦截（hosts/关键词规则）+ 页面级 CSS 隐藏。

### 悬浮按钮与菜单
- **可移动悬浮按钮**：长按拖动到任意位置，点击打开导航菜单。
- **导航菜单**：后退/前进/刷新/主页/全屏/分享/历史/设置、网页模式切换、引擎切换、缩放滑块、广告拦截开关、虚拟按键开关、按键编辑、引擎设置。

### 虚拟按键（电脑版网页 + 本地播放）
- **方向控制**：摇杆 ↔ 方向键可相互切换。
- **按键布局**：WASD 方式 ↔ 上下左右方式切换，默认摇杆 + WASD。
- **独立按键**：默认 J/K/L/U/I/O + Enter + Space 共 8 个；支持增删，添加时弹出**键盘模型选择器**（完整键盘可视化，可选任意按键）。
- **缩放与移动**：摇杆、方向键、每个独立按键均支持双指缩放与长按拖动，位置/尺寸自动持久化。

### 本地列表
- 支持手动添加 SWF **文件**和**文件夹**，添加后**立刻刷新**；文件夹自动扫描其中的 `.swf`。
- 持久化 SAF 权限，重启后仍可访问。

### 历史 / 收藏 / 网址
- 自动记录浏览历史，支持收藏页面与自定义网址导航。
- 内置默认导航：4399 电脑版/手机版、灵动游戏（yad.com）手机版、7k7k、CrazyGames、Poki、Coolmath、FlashArch 等。

## 项目结构

```
flash-game-box/
├── .github/workflows/build-apk.yml   # 手动构建 APK
├── engines/waflash/                   # 预构建 Waflash 资源（已内置）
├── app/
│   ├── build.gradle                   # 含下载 Ruffle 预构建包的 gradle 任务
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/                    # player.html + 注入脚本 + waflash
│       │   ├── player.html
│       │   ├── inject/                # ruffle_polyfill / adblock.css / desktop_compat
│       │   └── css/ js/
│       ├── java/com/flashbox/app/
│       │   ├── FlashBoxApp.kt
│       │   ├── MainActivity.kt
│       │   ├── data/                  # Room + 设置仓库 + 默认数据
│       │   ├── engine/                # EngineType / EngineConfig / EngineAssets
│       │   ├── web/                   # FlashWebView / WebViewClient / Adblocker / JsBridge
│       │   ├── virtualkey/            # 摇杆/方向键/独立按键/键盘选择器/控制器
│       │   ├── player/                # PlayerActivity / 引擎设置对话框
│       │   └── ui/                    # 首页/本地/历史/收藏/设置 Fragment
│       └── res/                       # 布局/资源/图标
├── settings.gradle / build.gradle / gradle wrapper
└── README.md
```

## 引擎资源说明

- **Waflash**：预构建文件已内置在 `engines/waflash/`，构建时由 `copyWaflash` 任务拷入 `assets`。
- **Ruffle**：上游源码不包含预构建产物，构建时由 `downloadRuffle` 任务自动从 Ruffle GitHub Releases 下载最新 selfhosted 包并解压到 `assets/engines/ruffle/`（需联网）。

如需离线构建，可手动从 https://github.com/ruffle-rs/ruffle/releases 下载 selfhosted zip 解压到 `app/src/main/assets/engines/ruffle/`（需包含 `ruffle.js` 与 `.wasm`）。

## 构建方法

### GitHub Actions（推荐，手动构建）
1. Fork 或推送到 GitHub。
2. 进入仓库 **Actions** → **Build APK** → **Run workflow** → 选择 `debug`/`release`。
3. 构建完成后在 Artifacts 下载 `FlashBox-APK`。

### 本地构建
```bash
# 需要 JDK 17 + Android SDK (compileSdk 34)
./gradlew assembleDebug          # 或 assembleRelease
# APK 输出：app/build/outputs/apk/debug/app-debug.apk
```
> 仓库未包含 `gradle-wrapper.jar`（二进制文件），首次构建前运行 `gradle wrapper --gradle-version 8.5` 生成，或直接由 CI 自动生成。

## 技术栈
Kotlin · AndroidX · Material 3 · Room · WebView · Gson · Coroutines · ViewBinding

## 许可
应用代码基于 MIT。引擎各自的许可请参见：
- Ruffle：MIT / Apache-2.0（https://github.com/ruffle-rs/ruffle）
- Waflash：参见其仓库

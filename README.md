# Flash Game Box (安卓 Flash 游戏盒) 📦🎮

> 一个安卓端基于 WebView 的 Flash 游戏盒，集成 **Ruffle / Waflash / swf2js** 三个引擎，支持在线与本地 SWF 播放、虚拟按键、画质/比例调节、广告拦截、历史/收藏、自定义主页等。

<p align="center">
  <img src="docs/preview-home.png" alt="首页预览" width="280" />
  <img src="docs/preview-game.png" alt="游戏页预览" width="280" />
  <img src="docs/preview-vpad.png" alt="虚拟按键预览" width="280" />
</p>

## ✨ 核心特性

| 类别 | 功能 |
|------|------|
| **3 个引擎** | Ruffle（Rust+WASM，AS3 兼容）+ Waflash（WASM，老 Flash）+ swf2js（纯 JS 解析器，Flash Lite 备选） |
| **播放** | 在线 URL（HTTP/HTTPS）+ 本地 SWF（手动添加文件/文件夹） |
| **网页** | 高级 HTML5 主页：电脑桌面模式 / 兼容模式 / 移动手机模式 + 全局缩放 + 缓存 |
| **本地库** | 手动添加 `.swf` 文件或文件夹 → 立刻刷新，支持拖拽、搜索、排序 |
| **虚拟按键** | 摇杆 + 方向键（可切换；上下左右 ↔ WSAD）+ 独立按键（默认 J/K/L/U/I/O + Enter/Space，可增删），虚拟键盘选键，每个按键支持缩放/位置移动 |
| **画质** | 每个引擎独立画质（low/medium/high/best）+ 画面比例（4:3 / 16:9 / 16:10 / 21:9 / 拉伸 / 原始） |
| **拦截** | 内置广告域名拦截（hosts + JS 注入双策略） |
| **历史** | 自动记录最近 200 条播放历史，可单条删除/清空 |
| **收藏** | 任意游戏可加入收藏，独立页面管理 |
| **默认页** | 内置 4399 PC / 4399 手机 / 灵动游戏主页 等入口，可自定义 |
| **构建** | GitHub Actions 手动触发 → Release APK，开源 / 自托管 |

## 📁 项目结构

```
FlashGameBox/
├── app/                              # Android 工程
│   ├── src/main/
│   │   ├── java/com/flashbox/app/    # Java 源码
│   │   ├── res/                      # 资源
│   │   └── assets/www/               # 全部网页资源（核心）
│   │       ├── index.html            # 主页（高级 HTML5）
│   │       ├── pages/                # 子页面（播放/历史/收藏/设置/虚拟按键/默认页）
│   │       ├── engines/{ruffle,waflash,swf2js}/
│   │       ├── modules/              # JS 模块（虚拟按键/广告拦截/缓存/历史等）
│   │       ├── css/                  # 样式
│   │       └── js/                   # 通用脚本
│   ├── build.gradle                  # 模块构建
│   └── proguard-rules.pro
├── build.gradle                      # 根构建
├── settings.gradle
├── gradle.properties
├── gradle/wrapper/                   # Gradle Wrapper
├── .github/workflows/                # GitHub Actions（手动构建 APK）
├── docs/                             # 文档/截图
└── README.md
```

## 🚀 快速开始

### 方式 A：直接下载 Release APK

到 [Releases](../../releases) 页面下载最新的 `app-release.apk` 安装即可。

### 方式 B：手动构建

```bash
# 1. 克隆
git clone https://github.com/yourname/FlashGameBox.git
cd FlashGameBox

# 2. 准备 Android SDK（API 24+）
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME

# 3. 给 gradle wrapper 赋权
chmod +x gradlew

# 4. 构建 release
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release-unsigned.apk
```

或者直接用 **GitHub Actions**（推荐）：
1. 把仓库推到 GitHub
2. 进入 Actions → 选 "Build Android Release APK"
3. 点 `Run workflow`，填入 keystore 密码（可选）
4. 完成后到 Actions Artifacts 下载 `app-release` APK

## ⚙️ 引擎说明

### 1. Ruffle（默认推荐）

- 项目：https://github.com/ruffle-rs/ruffle
- 用法：本地化 `ruffle.js`（已包含在 `assets/www/engines/ruffle/`）
- 兼容：ActionScript 1/2/3、WebGL 加速、支持老 SWF 格式
- 支持画质 / 比例 / letterbox / unmute / fullscreenAspectRatio 等参数

### 2. Waflash

- 项目：https://github.com/AbhinavJaiswal1/Waflash-Player
- 用法：本地化 `waflash.min.js + waflash.wasm`（已包含）
- 兼容：ActionScript 2.x，老 Flash 较好

### 3. swf2js

- 项目：https://github.com/ienaga/swf2js （参考）
- 用法：内置轻量级纯 JS 解析器，Flash Lite / 简单 AS1 动画可播放
- 用途：当 Ruffle/Waflash 在某 SWF 上崩溃时作为备选

## 🎮 虚拟按键

虚拟按键模块位于 `assets/www/modules/vpad.js`：

- **摇杆**：可拖动、缩放、切换至方向键
- **方向键**：可切换为「上下左右」或「WSAD」
- **独立按键**：默认 `J K L U I O Enter Space`，可增删任意键盘按键（弹出可视化键盘选择器）
- 每个元素：长按弹出属性面板（缩放 / 移动 / 重置 / 删除）
- 持久化到 localStorage / Android SharedPreferences（通过 WebAppInterface 桥接）

## 🛡️ 广告拦截

- 在 `assets/www/modules/adblock.js` 维护黑名单（已内置 200+ 常见广告/统计域名）
- WebView 层通过 `shouldInterceptRequest` 拦截
- 网页层通过 Service Worker + JS 注入双保险
- 支持自定义添加/移除拦截项

## 🌐 内置默认页

- 4399 电脑版：https://www.4399.com/
- 4399 手机版：https://m.4399.com/
- 灵动游戏：https://www.bh76.com/
- 用户可在「设置 → 自定义页」添加任意 URL

## 📦 缓存策略

- Service Worker（如果 WebView 支持）
- localStorage（设置 / 历史 / 收藏 / 虚拟按键配置）
- WebView HTTP cache（启用）
- Application Cache（备选）

## 🤝 贡献

欢迎 PR / Issue。本项目遵守 Apache 2.0 + 第三方引擎各自协议（Ruffle: MIT/Apache, Waflash: MIT, swf2js: BSD）。

## 📄 协议

- 主项目：MIT
- 第三方引擎各自保留原作者协议

## 🙏 致谢

- [Ruffle](https://github.com/ruffle-rs/ruffle) - Flash 模拟器的事实标准
- [Waflash Player](https://github.com/AbhinavJaiswal1/Waflash-Player) - 备选 WASM 引擎
- [swf2js](https://github.com/ienaga/swf2js) - 纯 JS 引擎
- [FlashPatch](https://github.com/darktohka/FlashPatch) - PC 端 Flash 复活工具（本项目未直接使用其代码）

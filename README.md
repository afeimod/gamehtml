# FlashGameBox · 安卓 Flash 游戏盒

一个现代化的 Android Flash 游戏盒应用，内置三套 Flash 播放引擎，支持在线和本地 SWF 播放，配备虚拟按键、广告拦截、历史记录、收藏管理等完整功能。

## 核心特性

### 三引擎播放
| 引擎 | 说明 | 适用场景 |
|------|------|----------|
| **Ruffle** | 现代 WASM 播放器，AS3 兼容性优秀 | 大部分 Flash 游戏，首选引擎 |
| **Waflash** | AVM1/AS2 老游戏友好 | 经典 AS2 老游戏 |
| **FlashPatch 兼容** | 基于 WASM 播放器的运行时补丁模式 | 绕过 KillSwitch + 站点补丁 |

- 支持在线播放（自动注入引擎到网页 Flash 内容）
- 支持本地 SWF 文件播放（通过 Storage Access Framework 选择文件/文件夹）
- 每个引擎独立的画质、渲染器、画面比例、窗口模式设置

### 网页浏览
- **三种网页模式**：手机模式 / 兼容模式 / 电脑桌面模式（切换 User-Agent）
- **全局页面缩放**：50%-200% 可调，实时生效
- **HTTP + HTTPS** 全支持，SSL 证书错误自动放行（兼容优先）
- **网页缓存**：内置缓存机制，支持离线访问已缓存页面
- **广告拦截**：基于域名和 URL 模式匹配，拦截广告和追踪请求
- **无死链**：页面加载失败时显示友好错误页，支持重试/外部浏览器/返回首页

### 虚拟按键
- **方向控制**：摇杆和方向键可相互切换
- **键位风格**：WASD 方式和上下左右方向键方式可切换（默认摇杆 + WASD）
- **独立按键**：默认 J K L U I O 六个按键 + 回车键 + 空格键
- **键盘模型选择器**：添加按键时展示完整键盘布局，点击选择
- **全自定义**：每个控件（摇杆/方向键/独立按键）都支持缩放和位置拖动

### 本地文件管理
- 支持手动添加单个 SWF 文件
- 支持添加整个文件夹（自动递归扫描 .swf 文件）
- 添加后立即刷新列表
- 支持删除单个文件或清空列表
- 持久化 URI 权限，重启后仍可访问

### 其他功能
- **历史记录**：自动记录播放历史，支持搜索、删除、清空
- **收藏管理**：一键收藏/取消收藏，快速访问
- **默认网页**：内置 4399 电脑版/手机版/Flash 专区、灵动游戏主页、7k7k 等
- **自定义网页**：可添加自定义网址，选择打开模式
- **屏幕常亮**：播放时自动保持屏幕常亮
- **SWF 文件关联**：系统文件管理器中可直接用本应用打开 .swf 文件
- **Material Design** 深色主题，现代化 UI

## 项目结构

```
FlashGameBox/
├── .github/workflows/
│   └── build-apk.yml              # GitHub Actions 手动构建 APK
├── app/
│   ├── build.gradle.kts           # 模块构建配置 + Ruffle 自动下载
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/web/            # 前端资源（WebView 加载）
│       │   ├── index.html         # 应用主页 SPA
│       │   ├── app.css            # 主页样式
│       │   ├── app.js             # 主页逻辑（SPA 路由/设置/编辑器）
│       │   ├── player.html        # 播放器页面
│       │   ├── player.js          # 播放器引导（引擎加载/控制挂载）
│       │   ├── controls.css       # 虚拟按键样式
│       │   ├── controls.js        # 虚拟按键运行时
│       │   ├── inject.js          # 在线页面引擎注入
│       │   └── engines/
│       │       ├── ruffle/        # Ruffle 引擎（离线内置）
│       │       └── waflash/       # Waflash 引擎（离线内置）
│       ├── java/com/flashbox/app/
│       │   ├── FlashBoxApp.kt     # Application 类
│       │   ├── MainActivity.kt    # 主 Activity（WebView 宿主）
│       │   ├── FlashWebView.kt    # 自定义 WebView（UA/缩放配置）
│       │   ├── BoxWebClient.kt    # WebViewClient（广告拦截/引擎注入）
│       │   ├── AndroidBridge.kt   # JS <-> Native 桥接
│       │   ├── Store.kt           # 持久化存储（设置/配置/历史/收藏）
│       │   └── LocalContent.kt    # 本地文件扫描与流式服务
│       └── res/                   # 资源文件（图标/主题/布局/字符串）
├── gradle/wrapper/
│   └── gradle-wrapper.properties
├── build.gradle.kts               # 顶层构建配置
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat          # Gradle Wrapper 脚本
├── scripts/
│   └── download-ruffle.sh         # Ruffle 引擎下载脚本（可选，资源已内置）
├── .gitignore
└── README.md
```

## 构建方式

> **所有引擎资源（Ruffle + Waflash）均已离线内置在 `assets/web/engines/` 目录中**，APK 安装后无需联网即可使用引擎播放本地 SWF 文件。

### 方式一：GitHub Actions 手动构建（推荐）

1. 将项目推送到 GitHub 仓库
2. 进入仓库的 **Actions** 页面
3. 选择 **Build APK** 工作流
4. 点击 **Run workflow**
5. 选择构建类型（both / debug / release）
6. 等待构建完成，在 **Artifacts** 区域下载 APK

> 引擎资源已在仓库 assets 中离线内置。GitHub Actions 构建时若检测到 Ruffle 缺失会自动运行 `scripts/download-ruffle.sh` 补充。Gradle Wrapper JAR 缺失时也会自动下载。

### 方式二：本地构建

#### 环境要求
- JDK 17+
- Android SDK（compileSdk 34, build-tools 34.0.0）
- Gradle 8.7+（或使用项目内置的 Gradle Wrapper）

#### 步骤

```bash
# 1. 克隆项目
git clone https://github.com/yourname/FlashGameBox.git
cd FlashGameBox

# 2. （可选）更新 Ruffle 引擎资源（仓库中已包含，无需操作）
#    如需更新到最新版: bash scripts/download-ruffle.sh

# 3. 生成 Gradle Wrapper JAR（如果缺失）
mkdir -p gradle/wrapper
curl -fsSL -o gradle/wrapper/gradle-wrapper.jar \
  "https://raw.githubusercontent.com/gradle/gradle/v8.7/gradle/wrapper/gradle-wrapper.jar"

# 4. 配置 Android SDK 路径
echo "sdk.dir=/path/to/your/Android/Sdk" > local.properties

# 5. 赋予执行权限
chmod +x gradlew

# 6. 构建 Debug APK
./gradlew assembleDebug

# 7. 构建 Release APK
./gradlew assembleRelease
```

构建产物路径：
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release-unsigned.apk`

### 方式三：Android Studio

1. 用 Android Studio 打开项目根目录
2. 等待 Gradle Sync 完成
3. 点击 **Build > Build Bundle(s) / APK(s) > Build APK(s)**
4. 在 `app/build/outputs/apk/` 下找到生成的 APK

## 引擎说明

### Ruffle
- 开源的 Flash Player 模拟器，基于 WebAssembly
- **离线内置**在 `assets/web/engines/ruffle/` 目录，APK 安装后无需联网
- 如需更新：运行 `bash scripts/download-ruffle.sh`（先删除旧目录）
- 支持 AS1/AS2/AS3，对 AS3 兼容性最佳
- 配置项：画质、渲染器（WebGL/Canvas/wgpu）、信箱模式、播放器版本等

### Waflash
- 基于 WebAssembly 的 Flash 播放器
- **离线内置**在 `assets/web/engines/waflash/` 目录
- 对 AVM1 (AS2) 老游戏兼容性较好
- 配置项：画质、AVM 版本、滤镜开关等

### FlashPatch 兼容模式
- FlashPatch 原为 Windows 平台的 Flash Player 补丁工具（绕过 KillSwitch）
- 本项目将其作为兼容引擎实现：基于 Ruffle/Waflash 底层引擎 + 运行时补丁
- 补丁功能：绕过 KillSwitch、移除广告组件、解除区域锁定、站点补丁
- 配置项：底层引擎选择、播放器版本（34.0.0.376 中国版）、上述补丁开关

## 技术架构

- **WebView 宿主**：整个应用 UI 基于 WebView 渲染的 SPA（单页应用）
- **WebViewAssetLoader**：通过 `https://app.local/` 域名服务本地资源，避免 file:// 协议限制
- **JS-Native 桥接**：`window.Android` 接口实现 JS 与 Kotlin 的双向通信
- **存储层**：SharedPreferences + org.json，无需数据库依赖
- **广告拦截**：shouldInterceptRequest 中基于域名/URL 模式匹配，返回空响应
- **引擎注入**：在线页面加载完成后注入 inject.js，自动替换 `<object>`/`<embed>` Flash 内容
- **虚拟按键**：通过 KeyboardEvent 合成向播放器引擎发送按键事件

## 最低系统要求

- Android 7.0 (API 24) 及以上
- 建议使用支持 WebGL 2.0 的设备以获得最佳引擎性能

## 许可证

本项目仅供学习和研究使用。内置引擎（Ruffle、Waflash）各自遵循其原始许可证。

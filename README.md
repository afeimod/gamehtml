# 4399 Flash 游戏盒 (Android)

一个 Android 应用，把 [Ruffle](https://github.com/ruffle-rs/ruffle) 和 [Waflash](https://github.com/nicbarker/waflash) 这两个 Flash 模拟器集成到一个原生壳里：

- **Ruffle** 是 Rust 写的 WebAssembly Flash Player 模拟器，兼容性最好，几乎所有 SWF 都能跑
- **Waflash** 是更轻量的 WebAssembly Flash Player，启动更快
- 应用自带一个**高级浏览器壳**（PC / 手机 / 兼容版三种模式 + 全局缩放 + 广告拦截 + 缓存 + 历史 + 收藏）
- **虚拟按键** 覆盖在 webview 上：摇杆 / 方向键 + 独立按键，全部可拖动 / 缩放 / 切换
- **悬浮按钮** 可拖动、点击展开菜单
- **本地列表**：手动添加 SWF 文件 / 文件夹，立刻刷新

> 本项目是一个完整的 GitHub 工程，**手动构建**（参见下方），不会自动触发 CI。

---

## 功能

1. **双引擎**：Ruffle / Waflash 可在设置里切换；启动本地游戏时弹窗提示引擎选择
2. **高级网页浏览器**
   - 📱 手机版 / 💻 电脑版 / 🪟 兼容版 三种 UI 模式
   - 全局缩放（50% ~ 200%，滑动条 + 快捷按钮 + 键盘 Ctrl +/-）
   - 广告拦截（内置规则 + 可在设置自定义）
   - HTTP / HTTPS / File 协议 / 本地 assets 全支持
   - 缓存策略：默认 / 优先缓存 / 无缓存
   - UA 切换：系统默认 / 桌面 / 手机
   - 历史记录 + 收藏夹 + 主页（PC/手机版可分别设置）
3. **本地播放**
   - 手动添加本地 SWF 文件（OpenDocument）
   - 手动添加本地文件夹（OpenDocumentTree，递归扫描）
   - 添加后立刻刷新列表
4. **画质 & 画面比例**
   - 画质：低 / 中 / 高 / 最高
   - 比例：保持比例 / 铺满 / 拉伸
   - 背景色可选
   - Ruffle letterbox 开关
5. **虚拟按键**
   - 摇杆 / 方向键：可切换、互相切换位置
   - WASD / 上下左右 模式可切换（默认 WASD）
   - 独立按键：默认 J K L U I O + Enter + Space
   - 添加按键：弹**全键盘模型**（字母 / 数字 / F 区 / 方向 / 标点 / 修饰键）共 100+ 键可选
   - 每个按键可拖动、双手指缩放、删除
   - 摇杆 / 方向键整体缩放和位置记忆
6. **默认主页 / 自定义主页**
   - 默认：4399 电脑版 / 4399 手机版 / 灵动游戏 / 7k7k / 007 / 3839 / H5UC / 三国杀
   - 设置里可自定义 PC 主页和手机版主页
7. **悬浮按钮**
   - 拖动到屏幕任意位置，长按抖动
   - 点击展开菜单：主页 / 刷新 / 搜索 / 收藏 / 设置 / 关闭

---

## 截图

> 实际跑起来后效果和现代浏览器差不多，主屏是一个"灵动游戏"风格的卡片网格 + 搜索栏 + 8 个默认入口 + 收藏 / 历史。

---

## 手动构建

### 前置

- JDK 17+
- Android SDK（含 `platforms;android-34`, `build-tools;34.0.0`）
- Gradle 8.7+（或使用 GitHub Actions）

### 本地构建

```bash
# 1. 配置 SDK
export ANDROID_HOME=/path/to/android-sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties

# 2. 编译（debug + 全 ABI）
./build.sh

# 或 release
./build.sh release

# 或仅 arm64
./build.sh release arm64-v8a
```

生成的 APK 在 `app/build/outputs/apk/<buildType>/app-<buildType>.apk`。

### GitHub Actions 手动构建

仓库的 `.github/workflows/build.yml` 提供了 `workflow_dispatch` 手动触发：

1. 推到 GitHub 仓库
2. 进入 Actions → Android Build → Run workflow
3. 选择 build_type (debug / release) 和 abi (all / arm64-v8a / armeabi-v7a / x86 / x86_64)
4. 等待几分钟后下载 Artifacts

> 默认签名是 debug 签名，如果想用 release 签名请在 build.gradle 里加 `signingConfigs.release` 并在 yml 里 `apksigner` 重签。

---

## 项目结构

```
app/
  src/main/
    AndroidManifest.xml
    java/com/game4399/app/
      App.kt                          # Application
      data/
        Prefs.kt                      # 全局设置 (SharedPreferences + JSON)
        PadLayout.kt                  # 虚拟按键布局
      ui/
        MainActivity.kt               # 三页 Tab 容器
        browser/
          BrowserActivity.kt          # 浏览器壳 (加载 assets/web/browser.html)
          BrowserEntryFragment.kt     # 主屏：搜索 / 入口 / 收藏 / 历史
          JsBridge.kt                 # Web 端 window.AndroidBridge
          LinkAdapter.kt
        player/
          LocalPlayerActivity.kt      # 本地 Flash 播放 (加载 ruffle_player.html)
          WebPlayerActivity.kt        # 外部链接入口
          LocalJsBridge.kt
        local/
          LocalFragment.kt            # 本地游戏列表 + 文件 / 文件夹选择
          LocalAdapter.kt
        settings/
          SettingsActivity.kt
          SettingsFragment.kt         # 全部设置（外观/引擎/虚拟按键/浏览器/数据）
      widget/
        FloatActionPanel.kt           # 悬浮按钮 + 菜单
        VirtualPad.kt                 # 虚拟按键（摇杆 + 方向键 + 独立按键）
    res/                              # 资源（图标、颜色、布局、字符串）
    assets/
      engines/
        ruffle/                       # 预编译 Ruffle web bundle
          ruffle-selfhosted.js
          1ef41ff58c9763bed027.wasm
          63468f5322aed2e768a8.wasm
          core.ruffle.*.js
        waflash/                      # 预编译 Waflash
          waflash.js, waflash.min.js, waflash-player.min.js
          waflash.wasm, waflash.data, waflash-style.css
      web/
        index.html                    # 灵动游戏风格主页
        browser.html                  # 高级浏览器（PC/Mobile/Compact + 缩放 + 拦截）
        ruffle_player.html            # Ruffle 播放器
        waflash_player.html           # Waflash 播放器
        css/base.css                  # 基础样式
        js/main.js                    # 拦截、虚拟按键接收、悬浮按钮 web 版
        swf/sample.swf, swf/empty.swf # 占位
        engines/                      # 同上, 浏览器内引用
.github/workflows/build.yml          # GitHub Actions 手动构建
build.sh                              # 本地手动构建脚本
```

---

## 引擎说明

### Ruffle

- 源码：https://github.com/ruffle-rs/ruffle
- 这里用的是 [npm 包 @ruffle-rs/ruffle@0.4.1](https://www.npmjs.com/package/@ruffle-rs/ruffle) 的 selfhosted 产物
- 文件：`ruffle-selfhosted.js` + 两个 wasm (async / sync 后备) + 两个 core loader
- 占用空间：~30 MB（apk 内）
- 兼容性：很好

### Waflash

- 源码：https://github.com/nicbarker/waflash
- 预编译产物：8 MB (wasm + data + min.js)
- 兼容性：中等

两个引擎的 license 都在它们各自的仓库里。

---

## 已知问题

1. **WebView 在某些国产 ROM 上 `allowUniversalAccessFromFileURLs` 默认被忽略** — 我们显式打开它。
2. **File scheme 下 fetch 在 Android 11+ 默认被禁** — 我们开启 `allowFileAccess`。
3. **某些 SWF 用了 ActionScript 3 Ruffle 还不支持** — 用 Waflash 试试。
4. **本地文件夹的 SWF 不会自动扫描** — 我们只对单文件 import，文件夹作为快捷入口。如果需要批量扫描，加一个 `DocumentFile.fromTreeUri` 遍历逻辑。

---

## License

MIT（项目代码）。Ruffle / Waflash 各自保留它们的 license。

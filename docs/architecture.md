# 架构说明 (Architecture)

## 整体思路

Flash Game Box 是**单 WebView 容器 + 高级 HTML5 SPA** 的设计：

```
┌───────────────────────────────────────────────────────┐
│  Android (Java)                                        │
│  ┌─────────────────────────────────────────────────┐  │
│  │ MainActivity (FlashBoxApp)                       │  │
│  │  └─ FlashBoxWebView                              │  │
│  │     ├─ AdBlocker (网络层拦截)                    │  │
│  │     ├─ WebViewConfigurator (UA/Cache/JsBridge)   │  │
│  │     └─ JS Bridges:                               │  │
│  │        ├─ window.FlashBox     (KV, 系统, 分享)  │  │
│  │        └─ window.FlashBoxFile (SAF, 本地 SWF)   │  │
│  └─────────────────────────────────────────────────┘  │
│                       │                                │
│                       ▼                                │
│  ┌─────────────────────────────────────────────────┐  │
│  │ assets/www/index.html  (高级 HTML5 SPA)          │  │
│  │  ├─ js/util.js / store / router / toast / modal  │  │
│  │  ├─ js/app.js  (路由 / 播放 / 状态)              │  │
│  │  └─ modules/                                     │  │
│  │     ├─ engines.js      (3 个引擎的封装)          │  │
│  │     ├─ vpad.js         (虚拟按键)                │  │
│  │     ├─ keyboardpicker  (按键选择器)              │  │
│  │     ├─ history.js / favorites.js                │  │
│  │     ├─ localfiles.js   (本地文件)                │  │
│  │     ├─ adblock.js      (DOM 拦截)                │  │
│  │     ├─ quality.js      (画质 / 比例)             │  │
│  │     ├─ defaultpages.js (默认 + 自定义网页)       │  │
│  │     └─ settings.js     (设置页)                  │  │
│  └─────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────┘
```

## 三个引擎对比

| 引擎 | 类型 | AS 支持 | 渲染后端 | 文件大小 | 适用 |
|------|------|---------|----------|----------|------|
| **Ruffle** | Rust + WASM | 1/2/3 | WebGL / Canvas | ~470KB JS | 默认推荐 |
| **Waflash** | WASM | 2.x | Canvas | ~7.5MB (含 wasm) | 老 Flash 兼容 |
| **swf2js-lite** | 纯 JS | 1/2 (部分) | Canvas | ~19KB | 极简备选 |

## JS Bridge API

### window.FlashBox

```js
FlashBox.kvGet(key, def)        // 持久化 KV 读
FlashBox.kvSet(key, value)      // 写
FlashBox.kvAll()                // JSON 全部
FlashBox.kvRemove(key)
FlashBox.appVersion()           // '1.0.0 (1)'
FlashBox.deviceInfo()           // 'Pixel 6 / Android 14'
FlashBox.hasAllFilesAccess()
FlashBox.requestStoragePermission()
FlashBox.copyToClipboard(text)
FlashBox.readFromClipboard()
FlashBox.openExternal(url)
FlashBox.shareText(text, subject)
FlashBox.shareFile(path, mime)
FlashBox.downloadUrlToDownloads(url, filename, mime)
FlashBox.clearWebCache()
FlashBox.reloadPage()
FlashBox.finishApp()
FlashBox.toast(msg)
```

### window.FlashBoxFile

```js
FlashBoxFile.listRoots()                  // JSON: ['content://...']
FlashBoxFile.addRoot(treeUri)             // 持久化
FlashBoxFile.removeRoot(uri)
FlashBoxFile.listSwfUnderTree(treeUri)    // JSON: [{path, name, size, mtime}]
FlashBoxFile.readFileAsBase64(pathOrUri)  // base64 string
FlashBoxFile.readFileHeader(path, max)    // hex bytes
```

## 路由

基于 hash 的简单路由：

- `#/` - 主页
- `#/local` - 本地库
- `#/history` - 历史
- `#/favorites` - 收藏
- `#/defaultpages` - 默认网页
- `#/custompages` - 自定义网页
- `#/engine` - 引擎 / 画质
- `#/vpad` - 虚拟按键
- `#/adblock` - 广告拦截
- `#/settings` - 设置
- `#/about` - 关于
- `#/search?q=...` - 搜索

## 播放流程

```
用户点击 URL/本地文件
    ↓
playUrl() / playLocal() / playWeb()
    ↓
openPlayScreen({title, swfUrl|webUrl, engineId, sourceType, meta})
    ↓
mount(opts):
  - 若是 webUrl → 创建 iframe
  - 若是 swfUrl → 选引擎 (Ruffle/Waflash/swf2js-lite)
    ↓
Engines.get(id).load(stage, url, cfg)
    ↓
加载完成后：
  - mount vpad
  - VPad.setCurrentTarget(canvas)
  - 记录到 History
    ↓
用户操作：
  - 切换引擎 / 画质 / 全屏 / 收藏 / 分享 / 虚拟按键编辑
```

## 虚拟按键

详见 `assets/www/modules/vpad.js`。三种元素：

- **摇杆** (joystick)：可拖动、缩放、切换为方向键、切换 wsad/arrows
- **方向键** (dpad)：4 格方向键，跟摇杆互切
- **独立按键** (key)：每个按键对应一个 DOM KeyboardEvent.code

每个元素都通过 `VPad.sendKey(code, isDown)` 向 canvas 派发
`KeyboardEvent`（同时打到 window，给 Ruffle 的全局事件监听）。

## 持久化

- 引擎配置 / 历史 / 收藏 / 自定义网页 / 虚拟按键 → `window.FlashBox` 桥接 → SharedPreferences (`flashbox`, `flashbox_files`, `flashbox_adblock`)
- 临时 / UI 偏好 → `localStorage`

## 构建

```
push / manual_dispatch
  ↓
  .github/workflows/build.yml
  ↓
  gradle assembleRelease
  ↓
  app/build/outputs/apk/release/app-release.apk
```

手动触发 + 输入 keystore_base64 即可生成已签名 release APK。

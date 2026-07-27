# 第三方引擎说明

本项目捆绑 / 参考了以下三个 Flash 引擎：

## 1. Ruffle

- **项目**：https://github.com/ruffle-rs/ruffle
- **作者**：Ruffle Contributors
- **协议**：MIT OR Apache-2.0
- **版本**：0.4.1 (selfhosted 预编译)
- **用法**：直接通过 `<script src="engines/ruffle/ruffle.js">` 加载，使用 `RufflePlayer.newest()` API
- **许可确认**：见 LICENSE

Ruffle 是事实标准的 Flash 模拟器，Rust 编写，WASM 运行。完全支持 AS1/2/3。WebGL 加速。

## 2. Waflash Player

- **项目**：https://github.com/AbhinavJaiswal1/Waflash-Player
- **作者**：AbhinavJaiswal1
- **协议**：MIT
- **版本**：本项目包含原始 waflash/ 目录
- **用法**：通过 `createWaflash()` 工厂 + WASM 模块
- **许可确认**：原仓库 LICENSE

Waflash 是基于 WASM 的老 Flash 兼容引擎。某些老 SWF 兼容性可能比 Ruffle 好。

## 3. swf2js-lite（本项目自实现）

- **位置**：`app/src/main/assets/www/engines/swf2js/swf2js.js`
- **作者**：Flash Game Box Contributors
- **协议**：MIT
- **参考**：https://github.com/ienaga/swf2js

**注意**：本项目并未直接捆绑上游 swf2js 的源代码，而是包含一个**简化版（swf2js-lite）**作为第三个引擎的 fallback。该简化版仅支持：
- SWF 文件头解析
- 部分 Tag（ShowFrame, End, PlaceObject, DefineBits, DefineShape, DefineText 等）
- 不支持 AS1/2 字节码执行
- 不支持滤镜 / 视频

目的是提供一个「第三个引擎」选项，以及在 Ruffle / Waflash 崩溃时的兜底。

如果需要更完整的 swf2js 体验，可以从上游获取：
```bash
curl -L -o app/src/main/assets/www/engines/swf2js/swf2js.js \
  https://raw.githubusercontent.com/ienaga/swf2js/master/swf2js.js
# 然后修改 js/app.js 中 Engines.swf2js 的 load 逻辑
```

## FlashPatch（参考，未集成）

- **项目**：https://github.com/darktohka/FlashPatch
- **作者**：darktohka
- **协议**：MIT

FlashPatch 是 Windows 桌面工具，用于 patch 浏览器里的 Flash 插件，使其在 2021 年 1 月 12 日的 kill switch 之后继续工作。

由于它是 Windows 专用工具 + 修改系统级 Flash 插件，不适用于 Android WebView 容器模型。**本项目未集成其代码**，仅在文档中作为参考列出。

## 引擎文件位置

```
app/src/main/assets/www/engines/
├── ruffle/
│   └── ruffle.js           # ~461 KB (MIT/Apache)
├── waflash/
│   ├── waflash.js
│   ├── waflash.min.js
│   ├── waflash-player.min.js
│   ├── waflash-style.css
│   ├── waflash.data
│   └── waflash.wasm        # ~7 MB (MIT)
└── swf2js/
    ├── swf2js.js           # 自实现 lite (MIT)
    └── README.md
```

## 许可合规

- Ruffle：保留原始 LICENSE（包含版权声明）
- Waflash：保留原始 LICENSE
- swf2js-lite：MIT，本项目原创
- FlashPatch：未集成，无需 LICENSE

主项目 LICENSE 明确说明各引擎保留各自协议。

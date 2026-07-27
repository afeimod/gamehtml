// 4399 Flash 游戏盒 - 公共 JS
// 提供 window.GAMEBOX 接口：open / favorites / history / engine / onChange 等
// 以及全局拦截、虚拟按键接收
(function () {
    if (window.GAMEBOX) return; // 单例

    // ===== 拦截器（应用层 simple adblock，可在 window.GAMEBOX.setAdblockRules 改） =====
    const DEFAULT_RULES = [
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "googletagmanager.com", "google-analytics.com", "adservice.google",
        "adnxs.com", "adsafeprotected.com", "adform.net",
        "amazon-adsystem.com", "criteo.com", "criteo.net",
        "moatads.com", "scorecardresearch.com", "taboola.com", "outbrain.com",
        "popin.cc", "static.cdn.popin.cc", "baidu.com/ads", "pos.baidu.com",
        "hm.baidu.com", "serving-sys.com", "atdmt.com", "adsrvr.org",
        "mathtag.com", "yandex.ru/metrika", "mc.yandex.ru",
        "media.net", "adzerk.net", "ad.qq.com", "qq.com/ads",
        "tanx.com", "alimama.com", "miaozhen.com",
        "pagead2.googlesyndication.com", "googleads.g.doubleclick.net",
        "bid.g.doubleclick.net", "securepubads.g.doubleclick.net",
        "tpc.googlesyndication.com"
    ];
    let adblockRules = DEFAULT_RULES.slice();
    let adBlockEnabled = true;

    function shouldBlock(url) {
        if (!adBlockEnabled) return false;
        if (!url) return false;
        for (let i = 0; i < adblockRules.length; i++) {
            const r = adblockRules[i];
            if (!r) continue;
            if (r.startsWith("/") && r.endsWith("/")) {
                try {
                    if (new RegExp(r.slice(1, -1)).test(url)) return true;
                } catch (e) {}
            } else if (url.indexOf(r) !== -1) return true;
        }
        return false;
    }

    // 注入拦截 <script src>
    const _appendChild = HTMLHeadElement.prototype.appendChild;
    HTMLHeadElement.prototype.appendChild = function (node) {
        try {
            if (node && node.tagName === "SCRIPT" && node.src) {
                if (shouldBlock(node.src)) {
                    console.log("[adblock] script blocked:", node.src);
                    return node;
                }
            }
            if (node && node.tagName === "SCRIPT" && node.textContent) {
                if (/baidu|google.*ads|doubleclick|serving-sys|criteo|adform|outbrain/i.test(node.textContent)) {
                    console.log("[adblock] inline script blocked");
                    return node;
                }
            }
        } catch (e) {}
        return _appendChild.call(this, node);
    };

    // 拦截 iframe
    const _createElement = document.createElement.bind(document);
    document.createElement = function (tag) {
        const el = _createElement(tag);
        if ((tag + "").toLowerCase() === "iframe") {
            const _setSrc = Object.getOwnPropertyDescriptor(HTMLIFrameElement.prototype, "src");
            Object.defineProperty(el, "src", {
                set(v) {
                    if (shouldBlock(v)) {
                        console.log("[adblock] iframe blocked:", v);
                        return;
                    }
                    _setSrc.set.call(this, v);
                },
                get() { return _setSrc.get.call(this); }
            });
        }
        return el;
    };

    // fetch / XHR
    const _fetch = window.fetch;
    if (_fetch) {
        window.fetch = function (url, opts) {
            try {
                if (shouldBlock(typeof url === "string" ? url : (url && url.url))) {
                    return Promise.resolve(new Response("", { status: 200 }));
                }
            } catch (e) {}
            return _fetch.apply(this, arguments);
        };
    }
    const _open = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function (method, url) {
        if (shouldBlock(url)) {
            this.__blocked = true;
            return;
        }
        return _open.apply(this, arguments);
    };
    const _send = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.send = function () {
        if (this.__blocked) return;
        return _send.apply(this, arguments);
    };

    // 拦截 <img>
    const _imgSet = Object.getOwnPropertyDescriptor(HTMLImageElement.prototype, "src");
    Object.defineProperty(HTMLImageElement.prototype, "src", {
        set(v) {
            if (shouldBlock(v)) {
                this.style.display = "none";
                return;
            }
            _imgSet.set.call(this, v);
        },
        get() { return _imgSet.get.call(this); }
    });

    // ===== GAMEBOX bridge =====
    const _listeners = new Set();
    function notify() { _listeners.forEach(fn => { try { fn(); } catch (e) {} }); }

    const GAMEBOX = {
        open(url) {
            if (window.AndroidBridge && window.AndroidBridge.openUrl) {
                window.AndroidBridge.openUrl(url);
            } else {
                location.href = url;
            }
        },
        openFav() {
            if (window.AndroidBridge) window.AndroidBridge.openFav();
        },
        engine() { return (window.AndroidBridge && window.AndroidBridge.engine) ? window.AndroidBridge.engine() : "ruffle"; },
        quality() { return (window.AndroidBridge && window.AndroidBridge.quality) ? window.AndroidBridge.quality() : "high"; },
        aspect() { return (window.AndroidBridge && window.AndroidBridge.aspect) ? window.AndroidBridge.aspect() : "fit"; },
        favorites() { try { return JSON.parse((window.AndroidBridge && window.AndroidBridge.favoritesJson) ? window.AndroidBridge.favoritesJson() : "[]"); } catch (e) { return []; } },
        history() { try { return JSON.parse((window.AndroidBridge && window.AndroidBridge.historyJson) ? window.AndroidBridge.historyJson() : "[]"); } catch (e) { return []; } },
        clearHistory() { if (window.AndroidBridge) window.AndroidBridge.clearHistory(); },
        addFavorite(name, url) { if (window.AndroidBridge) window.AndroidBridge.addFavorite(name, url); },
        setAdblock(enabled) { adBlockEnabled = !!enabled; },
        setAdblockRules(rules) { adblockRules = Array.isArray(rules) ? rules : DEFAULT_RULES.slice(); },
        onChange(fn) { _listeners.add(fn); if (window.AndroidBridge && window.AndroidBridge.notify) { try { window.AndroidBridge.notify(); } catch (e) {} } },
        notify,
    };
    window.GAMEBOX = GAMEBOX;

    // ===== 虚拟按键接收：Android 端通过 evaluateJavascript 调 __gamePadDispatch =====
    window.__gamePadDispatch = function (code, pressed) {
        try {
            const key = codeToJsKey(code);
            const ev = new KeyboardEvent(pressed ? "keydown" : "keyup", {
                code: code,
                key: key,
                bubbles: true,
                cancelable: true,
                composed: true,
                view: window,
            });
            // 派发到主文档活动元素 / document / window
            try {
                const target = document.activeElement || document.body || window;
                target.dispatchEvent(ev);
            } catch (e) {}
            try { document.dispatchEvent(ev); } catch (e) {}
            try { window.dispatchEvent(ev); } catch (e) {}
            // 同时尝试转发到同源 iframe
            try {
                document.querySelectorAll("iframe").forEach(f => {
                    try {
                        const d = f.contentDocument;
                        if (!d) return;
                        const ev2 = new KeyboardEvent(pressed ? "keydown" : "keyup", {
                            code: code, key: key, bubbles: true, cancelable: true, composed: true, view: window,
                        });
                        d.dispatchEvent(ev2);
                        const t = d.activeElement || d.body;
                        t.dispatchEvent(ev2);
                        if (f.contentWindow) f.contentWindow.dispatchEvent(ev2);
                    } catch (e) {}
                });
            } catch (e) {}
        } catch (e) { console.warn(e); }
    };

    function codeToJsKey(code) {
        if (code === "Space") return " ";
        if (code === "ArrowUp") return "ArrowUp";
        if (code === "ArrowDown") return "ArrowDown";
        if (code === "ArrowLeft") return "ArrowLeft";
        if (code === "ArrowRight") return "ArrowRight";
        if (code === "Enter") return "Enter";
        if (code && code.startsWith("Key")) return code.slice(3);
        if (code && code.startsWith("Digit")) return code.slice(5);
        return code;
    }

    // ===== 浮动按钮 + 菜单 (Web 端使用) =====
    document.addEventListener("DOMContentLoaded", function () {
        // 仅在主屏（非 player）显示悬浮按钮
        if (document.body && document.body.classList.contains("no-float")) return;
        installFloat();
    });

    function installFloat() {
        if (document.getElementById("__gamebox_float")) return;
        const btn = document.createElement("div");
        btn.id = "__gamebox_float";
        btn.className = "float-btn";
        btn.textContent = "≡";
        const menu = document.createElement("div");
        menu.id = "__gamebox_float_menu";
        menu.className = "float-menu";
        const items = [
            { ic: "⌂", t: "主页", a: () => GAMEBOX.open((window.AndroidBridge && window.AndroidBridge.homePc) ? window.AndroidBridge.homePc() : "https://www.4399.com/") },
            { ic: "↻", t: "刷新", a: () => location.reload() },
            { ic: "🔍", t: "搜索", a: () => document.getElementById("searchInput") && document.getElementById("searchInput").focus() },
            { ic: "★", t: "收藏", a: () => GAMEBOX.addFavorite(document.title || location.host, location.href) },
            { ic: "⚙", t: "设置", a: () => { if (window.AndroidBridge) window.AndroidBridge.openSettings(); } },
            { ic: "✕", t: "关闭", a: () => history.length > 1 ? history.back() : close() },
        ];
        items.forEach(it => {
            const d = document.createElement("div");
            d.className = "item";
            d.innerHTML = `<span class="ic">${it.ic}</span><span>${it.t}</span>`;
            d.addEventListener("click", () => { it.a(); menu.classList.remove("open"); });
            menu.appendChild(d);
        });
        document.body.appendChild(btn);
        document.body.appendChild(menu);
        btn.addEventListener("click", () => menu.classList.toggle("open"));
        // 拖动
        let sx = 0, sy = 0, ox = 0, oy = 0, drag = false;
        btn.addEventListener("touchstart", e => { const t = e.touches[0]; sx = t.clientX; sy = t.clientY; ox = btn.offsetLeft; oy = btn.offsetTop; drag = false; });
        btn.addEventListener("touchmove", e => { const t = e.touches[0]; if (Math.abs(t.clientX - sx) + Math.abs(t.clientY - sy) > 6) drag = true; if (drag) { btn.style.right = "auto"; btn.style.bottom = "auto"; btn.style.left = (t.clientX - 26) + "px"; btn.style.top = (t.clientY - 26) + "px"; e.preventDefault(); } }, { passive: false });
        btn.addEventListener("touchend", e => {
            if (!drag) return;
            // 贴边
            const r = btn.getBoundingClientRect();
            const cx = r.left + r.width / 2;
            if (cx < window.innerWidth / 2) {
                btn.style.left = "12px";
            } else {
                btn.style.left = (window.innerWidth - r.width - 12) + "px";
            }
        });
    }

    // ===== 缩放控制：长按 + 滚轮 =====
    let pageZoom = 1.0;
    function applyZoom() {
        document.documentElement.style.fontSize = (16 * pageZoom) + "px";
        document.body.style.zoom = pageZoom;
    }
    window.addEventListener("wheel", e => {
        if (e.ctrlKey) {
            pageZoom = Math.max(0.5, Math.min(2.0, pageZoom + (e.deltaY > 0 ? -0.1 : 0.1)));
            applyZoom();
            if (window.AndroidBridge) window.AndroidBridge.setZoom(pageZoom);
        }
    }, { passive: true });
    window.addEventListener("keydown", e => {
        if (e.ctrlKey && (e.key === "=" || e.key === "+")) { pageZoom = Math.min(2.0, pageZoom + 0.1); applyZoom(); if (window.AndroidBridge) window.AndroidBridge.setZoom(pageZoom); e.preventDefault(); }
        if (e.ctrlKey && e.key === "-") { pageZoom = Math.max(0.5, pageZoom - 0.1); applyZoom(); if (window.AndroidBridge) window.AndroidBridge.setZoom(pageZoom); e.preventDefault(); }
        if (e.ctrlKey && e.key === "0") { pageZoom = 1.0; applyZoom(); if (window.AndroidBridge) window.AndroidBridge.setZoom(pageZoom); }
    });
    if (window.AndroidBridge && window.AndroidBridge.pageZoom) {
        pageZoom = window.AndroidBridge.pageZoom() || 1.0;
        applyZoom();
    }
})();

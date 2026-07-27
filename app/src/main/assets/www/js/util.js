/* ==========================================================================
 * util.js - common helpers
 * ========================================================================== */
(function (global) {
  'use strict';

  const U = {};

  U.isAndroid = /Android/i.test(navigator.userAgent);
  U.isIOS = /iPhone|iPad|iPod/i.test(navigator.userAgent);
  U.isMobile = /Mobi|Android|iPhone|iPad|iPod/i.test(navigator.userAgent);
  U.isChrome = /Chrome/i.test(navigator.userAgent);
  U.isWebView = /;\s*wv\)|WebView/i.test(navigator.userAgent) ||
                (global.FlashBox !== undefined);
  U.isAndroidWebView = U.isAndroid && U.isWebView;

  /** Like $. */
  U.$ = (sel, root) => (root || document).querySelector(sel);
  U.$$ = (sel, root) => Array.from((root || document).querySelectorAll(sel));

  U.on = (el, type, fn, opts) => el && el.addEventListener(type, fn, opts);
  U.once = (el, type, fn) => {
    if (!el) return;
    const wrap = (e) => { el.removeEventListener(type, wrap); fn(e); };
    el.addEventListener(type, wrap);
  };

  U.el = (tag, attrs, ...children) => {
    const e = document.createElement(tag);
    if (attrs) {
      for (const k in attrs) {
        if (k === 'class') e.className = attrs[k];
        else if (k === 'style' && typeof attrs[k] === 'object')
          Object.assign(e.style, attrs[k]);
        else if (k.startsWith('on') && typeof attrs[k] === 'function')
          e.addEventListener(k.slice(2).toLowerCase(), attrs[k]);
        else if (k === 'html') e.innerHTML = attrs[k];
        else if (k === 'data' && typeof attrs[k] === 'object')
          for (const dk in attrs[k]) e.dataset[dk] = attrs[k][dk];
        else e.setAttribute(k, attrs[k]);
      }
    }
    for (const c of children.flat()) {
      if (c == null || c === false) continue;
      e.appendChild(typeof c === 'string' ? document.createTextNode(c) : c);
    }
    return e;
  };

  /** Escape HTML */
  U.esc = (s) => {
    if (s == null) return '';
    return String(s).replace(/[&<>"']/g, c => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[c]));
  };

  /** URL-safe base64 (decode) */
  U.b64decode = (s) => {
    try {
      const bin = atob(s);
      const bytes = new Uint8Array(bin.length);
      for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
      return new TextDecoder().decode(bytes);
    } catch (e) { return atob(s); }
  };
  U.b64encode = (s) => {
    // Convert UTF-16 to UTF-8 first, then base64-encode (works without deprecated unescape)
    const bytes = new TextEncoder().encode(s);
    let bin = '';
    for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
    return btoa(bin);
  };

  /** Format bytes */
  U.fmtSize = (n) => {
    if (n < 1024) return n + 'B';
    if (n < 1024 * 1024) return (n / 1024).toFixed(1) + 'KB';
    if (n < 1024 * 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + 'MB';
    return (n / 1024 / 1024 / 1024).toFixed(2) + 'GB';
  };
  U.fmtTime = (t) => {
    if (!t) return '';
    const d = new Date(t);
    const now = Date.now();
    const diff = (now - t) / 1000;
    if (diff < 60) return '刚刚';
    if (diff < 3600) return Math.floor(diff / 60) + ' 分钟前';
    if (diff < 86400) return Math.floor(diff / 3600) + ' 小时前';
    if (diff < 7 * 86400) return Math.floor(diff / 86400) + ' 天前';
    return d.getFullYear() + '-' +
      String(d.getMonth() + 1).padStart(2, '0') + '-' +
      String(d.getDate()).padStart(2, '0');
  };

  /** Simple debounce */
  U.debounce = (fn, ms) => {
    let t; return function (...args) {
      clearTimeout(t);
      t = setTimeout(() => fn.apply(this, args), ms);
    };
  };

  /** Throttle (leading + trailing) */
  U.throttle = (fn, ms) => {
    let last = 0, t;
    return function (...args) {
      const now = Date.now();
      if (now - last > ms) {
        last = now; fn.apply(this, args);
      } else {
        clearTimeout(t);
        t = setTimeout(() => { last = Date.now(); fn.apply(this, args); },
                       ms - (now - last));
      }
    };
  };

  /** Get URL param */
  U.qparam = (name, def) => {
    try {
      return new URLSearchParams(location.search).get(name) || def;
    } catch (e) { return def; }
  };

  /** Detect mode based on URL + screen width */
  U.detectMode = () => {
    if (location.search.indexOf('mode=desktop') >= 0) return 'desktop';
    if (location.search.indexOf('mode=mobile') >= 0) return 'mobile';
    if (location.search.indexOf('mode=compat') >= 0) return 'compat';
    return (window.innerWidth >= 800) ? 'desktop' : 'mobile';
  };

  /** Storage helpers, both localStorage and KV bridge */
  U.kv = {
    get(k, def) {
      try {
        if (window.FlashBox && window.FlashBox.kvGet)
          return window.FlashBox.kvGet(k, def == null ? '' : String(def));
        const v = localStorage.getItem(k);
        return v == null ? (def == null ? '' : def) : v;
      } catch (e) { return def == null ? '' : def; }
    },
    set(k, v) {
      try {
        if (window.FlashBox && window.FlashBox.kvSet)
          return window.FlashBox.kvSet(k, v == null ? '' : String(v));
        localStorage.setItem(k, v == null ? '' : String(v));
      } catch (e) {}
    },
    remove(k) {
      try {
        if (window.FlashBox && window.FlashBox.kvRemove)
          return window.FlashBox.kvRemove(k);
        localStorage.removeItem(k);
      } catch (e) {}
    },
    all() {
      try {
        if (window.FlashBox && window.FlashBox.kvAll) {
          return JSON.parse(window.FlashBox.kvAll() || '{}');
        }
        const o = {}; for (let i = 0; i < localStorage.length; i++) {
          const k = localStorage.key(i); o[k] = localStorage.getItem(k);
        }
        return o;
      } catch (e) { return {}; }
    }
  };

  /** Apply a global page-zoom transform (CSS variable based) */
  U.applyZoom = (z) => {
    const v = Math.max(0.5, Math.min(2.0, z || 1));
    document.documentElement.style.setProperty('--fb-zoom', v);
    document.body.style.transform = `scale(${v})`;
    document.body.style.transformOrigin = 'top center';
    document.body.style.width = (100 / v) + '%';
    document.body.style.minHeight = (100 / v) + 'vh';
  };

  U.toggleDrawer = (open) => {
    const d = document.getElementById('drawer');
    if (d) d.classList.toggle('open', open);
  };

  /** Choose a color from a string hash. */
  U.colorFromString = (s) => {
    let h = 0; for (let i = 0; i < (s || '').length; i++) {
      h = (h * 31 + s.charCodeAt(i)) | 0;
    }
    const hue = Math.abs(h) % 360;
    return `hsl(${hue}, 65%, 55%)`;
  };

  /** Safe getElementById */
  U.gebi = (id) => document.getElementById(id);

  global.U = U;
})(window);

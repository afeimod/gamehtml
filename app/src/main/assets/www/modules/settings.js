/* ==========================================================================
 * settings.js - user settings & adblock UI
 * ========================================================================== */
(function (global) {
  'use strict';

  const Settings = {};

  function render(container) {
    const html = `
      <div class="section">
        <div class="section-h"><h2>显示</h2></div>
        <div class="form-row inline">
          <label>页面缩放</label>
          <input type="range" min="50" max="200" step="5" id="zoomRange" value="${(parseFloat(U.kv.get('ui_zoom', '1'))*100) || 100}">
          <span id="zoomLabel" class="muted">100%</span>
        </div>
        <div class="form-row inline">
          <label>网页模式</label>
          <select id="webMode">
            <option value="auto">自动（按屏幕）</option>
            <option value="desktop">电脑桌面模式</option>
            <option value="compat">兼容模式</option>
            <option value="mobile">移动手机模式</option>
          </select>
        </div>
        <div class="form-row inline">
          <label>暗色模式</label>
          <select id="darkMode">
            <option value="system">跟随系统</option>
            <option value="light">亮色</option>
            <option value="dark">暗色</option>
          </select>
        </div>
      </div>

      <div class="section">
        <div class="section-h"><h2>广告拦截</h2></div>
        <div class="form-row inline">
          <label>启用广告拦截（网络层）</label>
          <label class="switch"><input type="checkbox" id="adBlockNet" ${U.kv.get('ad_block','true')==='true'?'checked':''}><span class="slider"></span></label>
        </div>
        <div class="form-row inline">
          <label>JS 注入式广告隐藏</label>
          <label class="switch"><input type="checkbox" id="adBlockJs" ${U.kv.get('js_adblock','true')==='true'?'checked':''}><span class="slider"></span></label>
        </div>
        <div class="form-row">
          <label>添加黑名单主机（域名）</label>
          <div class="row gap-8">
            <input type="text" id="adBlockHost" placeholder="例如 ads.example.com" style="flex:1">
            <button class="btn" id="adBlockHostAdd">添加</button>
          </div>
        </div>
        <div class="form-row">
          <label>添加黑名单 URL 片段</label>
          <div class="row gap-8">
            <input type="text" id="adBlockUrl" placeholder="例如 /ads/" style="flex:1">
            <button class="btn" id="adBlockUrlAdd">添加</button>
          </div>
        </div>
        <div class="form-row">
          <button class="btn outline sm" id="adBlockClear">清空自定义规则</button>
        </div>
      </div>

      <div class="section">
        <div class="section-h"><h2>缓存</h2></div>
        <div class="form-row">
          <button class="btn outline" id="clearCache">清空网页缓存</button>
          <div class="help">清空 Service Worker / localStorage / WebView 缓存</div>
        </div>
      </div>

      <div class="section">
        <div class="section-h"><h2>关于</h2></div>
        <div class="form-row">
          <div>应用版本：<span id="verLabel">1.0.0</span></div>
          <div>设备：<span id="devLabel"></span></div>
          <div>用户代理：<span id="uaLabel" class="muted" style="word-break:break-all;font-size:11px"></span></div>
        </div>
        <div class="form-row">
          <a href="https://github.com/ruffle-rs/ruffle" target="_blank">Ruffle</a> ·
          <a href="https://github.com/AbhinavJaiswal1/Waflash-Player" target="_blank">Waflash</a> ·
          <a href="https://github.com/ienaga/swf2js" target="_blank">swf2js</a>
        </div>
      </div>
    `;
    container.innerHTML = html;

    const zoom = container.querySelector('#zoomRange');
    const zLbl = container.querySelector('#zoomLabel');
    function applyZoomFromRange() {
      const v = parseFloat(zoom.value) / 100;
      U.kv.set('ui_zoom', String(v));
      U.applyZoom(v);
      zLbl.textContent = zoom.value + '%';
    }
    zoom.addEventListener('input', U.debounce(applyZoomFromRange, 80));
    zLbl.textContent = zoom.value + '%';

    container.querySelector('#webMode').value = U.kv.get('web_mode', 'auto');
    container.querySelector('#webMode').addEventListener('change', (e) => {
      U.kv.set('web_mode', e.target.value);
      Toast.show('下次打开网页生效');
    });

    container.querySelector('#darkMode').value = U.kv.get('dark_mode', 'system');
    container.querySelector('#darkMode').addEventListener('change', (e) => {
      U.kv.set('dark_mode', e.target.value);
      applyDarkMode();
    });

    container.querySelector('#adBlockNet').addEventListener('change', (e) => {
      U.kv.set('ad_block', e.target.checked ? 'true' : 'false');
    });
    container.querySelector('#adBlockJs').addEventListener('change', (e) => {
      U.kv.set('js_adblock', e.target.checked ? 'true' : 'false');
      if (e.target.checked) AdBlock.start(); else AdBlock.stop();
    });
    container.querySelector('#adBlockHostAdd').addEventListener('click', () => {
      const v = container.querySelector('#adBlockHost').value.trim();
      if (!v) return;
      AdBlock.addHost(v);
      Toast.show('已添加：' + v);
      container.querySelector('#adBlockHost').value = '';
    });
    container.querySelector('#adBlockUrlAdd').addEventListener('click', () => {
      const v = container.querySelector('#adBlockUrl').value.trim();
      if (!v) return;
      AdBlock.addUrl(v);
      Toast.show('已添加：' + v);
      container.querySelector('#adBlockUrl').value = '';
    });
    container.querySelector('#adBlockClear').addEventListener('click', () => {
      Modal.confirm({ title: '清空自定义规则', message: '将只保留默认规则，无法撤销', danger: true })
        .then(ok => { if (ok) { U.kv.remove('extra_ad_hosts'); U.kv.remove('extra_ad_urls'); Toast.show('已清空'); } });
    });

    container.querySelector('#clearCache').addEventListener('click', () => {
      Modal.confirm({ title: '清空缓存', message: '将清空所有网页缓存、Cookie 与 localStorage', danger: true })
        .then(ok => {
          if (!ok) return;
          try { localStorage.clear(); } catch (e) {}
          try {
            if (window.FlashBox && window.FlashBox.clearWebCache) window.FlashBox.clearWebCache();
          } catch (e) {}
          if (navigator.serviceWorker) {
            navigator.serviceWorker.getRegistrations().then(rs => rs.forEach(r => r.unregister()));
          }
          if (window.caches) {
            caches.keys().then(keys => keys.forEach(k => caches.delete(k)));
          }
          Toast.show('已清空');
          setTimeout(() => location.reload(), 600);
        });
    });

    // About info
    const ver = (window.FlashBox && window.FlashBox.appVersion) ? window.FlashBox.appVersion() : '1.0.0';
    container.querySelector('#verLabel').textContent = ver;
    container.querySelector('#devLabel').textContent =
      (window.FlashBox && window.FlashBox.deviceInfo) ? window.FlashBox.deviceInfo() : 'Web';
    container.querySelector('#uaLabel').textContent = navigator.userAgent;
  }

  function applyDarkMode() {
    const m = U.kv.get('dark_mode', 'system');
    let effective;
    if (m === 'system') {
      effective = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    } else {
      effective = m;
    }
    document.documentElement.dataset.theme = effective;
  }

  Settings.render = render;
  Settings.applyDarkMode = applyDarkMode;
  global.Settings = Settings;
})(window);

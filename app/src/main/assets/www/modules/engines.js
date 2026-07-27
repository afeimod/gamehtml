/* ==========================================================================
 * engines.js - encapsulates the three Flash engines
 *
 * Engines:
 *   - ruffle  (Ruffle WASM)
 *   - waflash (Waflash WASM)
 *   - swf2js  (Pure-JS SWF parser)
 *
 * Each engine exposes the same start/stop interface.
 * ========================================================================== */
(function (global) {
  'use strict';

  const Engines = {};

  // -------- ruffle ---------------------------------------------------------
  Engines.ruffle = {
    id: 'ruffle',
    name: 'Ruffle',
    description: 'Rust + WASM 引擎，AS3 兼容最好（推荐）',
    config: { quality: 'high', letterbox: 'on', scale: 'showAll', forceScale: false, wmode: 'direct' },
    async load(stage, swfUrl, opts) {
      const cfg = Object.assign({}, this.config, opts || {});
      // ensure script
      if (!window.RufflePlayer) {
        await loadScript('engines/ruffle/ruffle.js');
      }
      // build ruffle instance
      const ruffle = window.RufflePlayer.newest();
      const player = ruffle.createPlayer();
      applyConfig(player, cfg);
      stage.innerHTML = '';
      stage.appendChild(player);
      player.style.width = '100%';
      player.style.height = '100%';
      player.style.display = 'block';
      try { await player.load(swfUrl); }
      catch (e) { console.error('Ruffle load error', e); throw e; }
      return player;
    },
    applyConfig(player, cfg) {
      try {
        const c = window.RufflePlayer.config;
        c.quality = cfg.quality || 'high';
        c.letterbox = cfg.letterbox || 'on';
        c.scale = cfg.scale || 'showAll';
        c.forceScale = !!cfg.forceScale;
        c.wmode = cfg.wmode || 'direct';
        c.backgroundColor = cfg.backgroundColor || null;
        c.unmuteOverlay = 'visible';
        c.autoplay = 'auto';
      } catch (e) {}
    }
  };

  // -------- waflash -------------------------------------------------------
  Engines.waflash = {
    id: 'waflash',
    name: 'Waflash',
    description: 'WASM 引擎，老 Flash 兼容好',
    config: { quality: 'high' },
    async load(stage, swfUrl, opts) {
      const cfg = Object.assign({}, this.config, opts || {});
      if (!window.createWaflash) {
        await loadScript('engines/waflash/waflash-player.min.js');
      }
      // Inject style once
      if (!document.getElementById('waflash-style')) {
        const link = document.createElement('link');
        link.id = 'waflash-style';
        link.rel = 'stylesheet';
        link.href = 'engines/waflash/waflash-style.css';
        document.head.appendChild(link);
      }
      stage.innerHTML = `
        <div id="waflashContainer" style="width:100%;height:100%;position:relative;display:flex;align-items:center;justify-content:center;">
          <canvas class="waflashCanvas" id="waflashCanvas" tabindex="1"
            style="outline:none;display:block;max-width:100%;max-height:100%;"></canvas>
        </div>
      `;
      try {
        const wf = await window.createWaflash({
          canvas: document.getElementById('waflashCanvas'),
          wasm: 'engines/waflash/waflash.wasm',
          data: 'engines/waflash/waflash.data',
          quality: cfg.quality || 'high'
        });
        await wf.load(swfUrl);
        return wf;
      } catch (e) {
        console.error('Waflash load error', e);
        throw e;
      }
    },
    applyConfig(player, cfg) {
      // Waflash has limited runtime config; quality via initial load
    }
  };

  // -------- swf2js ---------------------------------------------------------
  Engines.swf2js = {
    id: 'swf2js',
    name: 'swf2js',
    description: '纯 JS 引擎，Flash Lite 备选',
    config: { quality: 'high' },
    async load(stage, swfUrl, opts) {
      if (!window.swf2js) {
        await loadScript('engines/swf2js/swf2js.js');
      }
      stage.innerHTML = `<div id="swf2js-stage" style="width:100%;height:100%;display:flex;align-items:center;justify-content:center;"></div>`;
      const target = stage.querySelector('#swf2js-stage');
      try {
        const player = await window.swf2js.load(swfUrl, {
          target: target,
          width: '100%',
          height: '100%',
          // Some 3rd-party swf2js builds have these:
          onError: (e) => console.warn('swf2js err', e)
        });
        return player;
      } catch (e) {
        console.error('swf2js load error', e);
        throw e;
      }
    },
    applyConfig(player, cfg) {}
  };

  // -----------------------------------------------------------------------
  function loadScript(src) {
    return new Promise((resolve, reject) => {
      const s = document.createElement('script');
      s.src = src; s.async = false;
      s.onload = () => resolve();
      s.onerror = () => reject(new Error('load failed: ' + src));
      document.head.appendChild(s);
    });
  }

  function applyConfig(player, cfg) {
    const eng = Engines[currentEngineId()];
    if (eng && eng.applyConfig) eng.applyConfig(player, cfg);
  }

  function list() {
    return Object.values(Engines).map(e => ({
      id: e.id, name: e.name, description: e.description
    }));
  }

  function get(id) { return Engines[id] || Engines.ruffle; }

  function currentEngineId() {
    return U.kv.get('current_engine', U.kv.get('default_engine', 'ruffle'));
  }
  function setCurrentEngine(id) { U.kv.set('current_engine', id); }

  function configOf(id) {
    const e = Engines[id] || Engines.ruffle;
    return Object.assign({}, e.config,
      JSON.parse(U.kv.get('engine_cfg_' + id) || 'null') || {});
  }
  function setConfig(id, cfg) {
    U.kv.set('engine_cfg_' + id, JSON.stringify(cfg || {}));
  }

  global.EnginesMod = { list, get, currentEngineId, setCurrentEngine, configOf, setConfig };
})(window);

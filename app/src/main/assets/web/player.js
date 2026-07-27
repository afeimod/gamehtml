/* ===== FlashGameBox player bootstrap =====
   Loads the selected engine (Ruffle / Waflash / FlashPatch-compat), applies the
   per-engine quality & aspect-ratio config, then mounts virtual controls. */
const A = () => window.Android;
const q = new URLSearchParams(location.search);
const params = {
  local: q.get('local'),
  src: q.get('src'),
  name: q.get('name') || 'Flash',
  engine: q.get('engine') || (A() ? JSON.parse(A().getSettings()).engine : 'ruffle')
};

function settings() { return A() ? JSON.parse(A().getSettings()) : { jsInject: true }; }
function engineConfigs() { return A() ? JSON.parse(A().getEngineConfigs()) : {}; }
function controlsCfg() { return A() ? JSON.parse(A().getControls()) : null; }

const ORIGIN = 'https://app.local';
const swfUrl = params.local ? `${ORIGIN}/local/${encodeURIComponent(params.local)}` : params.src;
const cfg = engineConfigs()[params.engine] || {};
const stage = document.getElementById('stage');
const engTag = document.getElementById('engTag');
const waflashContainer = document.getElementById('waflashContainer');
const engNames = { ruffle: 'Ruffle', waflash: 'Waflash', flashpatch: 'FlashPatch' };
engTag.textContent = engNames[params.engine] || params.engine;

function scaleFor(aspect) {
  return ({ contain: 'showAll', cover: 'noBorder', stretch: 'exactFit', original: 'noScale' })[aspect] || 'showAll';
}
function applyAspectToStage(aspect) {
  stage.classList.toggle('fill', aspect === 'cover' || aspect === 'stretch' || aspect === 'contain');
}

function hideLoading() { const l = document.getElementById('loading'); if (l) l.style.display = 'none'; }
function fail(msg) {
  hideLoading();
  const e = document.getElementById('err');
  document.getElementById('errMsg').textContent = msg;
  e.style.display = 'flex';
  if (A()) A().keepScreenOn(false);
}

// toolbar wiring
document.getElementById('bHome').onclick = () => A() && A().goHome();
document.getElementById('bBack').onclick = () => A() ? A().goBack() : history.back();
document.getElementById('bFav').onclick = () => {
  if (!A()) return;
  const item = JSON.stringify({ id: 'f_' + Date.now().toString(36), url: location.href, title: params.name });
  const on = A().toggleFavorite(item);
  A().toast(on ? '已收藏' : '已取消收藏');
};
let ctrlHidden = false;
document.getElementById('bCtrl').onclick = () => {
  ctrlHidden = !ctrlHidden;
  const c = document.getElementById('fbControls');
  if (c) c.style.display = ctrlHidden ? 'none' : 'block';
  document.getElementById('bCtrl').textContent = ctrlHidden ? '⊟ 按键' : '⊞ 按键';
};

// record history
if (A() && settings().autoHistory !== false) {
  const item = {
    id: 'h_' + Date.now().toString(36),
    url: location.href, title: params.name, time: Date.now(),
    type: 'local', localId: params.local
  };
  A().addHistory(JSON.stringify(item));
}

// keep screen on while playing
if (A()) A().keepScreenOn(true);
window.addEventListener('pagehide', () => A() && A().keepScreenOn(false));

function loadScript(url) {
  return new Promise((res, rej) => {
    const s = document.createElement('script');
    s.src = url; s.onload = res; s.onerror = () => rej(new Error('加载失败: ' + url));
    document.head.appendChild(s);
  });
}

async function boot() {
  try {
    if (!swfUrl) { fail('未指定SWF文件'); return; }
    if (params.engine === 'waflash') {
      await playWaflash();
    } else if (params.engine === 'flashpatch') {
      const base = cfg.baseEngine || 'ruffle';
      if (base === 'waflash') await playWaflash(true);
      else await playRuffle(true);
    } else {
      await playRuffle();
    }
    hideLoading();
    mountControls();
  } catch (e) {
    fail('引擎加载失败：' + (e && e.message || e));
  }
}

// ==================== Ruffle ====================
async function playRuffle(isFlashPatch) {
  // Hide waflash container, show stage directly
  waflashContainer.style.display = 'none';

  await loadScript(`${ORIGIN}/engines/ruffle/ruffle.js`);
  window.RufflePlayer = window.RufflePlayer || {};
  const ruffle = window.RufflePlayer.newest ? window.RufflePlayer.newest() : window.RufflePlayer;

  // Apply global config before creating player
  window.RufflePlayer.config = {
    allowScriptAccess: true,
    autoplay: 'on',
    unmuteOverlay: 'visible',
    backgroundColor: cfg.backgroundColor || '#000000',
    letterbox: cfg.letterbox || 'fullscreen',
    quality: cfg.quality || 'high',
    scale: scaleFor(cfg.aspect),
    wmode: cfg.wmode || 'opaque',
    preferredRenderer: cfg.renderer || null,
    upgradeToHttps: cfg.upgradeToHttps !== false,
    compatibilityRules: true,
    maxExecutionDuration: cfg.maxExec || 15,
    playerVersion: (isFlashPatch && cfg.playerVersion) ? cfg.playerVersion : (cfg.playerVersion || null),
    showSwfDownload: !!cfg.showSwfDownload,
    polyfills: true
  };

  const player = ruffle.createPlayer();
  player.style.width = '100%';
  player.style.height = '100%';
  stage.appendChild(player);
  applyAspectToStage(cfg.aspect || 'contain');

  player.ruffle().load(swfUrl, {
    allowScriptAccess: true,
    autoplay: 'on',
    quality: cfg.quality || 'high',
    scale: scaleFor(cfg.aspect),
    wmode: cfg.wmode || 'opaque'
  });
}

// ==================== Waflash ====================
async function playWaflash(isFlashPatch) {
  // Show waflash container with canvas + status
  waflashContainer.style.display = 'block';
  applyAspectToStage(cfg.aspect || 'contain');

  // Set canvas size
  const canvas = document.getElementById('canvas');
  canvas.style.width = '100%';
  canvas.style.height = '100%';
  canvas.style.background = cfg.backgroundColor || '#000000';

  // Load waflash engine as ES module
  const mod = await import(`${ORIGIN}/engines/waflash/waflash-player.min.js`);
  const createWaflash = mod.createWaflash || (mod.default && mod.default.createWaflash);
  if (typeof createWaflash !== 'function') {
    throw new Error('waflash createWaflash 未找到');
  }

  // Waflash options
  const opts = {
    enableFilters: cfg.enableFilters !== false,
    avm: cfg.avm || 'auto',
    wmode: cfg.wmode || 'opaque',
    scale: scaleFor(cfg.aspect),
    gpu: cfg.renderer === 'webgl'
  };

  createWaflash(swfUrl, opts);

  // Focus canvas for keyboard input
  setTimeout(() => { try { canvas.focus(); } catch (e) {} }, 100);
}

// ==================== Controls ====================
function mountControls() {
  const c = controlsCfg();
  if (c && c.enabled !== false && window.FBControls) {
    FBControls.mount(c, { online: false, engineName: engTag.textContent });
  }
}

boot();

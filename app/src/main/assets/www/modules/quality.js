/* ==========================================================================
 * quality.js - global quality + aspect ratio helpers
 * ========================================================================== */
(function (global) {
  'use strict';

  const QUALITIES = [
    { id: 'low',    label: '低 (low)',   description: '省电，老机型' },
    { id: 'medium', label: '中 (medium)' },
    { id: 'high',   label: '高 (high)', description: '默认' },
    { id: 'best',   label: '最高 (best)' }
  ];

  const RATIOS = [
    { id: 'original', label: '原始',   css: null },
    { id: '4:3',      label: '4:3',    css: '4 / 3' },
    { id: '16:9',     label: '16:9',   css: '16 / 9' },
    { id: '16:10',    label: '16:10',  css: '16 / 10' },
    { id: '21:9',     label: '21:9',   css: '21 / 9' },
    { id: 'stretch',  label: '拉伸',   css: 'auto' }
  ];

  function currentQuality() { return U.kv.get('quality', 'high'); }
  function setQuality(q) { U.kv.set('quality', q); }

  function currentRatio() { return U.kv.get('ratio', '16:9'); }
  function setRatio(r) { U.kv.set('ratio', r); }

  function applyStageRatio(stageEl) {
    const r = RATIOS.find(x => x.id === currentRatio()) || RATIOS[2];
    if (!stageEl) return;
    if (r.id === 'original') {
      stageEl.style.aspectRatio = 'auto';
    } else if (r.id === 'stretch') {
      stageEl.style.aspectRatio = 'auto';
    } else {
      stageEl.style.aspectRatio = r.css;
    }
  }

  global.Quality = {
    QUALITIES, RATIOS,
    currentQuality, setQuality,
    currentRatio, setRatio,
    applyStageRatio
  };
})(window);

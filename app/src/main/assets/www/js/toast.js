/* ==========================================================================
 * toast.js - small toast/snackbar helper
 * ========================================================================== */
(function (global) {
  'use strict';

  let timer = null;
  const el = () => document.getElementById('toast');

  function show(msg, ms) {
    const e = el();
    if (!e) return;
    e.textContent = msg;
    e.classList.add('show');
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => e.classList.remove('show'), ms || 2200);
  }
  function hide() { const e = el(); if (e) e.classList.remove('show'); }

  global.Toast = { show, hide };
})(window);

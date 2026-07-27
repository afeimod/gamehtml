/* ==========================================================================
 * history.js - tracks play history
 * ========================================================================== */
(function (global) {
  'use strict';

  const KEY = 'history';
  const MAX = 200;

  function all() {
    try { return JSON.parse(U.kv.get(KEY) || '[]'); }
    catch (e) { return []; }
  }

  function add(entry) {
    const list = all();
    // de-dup by url
    const idx = list.findIndex(e => e.url === entry.url);
    if (idx >= 0) list.splice(idx, 1);
    list.unshift(Object.assign({ t: Date.now() }, entry));
    if (list.length > MAX) list.length = MAX;
    U.kv.set(KEY, JSON.stringify(list));
  }

  function clear() { U.kv.set(KEY, '[]'); }

  function remove(url) {
    const list = all().filter(e => e.url !== url);
    U.kv.set(KEY, JSON.stringify(list));
  }

  function count() { return all().length; }

  global.History = { all, add, clear, remove, count };
})(window);

/* ==========================================================================
 * favorites.js
 * ========================================================================== */
(function (global) {
  'use strict';

  const KEY = 'favorites';

  function all() {
    try { return JSON.parse(U.kv.get(KEY) || '[]'); }
    catch (e) { return []; }
  }

  function add(entry) {
    const list = all();
    if (list.some(e => e.url === entry.url)) return false;
    list.unshift(Object.assign({ t: Date.now() }, entry));
    U.kv.set(KEY, JSON.stringify(list));
    return true;
  }

  function remove(url) {
    const list = all().filter(e => e.url !== url);
    U.kv.set(KEY, JSON.stringify(list));
  }

  function has(url) { return all().some(e => e.url === url); }

  function toggle(entry) {
    if (has(entry.url)) { remove(entry.url); return false; }
    add(entry); return true;
  }

  function clear() { U.kv.set(KEY, '[]'); }

  global.Favorites = { all, add, remove, has, toggle, clear };
})(window);

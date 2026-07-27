/* ==========================================================================
 * router.js - lightweight hash-based router
 * ========================================================================== */
(function (global) {
  'use strict';

  const routes = {};
  const beforeHooks = [];
  let currentRoute = null;

  function parse() {
    let h = location.hash.replace(/^#/, '') || '/';
    const qix = h.indexOf('?');
    let query = '';
    if (qix >= 0) { query = h.substring(qix); h = h.substring(0, qix); }
    return { path: h, query: query, params: parseQuery(query) };
  }

  function parseQuery(q) {
    const p = {};
    if (!q) return p;
    const usp = new URLSearchParams(q.replace(/^\?/, ''));
    for (const [k, v] of usp) p[k] = v;
    return p;
  }

  function build(path, params) {
    let u = path;
    if (params && Object.keys(params).length) {
      const sp = new URLSearchParams();
      for (const k in params) sp.set(k, params[k]);
      u += '?' + sp.toString();
    }
    return u;
  }

  function go(path, params) {
    const u = '#' + build(path, params);
    if (location.hash !== u) location.hash = u;
    else trigger();
  }

  function replace(path, params) {
    const u = '#' + build(path, params);
    history.replaceState(null, '', u);
    trigger();
  }

  function on(path, handler) { routes[path] = handler; }

  function before(fn) { beforeHooks.push(fn); }

  async function trigger() {
    const r = parse();
    currentRoute = r;
    let to = r.path;
    let handler = routes[to];
    // Match wildcards
    if (!handler) {
      for (const k in routes) {
        if (k.endsWith('/*') && to.startsWith(k.slice(0, -1))) {
          handler = routes[k]; break;
        }
      }
    }
    if (!handler) handler = routes['/'] || routes['*'];
    if (!handler) return;
    for (const h of beforeHooks) {
      try {
        const ret = h(r);
        if (ret === false) return;
        if (ret && ret.then) { const ok = await ret; if (ok === false) return; }
      } catch (e) { console.error(e); }
    }
    try { await handler(r); } catch (e) { console.error(e); }
    // Scroll to top on route change
    document.getElementById('content') && (document.getElementById('content').scrollTop = 0);
    window.scrollTo(0, 0);
  }

  function current() { return currentRoute; }

  // Init
  window.addEventListener('hashchange', trigger);

  global.Router = { on, go, replace, current, before, build, parse };
})(window);

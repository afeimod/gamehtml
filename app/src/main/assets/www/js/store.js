/* ==========================================================================
 * store.js - simple observable store
 * ========================================================================== */
(function (global) {
  'use strict';

  function createStore(initial) {
    let state = Object.assign({}, initial);
    const listeners = new Set();

    return {
      get: (k) => state[k],
      all: () => Object.assign({}, state),
      set: (patch) => {
        const changed = [];
        for (const k in patch) {
          if (state[k] !== patch[k]) {
            state[k] = patch[k]; changed.push(k);
          }
        }
        if (changed.length) listeners.forEach(fn => { try { fn(state, changed); } catch (e) { console.error(e); } });
      },
      subscribe: (fn) => { listeners.add(fn); return () => listeners.delete(fn); },
      reset: (init) => { state = Object.assign({}, init); listeners.forEach(fn => fn(state, Object.keys(state))); }
    };
  }

  global.createStore = createStore;
})(window);

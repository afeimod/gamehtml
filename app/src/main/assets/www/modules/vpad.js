/* ==========================================================================
 * vpad.js - Virtual Gamepad
 *
 * Three control types:
 *   1. joystick  - virtual analog stick (default WSAD mapping)
 *   2. dpad      - 4-way directional pad (Up/Down/Left/Right or WSAD)
 *   3. key       - single virtual key (any keyboard key)
 *
 * Each element is draggable, scalable and configurable.
 * Persistent layout is saved to localStorage; can be edited via long-press.
 * ========================================================================== */
(function (global) {
  'use strict';

  // Keyboard code mapping (DOM KeyboardEvent.code)
  const KEY_CODES = [
    // letters
    'KeyA','KeyB','KeyC','KeyD','KeyE','KeyF','KeyG','KeyH','KeyI','KeyJ',
    'KeyK','KeyL','KeyM','KeyN','KeyO','KeyP','KeyQ','KeyR','KeyS','KeyT',
    'KeyU','KeyV','KeyW','KeyX','KeyY','KeyZ',
    // digits
    'Digit0','Digit1','Digit2','Digit3','Digit4','Digit5','Digit6','Digit7','Digit8','Digit9',
    // common
    'Space','Enter','Escape','Backspace','Tab','ShiftLeft','ShiftRight','ControlLeft','ControlRight',
    'AltLeft','AltRight','MetaLeft','MetaRight','ContextMenu',
    'ArrowUp','ArrowDown','ArrowLeft','ArrowRight',
    'Numpad0','Numpad1','Numpad2','Numpad3','Numpad4','Numpad5','Numpad6','Numpad7','Numpad8','Numpad9',
    'NumpadAdd','NumpadSubtract','NumpadMultiply','NumpadDivide','NumpadEnter','NumpadDecimal',
    'F1','F2','F3','F4','F5','F6','F7','F8','F9','F10','F11','F12',
    'Backquote','Minus','Equal','BracketLeft','BracketRight','Backslash','Semicolon','Quote','Comma','Period','Slash'
  ];

  const KEY_LABEL = {};
  for (const c of KEY_CODES) {
    if (c.startsWith('Key')) KEY_LABEL[c] = c.slice(3);
    else if (c.startsWith('Digit')) KEY_LABEL[c] = c.slice(5);
    else if (c.startsWith('Numpad')) KEY_LABEL[c] = 'Num' + c.slice(6);
    else if (c.startsWith('Arrow')) KEY_LABEL[c] = c.slice(5);
    else if (c === 'Space') KEY_LABEL[c] = 'Space';
    else if (c === 'Enter' || c === 'NumpadEnter') KEY_LABEL[c] = 'Enter';
    else if (c === 'ShiftLeft' || c === 'ShiftRight') KEY_LABEL[c] = 'Shift';
    else if (c === 'ControlLeft' || c === 'ControlRight') KEY_LABEL[c] = 'Ctrl';
    else if (c === 'AltLeft' || c === 'AltRight') KEY_LABEL[c] = 'Alt';
    else if (c === 'MetaLeft' || c === 'MetaRight') KEY_LABEL[c] = 'Win';
    else KEY_LABEL[c] = c;
  }

  const STORAGE_KEY = 'vpad_layout_v1';

  // ---- default layout -----------------------------------------------------
  function defaultLayout() {
    return {
      v: 1,
      joystick: {
        type: 'joystick',
        keys: ['KeyW', 'KeyS', 'KeyA', 'KeyD'],
        layout: 'wsad',  // 'wsad' | 'arrows'
        x: 0.05, y: 0.6,  // normalized 0..1
        scale: 1.0,
        mode: 'joystick'  // 'joystick' | 'dpad'
      },
      keys: [
        { type: 'key', code: 'KeyJ',  x: 0.72, y: 0.55, scale: 1.0 },
        { type: 'key', code: 'KeyK',  x: 0.86, y: 0.55, scale: 1.0 },
        { type: 'key', code: 'KeyL',  x: 0.72, y: 0.70, scale: 1.0 },
        { type: 'key', code: 'KeyU',  x: 0.86, y: 0.70, scale: 1.0 },
        { type: 'key', code: 'KeyI',  x: 0.72, y: 0.85, scale: 1.0 },
        { type: 'key', code: 'KeyO',  x: 0.86, y: 0.85, scale: 1.0 },
        { type: 'key', code: 'Enter', x: 0.78, y: 0.40, scale: 1.1, label: '确认' },
        { type: 'key', code: 'Space', x: 0.78, y: 0.25, scale: 1.0, label: '跳跃' }
      ]
    };
  }

  function load() {
    try {
      const raw = U.kv.get(STORAGE_KEY, '');
      if (raw) return Object.assign(defaultLayout(), JSON.parse(raw));
    } catch (e) {}
    return defaultLayout();
  }

  function save(layout) {
    U.kv.set(STORAGE_KEY, JSON.stringify(layout));
  }

  // ---- create a single key element ---------------------------------------

  function makeKey(cfg) {
    const el = document.createElement('div');
    el.className = 'vpad-el vpad-key';
    el.dataset.type = 'key';
    el.dataset.code = cfg.code;
    el.textContent = cfg.label || KEY_LABEL[cfg.code] || cfg.code;
    el.style.left = (cfg.x * 100) + '%';
    el.style.top  = (cfg.y * 100) + '%';
    el.style.transform = `translate(-50%, -50%) scale(${cfg.scale || 1})`;

    let active = false;
    const down = (e) => {
      e.preventDefault(); e.stopPropagation();
      if (!active) { sendKey(cfg.code, true); el.classList.add('active'); active = true; }
    };
    const up = (e) => {
      e.preventDefault(); e.stopPropagation();
      if (active) { sendKey(cfg.code, false); el.classList.remove('active'); active = false; }
    };
    el.addEventListener('pointerdown', (e) => { el.setPointerCapture(e.pointerId); down(e); });
    el.addEventListener('pointerup',   (e) => up(e));
    el.addEventListener('pointercancel', (e) => up(e));
    el.addEventListener('pointerleave', (e) => up(e));
    bindEdit(el, cfg, 'key');
    return el;
  }

  // ---- joystick / dpad ---------------------------------------------------

  function makeJoystick(cfg) {
    const el = document.createElement('div');
    el.className = 'vpad-el vpad-stick';
    el.dataset.type = 'joystick';
    el.style.left = (cfg.x * 100) + '%';
    el.style.top  = (cfg.y * 100) + '%';
    el.style.transform = `translate(-50%, -50%) scale(${cfg.scale || 1})`;

    if (cfg.mode === 'dpad') {
      // d-pad
      const dirs = [
        { dir: 'up',    col: 1, row: 0, code: cfg.layout === 'wsad' ? 'KeyW' : 'ArrowUp', label: '↑' },
        { dir: 'left',  col: 0, row: 1, code: cfg.layout === 'wsad' ? 'KeyA' : 'ArrowLeft', label: '←' },
        { dir: 'right', col: 2, row: 1, code: cfg.layout === 'wsad' ? 'KeyD' : 'ArrowRight', label: '→' },
        { dir: 'down',  col: 1, row: 2, code: cfg.layout === 'wsad' ? 'KeyS' : 'ArrowDown', label: '↓' }
      ];
      const dpad = document.createElement('div');
      dpad.className = 'vpad-dpad';
      // 3x3 grid: empty cells where applicable
      for (let row = 0; row < 3; row++) {
        for (let col = 0; col < 3; col++) {
          const cell = document.createElement('div');
          cell.className = 'dp-cell';
          if (col === 1 && row === 1) {
            cell.textContent = cfg.layout === 'wsad' ? 'WASD' : '↑↓←→';
            cell.style.opacity = '.4';
            cell.style.fontSize = '12px';
          } else {
            const d = dirs.find(x => x.col === col && x.row === row);
            if (d) {
              cell.textContent = d.label;
              cell.dataset.dir = d.dir;
              cell.dataset.code = d.code;
              let active = false;
              const down = () => { if (!active) { sendKey(d.code, true); cell.classList.add('active'); active = true; } };
              const up   = () => { if (active) { sendKey(d.code, false); cell.classList.remove('active'); active = false; } };
              cell.addEventListener('pointerdown', (e) => { e.preventDefault(); cell.setPointerCapture(e.pointerId); down(); });
              cell.addEventListener('pointerup',   (e) => { e.preventDefault(); up(); });
              cell.addEventListener('pointercancel', (e) => up());
              cell.addEventListener('pointerleave',  (e) => up());
            }
          }
          dpad.appendChild(cell);
        }
      }
      el.appendChild(dpad);
    } else {
      // analog stick
      const base = document.createElement('div');
      base.className = 'vpad-stick-base';
      const knob = document.createElement('div');
      knob.className = 'vpad-stick-knob';
      base.appendChild(knob);
      el.appendChild(base);
      let pointerId = null;
      const state = { up: false, down: false, left: false, right: false, knobX: 0, knobY: 0 };
      const update = (dx, dy) => {
        // dx,dy in -1..1, derived from pointer relative to base center
        const r = 60; // approximate base radius
        const kx = Math.max(-r, Math.min(r, dx * r));
        const ky = Math.max(-r, Math.min(r, dy * r));
        knob.style.transform = `translate(${kx}px, ${ky}px)`;
        const threshold = 0.3;
        const want = {
          up:    ky < -r * threshold,
          down:  ky >  r * threshold,
          left:  kx < -r * threshold,
          right: kx >  r * threshold
        };
        const keys = cfg.layout === 'wsad'
          ? { up: 'KeyW', down: 'KeyS', left: 'KeyA', right: 'KeyD' }
          : { up: 'ArrowUp', down: 'ArrowDown', left: 'ArrowLeft', right: 'ArrowRight' };
        for (const k in want) {
          if (want[k] && !state[k]) { sendKey(keys[k], true); state[k] = true; }
          else if (!want[k] && state[k]) { sendKey(keys[k], false); state[k] = false; }
        }
        state.knobX = kx; state.knobY = ky;
      };
      const onDown = (e) => {
        e.preventDefault(); e.stopPropagation();
        if (pointerId !== null) return;
        pointerId = e.pointerId;
        el.setPointerCapture(pointerId);
        const r = el.getBoundingClientRect();
        const cx = r.left + r.width / 2, cy = r.top + r.height / 2;
        update((e.clientX - cx) / 50, (e.clientY - cy) / 50);
      };
      const onMove = (e) => {
        if (pointerId !== e.pointerId) return;
        const r = el.getBoundingClientRect();
        const cx = r.left + r.width / 2, cy = r.top + r.height / 2;
        update((e.clientX - cx) / 50, (e.clientY - cy) / 50);
      };
      const onUp = (e) => {
        if (pointerId !== e.pointerId) return;
        pointerId = null;
        knob.style.transform = 'translate(0,0)';
        for (const k of ['up','down','left','right']) {
          if (state[k]) {
            const keys = cfg.layout === 'wsad'
              ? { up: 'KeyW', down: 'KeyS', left: 'KeyA', right: 'KeyD' }
              : { up: 'ArrowUp', down: 'ArrowDown', left: 'ArrowLeft', right: 'ArrowRight' };
            sendKey(keys[k], false);
            state[k] = false;
          }
        }
      };
      el.addEventListener('pointerdown', onDown);
      el.addEventListener('pointermove', onMove);
      el.addEventListener('pointerup', onUp);
      el.addEventListener('pointercancel', onUp);
    }

    bindEdit(el, cfg, 'joystick');
    return el;
  }

  // ---- send key event to engine ------------------------------------------

  function sendKey(code, isDown) {
    if (currentTarget) {
      try {
        const ev = new KeyboardEvent(isDown ? 'keydown' : 'keyup', {
          code, key: KEY_LABEL[code] || code,
          bubbles: true, cancelable: true
        });
        currentTarget.dispatchEvent(ev);
        // Also try window (Ruffle picks it up via document)
        try { window.dispatchEvent(ev); } catch (e) {}
      } catch (e) {}
    }
    // also notify via event so app code can react
    window.dispatchEvent(new CustomEvent('vpad-key', {
      detail: { code, down: isDown }
    }));
  }

  // ---- editor: long-press to enter edit mode ----------------------------

  let editing = false;
  let currentTarget = null; // canvas / element to dispatch keyboard events
  let layout = null;
  let hostEl = null;

  function bindEdit(el, cfg, type) {
    let pressTimer = null;
    let moved = false;
    let startX = 0, startY = 0;
    el.addEventListener('pointerdown', (e) => {
      if (!editing) return;
      e.stopPropagation();
      startX = e.clientX; startY = e.clientY;
      moved = false;
      pressTimer = setTimeout(() => {
        if (!moved) openElementMenu(el, cfg, type);
      }, 380);
    });
    el.addEventListener('pointermove', (e) => {
      if (!editing) return;
      if (Math.abs(e.clientX - startX) + Math.abs(e.clientY - startY) > 6) {
        moved = true; clearTimeout(pressTimer);
      }
    });
    el.addEventListener('pointerup', () => { clearTimeout(pressTimer); });
    el.addEventListener('pointercancel', () => { clearTimeout(pressTimer); });
  }

  function openElementMenu(el, cfg, type) {
    document.querySelectorAll('.vpad-menu').forEach(m => m.remove());
    const menu = document.createElement('div');
    menu.className = 'vpad-menu';
    const r = el.getBoundingClientRect();
    menu.style.left = (r.left + r.width / 2) + 'px';
    menu.style.top  = (r.top - 4) + 'px';
    const buttons = [];
    if (type === 'joystick') {
      buttons.push({ l: cfg.mode === 'joystick' ? '切换为方向键' : '切换为摇杆',
                     fn: () => { cfg.mode = cfg.mode === 'joystick' ? 'dpad' : 'joystick'; save(layout); menu.remove(); rebuild(); } });
      buttons.push({ l: cfg.layout === 'wsad' ? '切换为方向键' : '切换为 WSAD',
                     fn: () => { cfg.layout = cfg.layout === 'wsad' ? 'arrows' : 'wsad'; save(layout); menu.remove(); rebuild(); } });
    }
    if (type === 'key') {
      buttons.push({ l: '修改按键', fn: () => { menu.remove(); openKeyPicker(el, cfg); } });
    }
    buttons.push({ l: '放大', fn: () => { cfg.scale = Math.min(3, (cfg.scale || 1) + 0.1); save(layout); menu.remove(); rebuild(); } });
    buttons.push({ l: '缩小', fn: () => { cfg.scale = Math.max(0.4, (cfg.scale || 1) - 0.1); save(layout); menu.remove(); rebuild(); } });
    buttons.push({ l: '重置', fn: () => { cfg.x = 0.5; cfg.y = 0.5; cfg.scale = 1; save(layout); menu.remove(); rebuild(); } });
    if (type === 'key') {
      buttons.push({ l: '删除', danger: true, fn: () => {
        layout.keys = layout.keys.filter(k => k !== cfg);
        save(layout); menu.remove(); rebuild();
      } });
    }
    for (const b of buttons) {
      const btn = document.createElement('button');
      btn.textContent = b.l;
      if (b.danger) btn.style.color = 'var(--danger)';
      btn.addEventListener('click', (e) => { e.stopPropagation(); b.fn(); });
      menu.appendChild(btn);
    }
    document.body.appendChild(menu);
    setTimeout(() => {
      const close = (e) => {
        if (!menu.contains(e.target)) { menu.remove(); document.removeEventListener('click', close); }
      };
      document.addEventListener('click', close);
    }, 0);
  }

  // ---- pick a keyboard key ------------------------------------------------

  function openKeyPicker(el, cfg) {
    // keyboardpicker is loaded as a regular <script> in index.html, so it's a global.
    if (!window.Picker) {
      Toast.show('键盘选择器未加载');
      return;
    }
    window.Picker.open({
      current: cfg.code,
      onPick: (code) => {
        cfg.code = code;
        if (KEY_LABEL[code] && code !== cfg.code) cfg.label = null;
        save(layout);
        rebuild();
      }
    });
  }

  // ---- drag element in edit mode -----------------------------------------

  function makeDraggable(el, cfg) {
    let pid = null; let offX = 0, offY = 0;
    el.addEventListener('pointerdown', (e) => {
      if (!editing) return;
      // Only start drag if pointer down on the el itself, not a child
      pid = e.pointerId; el.setPointerCapture(pid);
      const r = el.getBoundingClientRect();
      const parent = el.parentElement.getBoundingClientRect();
      offX = e.clientX - (r.left - parent.left);
      offY = e.clientY - (r.top - parent.top);
      e.stopPropagation();
      e.preventDefault();
    });
    el.addEventListener('pointermove', (e) => {
      if (pid !== e.pointerId) return;
      const parent = el.parentElement.getBoundingClientRect();
      const x = e.clientX - parent.left - offX + el.offsetWidth / 2;
      const y = e.clientY - parent.top - offY + el.offsetHeight / 2;
      cfg.x = Math.max(0, Math.min(1, x / parent.width));
      cfg.y = Math.max(0, Math.min(1, y / parent.height));
      el.style.left = (cfg.x * 100) + '%';
      el.style.top  = (cfg.y * 100) + '%';
    });
    const end = (e) => { if (pid === e.pointerId) { pid = null; save(layout); } };
    el.addEventListener('pointerup', end);
    el.addEventListener('pointercancel', end);
  }

  // ---- rebuild / mount ---------------------------------------------------

  function rebuild() {
    if (!hostEl) return;
    hostEl.innerHTML = '';
    layout = load();
    const stick = makeJoystick(layout.joystick);
    makeDraggable(stick, layout.joystick);
    hostEl.appendChild(stick);
    for (const k of layout.keys) {
      const kEl = makeKey(k);
      makeDraggable(kEl, k);
      hostEl.appendChild(kEl);
    }
    if (editing) showHandles(true);
  }

  function showHandles(on) {
    if (!hostEl) return;
    hostEl.querySelectorAll('.vpad-el').forEach(el => {
      el.classList.toggle('editing', on);
      el.querySelectorAll('.vpad-handle').forEach(h => h.remove());
      if (!on) return;
      const del = document.createElement('div');
      del.className = 'vpad-handle del';
      del.textContent = '×';
      del.addEventListener('click', (e) => {
        e.stopPropagation();
        if (el.dataset.type === 'joystick') {
          // Joystick can't be removed
          return;
        }
        // Find the matching key by code AND position (more reliable)
        const code = el.dataset.code;
        const idx = layout.keys.findIndex(k =>
          k.code === code && el.style.left === (k.x*100)+'%' && el.style.top === (k.y*100)+'%');
        if (idx >= 0) {
          layout.keys.splice(idx, 1);
          save(layout);
          rebuild();
        } else {
          // Fallback: filter by code
          layout.keys = layout.keys.filter(k => k.code !== code);
          save(layout);
          rebuild();
        }
      });
      const sc = document.createElement('div');
      sc.className = 'vpad-handle scale';
      sc.textContent = '⇲';
      // Scale handle (similar to drag)
      let pid = null; let startDist = 0; let startScale = 0;
      sc.addEventListener('pointerdown', (e) => {
        pid = e.pointerId; sc.setPointerCapture(pid);
        const r = el.getBoundingClientRect();
        startDist = Math.hypot(e.clientX - (r.left + r.width), e.clientY - (r.top + r.height));
        const cfg = el.dataset.type === 'joystick' ? layout.joystick
                  : layout.keys.find(k => k.code === el.dataset.code);
        startScale = cfg.scale || 1;
        e.stopPropagation(); e.preventDefault();
      });
      sc.addEventListener('pointermove', (e) => {
        if (pid !== e.pointerId) return;
        const r = el.getBoundingClientRect();
        const curDist = Math.hypot(e.clientX - (r.left + r.width), e.clientY - (r.top + r.height));
        const factor = curDist / Math.max(20, startDist);
        const cfg = el.dataset.type === 'joystick' ? layout.joystick
                  : layout.keys.find(k => k.code === el.dataset.code);
        cfg.scale = Math.max(0.4, Math.min(3, startScale * factor));
        el.style.transform = `translate(-50%, -50%) scale(${cfg.scale})`;
      });
      sc.addEventListener('pointerup', () => { pid = null; save(layout); });
      el.appendChild(del);
      el.appendChild(sc);
    });
  }

  function setEditing(on) {
    editing = !!on;
    if (!hostEl) return;
    showHandles(editing);
  }

  function isEditing() { return editing; }

  function mount(target) {
    hostEl = target;
    rebuild();
  }

  function setCurrentTarget(el) { currentTarget = el; }

  function unmount() {
    if (hostEl) hostEl.innerHTML = '';
    hostEl = null;
    currentTarget = null;
    editing = false;
  }

  function getLayout() { return layout || load(); }
  function setLayout(l) { layout = l; save(layout); rebuild(); }
  function resetLayout() { layout = defaultLayout(); save(layout); rebuild(); }

  // ---- add/remove a key from editor -------------------------------------

  function addKey(code) {
    if (!layout) layout = load();
    const newK = { type: 'key', code, x: 0.85, y: 0.5, scale: 1 };
    layout.keys.push(newK);
    save(layout);
    if (hostEl) {
      const el = makeKey(newK);
      makeDraggable(el, newK);
      hostEl.appendChild(el);
    }
    return newK;
  }

  function removeKey(code) {
    if (!layout) layout = load();
    layout.keys = layout.keys.filter(k => k.code !== code);
    save(layout);
    rebuild();
  }

  function setJoystickMode(mode) {
    if (!layout) layout = load();
    layout.joystick.mode = mode;
    save(layout);
    rebuild();
  }

  function setJoystickLayout(l) {
    if (!layout) layout = load();
    layout.joystick.layout = l;
    save(layout);
    rebuild();
  }

  global.VPad = {
    mount, unmount, setCurrentTarget,
    setEditing, isEditing,
    getLayout, setLayout, resetLayout,
    addKey, removeKey,
    setJoystickMode, setJoystickLayout,
    KEY_CODES, KEY_LABEL, defaultLayout
  };
})(window);

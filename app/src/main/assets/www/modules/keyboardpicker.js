/* ==========================================================================
 * keyboardpicker.js - on-screen visual keyboard for picking a key code
 * ========================================================================== */
(function (global) {
  'use strict';

  // US QWERTY layout as 2D arrays
  const ROWS = [
    [
      { code: 'Escape', label: 'Esc' },
      { code: 'F1', label: 'F1' }, { code: 'F2', label: 'F2' }, { code: 'F3', label: 'F3' },
      { code: 'F4', label: 'F4' }, { code: 'F5', label: 'F5' }, { code: 'F6', label: 'F6' },
      { code: 'F7', label: 'F7' }, { code: 'F8', label: 'F8' }, { code: 'F9', label: 'F9' },
      { code: 'F10', label: 'F10' }, { code: 'F11', label: 'F11' }, { code: 'F12', label: 'F12' }
    ],
    [
      { code: 'Backquote', label: '`' }, { code: 'Digit1', label: '1' }, { code: 'Digit2', label: '2' },
      { code: 'Digit3', label: '3' }, { code: 'Digit4', label: '4' }, { code: 'Digit5', label: '5' },
      { code: 'Digit6', label: '6' }, { code: 'Digit7', label: '7' }, { code: 'Digit8', label: '8' },
      { code: 'Digit9', label: '9' }, { code: 'Digit0', label: '0' },
      { code: 'Minus', label: '-' }, { code: 'Equal', label: '=' },
      { code: 'Backspace', label: 'Bksp', wide: true }
    ],
    [
      { code: 'Tab', label: 'Tab', wide: true },
      { code: 'KeyQ', label: 'Q' }, { code: 'KeyW', label: 'W' }, { code: 'KeyE', label: 'E' },
      { code: 'KeyR', label: 'R' }, { code: 'KeyT', label: 'T' }, { code: 'KeyY', label: 'Y' },
      { code: 'KeyU', label: 'U' }, { code: 'KeyI', label: 'I' }, { code: 'KeyO', label: 'O' },
      { code: 'KeyP', label: 'P' },
      { code: 'BracketLeft', label: '[' }, { code: 'BracketRight', label: ']' },
      { code: 'Backslash', label: '\\' }
    ],
    [
      { code: 'CapsLock', label: 'Caps', xwide: true },
      { code: 'KeyA', label: 'A' }, { code: 'KeyS', label: 'S' }, { code: 'KeyD', label: 'D' },
      { code: 'KeyF', label: 'F' }, { code: 'KeyG', label: 'G' }, { code: 'KeyH', label: 'H' },
      { code: 'KeyJ', label: 'J' }, { code: 'KeyK', label: 'K' }, { code: 'KeyL', label: 'L' },
      { code: 'Semicolon', label: ';' }, { code: 'Quote', label: "'" },
      { code: 'Enter', label: 'Enter', wide: true }
    ],
    [
      { code: 'ShiftLeft', label: 'Shift', xwide: true },
      { code: 'KeyZ', label: 'Z' }, { code: 'KeyX', label: 'X' }, { code: 'KeyC', label: 'C' },
      { code: 'KeyV', label: 'V' }, { code: 'KeyB', label: 'B' },
      { code: 'KeyN', label: 'N' }, { code: 'KeyM', label: 'M' },
      { code: 'Comma', label: ',' }, { code: 'Period', label: '.' }, { code: 'Slash', label: '/' },
      { code: 'ShiftRight', label: 'Shift', xwide: true }
    ],
    [
      { code: 'ControlLeft', label: 'Ctrl' },
      { code: 'MetaLeft', label: 'Win' },
      { code: 'AltLeft', label: 'Alt' },
      { code: 'Space', label: 'Space', xwide: true },
      { code: 'AltRight', label: 'Alt' },
      { code: 'ContextMenu', label: 'Menu' },
      { code: 'ControlRight', label: 'Ctrl' }
    ],
    [
      { code: 'ArrowUp', label: '↑' }, { code: 'ArrowDown', label: '↓' },
      { code: 'ArrowLeft', label: '←' }, { code: 'ArrowRight', label: '→' }
    ]
  ];

  // Numpad as separate page
  const NUMPAD = [
    [
      { code: 'NumLock', label: 'Num' },
      { code: 'NumpadDivide', label: '/' },
      { code: 'NumpadMultiply', label: '*' },
      { code: 'NumpadSubtract', label: '-' }
    ],
    [
      { code: 'Numpad7', label: '7' }, { code: 'Numpad8', label: '8' },
      { code: 'Numpad9', label: '9' }, { code: 'NumpadAdd', label: '+' }
    ],
    [
      { code: 'Numpad4', label: '4' }, { code: 'Numpad5', label: '5' },
      { code: 'Numpad6', label: '6' }, { code: 'NumpadEnter', label: '⏎' }
    ],
    [
      { code: 'Numpad1', label: '1' }, { code: 'Numpad2', label: '2' },
      { code: 'Numpad3', label: '3' }, { code: 'NumpadDecimal', label: '.' }
    ],
    [
      { code: 'Numpad0', label: '0', xwide: true }
    ]
  ];

  const Picker = {};

  Picker.open = function ({ current, onPick, onCancel }) {
    const body = document.createElement('div');
    body.innerHTML = `
      <div class="kb-modal">
        <div class="kb-body">
          <div class="kb-search">
            <input type="search" placeholder="搜索按键（如 J、Space、Enter）" id="kbSearch" />
          </div>
          <div id="kbPages">
            <div class="kb-page" data-page="main"></div>
            <div class="kb-page" data-page="numpad" style="display:none"></div>
            <div class="kb-page" data-page="arrows" style="display:none"></div>
            <div class="kb-page" data-page="search" style="display:none"></div>
          </div>
          <div class="row" style="margin-top:8px;justify-content:center;gap:8px">
            <button class="chip" data-page="main">主键盘</button>
            <button class="chip" data-page="numpad">数字键</button>
            <button class="chip" data-page="arrows">方向键</button>
          </div>
        </div>
      </div>
    `;
    const fill = (pageName, rows) => {
      const p = body.querySelector(`.kb-page[data-page="${pageName}"]`);
      p.innerHTML = '';
      for (const row of rows) {
        const r = document.createElement('div');
        r.className = 'kb-key-row';
        for (const k of row) {
          const b = document.createElement('button');
          b.className = 'kb-key' + (k.wide ? ' wide' : '') + (k.xwide ? ' xwide' : '');
          b.textContent = k.label;
          b.dataset.code = k.code;
          b.dataset.label = k.label;
          if (k.code === current) {
            b.style.background = 'var(--accent)';
            b.style.color = '#fff';
          }
          r.appendChild(b);
        }
        p.appendChild(r);
      }
    };
    fill('main', ROWS);
    fill('numpad', NUMPAD);
    fill('arrows', [ROWS[ROWS.length - 1]]);

    const modal = Modal.open({
      title: '选择按键',
      size: 'lg',
      body,
      footer: `<button class="btn outline" data-act="cancel">取消</button>`
    });
    const pages = body.querySelectorAll('.kb-page');
    const chips = body.querySelectorAll('.chip');
    function showPage(name) {
      pages.forEach(p => p.style.display = p.dataset.page === name ? '' : 'none');
      chips.forEach(c => c.classList.toggle('active', c.dataset.page === name));
    }
    chips.forEach(c => c.addEventListener('click', () => showPage(c.dataset.page)));
    showPage('main');

    body.querySelectorAll('.kb-key').forEach(b => {
      b.addEventListener('click', () => {
        const code = b.dataset.code;
        if (onPick) onPick(code);
        modal.close();
      });
    });
    modal.box.querySelector('[data-act=cancel]').addEventListener('click', () => {
      if (onCancel) onCancel();
      modal.close();
    });
    // Search
    const search = body.querySelector('#kbSearch');
    const allPages = body.querySelector('.kb-modal');
    search.addEventListener('input', () => {
      const q = search.value.trim().toLowerCase();
      if (!q) { showPage('main'); return; }
      // Switch to search page showing all keys that match
      const all = [].concat(...ROWS, ...NUMPAD);
      const matches = all.filter(k =>
        k.code.toLowerCase().includes(q) || (k.label || '').toLowerCase().includes(q));
      fill('search', [matches]);
      showPage('search');
    });
    search.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') { modal.close(); }
    });
    setTimeout(() => search.focus(), 50);

    return modal;
  };

  global.Picker = Picker;
})(window);

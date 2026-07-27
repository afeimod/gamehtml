/* ==========================================================================
 * modal.js - simple modal helper
 * ========================================================================== */
(function (global) {
  'use strict';

  const stack = [];

  function open({ title, body, footer, size, onClose, dismissable }) {
    const root = document.getElementById('modal');
    if (!root) return null;
    close(); // close any existing
    root.classList.remove('hidden');
    root.innerHTML = '';

    const box = document.createElement('div');
    box.className = 'modal-box' + (size ? ' ' + size : '');
    box.innerHTML = `
      <div class="modal-header">
        <h3>${title || ''}</h3>
        <button class="modal-close" aria-label="关闭">×</button>
      </div>
      <div class="modal-body"></div>
      ${footer ? '<div class="modal-footer"></div>' : ''}
    `;
    root.appendChild(box);

    const bodyEl = box.querySelector('.modal-body');
    if (body instanceof Node) bodyEl.appendChild(body);
    else if (typeof body === 'string') bodyEl.innerHTML = body;

    if (footer) {
      const f = box.querySelector('.modal-footer');
      if (footer instanceof Node) f.appendChild(footer);
      else f.innerHTML = footer;
    }

    function closeFn() { close(onClose); }
    box.querySelector('.modal-close').addEventListener('click', closeFn);
    if (dismissable !== false) {
      root.addEventListener('click', (e) => { if (e.target === root) closeFn(); });
    }
    const escHandler = (e) => { if (e.key === 'Escape') closeFn(); };
    document.addEventListener('keydown', escHandler);

    stack.push({ root, box, escHandler, close: closeFn });
    return { close: closeFn, box };
  }

  function close(cb) {
    const item = stack.pop();
    if (!item) {
      const r = document.getElementById('modal');
      if (r) r.classList.add('hidden');
      if (cb) try { cb(); } catch (e) {}
      return;
    }
    document.removeEventListener('keydown', item.escHandler);
    item.root.classList.add('hidden');
    item.root.innerHTML = '';
    if (cb) try { cb(); } catch (e) {}
    if (item.onClose) try { item.onClose(); } catch (e) {}
  }

  function confirm({ title, message, okText, cancelText, danger }) {
    return new Promise((resolve) => {
      const m = open({
        title: title || '确认',
        body: `<div style="padding:8px 0;line-height:1.5;">${message || ''}</div>`,
        footer: `<button class="btn outline" data-act="cancel">${cancelText || '取消'}</button>
                 <button class="btn ${danger ? 'danger' : ''}" data-act="ok">${okText || '确定'}</button>`
      });
      m.box.querySelector('[data-act=cancel]').addEventListener('click', () => { m.close(); resolve(false); });
      m.box.querySelector('[data-act=ok]').addEventListener('click', () => { m.close(); resolve(true); });
    });
  }

  function prompt({ title, label, value, placeholder, okText, cancelText, type }) {
    return new Promise((resolve) => {
      const body = document.createElement('div');
      body.innerHTML = `
        <div class="form-row">
          <label>${label || ''}</label>
          <input type="${type || 'text'}" value="${(value || '').replace(/"/g, '&quot;')}" placeholder="${placeholder || ''}">
        </div>`;
      const m = open({
        title: title || '输入',
        body,
        footer: `<button class="btn outline" data-act="cancel">${cancelText || '取消'}</button>
                 <button class="btn" data-act="ok">${okText || '确定'}</button>`
      });
      const input = m.box.querySelector('input');
      input.focus(); input.select();
      m.box.querySelector('[data-act=cancel]').addEventListener('click', () => { m.close(); resolve(null); });
      m.box.querySelector('[data-act=ok]').addEventListener('click', () => {
        const v = input.value; m.close(); resolve(v);
      });
      input.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') m.box.querySelector('[data-act=ok]').click();
        if (e.key === 'Escape') m.box.querySelector('[data-act=cancel]').click();
      });
    });
  }

  global.Modal = { open, close, confirm, prompt };
})(window);

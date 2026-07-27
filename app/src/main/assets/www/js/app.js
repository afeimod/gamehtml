/* ==========================================================================
 * app.js - bootstrap
 *
 * Sets up routes, wires top-bar, drawer, adblock, default page, etc.
 * ========================================================================== */
(function (global) {
  'use strict';

  const App = {
    playSession: null  // {stage, engine, swfUrl, title, onClose}
  };

  // ------ top-bar wiring --------------------------------------------------
  function initTopBar() {
    U.on(U.$('#menuBtn'), 'click', () => U.toggleDrawer(true));
    U.on(U.$('#drawerOverlay'), 'click', () => U.toggleDrawer(false));
    U.on(U.$('#searchBtn'), 'click', () => toggleSearch(true));
    U.on(U.$('#searchClose'), 'click', () => toggleSearch(false));
    U.on(U.$('#historyBtn'), 'click', () => Router.go('/history'));
    U.on(U.$('#favBtn'), 'click', () => Router.go('/favorites'));
    U.on(U.$('#settingsBtn'), 'click', () => Router.go('/settings'));
    U.on(U.$$('.drawer-list li'), 'click', (e) => {
      const t = e.currentTarget.dataset.go;
      U.toggleDrawer(false);
      switch (t) {
        case 'home':         Router.go('/'); break;
        case 'local':        Router.go('/local'); break;
        case 'history':      Router.go('/history'); break;
        case 'favorites':    Router.go('/favorites'); break;
        case 'defaultpages': Router.go('/defaultpages'); break;
        case 'custompages':  Router.go('/custompages'); break;
        case 'engine':       Router.go('/engine'); break;
        case 'vpad':         Router.go('/vpad'); break;
        case 'adblock':      Router.go('/adblock'); break;
        case 'about':        Router.go('/about'); break;
      }
    });

    U.on(U.$('#searchInput'), 'input', U.debounce(() => {
      const q = U.$('#searchInput').value.trim();
      if (q) Router.go('/search', { q });
    }, 250));
  }

  function toggleSearch(on) {
    const sb = U.$('#searchBar');
    if (!sb) return;
    sb.classList.toggle('hidden', !on);
    if (on) U.$('#searchInput').focus();
    else U.$('#searchInput').value = '';
  }

  // ------ home / featured --------------------------------------------------
  function renderHome(root) {
    const page = location.hash;
    const html = `
      <div class="hero">
        <div class="hero-content">
          <h1>重温经典 Flash 游戏</h1>
          <p>集成 Ruffle / Waflash / swf2js 三大引擎，支持在线与本地播放，虚拟按键让你畅玩操作类游戏</p>
          <a class="cta" href="#/defaultpages">开始浏览 →</a>
        </div>
      </div>

      <div class="section">
        <div class="section-h"><h2>默认网页</h2><a class="more" href="#/defaultpages">更多</a></div>
        <div class="grid" id="homeDefaults"></div>
      </div>

      <div class="section">
        <div class="section-h"><h2>继续上次</h2><a class="more" href="#/history">更多</a></div>
        <div class="grid" id="homeRecent"></div>
      </div>

      <div class="section">
        <div class="section-h"><h2>收藏</h2><a class="more" href="#/favorites">更多</a></div>
        <div class="grid" id="homeFav"></div>
      </div>

      <div class="section">
        <div class="section-h"><h2>本地游戏</h2><a class="more" href="#/local">管理</a></div>
        <div class="grid" id="homeLocal"></div>
      </div>
    `;
    root.innerHTML = html;
    fillGrid(U.$('#homeDefaults'), DefaultPages.defaults().slice(0, 8).map(p => ({
      thumb: p.icon || '🌐',
      name: p.name,
      meta: p.desc || '',
      onClick: () => playWeb(p),
      onFav: null
    })));
    const recents = History.all().slice(0, 8);
    if (!recents.length) {
      U.$('#homeRecent').outerHTML = `<div class="empty">
        <div class="em-icon">🕘</div>
        <div class="em-title">还没有历史</div>
        <div class="em-msg">玩过的游戏会出现在这里</div>
      </div>`;
    } else {
      fillGrid(U.$('#homeRecent'), recents.map(e => ({
        thumb: e.icon || '🎮', name: e.title || e.name || e.url, meta: U.fmtTime(e.t),
        onClick: () => playUrl(e.url, e.title || e.name, e),
        onFav: () => Favorites.toggle({ url: e.url, title: e.title || e.name })
      })));
    }
    const favs = Favorites.all().slice(0, 8);
    if (!favs.length) {
      U.$('#homeFav').outerHTML = `<div class="empty">
        <div class="em-icon">⭐</div>
        <div class="em-title">还没有收藏</div>
        <div class="em-msg">点卡片右上角的星标即可收藏</div>
      </div>`;
    } else {
      fillGrid(U.$('#homeFav'), favs.map(e => ({
        thumb: e.icon || '⭐', name: e.title || e.name || e.url, meta: U.fmtTime(e.t),
        onClick: () => playUrl(e.url, e.title || e.name, e),
        onFav: () => { Favorites.remove(e.url); renderHome(root); }
      })));
    }
    const local = LocalFiles.all().slice(0, 8);
    if (!local.length) {
      U.$('#homeLocal').outerHTML = `<div class="empty">
        <div class="em-icon">📂</div>
        <div class="em-title">本地库是空的</div>
        <div class="em-msg"><a href="#/local">去添加 SWF 文件或文件夹 →</a></div>
      </div>`;
    } else {
      fillGrid(U.$('#homeLocal'), local.map(e => ({
        thumb: '💾', name: e.name, meta: U.fmtSize(e.size),
        onClick: () => playLocal(e),
        onFav: null
      })));
    }
  }

  function fillGrid(parent, items) {
    if (!parent) return;
    parent.innerHTML = '';
    if (!items.length) {
      parent.innerHTML = `<div class="empty" style="grid-column: 1 / -1;">
        <div class="em-icon">📭</div><div class="em-title">暂无内容</div></div>`;
      return;
    }
    for (const it of items) {
      const card = document.createElement('div');
      card.className = 'card';
      card.innerHTML = `
        <div class="thumb">${U.esc(it.thumb || '🎮')}</div>
        <div class="body">
          <div class="name">${U.esc(it.name)}</div>
          <div class="meta">${U.esc(it.meta || '')}</div>
        </div>
        ${it.onFav ? '<button class="fav-btn" aria-label="收藏">⭐</button>' : ''}
      `;
      card.addEventListener('click', () => it.onClick && it.onClick());
      if (it.onFav) {
        card.querySelector('.fav-btn').addEventListener('click', (e) => {
          e.stopPropagation(); it.onFav();
        });
      }
      parent.appendChild(card);
    }
  }

  // ------ engine select / quality / ratio UI -----------------------------

  function renderEngineSettings(root) {
    const engines = EnginesMod.list();
    const cfg = (id) => EnginesMod.configOf(id);
    const curEng = EnginesMod.currentEngineId();
    const html = `
      <div class="section">
        <div class="section-h"><h2>默认引擎</h2></div>
        <div class="grid" id="engList"></div>
      </div>
      <div class="section" id="engConfigSection"></div>
    `;
    root.innerHTML = html;
    const list = U.$('#engList');
    for (const e of engines) {
      const card = document.createElement('div');
      card.className = 'card';
      card.innerHTML = `
        <div class="thumb">⚙️</div>
        <div class="body">
          <div class="name">${U.esc(e.name)}</div>
          <div class="meta">${U.esc(e.description)}</div>
        </div>
        <div style="position:absolute;left:8px;bottom:8px">
          <label class="switch">
            <input type="radio" name="enginePick" value="${e.id}" ${curEng===e.id?'checked':''}>
            <span class="slider"></span>
          </label>
        </div>
      `;
      card.querySelector('input').addEventListener('change', () => {
        EnginesMod.setCurrentEngine(e.id);
        Toast.show('已选默认引擎：' + e.name);
        renderEngineConfig();
      });
      card.addEventListener('click', (ev) => {
        if (ev.target.tagName !== 'INPUT') {
          const input = card.querySelector('input');
          input.checked = true; input.dispatchEvent(new Event('change'));
        }
      });
      list.appendChild(card);
    }
    renderEngineConfig();

    function renderEngineConfig() {
      const sec = U.$('#engConfigSection');
      const id = EnginesMod.currentEngineId();
      const c = cfg(id);
      sec.innerHTML = `
        <div class="section-h"><h2>${U.esc(engines.find(x=>x.id===id).name)} 配置</h2></div>
        <div class="form-row">
          <label>画质 (quality)</label>
          <div class="qr-panel" id="qPanel">
            ${Quality.QUALITIES.map(q => `
              <div class="qr-chip ${c.quality===q.id?'active':''}" data-q="${q.id}">${U.esc(q.label)}</div>
            `).join('')}
          </div>
          <div class="help">Ruffle: high/medium/low/best · Waflash 启动时生效</div>
        </div>
        <div class="form-row">
          <label>画面比例</label>
          <div class="qr-panel" id="rPanel">
            ${Quality.RATIOS.map(r => `
              <div class="qr-chip ${c.scale===r.id || (c.scale===undefined && r.id==='16:9')?'active':''}" data-r="${r.id}">${U.esc(r.label)}</div>
            `).join('')}
          </div>
        </div>
        ${id==='ruffle' ? `
        <div class="form-row inline">
          <label>黑边 (letterbox)</label>
          <label class="switch">
            <input type="checkbox" id="letterbox" ${c.letterbox!=='off'?'checked':''}>
            <span class="slider"></span>
          </label>
        </div>
        <div class="form-row inline">
          <label>强制缩放 (forceScale)</label>
          <label class="switch">
            <input type="checkbox" id="forceScale" ${c.forceScale?'checked':''}>
            <span class="slider"></span>
          </label>
        </div>` : ''}
      `;
      sec.querySelectorAll('[data-q]').forEach(el => {
        el.addEventListener('click', () => {
          c.quality = el.dataset.q;
          EnginesMod.setConfig(id, c);
          sec.querySelectorAll('[data-q]').forEach(x => x.classList.toggle('active', x===el));
        });
      });
      sec.querySelectorAll('[data-r]').forEach(el => {
        el.addEventListener('click', () => {
          c.scale = el.dataset.r;
          EnginesMod.setConfig(id, c);
          sec.querySelectorAll('[data-r]').forEach(x => x.classList.toggle('active', x===el));
        });
      });
      const lb = sec.querySelector('#letterbox');
      if (lb) lb.addEventListener('change', () => { c.letterbox = lb.checked ? 'on' : 'off'; EnginesMod.setConfig(id, c); });
      const fs = sec.querySelector('#forceScale');
      if (fs) fs.addEventListener('change', () => { c.forceScale = fs.checked; EnginesMod.setConfig(id, c); });
    }
  }

  // ------ adblock settings page -------------------------------------------
  function renderAdblock(root) {
    Settings.render(root);
  }

  // ------ local files page -------------------------------------------------
  function renderLocal(root) {
    const list = LocalFiles.all();
    const roots = LocalFiles.getRoots();
    const html = `
      <div class="section">
        <div class="section-h"><h2>本地游戏库</h2>
          <div class="row gap-8">
            <button class="btn sm" id="addFile">+ 文件</button>
            <button class="btn sm" id="addFolder">+ 文件夹</button>
            <button class="btn sm outline" id="refresh">↻ 刷新</button>
          </div>
        </div>
        <div id="rootsList" class="row wrap gap-8 mb-12"></div>
        <input type="file" accept=".swf,application/x-shockwave-flash" id="fileInput" multiple style="display:none">
        <div id="fileList"></div>
      </div>
    `;
    root.innerHTML = html;

    U.on(U.$('#addFile'), 'click', () => U.$('#fileInput').click());
    U.on(U.$('#addFolder'), 'click', async () => {
      const r = await LocalFiles.pickFolder();
      if (r) Toast.show('已添加文件夹，刷新后查看');
    });
    U.on(U.$('#refresh'), 'click', () => { renderLocal(root); Toast.show('已刷新'); });
    U.on(U.$('#fileInput'), 'change', async (e) => {
      const files = Array.from(e.target.files || []);
      let n = 0;
      for (const f of files) {
        const id = await LocalFiles.addPickedFile(f);
        if (id) n++;
      }
      if (n) { Toast.show(`已添加 ${n} 个文件`); renderLocal(root); }
    });

    const rL = U.$('#rootsList');
    if (roots.length) {
      for (const r of roots) {
        const c = document.createElement('div');
        c.className = 'chip';
        c.textContent = '📁 ' + r.name + ' (' + r.fileCount + ')';
        c.title = '点击移除';
        c.style.cursor = 'pointer';
        c.addEventListener('click', () => {
          Modal.confirm({ title: '移除文件夹', message: `确定移除 ${r.name}？`, danger: true })
            .then(ok => {
              if (ok) {
                const newRoots = LocalFiles.getRoots().filter(x => x.id !== r.id);
                LocalFiles.setRoots(newRoots);
                renderLocal(root);
              }
            });
        });
        rL.appendChild(c);
      }
    } else {
      rL.innerHTML = '<span class="muted" style="font-size:12px">尚未添加文件夹，可点击上方「+ 文件夹」</span>';
    }

    const listEl = U.$('#fileList');
    if (!list.length) {
      listEl.innerHTML = `<div class="empty">
        <div class="em-icon">📂</div>
        <div class="em-title">本地库为空</div>
        <div class="em-msg">点击「+ 文件」选择 SWF，或「+ 文件夹」批量添加</div>
        <div class="btn-row" style="justify-content:center">
          <button class="btn" onclick="document.getElementById('fileInput').click()">+ 添加文件</button>
        </div>
      </div>`;
      return;
    }
    for (const e of list) {
      const item = document.createElement('div');
      item.className = 'entry';
      item.innerHTML = `
        <div class="icon">SWF</div>
        <div class="info">
          <div class="t">${U.esc(e.name)}</div>
          <div class="s">${U.esc(e.relPath || '')} · ${U.fmtSize(e.size)} · ${U.fmtTime(e.mtime)}</div>
        </div>
        <div class="actions">
          <button class="btn sm" data-act="play">播放</button>
          <button class="btn sm outline" data-act="del">删除</button>
        </div>
      `;
      item.querySelector('[data-act=play]').addEventListener('click', () => playLocal(e));
      item.querySelector('[data-act=del]').addEventListener('click', () => {
        Modal.confirm({ title: '删除', message: `确定删除 ${e.name}？`, danger: true })
          .then(ok => { if (ok) { LocalFiles.remove(e.id); renderLocal(root); } });
      });
      listEl.appendChild(item);
    }
  }

  // ------ history page ----------------------------------------------------
  function renderHistory(root) {
    const list = History.all();
    root.innerHTML = `
      <div class="section">
        <div class="section-h">
          <h2>历史记录（${list.length}）</h2>
          <button class="btn sm outline" id="clear">清空</button>
        </div>
        <div id="histList"></div>
      </div>`;
    U.on(U.$('#clear'), 'click', () => {
      Modal.confirm({ title: '清空历史', message: '将清空所有历史记录', danger: true })
        .then(ok => { if (ok) { History.clear(); renderHistory(root); } });
    });
    if (!list.length) {
      U.$('#histList').innerHTML = `<div class="empty">
        <div class="em-icon">🕘</div><div class="em-title">还没有历史</div>
        <div class="em-msg">玩过的游戏会按时间倒序出现在这里</div>
      </div>`;
      return;
    }
    const ll = U.$('#histList');
    for (const e of list) {
      const item = document.createElement('div');
      item.className = 'entry';
      item.innerHTML = `
        <div class="icon">${U.esc(e.icon || '🎮')}</div>
        <div class="info">
          <div class="t">${U.esc(e.title || e.name || e.url)}</div>
          <div class="s">${U.esc(e.engine || '')} · ${U.fmtTime(e.t)}</div>
        </div>
        <div class="actions">
          <button class="btn sm" data-act="play">播放</button>
          <button class="btn sm outline" data-act="fav">${Favorites.has(e.url)?'★ 已收藏':'☆ 收藏'}</button>
          <button class="btn sm outline" data-act="del">删除</button>
        </div>
      `;
      item.querySelector('[data-act=play]').addEventListener('click', () => playUrl(e.url, e.title || e.name, e));
      item.querySelector('[data-act=fav]').addEventListener('click', (ev) => {
        const added = Favorites.toggle({ url: e.url, title: e.title || e.name, engine: e.engine });
        ev.target.textContent = added ? '★ 已收藏' : '☆ 收藏';
      });
      item.querySelector('[data-act=del]').addEventListener('click', () => {
        History.remove(e.url); renderHistory(root);
      });
      ll.appendChild(item);
    }
  }

  // ------ favorites page --------------------------------------------------
  function renderFavorites(root) {
    const list = Favorites.all();
    root.innerHTML = `
      <div class="section">
        <div class="section-h">
          <h2>收藏（${list.length}）</h2>
          <button class="btn sm outline" id="clear">清空</button>
        </div>
        <div id="favList"></div>
      </div>`;
    U.on(U.$('#clear'), 'click', () => {
      Modal.confirm({ title: '清空收藏', message: '将清空所有收藏', danger: true })
        .then(ok => { if (ok) { Favorites.clear(); renderFavorites(root); } });
    });
    if (!list.length) {
      U.$('#favList').innerHTML = `<div class="empty">
        <div class="em-icon">⭐</div><div class="em-title">还没有收藏</div>
        <div class="em-msg">玩到喜欢的游戏时点 ☆ 加收藏</div>
      </div>`;
      return;
    }
    const ll = U.$('#favList');
    for (const e of list) {
      const item = document.createElement('div');
      item.className = 'entry';
      item.innerHTML = `
        <div class="icon">${U.esc(e.icon || '⭐')}</div>
        <div class="info">
          <div class="t">${U.esc(e.title || e.name || e.url)}</div>
          <div class="s">${U.esc(e.engine || '')} · ${U.fmtTime(e.t)}</div>
        </div>
        <div class="actions">
          <button class="btn sm" data-act="play">播放</button>
          <button class="btn sm outline" data-act="del">删除</button>
        </div>
      `;
      item.querySelector('[data-act=play]').addEventListener('click', () => playUrl(e.url, e.title || e.name, e));
      item.querySelector('[data-act=del]').addEventListener('click', () => {
        Favorites.remove(e.url); renderFavorites(root);
      });
      ll.appendChild(item);
    }
  }

  // ------ default pages ---------------------------------------------------
  function renderDefaultPages(root) {
    const def = DefaultPages.defaults();
    const cur = DefaultPages.currentDefaultId();
    root.innerHTML = `
      <div class="section">
        <div class="section-h"><h2>默认网页</h2>
          <a class="more" href="#/custompages">+ 自定义网页</a>
        </div>
        <div class="grid" id="defList"></div>
        <div class="form-row mt-12">
          <button class="btn" id="openCur">打开当前默认页：${U.esc((def.find(d=>d.id===cur)||{}).name || '未设置')}</button>
        </div>
      </div>
    `;
    const l = U.$('#defList');
    for (const p of def) {
      const card = document.createElement('div');
      card.className = 'card';
      card.innerHTML = `
        <div class="thumb">${U.esc(p.icon || '🌐')}</div>
        <div class="body">
          <div class="name">${U.esc(p.name)}</div>
          <div class="meta">${U.esc(p.desc || '')}</div>
        </div>
        ${p.id === cur ? '<div class="badge">默认</div>' : ''}
      `;
      card.addEventListener('click', () => {
        Modal.open({
          title: p.name,
          body: `<div style="line-height:1.6">
            <div>${U.esc(p.desc || '')}</div>
            <div class="muted" style="margin-top:6px;word-break:break-all">${U.esc(p.url)}</div>
          </div>`,
          footer: `<button class="btn outline" data-act="set">设为默认</button>
                   <button class="btn" data-act="open">打开</button>`
        });
        // Wire up footer buttons after modal opens
        setTimeout(() => {
          const m = document.querySelector('.modal-box');
          if (!m) return;
          m.querySelector('[data-act=set]').addEventListener('click', () => {
            DefaultPages.setCurrentDefaultId(p.id);
            Toast.show('已设为默认'); Modal.close();
            renderDefaultPages(root);
          });
          m.querySelector('[data-act=open]').addEventListener('click', () => {
            Modal.close(); playWeb(p);
          });
        }, 0);
      });
      l.appendChild(card);
    }
    U.on(U.$('#openCur'), 'click', () => {
      const id = DefaultPages.currentDefaultId();
      const p = DefaultPages.findById(id);
      if (p) playWeb(p);
      else Toast.show('请先选择默认页');
    });
  }

  // ------ custom pages ----------------------------------------------------
  function renderCustomPages(root) {
    const list = DefaultPages.customs();
    root.innerHTML = `
      <div class="section">
        <div class="section-h">
          <h2>自定义网页（${list.length}）</h2>
          <div class="row gap-8">
            <button class="btn sm" id="add">+ 添加</button>
            <button class="btn sm outline" id="clear">清空</button>
          </div>
        </div>
        <div id="cpList"></div>
      </div>`;
    U.on(U.$('#add'), 'click', () => addCustomPageDialog().then(p => {
      if (p) { renderCustomPages(root); Toast.show('已添加'); }
    }));
    U.on(U.$('#clear'), 'click', () => {
      Modal.confirm({ title: '清空', message: '将清空所有自定义网页', danger: true })
        .then(ok => { if (ok) { DefaultPages.clearCustom(); renderCustomPages(root); } });
    });

    if (!list.length) {
      U.$('#cpList').innerHTML = `<div class="empty">
        <div class="em-icon">➕</div><div class="em-title">没有自定义网页</div>
        <div class="em-msg">把常用的网页 URL 收录到这里，一键打开</div>
      </div>`;
      return;
    }
    for (const p of list) {
      const item = document.createElement('div');
      item.className = 'entry';
      item.innerHTML = `
        <div class="icon">${U.esc(p.icon || '🌐')}</div>
        <div class="info">
          <div class="t">${U.esc(p.name)}</div>
          <div class="s">${U.esc(p.url)}</div>
        </div>
        <div class="actions">
          <button class="btn sm" data-act="play">打开</button>
          <button class="btn sm outline" data-act="edit">编辑</button>
          <button class="btn sm outline" data-act="del">删除</button>
        </div>
      `;
      item.querySelector('[data-act=play]').addEventListener('click', () => playWeb(p));
      item.querySelector('[data-act=edit]').addEventListener('click', () => {
        addCustomPageDialog(p).then(np => { if (np) renderCustomPages(root); });
      });
      item.querySelector('[data-act=del]').addEventListener('click', () => {
        Modal.confirm({ title: '删除', message: `确定删除 ${p.name}？`, danger: true })
          .then(ok => { if (ok) { DefaultPages.removeCustom(p.id); renderCustomPages(root); } });
      });
      document.getElementById('cpList').appendChild(item);
    }
  }

  function addCustomPageDialog(p) {
    return new Promise((resolve) => {
      const body = document.createElement('div');
      body.innerHTML = `
        <div class="form-row"><label>名称</label><input id="cpName" value="${U.esc(p?.name || '')}" placeholder="我的游戏站"></div>
        <div class="form-row"><label>URL（http/https）</label><input id="cpUrl" value="${U.esc(p?.url || '')}" placeholder="https://..."></div>
        <div class="form-row"><label>图标（emoji）</label><input id="cpIcon" value="${U.esc(p?.icon || '🌐')}" maxlength="2"></div>
        <div class="form-row"><label>说明</label><input id="cpDesc" value="${U.esc(p?.desc || '')}" placeholder="选填"></div>
        <div class="form-row">
          <label>模式</label>
          <select id="cpMode">
            <option value="desktop" ${p?.mode==='desktop'?'selected':''}>桌面模式</option>
            <option value="mobile"  ${p?.mode==='mobile'?'selected':''}>移动模式</option>
            <option value="compat"  ${p?.mode==='compat'?'selected':''}>兼容模式</option>
            <option value="auto"    ${(!p?.mode || p?.mode==='auto')?'selected':''}>自动</option>
          </select>
        </div>`;
      const m = Modal.open({
        title: p ? '编辑自定义网页' : '添加自定义网页',
        body,
        footer: `<button class="btn outline" data-act="cancel">取消</button>
                 <button class="btn" data-act="ok">${p?'保存':'添加'}</button>`
      });
      m.box.querySelector('[data-act=cancel]').addEventListener('click', () => { m.close(); resolve(null); });
      m.box.querySelector('[data-act=ok]').addEventListener('click', () => {
        const data = {
          name: m.box.querySelector('#cpName').value.trim() || '未命名',
          url:  m.box.querySelector('#cpUrl').value.trim(),
          icon: m.box.querySelector('#cpIcon').value.trim() || '🌐',
          desc: m.box.querySelector('#cpDesc').value.trim(),
          mode: m.box.querySelector('#cpMode').value
        };
        if (!/^https?:\/\//i.test(data.url)) { Toast.show('URL 必须以 http(s):// 开头'); return; }
        if (p) DefaultPages.updateCustom(p.id, data);
        else DefaultPages.addCustom(data);
        m.close();
        resolve(data);
      });
    });
  }

  // ------ vpad settings page ----------------------------------------------
  function renderVPadSettings(root) {
    const lay = VPad.getLayout();
    root.innerHTML = `
      <div class="section">
        <div class="section-h">
          <h2>虚拟按键</h2>
          <div class="row gap-8">
            <button class="btn sm" id="addKey">+ 添加按键</button>
            <button class="btn sm outline" id="reset">重置</button>
          </div>
        </div>
        <div class="form-row inline">
          <label>方向控制方式</label>
          <select id="stickMode">
            <option value="joystick" ${lay.joystick.mode==='joystick'?'selected':''}>摇杆</option>
            <option value="dpad" ${lay.joystick.mode==='dpad'?'selected':''}>方向键</option>
          </select>
          <select id="stickLayout">
            <option value="wsad" ${lay.joystick.layout==='wsad'?'selected':''}>WSAD</option>
            <option value="arrows" ${lay.joystick.layout==='arrows'?'selected':''}>↑↓←→</option>
          </select>
        </div>
        <div class="form-row">
          <button class="btn" id="enterEdit">进入编辑模式</button>
          <button class="btn outline" id="exitEdit" style="display:none">退出编辑</button>
          <div class="help">长按任意按键/摇杆可弹出操作菜单（修改、缩放、删除等）</div>
        </div>
        <div class="form-row">
          <label>当前按键（${lay.keys.length}）</label>
          <div id="keyList"></div>
        </div>
      </div>
    `;
    U.on(U.$('#addKey'), 'click', () => {
      Picker.open({
        onPick: (code) => {
          VPad.addKey(code);
          Toast.show('已添加 ' + (VPad.KEY_LABEL[code] || code));
          renderVPadSettings(root);
        }
      });
    });
    U.on(U.$('#reset'), 'click', () => {
      Modal.confirm({ title: '重置', message: '重置虚拟按键为默认布局？', danger: true })
        .then(ok => { if (ok) { VPad.resetLayout(); renderVPadSettings(root); Toast.show('已重置'); } });
    });
    U.on(U.$('#stickMode'), 'change', (e) => VPad.setJoystickMode(e.target.value));
    U.on(U.$('#stickLayout'), 'change', (e) => VPad.setJoystickLayout(e.target.value));
    U.on(U.$('#enterEdit'), 'click', () => {
      VPad.setEditing(true);
      U.$('#enterEdit').style.display = 'none';
      U.$('#exitEdit').style.display = '';
      // Mount vpad over the page (so the user can see it while editing)
      const host = document.createElement('div');
      host.id = 'vpadEditHost';
      host.style.cssText = 'position:fixed;inset:0;z-index:230;pointer-events:none';
      document.body.appendChild(host);
      VPad.mount(host);
      Toast.show('长按按键/摇杆可编辑，编辑完点退出');
    });
    U.on(U.$('#exitEdit'), 'click', () => {
      VPad.setEditing(false);
      const h = U.$('#vpadEditHost'); if (h) h.remove();
      VPad.unmount();
      U.$('#enterEdit').style.display = '';
      U.$('#exitEdit').style.display = 'none';
    });

    const kList = U.$('#keyList');
    if (!lay.keys.length) {
      kList.innerHTML = '<div class="muted" style="font-size:12px">没有独立按键，点击上方「+ 添加按键」</div>';
    } else {
      for (const k of lay.keys) {
        const item = document.createElement('div');
        item.className = 'entry';
        item.innerHTML = `
          <div class="icon">⌨️</div>
          <div class="info">
            <div class="t">${U.esc(k.label || VPad.KEY_LABEL[k.code] || k.code)}</div>
            <div class="s">${U.esc(k.code)} · 位置 (${(k.x*100).toFixed(0)}%, ${(k.y*100).toFixed(0)}%) · 缩放 ${(k.scale||1).toFixed(2)}</div>
          </div>
          <div class="actions">
            <button class="btn sm outline" data-act="edit">修改</button>
            <button class="btn sm outline" data-act="del">删除</button>
          </div>
        `;
        item.querySelector('[data-act=edit]').addEventListener('click', () => {
          Picker.open({
            current: k.code,
            onPick: (code) => {
              k.code = code; k.label = null;
              VPad.setLayout(lay);
              renderVPadSettings(root);
            }
          });
        });
        item.querySelector('[data-act=del]').addEventListener('click', () => {
          Modal.confirm({ title: '删除按键', message: `确定删除？`, danger: true })
            .then(ok => { if (ok) { VPad.removeKey(k.code); renderVPadSettings(root); } });
        });
        kList.appendChild(item);
      }
    }
  }

  // ------ about page ------------------------------------------------------
  function renderAbout(root) {
    root.innerHTML = `
      <div class="section">
        <div class="section-h"><h2>关于 Flash 游戏盒</h2></div>
        <div class="form-row">
          <p>Flash Game Box 是一个开源的安卓端 Flash 游戏盒，集成 Ruffle / Waflash / swf2js 三大引擎，让你在手机上重温经典 Flash 游戏。</p>
        </div>
        <div class="form-row">
          <h3>特性</h3>
          <ul style="line-height:1.8">
            <li>✅ 三个 Flash 引擎可选</li>
            <li>✅ 在线 / 本地 SWF 播放</li>
            <li>✅ 内置虚拟按键（摇杆 + 方向键 + 独立按键）</li>
            <li>✅ 每个按键支持缩放、移动、修改</li>
            <li>✅ 默认网页（4399 PC/手机版、灵动游戏等）</li>
            <li>✅ 自定义网页 + 历史 + 收藏</li>
            <li>✅ 广告拦截（网络层 + DOM 层）</li>
            <li>✅ 画质 / 画面比例可调</li>
            <li>✅ 高级 HTML5 响应式 UI</li>
          </ul>
        </div>
        <div class="form-row">
          <h3>致谢</h3>
          <p>Ruffle · Waflash · swf2js · Material Design</p>
        </div>
        <div class="form-row">
          <h3>协议</h3>
          <p>主项目 MIT · 第三方引擎保留各自协议</p>
        </div>
      </div>
    `;
  }

  // ------ search page -----------------------------------------------------
  function renderSearch(root, params) {
    const q = (params.q || '').toLowerCase();
    root.innerHTML = `
      <div class="section">
        <div class="section-h"><h2>搜索：${U.esc(params.q || '')}</h2></div>
        <div id="searchResults"></div>
      </div>`;
    const results = [];
    for (const p of DefaultPages.defaults()) {
      if ((p.name + p.url + (p.desc||'')).toLowerCase().includes(q)) {
        results.push({ thumb: p.icon || '🌐', name: p.name, meta: p.desc, onClick: () => playWeb(p) });
      }
    }
    for (const p of DefaultPages.customs()) {
      if ((p.name + p.url + (p.desc||'')).toLowerCase().includes(q)) {
        results.push({ thumb: p.icon || '🌐', name: p.name, meta: p.url, onClick: () => playWeb(p) });
      }
    }
    for (const e of LocalFiles.all()) {
      if (e.name.toLowerCase().includes(q)) {
        results.push({ thumb: '💾', name: e.name, meta: U.fmtSize(e.size), onClick: () => playLocal(e) });
      }
    }
    for (const e of History.all()) {
      if ((e.title || e.url).toLowerCase().includes(q)) {
        results.push({ thumb: e.icon || '🎮', name: e.title || e.url, meta: '历史', onClick: () => playUrl(e.url, e.title || e.name, e) });
      }
    }
    for (const e of Favorites.all()) {
      if ((e.title || e.url).toLowerCase().includes(q)) {
        results.push({ thumb: e.icon || '⭐', name: e.title || e.url, meta: '收藏', onClick: () => playUrl(e.url, e.title || e.name, e) });
      }
    }
    if (!results.length) {
      U.$('#searchResults').innerHTML = `<div class="empty">
        <div class="em-icon">🔍</div><div class="em-title">没有匹配结果</div>
      </div>`;
      return;
    }
    fillGrid(U.$('#searchResults'), results);
  }

  // ------ play session ----------------------------------------------------
  // Plays a SWF URL (online)
  function playUrl(url, title, meta) {
    if (!/^https?:\/\//i.test(url) && !url.startsWith('content:') && !url.startsWith('blob:') && !url.startsWith('data:')) {
      Toast.show('URL 格式错误');
      return;
    }
    openPlayScreen({
      title: title || url,
      swfUrl: url,
      engineId: EnginesMod.currentEngineId(),
      sourceType: 'url',
      meta: meta || { url, title }
    });
  }

  // Plays a local file
  function playLocal(entry) {
    const url = LocalFiles.getObjectUrl(entry);
    if (!url) { Toast.show('无法获取文件 URL'); return; }
    openPlayScreen({
      title: entry.name,
      swfUrl: url,
      engineId: EnginesMod.currentEngineId(),
      sourceType: 'local',
      meta: { url, title: entry.name }
    });
  }

  // Plays a web page
  function playWeb(p) {
    if (!p.url) { Toast.show('缺少 URL'); return; }
    openPlayScreen({
      title: p.name,
      webUrl: p.url,
      mode: p.mode || 'auto',
      sourceType: 'web',
      meta: { url: p.url, title: p.name, icon: p.icon }
    });
  }

  function openPlayScreen(opts) {
    if (App.playSession) { closePlayScreen(); }
    const screen = document.createElement('div');
    screen.className = 'play-screen';
    screen.id = 'playScreen';
    const safeTop = 'env(safe-area-inset-top,0)';
    const safeBot = 'env(safe-area-inset-bottom,0)';
    screen.innerHTML = `
      <div class="play-header" style="padding-top:calc(8px + ${safeTop})">
        <button class="icon-btn" id="psBack" aria-label="返回">‹</button>
        <div class="title" id="psTitle">${U.esc(opts.title || '游戏')}</div>
        <button class="icon-btn" id="psEngine" aria-label="切换引擎"><span class="engine-tag" id="psEngineTag">Ruffle</span></button>
        <button class="icon-btn" id="psQuality" aria-label="画质">⚙</button>
        <button class="icon-btn" id="psFs" aria-label="全屏">⛶</button>
      </div>
      <div class="play-stage" id="psStage"></div>
      <div class="play-footer" style="padding-bottom:calc(6px + ${safeBot})">
        <div class="group">
          <button class="icon-btn" id="psRefresh" aria-label="刷新">↻</button>
          <button class="icon-btn" id="psFav" aria-label="收藏">${Favorites.has(opts.meta?.url) ? '★' : '☆'}</button>
          <button class="icon-btn" id="psHist" aria-label="历史">🕘</button>
        </div>
        <div class="group">
          <button class="icon-btn" id="psVpad" aria-label="虚拟按键">🎮</button>
          <button class="icon-btn" id="psMore" aria-label="更多">⋮</button>
        </div>
      </div>
    `;
    document.body.appendChild(screen);
    document.body.style.overflow = 'hidden';

    // Pop in animation
    requestAnimationFrame(() => { screen.style.opacity = '1'; });

    const stage = U.$('#psStage');
    const engineTag = U.$('#psEngineTag');
    const engines = EnginesMod.list();

    let currentEngine = opts.engineId || EnginesMod.currentEngineId();
    let player = null;
    let vpadMounted = false;
    const vpadHost = U.$('#vpadHost');

    async function mount(opts2) {
      stage.innerHTML = '';
      if (vpadMounted) VPad.unmount();
      vpadMounted = false;

      if (opts2.webUrl) {
        // Web page
        const mode = opts2.mode || U.kv.get('web_mode', 'auto');
        const isMobile = (mode === 'mobile') ||
                         (mode === 'auto' && window.innerWidth < 800);
        const iframe = document.createElement('iframe');
        iframe.src = opts2.webUrl;
        iframe.style.cssText = 'width:100%;height:100%;border:0;background:#fff';
        if (isMobile) {
          iframe.setAttribute('sandbox', 'allow-scripts allow-same-origin allow-forms allow-popups allow-popups-to-escape-sandbox allow-downloads');
        } else {
          // desktop mode
          const widthMeta = document.createElement('meta');
          widthMeta.name = 'viewport';
          widthMeta.content = 'width=1280';
          try { iframe.contentDocument.head.appendChild(widthMeta); } catch (e) {}
        }
        stage.appendChild(iframe);
        // In-page adblock for the iframe contents (we can't reach its DOM, but
        // the network-layer native adblock still applies)
        engineTag.textContent = '网页';
        return;
      }

      // SWF play
      engineTag.textContent = (engines.find(e => e.id === currentEngine) || {}).name || currentEngine;
      const cfg = EnginesMod.configOf(currentEngine);
      const eng = EnginesMod.get(currentEngine);
      try {
        player = await eng.load(stage, opts2.swfUrl, cfg);
        // Quality.applyStageRatio(stage); // Optional CSS aspect ratio
        // vpad: target the canvas
        setTimeout(() => {
          const canvas = stage.querySelector('canvas, .ruffle-container, ruffle-player, [data-ruffle-player]');
          if (canvas) {
            VPad.setCurrentTarget(canvas);
            VPad.mount(vpadHost);
            vpadMounted = true;
          }
        }, 50);
      } catch (e) {
        stage.innerHTML = `<div class="empty" style="color:#aaa">
          <div class="em-icon">⚠️</div>
          <div class="em-title">加载失败</div>
          <div class="em-msg">${U.esc(e.message || String(e))}</div>
          <div class="btn-row" style="justify-content:center">
            <button class="btn" id="tryOther">尝试其他引擎</button>
          </div>
        </div>`;
        U.on(U.$('#tryOther'), 'click', () => {
          const idx = engines.findIndex(e => e.id === currentEngine);
          const next = engines[(idx + 1) % engines.length];
          currentEngine = next.id;
          EnginesMod.setCurrentEngine(currentEngine);
          mount(opts2);
        });
      }
    }

    function close() { closePlayScreen(); }
    U.on(U.$('#psBack'), 'click', close);
    U.on(U.$('#psMore'), 'click', () => {
      Modal.open({
        title: '更多',
        body: `<div class="form-row"><label>分享 URL</label><input value="${U.esc(opts.meta?.url || '')}" readonly></div>`,
        footer: `<button class="btn outline" data-act="copy">复制链接</button>
                 <button class="btn" data-act="share">分享</button>
                 <button class="btn outline" data-act="open">外部打开</button>`
      });
      setTimeout(() => {
        const m = document.querySelector('.modal-box');
        if (!m) return;
        m.querySelector('[data-act=copy]').addEventListener('click', () => {
          if (window.FlashBox && window.FlashBox.copyToClipboard) window.FlashBox.copyToClipboard(opts.meta?.url);
          else navigator.clipboard.writeText(opts.meta?.url);
          Toast.show('已复制');
        });
        m.querySelector('[data-act=share]').addEventListener('click', () => {
          if (window.FlashBox && window.FlashBox.shareText)
            window.FlashBox.shareText(opts.meta?.url, opts.title);
          else if (navigator.share) navigator.share({ url: opts.meta?.url, title: opts.title });
          else Toast.show('当前环境不支持分享');
        });
        m.querySelector('[data-act=open]').addEventListener('click', () => {
          if (window.FlashBox && window.FlashBox.openExternal)
            window.FlashBox.openExternal(opts.meta?.url);
          Modal.close();
        });
      }, 0);
    });
    U.on(U.$('#psEngine'), 'click', () => {
      // Build engine picker
      const items = engines.map(e => `<button class="btn ${e.id===currentEngine?'':'outline'}" data-eng="${e.id}">${U.esc(e.name)}</button>`).join('');
      Modal.open({
        title: '切换引擎',
        size: 'sm',
        body: `<div class="btn-row" style="flex-direction:column;align-items:stretch">${items}</div>
               <div class="muted" style="font-size:12px;margin-top:8px">不同引擎对同一 SWF 的兼容性不同</div>`,
        footer: `<button class="btn outline" data-act="cancel">关闭</button>`
      });
      setTimeout(() => {
        const m = document.querySelector('.modal-box');
        if (!m) return;
        m.querySelectorAll('[data-eng]').forEach(b => {
          b.addEventListener('click', () => {
            currentEngine = b.dataset.eng;
            EnginesMod.setCurrentEngine(currentEngine);
            Modal.close();
            mount(opts);
          });
        });
        m.querySelector('[data-act=cancel]').addEventListener('click', () => Modal.close());
      }, 0);
    });
    U.on(U.$('#psQuality'), 'click', () => {
      const c = EnginesMod.configOf(currentEngine);
      const qChips = Quality.QUALITIES.map(q =>
        `<div class="qr-chip ${c.quality===q.id?'active':''}" data-q="${q.id}">${U.esc(q.label)}</div>`).join('');
      const rChips = Quality.RATIOS.map(r =>
        `<div class="qr-chip ${c.scale===r.id?'active':''}" data-r="${r.id}">${U.esc(r.label)}</div>`).join('');
      Modal.open({
        title: '画质 / 比例',
        size: 'sm',
        body: `
          <div class="form-row"><label>画质</label><div class="qr-panel">${qChips}</div></div>
          <div class="form-row"><label>比例</label><div class="qr-panel">${rChips}</div></div>
        `,
        footer: `<button class="btn" data-act="ok">应用</button>`
      });
      setTimeout(() => {
        const m = document.querySelector('.modal-box');
        if (!m) return;
        m.querySelectorAll('[data-q]').forEach(el => el.addEventListener('click', () => {
          c.quality = el.dataset.q;
          m.querySelectorAll('[data-q]').forEach(x => x.classList.toggle('active', x===el));
        }));
        m.querySelectorAll('[data-r]').forEach(el => el.addEventListener('click', () => {
          c.scale = el.dataset.r;
          m.querySelectorAll('[data-r]').forEach(x => x.classList.toggle('active', x===el));
        }));
        m.querySelector('[data-act=ok]').addEventListener('click', () => {
          EnginesMod.setConfig(currentEngine, c);
          Toast.show('已应用，下次播放生效');
          Modal.close();
        });
      }, 0);
    });
    U.on(U.$('#psFs'), 'click', () => {
      // Try fullscreen
      const doc = document.documentElement;
      if (doc.requestFullscreen) doc.requestFullscreen().catch(() => {});
    });
    U.on(U.$('#psRefresh'), 'click', () => mount(opts));
    U.on(U.$('#psFav'), 'click', () => {
      const url = opts.meta?.url;
      if (!url) return;
      const added = Favorites.toggle(Object.assign({}, opts.meta, { engine: currentEngine }));
      U.$('#psFav').textContent = added ? '★' : '☆';
      Toast.show(added ? '已收藏' : '已取消收藏');
    });
    U.on(U.$('#psHist'), 'click', () => {
      const url = opts.meta?.url;
      if (!url) return;
      History.add(Object.assign({}, opts.meta, { engine: currentEngine }));
      Toast.show('已加入历史');
    });
    U.on(U.$('#psVpad'), 'click', () => {
      // Toggle vpad visibility / edit mode
      if (!vpadMounted) {
        // Use first canvas we can find
        const canvas = stage.querySelector('canvas, .ruffle-container, ruffle-player, [data-ruffle-player]');
        if (canvas) {
          VPad.setCurrentTarget(canvas);
          VPad.mount(vpadHost);
          vpadMounted = true;
        } else {
          // Web page: target iframe
          const iframe = stage.querySelector('iframe');
          if (iframe) {
            VPad.setCurrentTarget(iframe.contentWindow);
            VPad.mount(vpadHost);
            vpadMounted = true;
          } else {
            Toast.show('当前页无游戏画布');
            return;
          }
        }
        Toast.show('虚拟按键已开启');
      } else {
        VPad.unmount();
        vpadMounted = false;
        Toast.show('虚拟按键已关闭');
      }
    });

    App.playSession = { screen, mount, close, get engine(){return currentEngine;} };

    // History & favorites update
    if (opts.meta?.url && opts.sourceType !== 'web') {
      History.add(Object.assign({}, opts.meta, { engine: currentEngine, icon: opts.meta?.icon }));
    }
    // For web pages also add to history
    if (opts.meta?.url && opts.sourceType === 'web') {
      History.add(Object.assign({}, opts.meta, { kind: 'web' }));
    }

    mount(opts);
  }

  function closePlayScreen() {
    if (!App.playSession) return;
    try { VPad.unmount(); } catch (e) {}
    try {
      if (App.playSession.screen.parentNode) App.playSession.screen.parentNode.removeChild(App.playSession.screen);
    } catch (e) {}
    App.playSession = null;
    document.body.style.overflow = '';
  }

  // ------ routes ---------------------------------------------------------
  Router.on('/', renderHome);
  Router.on('/local', renderLocal);
  Router.on('/history', renderHistory);
  Router.on('/favorites', renderFavorites);
  Router.on('/defaultpages', renderDefaultPages);
  Router.on('/custompages', renderCustomPages);
  Router.on('/engine', renderEngineSettings);
  Router.on('/vpad', renderVPadSettings);
  Router.on('/adblock', renderAdblock);
  Router.on('/settings', (root) => Settings.render(root));
  Router.on('/about', renderAbout);
  Router.on('/search', renderSearch);

  // ------ global events --------------------------------------------------
  function wireGlobalEvents() {
    // Back from play screen
    window.addEventListener('vpad-key', () => {});
    window.addEventListener('hwkey', (e) => {
      // Optional: pass hardware key to the player
      if (App.playSession) {
        const { code, action } = e.detail;
        try {
          const canvas = document.querySelector('#psStage canvas, #psStage .ruffle-container, #psStage ruffle-player');
          if (canvas) {
            const ev = new KeyboardEvent(action === 'down' ? 'keydown' : 'keyup', { code, bubbles: true });
            canvas.dispatchEvent(ev);
          }
        } catch (err) {}
      }
    });
    // Open from intent
    window.FlashBox = window.FlashBox || {};
    window.FlashBox.onExternalSwf = function (url) {
      Router.go('/'); // ensure home is rendered first
      playUrl(url, url.split('/').pop());
    };
    window.FlashBox.onBack = function () {
      if (App.playSession) { closePlayScreen(); return 'back'; }
      if (U.$('#drawer').classList.contains('open')) { U.toggleDrawer(false); return 'back'; }
      if (U.$('#searchBar') && !U.$('#searchBar').classList.contains('hidden')) { toggleSearch(false); return 'back'; }
      if (location.hash !== '#/' && location.hash !== '' && location.hash !== '#') { history.back(); return 'back'; }
      Modal.confirm({ title: '退出', message: '确定要退出 Flash 游戏盒？' })
        .then(ok => { if (ok && window.FlashBox && window.FlashBox.finishApp) window.FlashBox.finishApp(); });
      return 'back';
    };
  }

  // ------ boot -----------------------------------------------------------
  function boot() {
    // Apply saved zoom
    const z = parseFloat(U.kv.get('ui_zoom', '1'));
    if (z && z !== 1) U.applyZoom(z);
    // Apply theme
    Settings.applyDarkMode();
    // Top bar
    initTopBar();
    // Adblock
    AdBlock.start();
    // Device label
    const devLabel = U.$('#deviceLabel');
    if (devLabel && window.FlashBox && window.FlashBox.deviceInfo)
      devLabel.textContent = window.FlashBox.deviceInfo();
    const ver = U.$('#versionLabel');
    if (ver && window.FlashBox && window.FlashBox.appVersion)
      ver.textContent = 'v' + window.FlashBox.appVersion();
    // Wire global events
    wireGlobalEvents();
    // Route
    if (!location.hash) location.hash = '#/';
    window.dispatchEvent(new Event('hashchange'));
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})(window);

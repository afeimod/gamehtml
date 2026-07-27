/* ==========================================================================
 * localfiles.js - manage local SWF library (user-added files/folders)
 *
 * Two storage strategies:
 *   1. Pure JS: store {name, size, mtime, dataUrl} in localStorage (small files only)
 *   2. Native: use FlashBoxFile bridge to read SAF files
 *
 * User can also drop / pick files via <input type=file>.
 * ========================================================================== */
(function (global) {
  'use strict';

  const KEY = 'local_files';
  const ROOTS_KEY = 'local_roots_v1';

  function allLocal() {
    try { return JSON.parse(U.kv.get(KEY) || '[]'); }
    catch (e) { return []; }
  }

  function addLocal(entry) {
    const list = allLocal();
    list.unshift(Object.assign({ t: Date.now() }, entry));
    if (list.length > 500) list.length = 500;
    U.kv.set(KEY, JSON.stringify(list));
  }

  function removeLocal(id) {
    const list = allLocal().filter(e => e.id !== id);
    U.kv.set(KEY, JSON.stringify(list));
  }

  function clearLocal() { U.kv.set(KEY, '[]'); }

  function updateLocal(id, patch) {
    const list = allLocal();
    const idx = list.findIndex(e => e.id === id);
    if (idx >= 0) {
      list[idx] = Object.assign({}, list[idx], patch);
      U.kv.set(KEY, JSON.stringify(list));
    }
  }

  function fileToDataURL(file) {
    return new Promise((resolve, reject) => {
      const r = new FileReader();
      r.onload = () => resolve(r.result);
      r.onerror = () => reject(r.error);
      r.readAsDataURL(file);
    });
  }

  async function addPickedFile(file) {
    if (!file) return null;
    const isSwf = /\.swf$/i.test(file.name) ||
                  file.type === 'application/x-shockwave-flash' ||
                  file.type === 'application/octet-stream';
    if (!isSwf) {
      Toast.show('请选择 .swf 文件');
      return null;
    }
    // For < 5MB store as data URL inline, otherwise keep only meta + read on demand
    const id = 'f_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8);
    if (file.size < 5 * 1024 * 1024) {
      try {
        const dataUrl = await fileToDataURL(file);
        addLocal({
          id, name: file.name, size: file.size, mtime: file.lastModified || Date.now(),
          source: 'inline', dataUrl
        });
      } catch (e) {
        Toast.show('读取失败: ' + e.message);
        return null;
      }
    } else {
      // For large files we use object URL (transient). Persist meta.
      const url = URL.createObjectURL(file);
      addLocal({
        id, name: file.name, size: file.size, mtime: file.lastModified || Date.now(),
        source: 'blob', blobUrl: url
      });
    }
    return id;
  }

  // ------ SAF tree management (Android native) ----------------------------

  function getRoots() {
    if (window.FlashBoxFile && window.FlashBoxFile.listRoots) {
      try { return JSON.parse(window.FlashBoxFile.listRoots() || '[]'); }
      catch (e) { return []; }
    }
    try { return JSON.parse(U.kv.get(ROOTS_KEY) || '[]'); }
    catch (e) { return []; }
  }

  function setRoots(list) { U.kv.set(ROOTS_KEY, JSON.stringify(list)); }

  /**
   * Prompt the user to pick a folder via the native SAF picker.
   * Returns Promise<string|null> - the granted tree URI.
   */
  function pickFolder() {
    return new Promise((resolve) => {
      // Inject a hidden <input type=file webkitdirectory> as a fallback
      const input = document.createElement('input');
      input.type = 'file';
      input.webkitdirectory = true;
      input.style.display = 'none';
      input.addEventListener('change', () => {
        const files = Array.from(input.files || []);
        if (!files.length) { resolve(null); return; }
        // First file directory path
        const dir = (files[0].webkitRelativePath || '').split('/')[0];
        const root = {
          name: dir || '本地目录',
          mtime: Date.now(),
          source: 'webdir',
          fileCount: files.length
        };
        // Cache the entire list as dataUrls for small files
        const small = files.filter(f => f.size < 4 * 1024 * 1024);
        Promise.all(small.map(fileToDataURL))
          .then(urls => {
            const list = small.map((f, i) => ({
              id: 'saf_' + Date.now() + '_' + i + '_' + Math.random().toString(36).slice(2, 6),
              name: f.name,
              size: f.size,
              mtime: f.lastModified,
              source: 'inline',
              dataUrl: urls[i],
              relPath: f.webkitRelativePath
            }));
            // Add entries
            for (const e of list) addLocal(e);
            // Big files
            const big = files.filter(f => f.size >= 4 * 1024 * 1024);
            for (const f of big) {
              const url = URL.createObjectURL(f);
              addLocal({
                id: 'saf_' + Date.now() + '_' + Math.random().toString(36).slice(2, 6),
                name: f.name, size: f.size, mtime: f.lastModified,
                source: 'blob', blobUrl: url,
                relPath: f.webkitRelativePath
              });
            }
            // Save the root summary
            const allRoots = getRoots();
            root.id = 'r_' + Date.now();
            allRoots.unshift(root);
            setRoots(allRoots);
            document.body.removeChild(input);
            resolve(root.id);
          })
          .catch(err => {
            document.body.removeChild(input);
            Toast.show('扫描失败: ' + err.message);
            resolve(null);
          });
      });
      document.body.appendChild(input);
      input.click();
    });
  }

  function getObjectUrl(entry) {
    if (entry.dataUrl) return entry.dataUrl;
    if (entry.blobUrl) return entry.blobUrl;
    if (entry.uri && window.FlashBoxFile && window.FlashBoxFile.readFileAsBase64) {
      const b64 = window.FlashBoxFile.readFileAsBase64(entry.uri);
      if (b64) return 'data:application/x-shockwave-flash;base64,' + b64;
    }
    return null;
  }

  global.LocalFiles = {
    all: allLocal, add: addLocal, addPickedFile,
    remove: removeLocal, clear: clearLocal, update: updateLocal,
    pickFolder,
    getRoots, setRoots,
    getObjectUrl
  };
})(window);

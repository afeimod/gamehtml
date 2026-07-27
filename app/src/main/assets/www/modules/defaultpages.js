/* ==========================================================================
 * defaultpages.js - built-in default web pages + custom user pages
 * ========================================================================== */
(function (global) {
  'use strict';

  const KEY_CUSTOM = 'custom_pages';
  const KEY_DEFAULT = 'default_page_id';

  const DEFAULTS = [
    { id: '4399_pc',  name: '4399 电脑版', url: 'https://www.4399.com/', icon: '🎮',
      desc: '经典 Flash 小游戏聚合', mode: 'desktop' },
    { id: '4399_m',   name: '4399 手机版', url: 'https://m.4399.com/', icon: '📱',
      desc: '4399 移动版', mode: 'mobile' },
    { id: '7k7k',     name: '7k7k 小游戏', url: 'https://www.7k7k.com/', icon: '🎯',
      desc: '海量休闲游戏', mode: 'desktop' },
    { id: '17yy',     name: '17yy 经典',  url: 'https://www.17yy.com/', icon: '🕹️',
      desc: '在线网页游戏大全', mode: 'desktop' },
    { id: 'bh76',     name: '灵动游戏',   url: 'https://www.bh76.com/', icon: '⚡',
      desc: '灵动游戏主页', mode: 'desktop' },
    { id: 'duoduoyx', name: '多多游戏',   url: 'https://www.duoduoyx.com/', icon: '🌈',
      desc: '经典 Flash 收藏', mode: 'desktop' },
    { id: 'flash1890', name: 'Flash 1890', url: 'https://www.flash1890.com/', icon: '✨',
      desc: '复古 Flash 资源', mode: 'desktop' },
    { id: 'r2',       name: 'Ruffle 演示', url: 'https://ruffle.rs/demo/', icon: '🦀',
      desc: '官方 Ruffle 在线演示', mode: 'desktop' },
    { id: 'newgrounds', name: 'Newgrounds', url: 'https://www.newgrounds.com/games', icon: '🎨',
      desc: '全球 Flash 游戏社区', mode: 'desktop' }
  ];

  function defaults() { return DEFAULTS.slice(); }

  function customs() {
    try { return JSON.parse(U.kv.get(KEY_CUSTOM) || '[]'); }
    catch (e) { return []; }
  }

  function addCustom(p) {
    const list = customs();
    const id = 'c_' + Date.now();
    list.unshift(Object.assign({ id, t: Date.now() }, p));
    U.kv.set(KEY_CUSTOM, JSON.stringify(list));
    return id;
  }

  function removeCustom(id) {
    const list = customs().filter(p => p.id !== id);
    U.kv.set(KEY_CUSTOM, JSON.stringify(list));
  }

  function updateCustom(id, patch) {
    const list = customs();
    const i = list.findIndex(p => p.id === id);
    if (i >= 0) {
      list[i] = Object.assign({}, list[i], patch);
      U.kv.set(KEY_CUSTOM, JSON.stringify(list));
    }
  }

  function clearCustom() { U.kv.set(KEY_CUSTOM, '[]'); }

  function currentDefaultId() { return U.kv.get(KEY_DEFAULT, '4399_pc'); }
  function setCurrentDefaultId(id) { U.kv.set(KEY_DEFAULT, id); }

  function findById(id) {
    return DEFAULTS.find(p => p.id === id) ||
           customs().find(p => p.id === id) || null;
  }

  function all() { return DEFAULTS.concat(customs()); }

  global.DefaultPages = {
    defaults, customs, addCustom, removeCustom, updateCustom, clearCustom,
    currentDefaultId, setCurrentDefaultId, findById, all
  };
})(window);

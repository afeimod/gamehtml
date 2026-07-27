/* ==========================================================================
 * adblock.js - in-page ad hiding module (DOM level)
 *
 * Network-level ad blocking is done natively in AdBlocker.java.
 * This module is a backup for content the network can't catch.
 * ========================================================================== */
(function (global) {
  'use strict';

  const SELECTORS = [
    '[id*="ad-"]', '[class*="ad-"]', '[id*="banner"]', '[class*="banner"]',
    'ins.adsbygoogle',
    'iframe[src*="ads"]', 'iframe[src*="ad-"]', 'iframe[src*="doubleclick"]',
    'div[id*="google_ads"]', 'div[class*="advert"]', 'div[id^="ad_"]',
    'div[class^="ad_"]', 'aside.ad', 'section.ad',
    '[id*="BAIDU_DUP_"]', 'iframe[src*="pos.baidu.com"]',
    'iframe[src*="cpro.baidu.com"]', 'iframe[src*="hm.baidu.com"]',
    'div[id*="tanx"]', 'div[id*="alimama"]',
    // 4399 / 7k7k / 17yy specific
    '.adsArea', '#ads', '#ad', '#adv', '.adv', '#topAdv', '#rightAd', '#leftAd',
    '#floatAd', '.floatAd', '.adsbygoogle', '.ad-container',
  ];

  let observer = null;
  let enabled = true;

  function hide(root) {
    if (!enabled) return;
    const nodes = (root || document).querySelectorAll(SELECTORS.join(','));
    let n = 0;
    for (const node of nodes) {
      // skip if has children that are visible content (heuristic)
      if (node.offsetParent === null && !node.textContent.trim()) {
        node.style.setProperty('display', 'none', 'important');
        n++;
      } else if (/^(div|aside|section|iframe|ins)$/i.test(node.tagName) &&
                 !node.querySelector('input,button,video,audio,canvas,svg')) {
        node.style.setProperty('display', 'none', 'important');
        n++;
      }
    }
    return n;
  }

  function start() {
    if (observer) return;
    enabled = U.kv.get('js_adblock', 'true') !== 'false';
    if (!enabled) return;
    observer = new MutationObserver(U.debounce(() => hide(document.body), 200));
    observer.observe(document.body, { childList: true, subtree: true });
    hide(document.body);
  }

  function stop() {
    if (observer) { observer.disconnect(); observer = null; }
  }

  function toggle(on) {
    U.kv.set('js_adblock', on ? 'true' : 'false');
    if (on) start(); else stop();
  }

  // Native ad block also exposed via FlashBox (when available)
  function addHost(h) {
    if (window.FlashBox && window.FlashBox.addAdHost) window.FlashBox.addAdHost(h);
  }
  function addUrl(u) {
    if (window.FlashBox && window.FlashBox.addAdUrl) window.FlashBox.addAdUrl(u);
  }

  global.AdBlock = { start, stop, toggle, hide, addHost, addUrl };
})(window);

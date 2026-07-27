/* ===== FlashGameBox online-page injection =====
   Loaded into remote Flash pages by the native WebViewClient (see window.__FB_INJECT__).
   - Loads the selected engine (Ruffle polyfill / Waflash / FlashPatch compat) so
     <object>/<embed> Flash content runs again.
   - Mounts virtual controls in desktop/compat mode.
   - Applies ad-blocking CSS.
   - Adds a floating toolbar (home / back / favorite / toggle controls). */
(function(){
"use strict";
var inj = window.__FB_INJECT__ || {engine:'ruffle', config:{}, controls:null, desktopMode:true, origin:'https://app.local', adblock:true};
var ORIGIN = inj.origin || 'https://app.local';
var A = window.Android;

function loadScript(url, cb){ var s=document.createElement('script'); s.src=url; s.onload=cb||function(){}; s.onerror=function(){ if(cb)cb(new Error('fail '+url)); }; document.head.appendChild(s); }
function loadStyle(css){ var s=document.createElement('style'); s.textContent=css; document.head.appendChild(s); }
function el(tag,attrs){var e=document.createElement(tag);for(var k in attrs){e.setAttribute(k,attrs[k]);}return e;}

// ---- ad-block CSS ----
if(inj.adblock){
  loadStyle(
    'ins.adsbygoogle,[id*="ad-"],[id*="ads-"],[class*="advertisement"],[class*="ad-banner"],'+
    '[class*="ad_"],iframe[src*="ads"],iframe[src*="doubleclick"],iframe[src*="syndication"],'+
    '.ad,.ads,.adbox,.ad-wrap,.banner-ad,.pop-ad,#ad,#ads,#banner,#popup{display:none!important;max-height:0!important;height:0!important;overflow:hidden!important}'
  );
}

// ---- engine ----
var engine = inj.engine || 'ruffle';
var cfg = inj.config || {};
function scaleFor(a){return ({contain:'showAll',cover:'noBorder',stretch:'exactFit',original:'noScale'})[a]||'showAll';}

function injectRuffle(isFlashPatch){
  window.RufflePlayer = window.RufflePlayer || {};
  window.RufflePlayer.config = {
    allowScriptAccess:true, autoplay:'on', unmuteOverlay:'visible',
    backgroundColor: cfg.backgroundColor||'#000000', letterbox: cfg.letterbox||'fullscreen',
    quality: cfg.quality||'high', scale: scaleFor(cfg.aspect), wmode: cfg.wmode||'opaque',
    preferredRenderer: cfg.renderer||null, upgradeToHttps: cfg.upgradeToHttps!==false,
    compatibilityRules:true, maxExecutionDuration: cfg.maxExec||15,
    playerVersion: (isFlashPatch&&cfg.playerVersion)?cfg.playerVersion:(cfg.playerVersion||null),
    polyfills:true, showSwfDownload:false
  };
  loadScript(ORIGIN+'/engines/ruffle/ruffle.js', function(){
    // Ruffle auto-polyfills <object>/<embed>; nothing else needed.
    patchSites();
  });
}

function injectWaflash(){
  // Best-effort: replace <object>/<embed> Flash with a waflash canvas.
  var objs = Array.prototype.slice.call(document.querySelectorAll('object[type*="shockwave"], embed[type*="shockwave"], object[data$=".swf"], embed[src$=".swf"]'));
  if(!objs.length){ patchSites(); return; }
  import(ORIGIN+'/engines/waflash/waflash-player.min.js').then(function(mod){
    var createWaflash = mod.createWaflash || (mod.default&&mod.default.createWaflash);
    objs.forEach(function(o){
      var src = o.getAttribute('data')||o.getAttribute('src')|| (o.querySelector('param[name=movie]')||{}).value;
      if(!src) return;
      var box = el('div',{style:'position:relative;width:100%;height:auto;min-height:400px;background:#000'});
      var canvas = el('canvas',{id:'waflashCanvas_'+Math.random().toString(36).slice(2), class:'waflashCanvas'});
      canvas.style.width='100%'; canvas.style.height='100%'; canvas.style.background=cfg.backgroundColor||'#000';
      box.appendChild(canvas); o.parentNode.replaceChild(box,o);
      try{ createWaflash(src, {enableFilters: cfg.enableFilters!==false, avm:cfg.avm||'auto', wmode:cfg.wmode||'opaque', scale:scaleFor(cfg.aspect)}); }catch(e){}
    });
    patchSites();
  }).catch(function(){ patchSites(); });
}

// ---- FlashPatch-style site compatibility patches ----
function patchSites(){
  try{
    // 4399 anti-block shim
    Object.defineProperty(window,'showBlockFlash',{value:function(){},writable:false,configurable:false});
  }catch(e){}
  try{
    // generic: neutralize common ad/redirect popups
    window.open = (function(orig){ return function(u){ try{ if(A&&u){ A.openUrl(u,'mobile'); return null; } }catch(e){} return orig.apply(window,arguments); }; })(window.open);
  }catch(e){}
}

if(engine==='waflash'){ injectWaflash(); }
else if(engine==='flashpatch'){ injectRuffle(true); }
else { injectRuffle(false); }

// ---- controls ----
if(inj.desktopMode && inj.controls && inj.controls.enabled!==false){
  loadScript(ORIGIN+'/controls.js', function(){
    if(window.FBControls){ window.FBControls.mount(inj.controls, {online:true, engineName:({ruffle:'Ruffle',waflash:'Waflash',flashpatch:'FlashPatch'}[engine])||engine}); }
  });
}

// ---- floating toolbar ----
(function(){
  if(document.getElementById('fbHome')) return;
  var home=el('button',{id:'fbHome'}); home.textContent='⌂'; home.title='返回首页';
  home.onclick=function(){ if(A)A.goHome(); };
  document.body.appendChild(home);
})();

// re-inject support
window.__fbReinject = function(){
  if(window.FBControls){ window.FBControls.refresh(inj.controls,{online:true}); }
  patchSites();
};
})();

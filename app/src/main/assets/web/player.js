/* ===== FlashGameBox player bootstrap =====
   Loads the selected engine (Ruffle / Waflash / FlashPatch-compat), applies the
   per-engine quality & aspect-ratio config, then mounts virtual controls.
   Note: controls.js is a classic <script> loaded before this deferred module,
   so window.FBControls is already available. */
const A = () => window.Android;
const q = new URLSearchParams(location.search);
const params = {
  local: q.get('local'),
  src: q.get('src'),
  name: q.get('name') || 'Flash',
  engine: q.get('engine') || (A()?JSON.parse(A().getSettings()).engine:'ruffle')
};

function settings(){ return A()?JSON.parse(A().getSettings()):{jsInject:true}; }
function engineConfigs(){ return A()?JSON.parse(A().getEngineConfigs()):{}; }
function controlsCfg(){ return A()?JSON.parse(A().getControls()):null; }

const ORIGIN = 'https://app.local';
const swfUrl = params.local ? `${ORIGIN}/local/${encodeURIComponent(params.local)}` : params.src;
const cfg = engineConfigs()[params.engine] || {};
const stage = document.getElementById('stage');
const engTag = document.getElementById('engTag');
engTag.textContent = ({ruffle:'Ruffle',waflash:'Waflash',flashpatch:'FlashPatch'}[params.engine]) || params.engine;

// aspect -> engine scale mapping
function scaleFor(aspect){
  return ({contain:'showAll',cover:'noBorder',stretch:'exactFit',original:'noScale'})[aspect] || 'showAll';
}
function applyAspectToStage(aspect){
  stage.classList.toggle('fill', aspect==='cover'||aspect==='stretch'||aspect==='contain');
}

function hideLoading(){ const l=document.getElementById('loading'); if(l) l.style.display='none'; }
function fail(msg){ hideLoading(); const e=document.getElementById('err'); document.getElementById('errMsg').textContent=msg; e.style.display='flex'; }

// toolbar wiring
document.getElementById('bHome').onclick=()=>A()&&A().goHome();
document.getElementById('bBack').onclick=()=>A()?A().goBack():history.back();
document.getElementById('bFav').onclick=()=>{
  if(!A())return; const item=JSON.stringify({id:'f_'+Date.now().toString(36),url:location.href,title:params.name});
  const on=A().toggleFavorite(item); A().toast(on?'已收藏':'已取消收藏');
};
let ctrlHidden=false;
document.getElementById('bCtrl').onclick=()=>{
  ctrlHidden=!ctrlHidden;
  const c=document.getElementById('fbControls'); if(c) c.style.display=ctrlHidden?'none':'block';
  document.getElementById('bCtrl').textContent=ctrlHidden?'⊟ 按键':'⊞ 按键';
};

// record history
if(A() && settings().autoHistory!==false){
  const item={id:'h_'+Date.now().toString(36),url:location.href,title:params.name,time:Date.now(),type:'local',localId:params.local};
  A().addHistory(JSON.stringify(item));
}

// keep screen on while playing
if(A()) A().keepScreenOn(true);
window.addEventListener('pagehide',()=>A()&&A().keepScreenOn(false));

function loadScript(url){ return new Promise((res,rej)=>{
  const s=document.createElement('script'); s.src=url; s.onload=res; s.onerror=()=>rej(new Error('load fail '+url));
  document.head.appendChild(s);
}); }

async function boot(){
  try{
    if(!swfUrl){ fail('未指定SWF文件'); return; }
    if(params.engine==='waflash'){
      await playWaflash();
    } else if(params.engine==='flashpatch'){
      // FlashPatch compat: use base engine with runtime patches (playerVersion etc.)
      const base=cfg.baseEngine||'ruffle';
      if(base==='waflash') await playWaflash(true); else await playRuffle(true);
    } else {
      await playRuffle();
    }
    hideLoading();
    mountControls();
  }catch(e){ fail('引擎加载失败：'+(e&&e.message||e)); }
}

async function playRuffle(isFlashPatch){
  await loadScript(`${ORIGIN}/engines/ruffle/ruffle.js`);
  window.RufflePlayer = window.RufflePlayer || {};
  const ruffle = window.RufflePlayer.newest ? window.RufflePlayer.newest() : window.RufflePlayer;
  const player = ruffle.createPlayer();
  player.style.width='100%'; player.style.height='100%';
  stage.appendChild(player);
  applyAspectToStage(cfg.aspect||'contain');
  const rconf = {
    allowScriptAccess: true,
    autoplay: 'on',
    unmuteOverlay: 'visible',
    backgroundColor: cfg.backgroundColor || '#000000',
    letterbox: cfg.letterbox || 'fullscreen',
    quality: cfg.quality || 'high',
    scale: scaleFor(cfg.aspect),
    wmode: cfg.wmode || 'opaque',
    preferredRenderer: cfg.renderer || null,
    upgradeToHttps: cfg.upgradeToHttps!==false,
    compatibilityRules: true,
    maxExecutionDuration: cfg.maxExec || 15,
    playerVersion: (isFlashPatch && cfg.playerVersion) ? cfg.playerVersion : (cfg.playerVersion||null),
    showSwfDownload: !!cfg.showSwfDownload
  };
  player.ruffle().load(swfUrl, rconf);
  // hide native splash if needed
  try{ player.style.display='block'; }catch(e){}
}

async function playWaflash(isFlashPatch){
  // waflash-player.min.js exports createWaflash as an ES module
  const mod = await import(`${ORIGIN}/engines/waflash/waflash-player.min.js`);
  const createWaflash = mod.createWaflash || (mod.default && mod.default.createWaflash);
  applyAspectToStage(cfg.aspect||'contain');
  const canvas=document.createElement('canvas');
  canvas.id='canvas'; canvas.className='waflashCanvas';
  canvas.style.width='100%'; canvas.style.height='100%'; canvas.style.background=cfg.backgroundColor||'#000000';
  stage.appendChild(canvas);
  // waflash needs the container #waflashContainer? It uses #canvas by id. Provide status div.
  const opts={ enableFilters: cfg.enableFilters!==false, avm: cfg.avm||'auto', wmode: cfg.wmode||'opaque', scale: scaleFor(cfg.aspect) };
  // waflash.js (loader) calls createWaflash(GAME.src, opts); we call directly
  if(typeof createWaflash!=='function'){ throw new Error('waflash createWaflash not found'); }
  createWaflash(swfUrl, opts);
  canvas.focus();
}

function mountControls(){
  const c=controlsCfg();
  if(c && c.enabled!==false && window.FBControls){
    FBControls.mount(c,{online:false,engineName:engTag.textContent});
  }
}

boot();

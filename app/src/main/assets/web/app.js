/* ===== FlashGameBox SPA ===== */
(function(){
"use strict";
const A = () => window.Android; // native bridge (may be undefined in browser dev)
const $ = (s,r=document)=>r.querySelector(s);
const $$ = (s,r=document)=>Array.from(r.querySelectorAll(s));
const h = (tag,attrs={},...kids)=>{
  const e=document.createElement(tag);
  for(const k in attrs){ if(k==='class')e.className=attrs[k]; else if(k==='html')e.innerHTML=attrs[k];
    else if(k.startsWith('on')&&typeof attrs[k]==='function')e.addEventListener(k.slice(2),attrs[k]);
    else e.setAttribute(k,attrs[k]); }
  for(const c of kids){ if(c==null)continue; e.appendChild(typeof c==='string'?document.createTextNode(c):c); }
  return e;
};
function toast(msg){ const t=$('#toast'); t.textContent=msg; t.classList.add('show');
  clearTimeout(toast._t); toast._t=setTimeout(()=>t.classList.remove('show'),1800); }
let modalCb=null;
function modal(title, contentNode, opts={}){
  const root=$('#modalRoot'); root.innerHTML='';
  const mask=h('div',{class:'modal-mask'});
  const box=h('div',{class:'modal'});
  box.appendChild(h('h2',{},title));
  box.appendChild(h('button',{class:'close',onclick:()=>closeModal()},'×'));
  box.appendChild(contentNode);
  root.appendChild(mask); root.appendChild(box);
  root.classList.add('show');
  modalCb=opts.onClose||null;
  mask.addEventListener('click',closeModal);
  if(opts.pushState){ try{history.pushState({modal:true},'');}catch(e){} }
}
function closeModal(){ $('#modalRoot').classList.remove('show'); $('#modalRoot').innerHTML='';
  if(modalCb){const c=modalCb;modalCb=null;c();} }

const App = {
  state:{ settings:{}, cfg:{}, controls:{}, library:[], history:[], favorites:[], sites:[], info:{} },
  cur:'home'
};

function loadAll(){
  const a=A();
  if(a){
    App.state.settings=JSON.parse(a.getSettings());
    App.state.cfg=JSON.parse(a.getEngineConfigs());
    App.state.controls=JSON.parse(a.getControls());
    App.state.library=JSON.parse(a.getLibrary());
    App.state.history=JSON.parse(a.getHistory());
    App.state.favorites=JSON.parse(a.getFavorites());
    App.state.sites=JSON.parse(a.getSites());
    try{App.state.info=JSON.parse(a.appInfo());}catch(e){}
  } else {
    App.state.settings={engine:'ruffle',pageMode:'mobile',zoom:100,adblock:true,cache:true,jsInject:true};
    App.state.cfg=DEFAULT_CFG(); App.state.controls=DEFAULT_CONTROLS();
    App.state.sites=DEFAULT_SITES(); App.state.library=[]; App.state.history=[]; App.state.favorites=[];
  }
}
function saveSettings(){ const a=A(); if(a)a.saveSettings(JSON.stringify(App.state.settings)); }
function saveCfg(){ const a=A(); if(a)a.saveEngineConfigs(JSON.stringify(App.state.cfg)); }
function saveControls(){ const a=A(); if(a)a.saveControls(JSON.stringify(App.state.controls)); }

/* ---------- routing ---------- */
function switchView(v){
  App.cur=v;
  $$('.view').forEach(s=>s.classList.toggle('active',s.id==='view-'+v));
  $$('.nav').forEach(n=>n.classList.toggle('active',n.dataset.view===v));
  const titles={home:'Flash游戏盒',library:'本地游戏',web:'网页浏览',history:'历史记录',favorites:'我的收藏',settings:'设置'};
  document.title=titles[v]||'';
  if(v==='home')renderHome();
  if(v==='library')renderLibrary();
  if(v==='web')renderWeb();
  if(v==='history')renderHistory();
  if(v==='favorites')renderFavorites();
  if(v==='settings')renderSettings();
}

/* ---------- HOME ---------- */
function renderHome(){
  const el=$('#view-home'); el.innerHTML='';
  el.appendChild(h('div',{class:'h'},'常用网站'));
  const grid=h('div',{class:'grid'});
  App.state.sites.filter(s=>s.id!=='home').forEach(s=>{
    const tile=h('div',{class:'tile',onclick:()=>playSite(s)});
    tile.appendChild(h('div',{class:'thumb',html:siteIcon(s)}));
    if(s.builtIn) tile.appendChild(h('div',{class:'tag'},s.category==='flash'?'Flash':'在线'));
    tile.appendChild(h('div',{class:'nm'},s.name));
    grid.appendChild(tile);
  });
  el.appendChild(grid);

  el.appendChild(h('div',{class:'h'},'最近播放'));
  const recent=App.state.history.slice(0,8);
  if(!recent.length){ el.appendChild(h('div',{class:'empty'},'还没有播放记录')); }
  else { const list=h('div'); recent.forEach(r=>{
    list.appendChild(itemRow(r.title||r.url, r.url, r.time?fmtTime(r.time):'', '▶', ()=>openAny(r)));
  }); el.appendChild(list); }

  el.appendChild(h('div',{class:'h'},'我的收藏'));
  const fav=App.state.favorites.slice(0,8);
  if(!fav.length){ el.appendChild(h('div',{class:'empty'},'点击 ★ 收藏喜欢的游戏')); }
  else { const list=h('div'); fav.forEach(f=>{
    list.appendChild(itemRow(f.title||f.url, f.url, '', '★', ()=>openAny(f)));
  }); el.appendChild(list); }
}

/* ---------- LIBRARY ---------- */
function renderLibrary(){
  const el=$('#view-library'); el.innerHTML='';
  const tb=h('div',{class:'row between'});
  tb.appendChild(h('div',{class:'sub',style:'margin:0'},'本地SWF文件，点击播放'));
  const bw=h('div',{class:'row'});
  bw.appendChild(h('button',{class:'btn sm',onclick:()=>A()&&A().pickFile() },'＋ 文件'));
  bw.appendChild(h('button',{class:'btn sm',onclick:()=>A()&&A().pickFolder() },'＋ 文件夹'));
  bw.appendChild(h('button',{class:'btn sm',onclick:()=>{ if(A()){App.state.library=JSON.parse(A().getLibrary());renderLibrary();} }},'⟳ 刷新'));
  tb.appendChild(bw); el.appendChild(tb);

  // group by parent folder
  const folders={}; const loose=[];
  App.state.library.forEach(it=>{
    if(it.isDir){ folders[it.id]={info:it,items:[]}; }
  });
  App.state.library.forEach(it=>{
    if(it.isDir) return;
    if(it.parent && folders[it.parent]) folders[it.parent].items.push(it);
    else loose.push(it);
  });
  if(!App.state.library.length){
    el.appendChild(h('div',{class:'empty',html:'本地列表为空<br>点击「＋ 文件 / ＋ 文件夹」添加SWF，支持立即刷新'}));
  }
  Object.values(folders).forEach(g=>{
    el.appendChild(h('div',{class:'group-h'},'📁 '+g.info.name+' （'+g.items.length+'）'));
    g.items.forEach(it=> el.appendChild(libItem(it)));
  });
  if(loose.length){ el.appendChild(h('div',{class:'group-h'},'📄 单独文件')); loose.forEach(it=>el.appendChild(libItem(it))); }
}
function libItem(it){
  return itemRow(it.name, humanSize(it.size||0), '本地', '▶', ()=>playLocal(it),
    [ h('button',{class:'btn sm danger',onclick:e=>{e.stopPropagation();removeLib(it.id);}},'删除') ]);
}
function removeLib(id){
  if(!A())return; App.state.library=JSON.parse(A().removeLibraryItem(id)); renderLibrary(); toast('已删除');
}

/* ---------- WEB ---------- */
function renderWeb(){
  const el=$('#view-web'); el.innerHTML='';
  const tb=h('div',{class:'toolbar'});
  const modeSel=h('select',{class:'select',id:'webMode'},
    h('option',{value:'mobile'},'手机模式'),
    h('option',{value:'compat'},'兼容模式'),
    h('option',{value:'desktop'},'电脑桌面模式'));
  modeSel.value=App.state.settings.pageMode||'mobile';
  const zoom=h('input',{type:'range',min:'50',max:'200',value:String(App.state.settings.zoom||100),style:'max-width:160px'});
  const zl=h('span',{class:'eng',id:'zl'},(App.state.settings.zoom||100)+'%');
  zoom.addEventListener('input',()=>{ zl.textContent=zoom.value+'%'; });
  zoom.addEventListener('change',()=>{ App.state.settings.zoom=+zoom.value; saveSettings(); if(A())A().setZoom(+zoom.value); });
  const inj=h('button',{class:'btn sm',onclick:()=>A()&&A().injectEngineNow()},'注入引擎');
  tb.appendChild(modeSel); tb.appendChild(h('label',{class:'eng'},'缩放')); tb.appendChild(zoom); tb.appendChild(zl); tb.appendChild(inj);
  el.appendChild(tb);

  const tip=h('div',{class:'webTip',html:
    '<b>网页浏览</b><br>① 在顶部地址栏输入网址回车，或在首页点网站卡片进入。<br>'+
    '② 选择「手机/兼容/电脑桌面」模式切换 UA 与排版。<br>'+
    '③ 在线 Flash 页面会自动注入所选引擎（可在设置关闭）。<br>'+
    '④ 电脑桌面/兼容模式下显示虚拟按键（摇杆/方向键+独立按键）。<br>'+
    '⑤ 直接点击 .swf 链接会进入播放器全屏播放。<br>'+
    '⑥ 支持返回键后退，HTTP 与 HTTPS 均可访问，已启用缓存与广告拦截。'});
  el.appendChild(tip);
}
function playSite(s){
  if(s.url==='about:home'){ switchView('home'); return; }
  const mode=s.mode||App.state.settings.pageMode||'mobile';
  $('#addr').value=s.url;
  if(A())A().openUrl(s.url,mode); else window.open(s.url,'_blank');
}

/* ---------- HISTORY ---------- */
function renderHistory(){
  const el=$('#view-history'); el.innerHTML='';
  el.appendChild(h('div',{class:'row between'},
    h('div',{class:'sub',style:'margin:0'},'共 '+App.state.history.length+' 条'),
    h('button',{class:'btn sm danger',onclick:()=>{ if(A()){A().clearHistory();App.state.history=[];renderHistory();toast('已清空');} }},'清空')));
  if(!App.state.history.length){ el.appendChild(h('div',{class:'empty'},'暂无历史记录')); return; }
  App.state.history.forEach(r=>{
    el.appendChild(itemRow(r.title||r.url, r.url, r.time?fmtTime(r.time):'', '⌛',
      ()=>openAny(r),
      [h('button',{class:'btn sm danger',onclick:e=>{e.stopPropagation(); if(A()){App.state.history=JSON.parse(A().removeHistory(r.id));renderHistory();}}},'删除')]));
  });
}

/* ---------- FAVORITES ---------- */
function renderFavorites(){
  const el=$('#view-favorites'); el.innerHTML='';
  el.appendChild(h('div',{class:'sub'},'共 '+App.state.favorites.length+' 个收藏'));
  if(!App.state.favorites.length){ el.appendChild(h('div',{class:'empty'},'暂无收藏，播放时点击 ★ 添加')); return; }
  App.state.favorites.forEach(f=>{
    el.appendChild(itemRow(f.title||f.url, f.url, '', '★',
      ()=>openAny(f),
      [h('button',{class:'btn sm danger',onclick:e=>{e.stopPropagation(); if(A()){App.state.favorites=JSON.parse(A().removeFavorite(f.id));renderFavorites();}}},'取消收藏')]));
  });
}

/* ---------- SETTINGS ---------- */
function renderSettings(){
  const el=$('#view-settings'); el.innerHTML='';
  const s=App.state.settings;

  // General
  const g=h('div',{class:'card'});
  g.appendChild(h('div',{class:'set-sec h',style:'margin:0 0 10px'},'通用'));
  g.appendChild(kv('默认引擎', engineChips(s.engine,v=>{s.engine=v;saveSettings();})));
  g.appendChild(kv('默认网页模式', modeChips(s.pageMode,v=>{s.pageMode=v;saveSettings();if(A())A().setPageMode(v);})));
  g.appendChild(sliderRow('全局页面缩放', s.zoom||100,50,200,'%',(v)=>{s.zoom=v;saveSettings();if(A())A().setZoom(v);}));
  g.appendChild(toggleRow('广告拦截', s.adblock,v=>{s.adblock=v;saveSettings();}));
  g.appendChild(toggleRow('启用缓存', s.cache,v=>{s.cache=v;saveSettings();}));
  g.appendChild(toggleRow('注入引擎到在线Flash页', s.jsInject,v=>{s.jsInject=v;saveSettings();}));
  g.appendChild(toggleRow('自动记录历史', s.autoHistory!==false,v=>{s.autoHistory=v;saveSettings();}));
  g.appendChild(toggleRow('保持屏幕常亮', false,v=>{ if(A())A().keepScreenOn(v); }));
  el.appendChild(g);

  // Engine configs
  const ec=h('div',{class:'card'});
  ec.appendChild(h('div',{class:'set-sec h',style:'margin:0 0 10px'},'引擎画质与画面比例'));
  ['ruffle','waflash','flashpatch'].forEach(eng=>{
    ec.appendChild(h('div',{class:'group-h'},engineName(eng)+' 设置'));
    ec.appendChild(engineConfigUI(eng));
  });
  el.appendChild(ec);

  // Controls editor
  const cc=h('div',{class:'card'});
  cc.appendChild(h('div',{class:'set-sec h',style:'margin:0 0 10px'},'虚拟按键'));
  cc.appendChild(controlsEditor());
  el.appendChild(cc);

  // Sites
  const sc=h('div',{class:'card'});
  sc.appendChild(h('div',{class:'set-sec h',style:'margin:0 0 10px'},'默认网页与自定义'));
  sc.appendChild(sitesManager());
  el.appendChild(sc);

  // About
  const ab=h('div',{class:'card'});
  ab.appendChild(h('div',{class:'set-sec h',style:'margin:0 0 10px'},'关于'));
  ab.appendChild(kv('版本', h('span',{},(App.state.info.version||'1.0.0'))));
  ab.appendChild(kv('内置引擎', h('span',{class:'eng'},'Ruffle · Waflash · FlashPatch')));
  ab.appendChild(h('div',{class:'sub',html:'三引擎可选：Ruffle（现代WASM，AS3兼容佳）、Waflash（AS2/AVM1老游戏友好）、FlashPatch兼容模式（绕过KillSwitch+站点补丁自动选择）。FlashPatch原为Windows补丁工具，此处作为兼容引擎基于WASM播放器实现运行时补丁。'}));
  el.appendChild(ab);
}

/* ---- settings helpers ---- */
function engineName(e){return {ruffle:'Ruffle',waflash:'Waflash',flashpatch:'FlashPatch兼容'}[e]||e;}
function engineChips(val,cb){
  const w=h('div',{class:'chips'});
  ['ruffle','waflash','flashpatch'].forEach(e=>{
    const c=h('div',{class:'chip'+(e===val?' on':''),onclick(){ $$('.chip',w).forEach(x=>x.classList.remove('on')); c.classList.add('on'); cb(e); }},engineName(e)); w.appendChild(c);
  }); return w;
}
function modeChips(val,cb){
  const w=h('div',{class:'chips'});
  [['mobile','手机模式'],['compat','兼容模式'],['desktop','电脑桌面']].forEach(([e,n])=>{
    const c=h('div',{class:'chip'+(e===val?' on':''),onclick(){ $$('.chip',w).forEach(x=>x.classList.remove('on')); c.classList.add('on'); cb(e); }},n); w.appendChild(c);
  }); return w;
}
function toggleRow(label,val,cb){
  const sw=h('div',{class:'switch'+(val?' on':'')});
  const row=h('div',{class:'kv'},h('span',{class:'k'},label),sw);
  sw.addEventListener('click',()=>{ const on=!sw.classList.contains('on'); sw.classList.toggle('on',on); cb(on); });
  return row;
}
function sliderRow(label,val,min,max,unit,cb){
  const wrap=h('label',{class:'fld'});
  wrap.appendChild(h('span',{},label+'：'+val+unit));
  const r=h('input',{type:'range',min:String(min),max:String(max),value:String(val),style:'width:100%'});
  r.addEventListener('input',()=>{ wrap.querySelector('span').textContent=label+'：'+r.value+unit; });
  r.addEventListener('change',()=>cb(+r.value));
  wrap.appendChild(r); return wrap;
}
function kv(k,v){ return h('div',{class:'kv'},h('span',{class:'k'},k),h('span',{class:'v'},v)); }
function selectRow(label,options,val,cb){
  const sel=h('select',{class:'select'});
  options.forEach(o=>sel.appendChild(h('option',{value:o[0]},o[1])));
  sel.value=val;
  sel.addEventListener('change',()=>cb(sel.value));
  return h('div',{class:'kv'},h('span',{class:'k'},label),sel);
}

function engineConfigUI(eng){
  const cfg=App.state.cfg[eng]||{};
  const w=h('div',{});
  if(eng==='ruffle'||eng==='flashpatch'){
    w.appendChild(selectRow('画质',QUALITY,cfg.quality||'high',v=>{cfg.quality=v;saveCfg();}));
    w.appendChild(selectRow('渲染器',[['webgl','WebGL'],['canvas','Canvas'],['wgpu','wgpu'],['','自动']],cfg.renderer||'webgl',v=>{cfg.renderer=v;saveCfg();}));
  } else {
    w.appendChild(selectRow('画质',QUALITY,cfg.quality||'high',v=>{cfg.quality=v;saveCfg();}));
  }
  w.appendChild(selectRow('画面比例',[['contain','适应(留边)'],['cover','铺满(裁切)'],['stretch','拉伸'],['original','原始大小']],cfg.aspect||'contain',v=>{cfg.aspect=v;saveCfg();}));
  w.appendChild(selectRow('缩放模式',[['showAll','显示全部'],['noBorder','无边框'],['exactFit','精确匹配'],['noScale','不缩放']],cfg.scale||'showAll',v=>{cfg.scale=v;saveCfg();}));
  w.appendChild(selectRow('窗口模式',[['opaque','不透明'],['transparent','透明'],['window','窗口'],['direct','直接'],['gpu','GPU']],cfg.wmode||'opaque',v=>{cfg.wmode=v;saveCfg();}));
  if(eng==='ruffle'||eng==='flashpatch'){
    w.appendChild(selectRow('信箱模式',[['fullscreen','全屏'],['off','关闭'],['topColor','顶色']],cfg.letterbox||'fullscreen',v=>{cfg.letterbox=v;saveCfg();}));
  }
  if(eng==='flashpatch'){
    w.appendChild(toggleRow('绕过KillSwitch(Flash终焉)',cfg.killswitchBypass!==false,v=>{cfg.killswitchBypass=v;saveCfg();}));
    w.appendChild(toggleRow('移除广告/弹窗组件',cfg.removeAdware!==false,v=>{cfg.removeAdware=v;saveCfg();}));
    w.appendChild(toggleRow('解除区域锁定',cfg.regionUnlock!==false,v=>{cfg.regionUnlock=v;saveCfg();}));
    w.appendChild(toggleRow('应用站点补丁',cfg.sitePatches!==false,v=>{cfg.sitePatches=v;saveCfg();}));
    w.appendChild(selectRow('底层引擎',[['ruffle','Ruffle'],['waflash','Waflash']],cfg.baseEngine||'ruffle',v=>{cfg.baseEngine=v;saveCfg();}));
  }
  if(eng==='waflash'){
    w.appendChild(toggleRow('启用滤镜',cfg.enableFilters!==false,v=>{cfg.enableFilters=v;saveCfg();}));
    w.appendChild(selectRow('AVM版本',[['auto','自动'],['1','AVM1(AS2)'],['2','AVM2(AS3)']],cfg.avm||'auto',v=>{cfg.avm=v;saveCfg();}));
  }
  return w;
}
const QUALITY=[['low','低'],['medium','中'],['high','高'],['best','最佳']];

/* ---------- CONTROLS EDITOR ---------- */
function controlsEditor(){
  const c=App.state.controls;
  const w=h('div',{});
  // primary type
  w.appendChild(h('div',{class:'row',style:'margin-bottom:8px'},
    h('span',{class:'k',style:'color:var(--tx2);font-size:13px'},'方向控制：'),
    h('div',{class:'chips'},(()=>{
      const x=h('div',{});
      [['joystick','摇杆'],['dpad','方向键']].forEach(([t,n])=>{
        const ch=h('div',{class:'chip'+(c.primaryType===t?' on':''),onclick(){$$('.chip',x).forEach(z=>z.classList.remove('on'));ch.classList.add('on');c.primaryType=t;saveControls();renderPreview();}},n); x.appendChild(ch);
      });
      return x;
    })()),
    h('span',{class:'k',style:'color:var(--tx2);font-size:13px;margin-left:8px'},'键位：'),
    h('div',{class:'chips'},(()=>{
      const x=h('div',{});
      [['wsad','WASD'],['arrows','上下左右']].forEach(([t,n])=>{
        const ch=h('div',{class:'chip'+(c.primaryStyle===t?' on':''),onclick(){$$('.chip',x).forEach(z=>z.classList.remove('on'));ch.classList.add('on');c.primaryStyle=t;saveControls();renderPreview();}},n); x.appendChild(ch);
      });
      return x;
    })())
  ));
  w.appendChild(sliderRow('方向控件大小',Math.round((c.primaryScale||1)*100),50,200,'%',v=>{c.primaryScale=v/100;saveControls();renderPreview();}));
  // buttons list
  w.appendChild(h('div',{class:'group-h'},'独立按键'));
  const bw=h('div',{class:'wrap'});
  function renderButtons(){
    bw.innerHTML='';
    (c.buttons||[]).forEach((b,i)=>{
      const chip=h('div',{class:'chip',style:'position:relative;padding-right:22px'},b.label||b.code);
      chip.appendChild(h('span',{style:'position:absolute;right:4px;top:2px;cursor:pointer;color:var(--ac2)',onclick(e){e.stopPropagation();c.buttons.splice(i,1);saveControls();renderButtons();renderPreview();}},'×'));
      chip.appendChild(h('div',{style:'font-size:9px;color:var(--tx3)'},b.code));
      bw.appendChild(chip);
    });
    const add=h('div',{class:'chip',style:'border-style:dashed',onclick:()=>openKeyboardPicker()},'＋ 添加按键');
    bw.appendChild(add);
  }
  renderButtons();
  w.appendChild(bw);
  w.appendChild(h('div',{class:'sub'},'提示：在下方预览中可拖动调整位置，长按元素可缩放'));
  // live preview
  const stage=h('div',{id:'ctrlStage',style:'position:relative;width:100%;height:280px;background:#0a0d11;border:1px solid var(--line);border-radius:12px;overflow:hidden;margin-top:8px'});
  w.appendChild(stage);
  setTimeout(renderPreview,0);
  function renderPreview(){ renderControlsPreview(stage,c); }
  // expose for re-render
  controlsEditor.render=renderPreview;
  return w;
}

function openKeyboardPicker(){
  const box=h('div',{});
  box.appendChild(h('div',{class:'sub'},'点击键盘按键添加为独立按键（已添加的会高亮）'));
  const kbd=h('div',{class:'kbd'});
  KEYBOARD.forEach(row=>{
    const r=h('div',{class:'kbd-row'});
    row.forEach(k=>{
      const exists=(App.state.controls.buttons||[]).some(b=>b.code===k.code);
      const key=h('div',{class:'kkey'+(k.cls||'')+(exists?' sel':''),onclick(){
        if((App.state.controls.buttons||[]).some(b=>b.code===k.code)){ toast('已存在该按键'); return; }
        App.state.controls.buttons.push({id:'b_'+k.code,label:k.label,code:k.code,x:60+Math.random()*20,y:80+Math.random()*10,scale:0.9});
        saveControls(); closeModal(); if(controlsEditor.render)controlsEditor.render(); renderSettings();
      }}, k.label);
      r.appendChild(key);
    });
    kbd.appendChild(r);
  });
  box.appendChild(kbd);
  modal('选择按键 · 键盘模型', box);
}

function renderControlsPreview(stage,c){
  stage.innerHTML='';
  // primary
  const p=h('div',{class:'ctrl-joy',style:`left:${c.primaryX}%;top:${c.primaryY}%;transform:translate(-50%,-50%) scale(${c.primaryScale||1})`});
  p.innerHTML = c.primaryType==='joystick' ? joystickSVG(c.primaryStyle) : dpadSVG(c.primaryStyle);
  stage.appendChild(p);
  makeDraggable(stage,p,(x,y)=>{c.primaryX=x;c.primaryY=y;saveControls();});
  (c.buttons||[]).forEach((b,i)=>{
    const btn=h('div',{class:'ctrl-btn',style:`left:${b.x}%;top:${b.y}%;transform:translate(-50%,-50%) scale(${b.scale||1})`},b.label||b.code);
    const del=h('span',{class:'ctrl-del',onclick(e){e.stopPropagation();c.buttons.splice(i,1);saveControls();renderControlsPreview(stage,c);}},'×');
    btn.appendChild(del);
    const sz=h('input',{type:'range',min:'50',max:'200',value:String(Math.round((b.scale||1)*100)),class:'ctrl-size'});
    sz.addEventListener('input',()=>{ b.scale=+sz.value/100; btn.style.transform=`translate(-50%,-50%) scale(${b.scale})`; });
    sz.addEventListener('change',()=>saveControls());
    btn.appendChild(sz);
    stage.appendChild(btn);
    makeDraggable(stage,btn,(x,y)=>{b.x=x;b.y=y;saveControls();});
  });
}
function makeDraggable(stage,el,cb){
  let sx,sy,ox,oy,drag=false;
  const start=(cx,cy)=>{ const r=el.getBoundingClientRect(); const sr=stage.getBoundingClientRect();
    drag=true; sx=cx; sy=cy; ox=(r.left+r.width/2-sr.left)/sr.width*100; oy=(r.top+r.height/2-sr.top)/sr.height*100; };
  const move=(cx,cy)=>{ if(!drag)return; const sr=stage.getBoundingClientRect();
    let x=ox+(cx-sx)/sr.width*100, y=oy+(cy-sy)/sr.height*100; x=Math.max(5,Math.min(95,x)); y=Math.max(5,Math.min(95,y));
    el.style.left=x+'%'; el.style.top=y+'%'; cb(x,y); };
  const end=()=>drag=false;
  el.addEventListener('touchstart',e=>{e.preventDefault();start(e.touches[0].clientX,e.touches[0].clientY);},{passive:false});
  el.addEventListener('touchmove',e=>{e.preventDefault();move(e.touches[0].clientX,e.touches[0].clientY);},{passive:false});
  el.addEventListener('touchend',end);
  el.addEventListener('mousedown',e=>{e.preventDefault();start(e.clientX,e.clientY);});
  window.addEventListener('mousemove',e=>move(e.clientX,e.clientY));
  window.addEventListener('mouseup',end);
}
function joystickSVG(style){
  const up=style==='wsad'?'W':'↑',left=style==='wsad'?'A':'←',down=style==='wsad'?'S':'↓',right=style==='wsad'?'D':'→';
  return `<div class="joy-base"><div class="joy-stick"></div></div>`;
}
function dpadSVG(style){
  const up=style==='wsad'?'W':'▲',left=style==='wsad'?'A':'◀',down=style==='wsad'?'S':'▼',right=style==='wsad'?'D':'▶';
  return `<div class="dpad"><div class="d up">${up}</div><div class="d left">${left}</div><div class="d right">${right}</div><div class="d down">${down}</div></div>`;
}

/* ---------- SITES MANAGER ---------- */
function sitesManager(){
  const w=h('div',{});
  App.state.sites.forEach(s=>{
    const row=h('div',{class:'li'});
    row.appendChild(h('div',{class:'ico',html:siteIcon(s)}));
    row.appendChild(h('div',{class:'meta'},h('div',{class:'t'},s.name),h('div',{class:'d'},s.url+' · '+(s.mode==='desktop'?'电脑':'手机'))));
    row.appendChild(h('div',{class:'act'},s.builtIn?null:h('button',{class:'btn sm danger',onclick(){ if(A()){App.state.sites=JSON.parse(A().removeSite(s.id));renderSettings();} }},'删除')));
    w.appendChild(row);
  });
  const add=h('button',{class:'btn primary',style:'margin-top:8px',onclick:openAddSite},'+ 添加自定义网页');
  w.appendChild(add);
  return w;
}
function openAddSite(){
  const box=h('div',{});
  const name=h('input',{class:'input',placeholder:'名称，如：我的游戏站'});
  const url=h('input',{class:'input',placeholder:'网址 https://...',style:'margin-top:8px'});
  const mode=h('select',{class:'select',style:'margin-top:8px;width:100%'},
    h('option',{value:'mobile'},'手机模式'),h('option',{value:'desktop'},'电脑桌面模式'),h('option',{value:'compat'},'兼容模式'));
  box.appendChild(h('label',{class:'fld'},h('span',{},'名称'),name));
  box.appendChild(h('label',{class:'fld'},h('span',{},'网址'),url));
  box.appendChild(h('label',{class:'fld'},h('span',{},'模式'),mode));
  box.appendChild(h('button',{class:'btn primary',style:'width:100%;margin-top:6px',onclick(){
    if(!url.value){toast('请输入网址');return;}
    const item={id:'cs_'+Date.now().toString(36),name:name.value||url.value,url:url.value,mode:mode.value,category:'custom',builtIn:false,icon:url.value};
    if(A()){App.state.sites=JSON.parse(A().addSite(JSON.stringify(item)));} else {App.state.sites.push(item);}
    closeModal(); renderSettings(); toast('已添加');
  }},'添加'));
  modal('添加自定义网页',box);
}

/* ---------- play / open helpers ---------- */
function playLocal(it){
  const eng=App.state.settings.engine;
  const url='https://app.local/player.html?local='+encodeURIComponent(it.id)+'&name='+encodeURIComponent(it.name)+'&engine='+eng;
  if(A()) A().openUrl(url,'mobile'); else window.open(url,'_blank');
}
function openAny(r){
  if(r.localId){ playLocal({id:r.localId,name:r.title}); return; }
  if(r.url && r.url.indexOf('app.local/player.html')>=0){ if(A())A().openUrl(r.url,'mobile'); else window.open(r.url); return; }
  if(r.url){ const mode=App.state.settings.pageMode||'mobile'; if(A())A().openUrl(r.url,mode); else window.open(r.url); }
}
function playSiteRef(s){ playSite(s); }

/* ---------- shared UI ---------- */
function itemRow(title, sub, badge, icon, onClick, actions){
  const li=h('div',{class:'li',onclick:onClick});
  li.appendChild(h('div',{class:'ico'},icon));
  li.appendChild(h('div',{class:'meta'},h('div',{class:'t'},title),h('div',{class:'d'},sub+(badge?' · '+badge:''))));
  if(actions&&actions.length){ const a=h('div',{class:'act'}); actions.forEach(x=>a.appendChild(x)); li.appendChild(a); }
  return li;
}
function siteIcon(s){
  if(s.id==='4399'||s.id==='4399m'||s.id==='4399flash') return '🎮';
  if(s.id==='mhhf'||s.id==='mhhfgames') return '🪽';
  if(s.id==='7k7k'||s.id==='7k7km') return '🎲';
  if(s.category==='custom') return '🔗';
  return '🌐';
}
function humanSize(b){ if(!b)return ''; if(b<1024)return b+' B'; if(b<1048576)return (b/1024).toFixed(1)+' KB'; return (b/1048576).toFixed(1)+' MB'; }
function fmtTime(t){ const d=new Date(t); const n=Date.now(); if(n-t<86400000)return d.getHours().toString().padStart(2,'0')+':'+d.getMinutes().toString().padStart(2,'0');
  return (d.getMonth()+1)+'/'+d.getDate(); }

/* ---------- native hooks ---------- */
window.__flashbox={
  onLibraryChanged(arr){ try{ App.state.library = Array.isArray(arr)?arr:JSON.parse(arr); }catch(e){ App.state.library=arr; }
    if(App.cur==='library')renderLibrary(); toast('本地列表已刷新'); },
  onNavChanged(o){ /* update address bar */ if(o&&o.url){ $('#addr').value=o.url; } },
  handleBack(){ if($('#modalRoot').classList.contains('show')){closeModal();return true;} return false; }
};

/* ---------- wiring ---------- */
function init(){
  loadAll();
  // bottom nav
  $$('.nav').forEach(n=>n.addEventListener('click',()=>switchView(n.dataset.view)));
  $('#btnBack').addEventListener('click',()=>{ if(A())A().goBack(); else history.back(); });
  $('#btnMenu').addEventListener('click',()=>switchView('settings'));
  $('#addr').addEventListener('keydown',e=>{ if(e.key==='Enter'){ const v=$('#addr').value.trim(); if(!v)return;
    let url=v; if(!/^https?:\/\//i.test(url)){ if(/^[\w-]+(\.[\w-]+)+/.test(url)) url='https://'+url; else url='https://www.baidu.com/s?wd='+encodeURIComponent(v); }
    const mode=$('#webMode')?$('#webMode').value:(App.state.settings.pageMode||'mobile');
    if(A())A().openUrl(url,mode); else window.open(url,'_blank'); }});
  $('#btnGo').addEventListener('click',()=>$('#addr').dispatchEvent(new KeyboardEvent('keydown',{key:'Enter'})));
  window.addEventListener('popstate',()=>{ if($('#modalRoot').classList.contains('show'))closeModal(); });
  switchView('home');
  // apply zoom on load
  if(A())A().setZoom(App.state.settings.zoom||100);
}

/* ---------- dev defaults (when no Android bridge) ---------- */
function DEFAULT_CFG(){ return { ruffle:{quality:'high',renderer:'webgl',scale:'showAll',aspect:'contain',wmode:'opaque'},
  waflash:{quality:'high',scale:'showAll',aspect:'contain',enableFilters:true,wmode:'opaque',avm:'auto'},
  flashpatch:{quality:'high',renderer:'webgl',scale:'showAll',aspect:'contain',baseEngine:'ruffle',killswitchBypass:true,removeAdware:true,regionUnlock:true,sitePatches:true} }; }
function DEFAULT_CONTROLS(){ return {enabled:true,primaryType:'joystick',primaryStyle:'wsad',primaryX:18,primaryY:70,primaryScale:1,
  buttons:[{id:'b_KeyJ',label:'J',code:'KeyJ',x:70,y:78,scale:1},{id:'b_KeyK',label:'K',code:'KeyK',x:80,y:70,scale:1},{id:'b_KeyL',label:'L',code:'KeyL',x:88,y:76,scale:1},{id:'b_KeyU',label:'U',code:'KeyU',x:58,y:80,scale:.9},{id:'b_KeyI',label:'I',code:'KeyI',x:66,y:88,scale:.9},{id:'b_KeyO',label:'O',code:'KeyO',x:82,y:88,scale:.9},{id:'b_Enter',label:'⏎',code:'Enter',x:50,y:90,scale:.8},{id:'b_Space',label:'␣',code:'Space',x:40,y:92,scale:1}] }; }
function DEFAULT_SITES(){ return [{id:'4399',name:'4399电脑版',url:'https://www.4399.com/',mode:'desktop',category:'flash',builtIn:true},{id:'4399m',name:'4399手机版',url:'https://www.4399.com/m/',mode:'mobile',category:'flash',builtIn:true},{id:'mhhf',name:'灵动游戏主页',url:'https://www.mhhf.com/',mode:'desktop',category:'mixed',builtIn:true}]; }

/* ---------- keyboard model ---------- */
const KEYBOARD=[
  [{label:'Esc',code:'Escape'},{label:'F1',code:'F1'},{label:'F2',code:'F2'},{label:'F3',code:'F3'},{label:'F4',code:'F4'},{label:'F5',code:'F5'},{label:'F6',code:'F6'},{label:'F7',code:'F7'},{label:'F8',code:'F8'},{label:'F9',code:'F9'},{label:'F10',code:'F10'},{label:'F11',code:'F11'},{label:'F12',code:'F12'}],
  [{label:'`',code:'Backquote'},{label:'1',code:'Digit1'},{label:'2',code:'Digit2'},{label:'3',code:'Digit3'},{label:'4',code:'Digit4'},{label:'5',code:'Digit5'},{label:'6',code:'Digit6'},{label:'7',code:'Digit7'},{label:'8',code:'Digit8'},{label:'9',code:'Digit9'},{label:'0',code:'Digit0'},{label:'-',code:'Minus'},{label:'=',code:'Equal'},{label:'⌫',code:'Backspace',cls:'wide2'}],
  [{label:'Tab',code:'Tab',cls:'wide2'},{label:'Q',code:'KeyQ'},{label:'W',code:'KeyW'},{label:'E',code:'KeyE'},{label:'R',code:'KeyR'},{label:'T',code:'KeyT'},{label:'Y',code:'KeyY'},{label:'U',code:'KeyU'},{label:'I',code:'KeyI'},{label:'O',code:'KeyO'},{label:'P',code:'KeyP'},{label:'[',code:'BracketLeft'},{label:']',code:'BracketRight'},{label:'\\',code:'Backslash'}],
  [{label:'Caps',code:'CapsLock',cls:'wide2'},{label:'A',code:'KeyA'},{label:'S',code:'KeyS'},{label:'D',code:'KeyD'},{label:'F',code:'KeyF'},{label:'G',code:'KeyG'},{label:'H',code:'KeyH'},{label:'J',code:'KeyJ'},{label:'K',code:'KeyK'},{label:'L',code:'KeyL'},{label:';',code:'Semicolon'},{label:"'",code:'Quote'},{label:'↵',code:'Enter',cls:'wide2'}],
  [{label:'⇧',code:'ShiftLeft',cls:'wide3'},{label:'Z',code:'KeyZ'},{label:'X',code:'KeyX'},{label:'C',code:'KeyC'},{label:'V',code:'KeyV'},{label:'B',code:'KeyB'},{label:'N',code:'KeyN'},{label:'M',code:'KeyM'},{label:',',code:'Comma'},{label:'.',code:'Period'},{label:'/',code:'Slash'},{label:'⇧',code:'ShiftRight',cls:'wide2'}],
  [{label:'Ctrl',code:'ControlLeft'},{label:'Alt',code:'AltLeft'},{label:'␣',code:'Space',cls:'space'},{label:'Alt',code:'AltRight'},{label:'Ctrl',code:'ControlRight'}],
  [{label:'←',code:'ArrowLeft'},{label:'↑',code:'ArrowUp'},{label:'↓',code:'ArrowDown'},{label:'→',code:'ArrowRight'}]
];

document.addEventListener('DOMContentLoaded', init);
})();

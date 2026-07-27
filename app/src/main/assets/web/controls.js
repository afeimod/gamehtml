/* ===== FlashGameBox runtime virtual controls overlay =====
   Renders joystick/dpad + independent buttons and dispatches synthetic
   KeyboardEvents to the active player (Ruffle <ruffle-player>, Waflash #canvas,
   or document). Usable on our own pages and injected into online pages. */
(function(){
"use strict";
const KEYCODE = buildKeyCodes();
function buildKeyCodes(){
  const m={};
  m['Space']=32; m['Enter']=13; m['Tab']=9; m['Escape']=27; m['Backspace']=8; m['CapsLock']=20; m['ShiftLeft']=16; m['ShiftRight']=16; m['ControlLeft']=17; m['ControlRight']=17; m['AltLeft']=18; m['AltRight']=18;
  m['ArrowUp']=38; m['ArrowDown']=40; m['ArrowLeft']=37; m['ArrowRight']=39;
  for(let i=0;i<26;i++) m['Key'+String.fromCharCode(65+i)]=65+i;
  for(let i=0;i<10;i++) m['Digit'+i]=48+i;
  for(let i=1;i<=12;i++) m['F'+i]=111+i;
  m['Minus']=189; m['Equal']=187; m['BracketLeft']=219; m['BracketRight']=221; m['Backslash']=220; m['Semicolon']=186; m['Quote']=222; m['Comma']=188; m['Period']=190; m['Slash']=191; m['Backquote']=192;
  return m;
}
function keyNameFromCode(code){
  if(!code) return '';
  if(code.startsWith('Key')) return code.slice(3).toLowerCase();
  if(code.startsWith('Digit')) return code.slice(5);
  if(code==='Space') return ' ';
  if(code==='Enter') return 'Enter';
  if(code.startsWith('Arrow')) return code;
  if(code.startsWith('F')) return code;
  const sym={Minus:'-',Equal:'=',BracketLeft:'[',BracketRight:']',Backslash:'\\',Semicolon:';',Quote:"'",Comma:',',Period:'.',Slash:'/',Backquote:'`'};
  return sym[code]||code;
}
function dirKeys(style){
  // returns {up,left,down,right} codes depending on wsad/arrows
  if(style==='arrows') return {up:'ArrowUp',left:'ArrowLeft',down:'ArrowDown',right:'ArrowRight'};
  return {up:'KeyW',left:'KeyA',down:'KeyS',right:'KeyD'};
}
function findTarget(){
  return document.querySelector('ruffle-player, #canvas.ruffle-canvas, #canvas, canvas') || document.body;
}
function press(code, down){
  const target=findTarget();
  const ev=new KeyboardEvent(down?'keydown':'keyup',{
    key:keyNameFromCode(code), code:code, keyCode:KEYCODE[code]||0, which:KEYCODE[code]||0,
    bubbles:true, cancelable:true, composed:true
  });
  try{ Object.defineProperty(ev,'location',{get:()=>0}); }catch(e){}
  target.dispatchEvent(ev);
  // also send to document for engines listening there
  if(target!==document){ document.dispatchEvent(new KeyboardEvent(down?'keydown':'keyup',{
    key:keyNameFromCode(code), code:code, keyCode:KEYCODE[code]||0, which:KEYCODE[code]||0, bubbles:true, cancelable:true })); }
}

let root=null, mounted=null, activeDir=new Set(), hideBtn=null, toolbar=null;

const FBControls={
  mount(cfg, opts){
    opts=opts||{};
    FBControls.unmount();
    if(!cfg||cfg.enabled===false) return;
    mounted={cfg,opts};
    root=document.createElement('div'); root.id='fbControls';
    document.body.appendChild(root);

    // primary directional
    const p=document.createElement('div');
    p.className='ctrl-joy';
    p.style.left=(cfg.primaryX??18)+'%'; p.style.top=(cfg.primaryY??70)+'%';
    p.style.transform=`translate(-50%,-50%) scale(${cfg.primaryScale??1})`;
    if((cfg.primaryType||'joystick')==='joystick'){
      p.innerHTML='<div class="joy-base"><div class="joy-stick"></div></div>';
      bindJoystick(p, cfg.primaryStyle||'wsad');
    } else {
      p.innerHTML=dpadHTML(cfg.primaryStyle||'wsad');
      bindDpad(p, cfg.primaryStyle||'wsad');
    }
    root.appendChild(p);

    // buttons
    (cfg.buttons||[]).forEach(b=>{
      const btn=document.createElement('div');
      btn.className='ctrl-btn'; btn.textContent=b.label||b.code;
      btn.style.left=b.x+'%'; btn.style.top=b.y+'%';
      btn.style.transform=`translate(-50%,-50%) scale(${b.scale??1})`;
      let down=false;
      const start=e=>{ e.preventDefault(); down=true; btn.classList.add('active'); press(b.code,true); };
      const end=e=>{ if(!down)return; down=false; btn.classList.remove('active'); press(b.code,false); };
      btn.addEventListener('touchstart',start,{passive:false});
      btn.addEventListener('touchend',end); btn.addEventListener('touchcancel',end);
      btn.addEventListener('mousedown',start); btn.addEventListener('mouseup',end); btn.addEventListener('mouseleave',end);
      root.appendChild(btn);
    });

    // hide/show toggle
    hideBtn=document.createElement('button'); hideBtn.id='fbHideCtrl'; hideBtn.textContent='⊞';
    hideBtn.onclick=()=>{ const h=root.style.display!=='none'; root.style.display=h?'none':'block'; hideBtn.textContent=h?'⊟':'⊞'; };
    document.body.appendChild(hideBtn);

    if(opts.online){ buildOnlineToolbar(opts); }
  },
  unmount(){
    if(root&&root.parentNode) root.parentNode.removeChild(root);
    if(hideBtn&&hideBtn.parentNode) hideBtn.parentNode.removeChild(hideBtn);
    if(toolbar&&toolbar.parentNode) toolbar.parentNode.removeChild(toolbar);
    root=null; hideBtn=null; toolbar=null; mounted=null; activeDir.clear();
  },
  refresh(cfg,opts){ FBControls.mount(cfg,opts); }
};
window.FBControls=FBControls;

function dpadHTML(style){
  const d=dirKeys(style);
  const lab=style==='wsad'?{up:'W',left:'A',down:'S',right:'D'}:{up:'▲',left:'◀',down:'▼',right:'▶'};
  return `<div class="dpad">
    <div class="d up" data-code="${d.up}">${lab.up}</div>
    <div class="d left" data-code="${d.left}">${lab.left}</div>
    <div class="d right" data-code="${d.right}">${lab.right}</div>
    <div class="d down" data-code="${d.down}">${lab.down}</div></div>`;
}
function setDir(code,down){
  if(down){ if(!activeDir.has(code)){ activeDir.add(code); press(code,true); } }
  else { if(activeDir.has(code)){ activeDir.delete(code); press(code,false); } }
}
function bindDpad(el,style){
  el.querySelectorAll('.d').forEach(d=>{
    const code=d.getAttribute('data-code');
    const start=e=>{ e.preventDefault(); d.classList.add('active'); setDir(code,true); };
    const end=e=>{ d.classList.remove('active'); setDir(code,false); };
    d.addEventListener('touchstart',start,{passive:false});
    d.addEventListener('touchend',end); d.addEventListener('touchcancel',end);
    d.addEventListener('mousedown',start); d.addEventListener('mouseup',end); d.addEventListener('mouseleave',end);
  });
}
function bindJoystick(el,style){
  const base=el.querySelector('.joy-base'); const stick=el.querySelector('.joy-stick');
  const dk=dirKeys(style);
  let active=false, id=null;
  const dirs={up:false,down:false,left:false,right:false};
  function apply(){
    setDir(dk.up, dirs.up); setDir(dk.down, dirs.down);
    setDir(dk.left, dirs.left); setDir(dk.right, dirs.right);
  }
  function start(e){ e.preventDefault(); active=true; id=e.changedTouches?e.changedTouches[0].identifier:'m'; move(e); }
  function move(e){
    if(!active) return; e.preventDefault();
    const t=e.changedTouches?Array.from(e.changedTouches).find(x=>x.identifier===id):e;
    if(!t) return;
    const r=base.getBoundingClientRect();
    let dx=t.clientX-(r.left+r.width/2), dy=t.clientY-(r.top+r.height/2);
    const max=r.width/2; const dist=Math.hypot(dx,dy); const ang=Math.atan2(dy,dx);
    const cl=Math.min(dist,max); const nx=Math.cos(ang)*cl, ny=Math.sin(ang)*cl;
    stick.style.transform=`translate(calc(-50% + ${nx}px), calc(-50% + ${ny}px))`;
    const TH=max*0.35;
    const nu=ny<-TH, nd=ny>TH, nl=nx<-TH, nr=nx>TH;
    if(nu!==dirs.up){dirs.up=nu;} if(nd!==dirs.down){dirs.down=nd;}
    if(nl!==dirs.left){dirs.left=nl;} if(nr!==dirs.right){dirs.right=nr;}
    apply();
  }
  function end(e){ if(!active)return; active=false; id=null;
    stick.style.transform='translate(-50%,-50%)';
    dirs.up=dirs.down=dirs.left=dirs.right=false; apply(); }
  base.addEventListener('touchstart',start,{passive:false});
  base.addEventListener('touchmove',move,{passive:false});
  base.addEventListener('touchend',end); base.addEventListener('touchcancel',end);
  base.addEventListener('mousedown',start); window.addEventListener('mousemove',move); window.addEventListener('mouseup',end);
}
function buildOnlineToolbar(opts){
  toolbar=document.createElement('div'); toolbar.id='fbToolbar';
  const home=document.createElement('button'); home.textContent='⌂ 首页'; home.onclick=()=>window.Android&&window.Android.goHome();
  const back=document.createElement('button'); back.textContent='‹ 后退'; back.onclick=()=>window.Android&&window.Android.goBack();
  const eng=document.createElement('button'); eng.textContent=(opts.engineName||'引擎');
  const fav=document.createElement('button'); fav.textContent='★'; fav.title='收藏';
  fav.onclick=()=>{ const item=JSON.stringify({id:'f_'+Date.now().toString(36),url:location.href,title:document.title});
    if(window.Android){ const on=window.Android.toggleFavorite(item); window.Android.toast(on?'已收藏':'已取消收藏'); } };
  toolbar.appendChild(back); toolbar.appendChild(home); toolbar.appendChild(eng); toolbar.appendChild(fav);
  document.body.appendChild(toolbar);
}
})();

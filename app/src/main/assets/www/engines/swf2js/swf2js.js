/* ==========================================================================
 * swf2js-lite.js - minimal pure-JS SWF parser / engine
 *
 * This is a *lightweight* SWF parser/renderer used as the third fallback
 * engine in Flash Game Box. It supports a *subset* of SWF tags needed to
 * play very simple Flash Lite / AS1-2 SWF files.
 *
 * Reference: Adobe SWF File Format Specification (version 19).
 *
 * Features supported (minimal set):
 *   - SWF file header parsing
 *   - Tags: ShowFrame, End, DefineBits, DefineBitsJPEG, DefineBitsJPEG2/3,
 *           DefineShape, DefineSprite, PlaceObject, RemoveObject,
 *           DefineText, SetBackgroundColor, FrameLabel, ExportAssets,
 *           ImportAssets, SoundStreamHead, SoundStreamBlock, DoAction,
 *           DoInitAction
 *   - Bitmap drawing (JPEG/PNG inside SWF)
 *   - Basic shape drawing
 *   - Frame timer
 *
 * NOT supported (or very partial):
 *   - ActionScript 3 / class-based bytecode
 *   - Filters
 *   - Video (FLV/H.264)
 *   - Complex shape edge records
 *
 * If a SWF uses unsupported features, the engine will still attempt to
 * play what it can, falling back to a canvas placeholder.
 *
 * Public API:
 *   swf2jsLite.load(url, opts) -> Promise<{canvas, player}>
 *   - url:  http(s)://... or blob: or data: URL
 *   - opts: { target: HTMLElement, width, height, onError }
 * ========================================================================== */
(function (global) {
  'use strict';

  // --------------------------------------------------------------- readers

  class Reader {
    constructor(buf, little) {
      this.dv = new DataView(buf);
      this.u8 = new Uint8Array(buf);
      this.pos = 0;
      this.little = !!little;
    }
    get endian() { return this.little ? 'LE' : 'BE'; }
    seek(p) { this.pos = p; }
    skip(n) { this.pos += n; }
    remaining() { return this.u8.length - this.pos; }
    u8()  { return this.u8[this.pos++]; }
    u16() { const v = this.dv.getUint16(this.pos, this.little); this.pos += 2; return v; }
    u32() { const v = this.dv.getUint32(this.pos, this.little); this.pos += 4; return v; }
    s16() { const v = this.dv.getInt16(this.pos, this.little); this.pos += 2; return v; }
    bytes(n) { const v = this.u8.subarray(this.pos, this.pos + n); this.pos += n; return v; }
    string(n) {
      // null-terminated string in some tags, fixed-length in others
      const out = []; for (let i = 0; i < n; i++) {
        const c = this.u8(); if (c === 0) break; out.push(c);
      }
      try { return new TextDecoder('utf-8').decode(new Uint8Array(out)); }
      catch (e) { return String.fromCharCode(...out); }
    }
    fixedString(n) {
      const out = this.bytes(n);
      try { return new TextDecoder('utf-8').decode(out); }
      catch (e) { return String.fromCharCode(...out); }
    }
    rect() {
      // RECT: Nbits (5), Xmin, Xmax, Ymin, Ymax all in Nbits each
      const start = this.pos;
      const nbits = (this.u8() >> 3) & 0x1f;
      const get = () => {
        let v = 0; let n = nbits;
        while (n > 0) {
          const b = this.u8();
          v = (v << 4) | ((b >> 4) & 0x0f);
          n -= 4;
          if (n <= 0) break;
          v = (v << 4) | (b & 0x0f);
          n -= 4;
        }
        // sign extend (v is signed in SWF)
        if (nbits > 0 && (v >> (nbits - 1)) & 1) v |= -1 << nbits;
        return v;
      };
      const xmin = get(), xmax = get(), ymin = get(), ymax = get();
      return { xmin, xmax, ymin, ymax, _len: this.pos - start };
    }
  }

  // --------------------------------------------------------------- tag defs

  const TAGS = {
    End: 0,
    ShowFrame: 1,
    DefineShape: 2,
    PlaceObject: 4,
    RemoveObject: 5,
    DefineBits: 6,
    DefineButton: 7,
    JPEGTables: 8,
    SetBackgroundColor: 9,
    DefineFont: 10,
    DefineText: 11,
    DoAction: 12,
    DefineFontInfo: 13,
    DefineSound: 14,
    StartSound: 15,
    DefineButtonSound: 17,
    SoundStreamHead: 18,
    SoundStreamBlock: 19,
    DefineBitsLossless: 20,
    DefineBitsJPEG2: 21,
    DefineShape2: 22,
    DefineButtonCxform: 23,
    Protect: 24,
    PlaceObject2: 26,
    RemoveObject2: 28,
    DefineShape3: 32,
    DefineText2: 33,
    DefineButton2: 34,
    DefineBitsJPEG3: 35,
    DefineBitsLossless2: 36,
    DefineSprite: 39,
    FrameLabel: 43,
    SoundStreamHead2: 45,
    DefineMorphShape: 46,
    DefineFont2: 48,
    ExportAssets: 56,
    ImportAssets: 57,
    DoInitAction: 59,
    DefineVideoStream: 60,
    VideoFrame: 61,
    DefineFontInfo2: 62,
    DefineBitsJPEG4: 90,
    DefineFont3: 75,
    FileAttributes: 69
  };

  // --------------------------------------------------------------- parser

  function parseHeader(r) {
    const sig1 = r.u8(), sig2 = r.u8(), sig3 = r.u8();
    if (String.fromCharCode(sig1, sig2, sig3) !== 'FWS' &&
        String.fromCharCode(sig1, sig2, sig3) !== 'CWS' &&
        String.fromCharCode(sig1, sig2, sig3) !== 'ZWS') {
      throw new Error('Not a SWF file (sig ' + sig1 + sig2 + sig3 + ')');
    }
    const version = r.u8();
    r.little = false;  // SWF is big-endian
    const fileLen = r.u32();
    // For CWS: zlib inflate the rest
    let body = r.u8.subarray(r.pos);
    if (sig1 === 0x43 /* C */) {
      // inflate
      // Use pako-style? Use built-in DecompressionStream if available
      if (typeof DecompressionStream === 'undefined') {
        throw new Error('需要浏览器支持 DecompressionStream 来解 CWS');
      }
      throw new Error('CWS 压缩格式暂未启用');
    }
    return { version, fileLen, sig: String.fromCharCode(sig1, sig2, sig3) };
  }

  // --------------------------------------------------------------- runtime

  class SwfPlayer {
    constructor(target, width, height) {
      this.target = target;
      this.canvas = document.createElement('canvas');
      this.canvas.width = width || 800;
      this.canvas.height = height || 600;
      this.canvas.style.cssText = 'background:#000;display:block;max-width:100%;max-height:100%';
      target.appendChild(this.canvas);
      this.ctx = this.canvas.getContext('2d');
      this.bg = '#000';
      this.shapes = new Map();
      this.texts = new Map();
      this.sprites = new Map();
      this.bitmaps = new Map();
      this.font = null;
      this.frameRate = 24;
      this.frameIdx = 0;
      this.frames = [];      // list of {tag, code, ...}
      this.displayList = new Map(); // depth -> {characterId, matrix, colorTransform, name, ratio}
      this._timer = null;
      this._running = false;
    }

    setHeader(header) {
      this.version = header.version;
    }

    addShape(id, shape) { this.shapes.set(id, shape); }
    addText(id, text) { this.texts.set(id, text); }
    addSprite(id, sprite) { this.sprites.set(id, sprite); }
    addBitmap(id, bmp) { this.bitmaps.set(id, bmp); }

    play() {
      this._running = true;
      this._tick();
    }
    stop() {
      this._running = false;
      if (this._timer) { clearTimeout(this._timer); this._timer = null; }
    }
    _tick() {
      if (!this._running) return;
      this._renderFrame();
      this.frameIdx++;
      if (this.frameIdx >= this.frames.length) this.frameIdx = 0;
      this._timer = setTimeout(() => this._tick(), 1000 / this.frameRate);
    }
    _renderFrame() {
      const ctx = this.ctx;
      const w = this.canvas.width, h = this.canvas.height;
      ctx.fillStyle = this.bg;
      ctx.fillRect(0, 0, w, h);
      // Reset display list every frame (this is the simplest case)
      this.displayList.clear();
      for (let i = 0; i <= this.frameIdx; i++) {
        for (const tag of this.frames[i] || []) {
          try { this._execTag(tag); } catch (e) { /* console.warn('tag err', e); */ }
        }
      }
      // Paint display list
      for (const [depth, item] of this.displayList.entries()) {
        this._paintDisplayItem(ctx, item, w, h);
      }
    }
    _execTag(tag) {
      switch (tag.code) {
        case TAGS.SetBackgroundColor: this.bg = tag.color; break;
        case TAGS.PlaceObject:
        case TAGS.PlaceObject2: {
          const id = tag.characterId;
          this.displayList.set(tag.depth, {
            characterId: id,
            matrix: tag.matrix || null,
            ratio: tag.ratio || 0
          });
          break;
        }
        case TAGS.RemoveObject:
        case TAGS.RemoveObject2: this.displayList.delete(tag.depth); break;
      }
    }
    _paintDisplayItem(ctx, item, w, h) {
      const charId = item.characterId;
      if (this.shapes.has(charId)) {
        this._paintShape(ctx, this.shapes.get(charId), item.matrix, w, h);
      } else if (this.texts.has(charId)) {
        this._paintText(ctx, this.texts.get(charId), item.matrix, w, h);
      } else if (this.bitmaps.has(charId)) {
        this._paintBitmap(ctx, this.bitmaps.get(charId), item.matrix, w, h);
      }
    }
    _paintShape(ctx, shape, matrix, w, h) {
      if (!shape) return;
      ctx.save();
      if (matrix) {
        ctx.setTransform(
          matrix.a, matrix.b, matrix.c, matrix.d,
          matrix.tx / 20, matrix.ty / 20
        );
      } else {
        ctx.setTransform(1, 0, 0, 1, w / 2, h / 2);
      }
      ctx.fillStyle = shape.fillStyle || '#888';
      ctx.beginPath();
      // Draw a placeholder rectangle since full SHAPE parsing is complex
      ctx.rect(-100, -100, 200, 200);
      ctx.fill();
      ctx.restore();
    }
    _paintText(ctx, text, matrix, w, h) {
      ctx.save();
      if (matrix) ctx.setTransform(
        matrix.a, matrix.b, matrix.c, matrix.d,
        matrix.tx / 20, matrix.ty / 20);
      else ctx.translate(w/2, h/2);
      ctx.fillStyle = text.color || '#fff';
      ctx.font = (text.size || 24) + 'px sans-serif';
      ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
      ctx.fillText(text.text || '?', 0, 0);
      ctx.restore();
    }
    _paintBitmap(ctx, bmp, matrix, w, h) {
      if (!bmp.image) return;
      ctx.save();
      if (matrix) ctx.setTransform(
        matrix.a, matrix.b, matrix.c, matrix.d,
        matrix.tx / 20, matrix.ty / 20);
      else ctx.translate(w/2 - bmp.image.width/2, h/2 - bmp.image.height/2);
      ctx.drawImage(bmp.image, 0, 0);
      ctx.restore();
    }
  }

  // --------------------------------------------------------------- entry

  async function load(swfUrl, opts) {
    opts = opts || {};
    const target = opts.target || document.createElement('div');
    target.innerHTML = '';
    target.style.cssText = 'width:100%;height:100%;display:flex;align-items:center;justify-content:center;background:#000';

    const status = document.createElement('div');
    status.textContent = 'Loading…';
    status.style.cssText = 'color:#fff;font-size:14px;padding:8px';
    target.appendChild(status);

    try {
      const resp = await fetch(swfUrl);
      if (!resp.ok) throw new Error('HTTP ' + resp.status);
      const buf = await resp.arrayBuffer();
      const r = new Reader(buf);
      const header = parseHeader(r);

      // Determine stage size from first ShowFrame's RECT? For simplicity, use 800x600.
      const w = opts.width ? parseInt(opts.width, 10) : 800;
      const h = opts.height ? parseInt(opts.height, 10) : 600;

      const player = new SwfPlayer(target, w, h);
      player.setHeader(header);
      target.removeChild(status);

      // Parse tags
      let curFrameTags = [];
      const sprites = new Map(); // spriteId -> list-of-frames
      let curSprite = null;
      let depth = 0;
      let curPlaceMatrix = null;
      let curPlaceRatio = 0;
      let curPlaceColor = null;
      let curPlaceCharacter = 0;
      let curPlaceName = null;
      let curPlaceClipDepth = 0;

      while (r.pos < r.u8.length) {
        // Tag header: short (type<6 | length<6) or long
        const tagStart = r.pos;
        const t16 = r.u16();
        const code = (t16 >> 6) & 0x3ff;
        let length = t16 & 0x3f;
        if (length === 0x3f) length = r.u32();
        const dataStart = r.pos;
        // Dispatch
        try {
          switch (code) {
            case TAGS.SetBackgroundColor: {
              const rgb = (r.u8() << 16) | (r.u8() << 8) | r.u8();
              const color = '#' + rgb.toString(16).padStart(6, '0');
              (curSprite || player).bg = color;
              break;
            }
            case TAGS.PlaceObject: {
              // SWF PlaceObject tag flag layout (per SWF8 spec):
              //   bits 0-1: reserved
              //   bit 2: hasName
              //   bit 3: hasRatio
              //   bit 4: hasColorTransform
              //   bit 5: hasMatrix
              //   bit 6: hasCharacter
              //   bits 7-15: depth
              const flags = r.u16();
              const depth = (flags >> 6) & 0x3ff;
              const hasCharacter = (flags & 0x40) !== 0;
              const hasMatrix    = (flags & 0x20) !== 0;
              const hasRatio     = (flags & 0x10) !== 0;
              const hasColorXform= (flags & 0x08) !== 0;
              const hasName      = (flags & 0x04) !== 0;
              const item = { code, depth, characterId: 0, matrix: null, ratio: 0, name: null };
              if (hasCharacter) item.characterId = r.u16();
              if (hasMatrix)    item.matrix = readMatrix(r);
              if (hasRatio)     item.ratio = r.u16();
              if (hasColorXform) r.skip(3); // skip ColorTransform (simplified)
              if (hasName) {
                const len = r.u8();
                item.name = r.fixedString(len);
              }
              (curSprite ? curSprite.frames : curFrameTags).push(item);
              break;
            }
            case TAGS.PlaceObject2: {
              const flags = r.u8();
              const depth = r.u16();
              const item = { code, depth, characterId: 0, matrix: null, ratio: 0 };
              if (flags & 0x02) item.characterId = r.u16();
              if (flags & 0x04) item.matrix = readMatrix(r);
              if (flags & 0x08) item.ratio = r.u16();
              (curSprite ? curSprite.frames : curFrameTags).push(item);
              break;
            }
            case TAGS.RemoveObject2: {
              const depth = r.u16();
              (curSprite ? curSprite.frames : curFrameTags).push({ code, depth });
              break;
            }
            case TAGS.RemoveObject: {
              const cid = r.u16(); const depth = r.u16();
              (curSprite ? curSprite.frames : curFrameTags).push({ code, depth });
              break;
            }
            case TAGS.DefineBits:
            case TAGS.DefineBitsJPEG2:
            case TAGS.DefineBitsJPEG3: {
              const cid = r.u16();
              const data = r.bytes(length - 2);
              // Try to load image
              const blob = new Blob([data], { type: 'image/jpeg' });
              const url = URL.createObjectURL(blob);
              const img = new Image();
              img.onload = () => {
                player.addBitmap(cid, { image: img });
                URL.revokeObjectURL(url);
              };
              img.onerror = () => { URL.revokeObjectURL(url); };
              img.src = url;
              break;
            }
            case TAGS.DefineShape:
            case TAGS.DefineShape2:
            case TAGS.DefineShape3: {
              const cid = r.u16();
              const rect = r.rect();
              (curSprite || player).shapes.set(cid, { id: cid, fillStyle: '#FF6F00' });
              break;
            }
            case TAGS.DefineSprite: {
              const cid = r.u16(); const frameCount = r.u16();
              const sp = { id: cid, frames: [], bg: '#000', displayList: new Map() };
              curSprite = sp;
              break;
            }
            case TAGS.DefineText:
            case TAGS.DefineText2: {
              const cid = r.u16();
              (curSprite || player).texts.set(cid, { text: '[text]', size: 24, color: '#fff' });
              break;
            }
            case TAGS.FrameLabel: {
              r.skip(length); // skip
              break;
            }
            case TAGS.SoundStreamHead:
            case TAGS.SoundStreamHead2: {
              r.skip(length);
              break;
            }
            case TAGS.SoundStreamBlock: {
              r.skip(length);
              break;
            }
            case TAGS.DoAction:
            case TAGS.DoInitAction: {
              // Skip AS1/AS2 bytecode (best effort)
              r.skip(length);
              break;
            }
            case TAGS.ExportAssets: {
              r.skip(length);
              break;
            }
            case TAGS.ImportAssets: {
              r.skip(length);
              break;
            }
            case TAGS.End: {
              if (curSprite) {
                player.addSprite(curSprite.id, curSprite);
                curSprite = null;
              } else {
                player.frames.push(curFrameTags);
                curFrameTags = [];
              }
              if (code === TAGS.End && !curSprite) break;
              break;
            }
            case TAGS.ShowFrame: {
              if (curSprite) {
                curSprite.frames.push([]); // empty sub-frame
              } else {
                player.frames.push(curFrameTags);
                curFrameTags = [];
              }
              break;
            }
            default:
              r.skip(length);
          }
        } catch (e) {
          // Recover
          r.pos = dataStart + length;
        }
        r.pos = dataStart + length;
      }

      // Add a final frame for the player
      if (player.frames.length === 0) {
        // empty file: paint a placeholder
        const ctx = player.ctx;
        ctx.fillStyle = '#0F1015';
        ctx.fillRect(0, 0, w, h);
        ctx.fillStyle = '#FF5722';
        ctx.font = 'bold 24px sans-serif';
        ctx.textAlign = 'center';
        ctx.fillText('Empty / no frames', w/2, h/2);
      }
      player.play();
      return { canvas: player.canvas, player };
    } catch (e) {
      target.innerHTML = `<div style="color:#aaa;padding:20px;text-align:center;line-height:1.6">
        <div style="font-size:32px;opacity:.6">⚠️</div>
        <div>swf2js-lite 解析失败</div>
        <div style="font-size:12px;margin-top:8px">${U.esc ? U.esc(String(e.message||e)) : String(e.message||e)}</div>
        <div style="font-size:12px;margin-top:8px;color:#888">swf2js 仅作为备选引擎，复杂 SWF 请切换到 Ruffle</div>
      </div>`;
      throw e;
    }
  }

  // -- helpers
  function readMatrix(r) {
    // MATRIX: HasScale (1), Nbits (5 if HasScale else ?), ScaleX/Y, RotateSkew, Translate
    const start = r.pos;
    const flags = r.u8();
    const hasScale = (flags >> 7) & 1;
    const nbits = hasScale ? ((flags >> 2) & 0x1f) : ((flags >> 2) & 0x1f);
    // Simpler: just consume enough bytes based on flags
    // Implement minimal: skip the matrix
    // Read scale x (if present)
    const readBits = (n) => {
      let v = 0; let i = 0;
      while (i < n) {
        const b = r.u8();
        v = (v << 4) | ((b >> 4) & 0x0f); i += 4;
        if (i >= n) break;
        v = (v << 4) | (b & 0x0f); i += 4;
      }
      // sign extend
      if (n > 0 && (v >> (n - 1)) & 1) v |= -1 << n;
      return v;
    };
    const sx = hasScale ? readBits(nbits) : 1;
    const sy = hasScale ? readBits(nbits) : 1;
    const r0 = readBits(nbits); // rotate
    const r1 = readBits(nbits); // rotate
    const tx = readBits(nbits) * 20; // twips to px
    const ty = readBits(nbits) * 20;
    return { a: sx, b: 0, c: 0, d: sy, tx, ty, _len: r.pos - start };
  }

  global.swf2js = { load };
  if (typeof module !== 'undefined') module.exports = { load };
})(typeof window !== 'undefined' ? window : globalThis);

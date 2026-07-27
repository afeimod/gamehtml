# swf2js-lite (built-in fallback engine)

This is a **lightweight pure-JS SWF parser / engine** included as a fallback
in Flash Game Box. It is *not* a full Flash Player — it supports only a
minimal subset of SWF tags and is intended to:

- Provide a "third engine" option for users
- Allow very simple Flash Lite / AS1-2 animations to display
- Display a graceful "fallback" canvas when the other engines fail

## When does it work?

It plays a "best effort" interpretation of:
- `ShowFrame`, `End`
- `SetBackgroundColor`
- `DefineBits`, `DefineBitsJPEG2/3` (renders the embedded JPEG as a sprite)
- `DefineShape`, `DefineShape2/3` (renders placeholder rectangles)
- `DefineText`, `DefineText2` (renders text labels)
- `PlaceObject`, `PlaceObject2`, `RemoveObject`, `RemoveObject2`
- `DefineSprite` (sub-timelines)
- `FrameLabel` (skipped)
- `SoundStreamHead/2/Block` (skipped)
- `DoAction`, `DoInitAction` (skipped — AS1/2 bytecode not executed)

## When does it NOT work?

- ActionScript 3 (uses different bytecode)
- Complex shape edge records
- Filters (DropShadow, Blur, Glow, Bevel)
- Video (FLV, H.264)
- CWS-compressed SWF (ZWS uses zlib; not currently inflated)
- Sound playback (no audio)

For any of the above, the engine will simply skip the unsupported tag and
continue to the next frame. The canvas will still render what it can.

## Public API

```js
swf2js.load(swfUrl, {
  target: HTMLElement,  // required
  width:  800,           // stage width
  height: 600            // stage height
})
  .then(({ canvas, player }) => {
    // canvas appended to target
    // player.stop() to stop
  })
  .catch(err => { /* ... */ });
```

## Files

- `swf2js.js` - the engine
- `README.md` - this file

## License

MIT.

// Ruffle polyfill injector for online pages.
// Replaces __RUFFLE_PATH__ with the on-disk engine path at injection time.
// Loads ruffle.js with polyfills enabled so legacy <object>/<embed> Flash
// content is automatically replaced by a Ruffle player.
(function () {
  if (window.__ruffleInjected) return;
  window.__ruffleInjected = true;
  var PATH = "__RUFFLE_PATH__";
  try {
    window.RufflePlayer = window.RufflePlayer || {};
    window.RufflePlayer.config = window.RufflePlayer.config || {
      allowScriptAccess: true,
      autoplay: "on",
      upgradeToHttps: true,
      compatibilityRules: true,
      warnOnUnsupportedContent: false,
      showSwfDownload: false,
      contextMenu: "on",
      polyfills: true,
      openUrlMode: "allow",
      allowNetworking: "all",
      backgroundExecutionMode: "mainThread",
      publicPath: PATH
    };
    // also keep publicPath fresh
    window.RufflePlayer.config.publicPath = PATH;
  } catch (e) {}
  var s = document.createElement("script");
  s.src = PATH + "ruffle.js";
  s.async = true;
  s.onload = function () { try { AndroidBridge.log("ruffle polyfill loaded"); } catch (e) {} };
  s.onerror = function () { try { AndroidBridge.log("ruffle polyfill failed: " + PATH); } catch (e) {} };
  document.head.appendChild(s);
})();

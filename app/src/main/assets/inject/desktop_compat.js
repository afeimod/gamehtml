// Desktop compatibility tweaks injected into browsed pages.
//  - keeps the active flash element focused so key events reach the game
//  - disables rubber-band scrolling that steals touch events
//  - forces a desktop-like viewport when in compat mode
(function () {
  if (window.__fbCompatInjected) return;
  window.__fbCompatInjected = true;

  // Prevent the page from scrolling when the user interacts with a flash canvas
  document.addEventListener("touchstart", function (e) {
    var t = e.target;
    if (t && (t.tagName === "CANVAS" || (t.tagName === "RUFFLE-PLAYER") ||
        (t.id === "canvas") || (t.className && t.className.indexOf("waflash") >= 0))) {
      e.stopPropagation();
    }
  }, { passive: true, capture: true });

  // Re-focus the flash element whenever the window gains focus
  window.addEventListener("focus", function () {
    var el = document.querySelector("ruffle-player") || document.getElementById("canvas");
    if (el && el.focus) { try { el.focus(); } catch (e) {} }
  });

  // Suppress context menu on flash surfaces for a smoother mobile experience
  document.addEventListener("contextmenu", function (e) {
    var t = e.target;
    if (t && (t.tagName === "CANVAS" || t.tagName === "RUFFLE-PLAYER" ||
        (t.className && t.className.indexOf("waflash") >= 0))) {
      e.preventDefault();
    }
  }, true);

  // Notify the host that the page is interactive
  try { AndroidBridge.log("compat injected: " + location.href); } catch (e) {}
})();

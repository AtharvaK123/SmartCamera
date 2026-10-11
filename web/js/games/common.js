/* Shared bits for the camera-tab games. */
window.SC = window.SC || {};
SC.games = SC.games || {};

(function () {
  let actx = null;
  // Soft, short tones — the "satisfying" layer. Silent if audio is unavailable.
  function tone(freq = 520, dur = 0.09, vol = 0.06, type = 'sine') {
    try {
      actx = actx || new (window.AudioContext || window.webkitAudioContext)();
      const o = actx.createOscillator(), g = actx.createGain();
      o.type = type; o.frequency.value = freq;
      g.gain.setValueAtTime(vol, actx.currentTime);
      g.gain.exponentialRampToValueAtTime(0.0001, actx.currentTime + dur);
      o.connect(g).connect(actx.destination);
      o.start(); o.stop(actx.currentTime + dur + 0.02);
    } catch {}
  }
  const buzz = (ms = 12) => { try { navigator.vibrate?.(ms); } catch {} };

  // Canvas that tracks its host's size at device pixel ratio.
  function fitCanvas(host, onResize) {
    const canvas = document.createElement('canvas');
    canvas.style.cssText = 'position:absolute;inset:0;width:100%;height:100%;touch-action:none;display:block';
    host.appendChild(canvas);
    const ctx = canvas.getContext('2d');
    const size = { w: 0, h: 0, dpr: 1 };
    const ro = new ResizeObserver(() => {
      size.dpr = window.devicePixelRatio || 1;
      size.w = host.clientWidth; size.h = host.clientHeight;
      canvas.width = Math.max(1, size.w * size.dpr); canvas.height = Math.max(1, size.h * size.dpr);
      ctx.setTransform(size.dpr, 0, 0, size.dpr, 0, 0);
      onResize && onResize(size);
    });
    ro.observe(host);
    return { canvas, ctx, size, dispose: () => { ro.disconnect(); canvas.remove(); } };
  }

  function roundRect(ctx, x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }

  // Overlay card inside a game (start / finished screens).
  function overlay(host, html) {
    const el = document.createElement('div');
    el.className = 'game-overlay';
    el.innerHTML = `<div class="game-card">${html}</div>`;
    host.appendChild(el);
    return el;
  }

  SC.games.util = { tone, buzz, fitCanvas, roundRect, overlay };
})();

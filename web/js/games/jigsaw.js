/* Jigsaw: interlocking pieces cut from a calm, freshly painted landscape.
   Stress sets the piece count — 3×3 when stressed, 4×4 moderate, 5×5 relaxed. */
(function () {
  const { tone, buzz, fitCanvas, overlay } = SC.games.util;

  const PALETTES = [
    { sky: ['#F9D9B8', '#F4A68C'], sun: '#FFF1D6', hills: ['#C98B7A', '#9C6B6B', '#6E4F5F', '#47394B'] },
    { sky: ['#CFE6EE', '#8FC1D4'], sun: '#FFFBEA', hills: ['#9BC59D', '#6FA27A', '#4B7F5E', '#2F5D50'] },
    { sky: ['#E5DDF5', '#B9A8E0'], sun: '#FFF6E5', hills: ['#9C8FC4', '#7568A6', '#544A82', '#352F5C'] },
    { sky: ['#FCE9C0', '#F2C14E'], sun: '#FFFDF2', hills: ['#E4A35C', '#C27C3E', '#8E5A33', '#5A3B26'] },
  ];

  function paint(size) {
    const c = document.createElement('canvas');
    c.width = c.height = size;
    const g = c.getContext('2d');
    const p = PALETTES[Math.floor(Math.random() * PALETTES.length)];
    const sky = g.createLinearGradient(0, 0, 0, size);
    sky.addColorStop(0, p.sky[0]); sky.addColorStop(0.7, p.sky[1]);
    g.fillStyle = sky; g.fillRect(0, 0, size, size);
    const sx = size * (0.25 + Math.random() * 0.5), sy = size * (0.25 + Math.random() * 0.15);
    g.fillStyle = p.sun; g.globalAlpha = 0.35;
    g.beginPath(); g.arc(sx, sy, size * 0.2, 0, Math.PI * 2); g.fill();
    g.globalAlpha = 1;
    g.beginPath(); g.arc(sx, sy, size * 0.11, 0, Math.PI * 2); g.fill();
    p.hills.forEach((col, i) => {
      const base = size * (0.5 + i * 0.13);
      const amp = size * (0.07 - i * 0.008), f = 1.5 + Math.random() * 2, ph = Math.random() * 6;
      g.fillStyle = col;
      g.beginPath(); g.moveTo(0, size);
      for (let x = 0; x <= size; x += 4) g.lineTo(x, base - Math.sin(x / size * Math.PI * f + ph) * amp - Math.sin(x / size * 9 + ph) * amp * 0.25);
      g.lineTo(size, size); g.closePath(); g.fill();
    });
    // birds
    g.strokeStyle = 'rgba(40,30,40,.45)'; g.lineWidth = Math.max(1.5, size / 300);
    for (let i = 0; i < 3; i++) {
      const bx = size * (0.15 + Math.random() * 0.7), by = size * (0.12 + Math.random() * 0.2), w = size * 0.025;
      g.beginPath(); g.moveTo(bx - w, by); g.quadraticCurveTo(bx - w / 2, by - w * 0.6, bx, by); g.quadraticCurveTo(bx + w / 2, by - w * 0.6, bx + w, by); g.stroke();
    }
    return c;
  }

  // One edge of a piece: 0 = flat, 1 = tab out, -1 = blank in. Drawn from (0,0) to (s,0) in local coords.
  function edge(path, s, kind, tf) {
    if (!kind) { const [x, y] = tf(s, 0); path.lineTo(x, y); return; }
    const t = s * 0.22 * kind;
    const pts = [[s * 0.36, 0], [s * 0.34, -t * 0.55], [s * 0.3, -t], [s * 0.5, -t * 1.08], [s * 0.7, -t], [s * 0.66, -t * 0.55], [s * 0.64, 0], [s, 0]];
    path.lineTo(...tf(...pts[0]));
    path.bezierCurveTo(...tf(...pts[1]), ...tf(...pts[2]), ...tf(...pts[3]));
    path.bezierCurveTo(...tf(...pts[4]), ...tf(...pts[5]), ...tf(...pts[6]));
    path.lineTo(...tf(...pts[7]));
  }

  function piecePath(s, e) {
    // e = [top, right, bottom, left]; positive = tab pointing outward
    const p = new Path2D();
    p.moveTo(0, 0);
    edge(p, s, e[0], (x, y) => [x, y]);
    edge(p, s, e[1], (x, y) => [s - y, x]);
    edge(p, s, e[2], (x, y) => [s - x, s - y]);
    edge(p, s, e[3], (x, y) => [y, s - x]);
    p.closePath();
    return p;
  }

  function start(host, { tier = 'moderate', onHud = () => {} } = {}) {
    const N = { high: 3, moderate: 4, low: 5 }[tier];
    let pieces = [], img = null, cell = 0, bx = 0, by = 0, drag = null, raf = 0, placed = 0, t0 = 0, done = false;

    // Random tab directions shared between neighbours.
    const hz = Array.from({ length: N - 1 }, () => Array.from({ length: N }, () => (Math.random() < 0.5 ? 1 : -1)));
    const vt = Array.from({ length: N }, () => Array.from({ length: N - 1 }, () => (Math.random() < 0.5 ? 1 : -1)));

    const view = fitCanvas(host, layout);
    const { canvas, ctx, size } = view;

    function layout(s) {
      const tall = s.h > s.w * 1.1;
      const boardSize = tall ? Math.min(s.w * 0.78, s.h * 0.56) : Math.min(s.h * 0.86, s.w * 0.52);
      cell = boardSize / N;
      bx = tall ? (s.w - boardSize) / 2 : s.w * 0.05;
      by = tall ? 10 : (s.h - boardSize) / 2;
      img = img && img.width >= boardSize ? img : paint(Math.ceil(boardSize * (window.devicePixelRatio || 1)));
      const tray = tall ? { x: 8, y: by + boardSize + 14, w: s.w - 16 - cell, h: s.h - (by + boardSize + 14) - cell - 6 }
        : { x: bx + boardSize + 24, y: s.h * 0.28, w: s.w - (bx + boardSize + 24) - cell - 8, h: s.h * 0.72 - cell - 10 };
      if (!pieces.length) {
        for (let r = 0; r < N; r++) for (let c = 0; c < N; c++) {
          const e = [r ? -vt[c][r - 1] : 0, c < N - 1 ? hz[c][r] : 0, r < N - 1 ? vt[c][r] : 0, c ? -hz[c - 1][r] : 0];
          pieces.push({ r, c, e, x: tray.x + Math.random() * Math.max(1, tray.w), y: tray.y + Math.random() * Math.max(1, tray.h), locked: false });
        }
        pieces.sort(() => Math.random() - 0.5);
      } else {
        // keep loose pieces on screen after a resize
        pieces.forEach((p) => {
          if (p.locked) { p.x = bx + p.c * cell; p.y = by + p.r * cell; }
          else { p.x = Math.min(Math.max(0, p.x), s.w - cell); p.y = Math.min(Math.max(0, p.y), s.h - cell); }
        });
      }
      pieces.forEach((p) => { p.path = piecePath(cell, p.e); });
    }

    function drawPiece(p, lift) {
      ctx.save();
      ctx.translate(p.x, p.y);
      if (lift) { ctx.shadowColor = 'rgba(0,0,0,.45)'; ctx.shadowBlur = 16; ctx.shadowOffsetY = 6; }
      ctx.fillStyle = '#000'; ctx.fill(p.path);
      ctx.shadowColor = 'transparent';
      ctx.clip(p.path);
      const k = img.width / (cell * N);
      ctx.drawImage(img, (p.c * cell - cell * 0.5) * k, (p.r * cell - cell * 0.5) * k, cell * 2 * k, cell * 2 * k, -cell * 0.5, -cell * 0.5, cell * 2, cell * 2);
      ctx.restore();
      ctx.save(); ctx.translate(p.x, p.y);
      ctx.strokeStyle = p.locked ? 'rgba(255,255,255,.12)' : 'rgba(255,255,255,.55)'; ctx.lineWidth = 1.2; ctx.stroke(p.path);
      ctx.restore();
    }

    function frame() {
      if (!img) { raf = requestAnimationFrame(frame); return; }
      ctx.clearRect(0, 0, size.w, size.h);
      ctx.globalAlpha = 0.14; ctx.drawImage(img, bx, by, cell * N, cell * N); ctx.globalAlpha = 1;
      ctx.strokeStyle = 'rgba(243,238,228,.35)'; ctx.setLineDash([5, 6]); ctx.strokeRect(bx, by, cell * N, cell * N); ctx.setLineDash([]);
      pieces.filter((p) => p.locked).forEach((p) => drawPiece(p));
      pieces.filter((p) => !p.locked && p !== drag?.p).forEach((p) => drawPiece(p));
      if (drag) drawPiece(drag.p, true);
      raf = requestAnimationFrame(frame);
    }

    // isPointInPath takes device pixels and applies the current transform to the path.
    function pick(x, y) {
      for (let i = pieces.length - 1; i >= 0; i--) {
        const p = pieces[i];
        if (p.locked) continue;
        ctx.save(); ctx.translate(p.x, p.y);
        const hit = ctx.isPointInPath(p.path, x * size.dpr, y * size.dpr);
        ctx.restore();
        if (hit) return p;
      }
      return null;
    }
    const pt = (e) => { const r = canvas.getBoundingClientRect(); return [e.clientX - r.left, e.clientY - r.top]; };

    canvas.addEventListener('pointerdown', (e) => {
      if (done) return;
      const [x, y] = pt(e);
      const p = pick(x, y);
      if (!p) return;
      if (!t0) t0 = performance.now();
      drag = { p, dx: x - p.x, dy: y - p.y };
      pieces.splice(pieces.indexOf(p), 1); pieces.push(p);
      canvas.setPointerCapture(e.pointerId);
      tone(480, 0.04, 0.025);
    });
    canvas.addEventListener('pointermove', (e) => {
      if (!drag) return;
      const [x, y] = pt(e);
      drag.p.x = x - drag.dx; drag.p.y = y - drag.dy;
    });
    canvas.addEventListener('pointerup', () => {
      if (!drag) return;
      const p = drag.p; drag = null;
      const tx = bx + p.c * cell, ty = by + p.r * cell;
      if (Math.hypot(p.x - tx, p.y - ty) < cell * 0.32) {
        p.x = tx; p.y = ty; p.locked = true; placed++;
        tone(620 + placed * 12, 0.12, 0.06); buzz(15);
        hud();
        if (placed === N * N) finish();
      }
    });

    function hud() { onHud(`${placed} / ${N * N} pieces`); }
    function finish() {
      done = true;
      const secs = Math.round((performance.now() - t0) / 1000);
      [523, 659, 784].forEach((f, i) => setTimeout(() => tone(f, 0.25, 0.06), i * 120));
      buzz(40);
      setTimeout(() => {
        const o = overlay(host, `<b>Solved</b><span>${N * N} pieces in ${Math.floor(secs / 60)}:${String(secs % 60).padStart(2, '0')}</span><button class="btn sm pop">New puzzle</button>`);
        o.querySelector('button').onclick = () => { stop(); SC.games.jigsaw._restart?.(); };
      }, 700);
    }

    hud();
    raf = requestAnimationFrame(frame);
    function stop() { cancelAnimationFrame(raf); view.dispose(); host.innerHTML = ''; }
    return stop;
  }

  SC.games.jigsaw = {
    name: 'Jigsaw',
    start(host, opts) {
      let stop = start(host, opts);
      SC.games.jigsaw._restart = () => { stop = start(host, opts); };
      return () => stop();
    },
  };
})();

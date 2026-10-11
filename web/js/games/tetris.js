/* Calm Tetris: soft colors, ghost piece, gentle sounds, no speed panic.
   Stress sets the pace — the more stressed, the slower it falls and the less it speeds up. */
(function () {
  const { tone, buzz, fitCanvas, roundRect, overlay } = SC.games.util;
  const COLS = 10, ROWS = 20;
  const SHAPES = {
    I: [[0, 1], [1, 1], [2, 1], [3, 1]], O: [[1, 0], [2, 0], [1, 1], [2, 1]], T: [[1, 0], [0, 1], [1, 1], [2, 1]],
    S: [[1, 0], [2, 0], [0, 1], [1, 1]], Z: [[0, 0], [1, 0], [1, 1], [2, 1]], J: [[0, 0], [0, 1], [1, 1], [2, 1]], L: [[2, 0], [0, 1], [1, 1], [2, 1]],
  };
  const COLORS = { I: '#7FC8D9', O: '#F2C14E', T: '#B79CED', S: '#9BC59D', Z: '#F78154', J: '#7EA6E0', L: '#F2A65A' };

  function start(host, { tier = 'moderate', onHud = () => {} } = {}) {
    const PACE = { high: { base: 1150, min: 900 }, moderate: { base: 900, min: 520 }, low: { base: 750, min: 300 } }[tier];
    const board = Array.from({ length: ROWS }, () => Array(COLS).fill(null));
    let bag = [], cur, next, score = 0, lines = 0, over = false, paused = true, raf = 0, last = 0, acc = 0;
    let flashes = []; // line-clear sparkles
    let cell = 20, ox = 16, oy = 0;

    const view = fitCanvas(host, (s) => {
      cell = Math.floor(Math.min((s.h - 8) / ROWS, (s.w * 0.62) / COLS));
      oy = Math.floor((s.h - cell * ROWS) / 2);
      ox = Math.max(12, Math.floor(s.w * 0.06));
    });
    const { canvas, ctx, size } = view;

    function take() {
      if (!bag.length) bag = Object.keys(SHAPES).sort(() => Math.random() - 0.5);
      const k = bag.pop();
      return { k, cells: SHAPES[k].map(([x, y]) => [x, y]), x: 3, y: -1 };
    }
    const blocks = (p, dx = 0, dy = 0, cells = p.cells) => cells.map(([x, y]) => [p.x + x + dx, p.y + y + dy]);
    const fits = (p, dx, dy, cells) => blocks(p, dx, dy, cells).every(([x, y]) => x >= 0 && x < COLS && y < ROWS && (y < 0 || !board[y][x]));

    function spawn() {
      cur = next || take();
      next = take();
      if (!fits(cur, 0, 0)) finish();
    }
    function rotate() {
      if (cur.k === 'O') return;
      const rot = cur.cells.map(([x, y]) => [2 - y, x]);
      for (const kick of [0, -1, 1, -2, 2]) {
        if (fits(cur, kick, 0, rot)) { cur.cells = rot; cur.x += kick; tone(660, 0.05, 0.03); return; }
      }
    }
    function move(dx) { if (fits(cur, dx, 0)) { cur.x += dx; tone(420, 0.03, 0.02); } }
    function drop() { if (fits(cur, 0, 1)) { cur.y++; return true; } lock(); return false; }
    function hardDrop() { let n = 0; while (fits(cur, 0, 1)) { cur.y++; n++; } score += n; lock(); }
    function ghostY() { let d = 0; while (fits(cur, 0, d + 1)) d++; return d; }

    function lock() {
      for (const [x, y] of blocks(cur)) { if (y < 0) return finish(); board[y][x] = cur.k; }
      tone(300, 0.07, 0.05, 'triangle'); buzz(8);
      const full = [];
      for (let y = 0; y < ROWS; y++) if (board[y].every(Boolean)) full.push(y);
      if (full.length) {
        full.forEach((y) => { flashes.push({ y, t: performance.now() }); board.splice(y, 1); board.unshift(Array(COLS).fill(null)); });
        lines += full.length;
        score += [0, 100, 300, 500, 800][full.length];
        full.forEach((_, i) => setTimeout(() => tone(660 + i * 180, 0.16, 0.06), i * 70));
        buzz(25);
      }
      hud();
      spawn();
    }
    function hud() { onHud(`${score.toLocaleString()} · ${lines} line${lines === 1 ? '' : 's'}`); }
    function interval() { return Math.max(PACE.min, PACE.base - Math.floor(lines / 10) * 60); }

    function finish() {
      over = true; paused = true;
      const o = overlay(host, `<b>Nice run</b><span>${score.toLocaleString()} points · ${lines} lines</span><button class="btn sm pop">Play again</button>`);
      o.querySelector('button').onclick = () => { o.remove(); reset(); };
    }
    function reset() {
      board.forEach((r) => r.fill(null));
      score = 0; lines = 0; over = false; paused = false; next = null; spawn(); hud();
    }

    function drawCell(x, y, color, alpha = 1) {
      const px = ox + x * cell, py = oy + y * cell;
      ctx.globalAlpha = alpha;
      ctx.fillStyle = color;
      roundRect(ctx, px + 1.5, py + 1.5, cell - 3, cell - 3, Math.max(3, cell * 0.22));
      ctx.fill();
      ctx.fillStyle = 'rgba(255,255,255,.28)';
      roundRect(ctx, px + 4, py + 4, cell - 8, Math.max(2, cell * 0.18), 2);
      ctx.fill();
      ctx.globalAlpha = 1;
    }

    function frame(now) {
      const dt = now - (last || now); last = now;
      if (!paused && !over) {
        acc += dt;
        if (acc >= interval()) { acc = 0; drop(); }
      }
      ctx.clearRect(0, 0, size.w, size.h);
      // well
      ctx.fillStyle = 'rgba(243,238,228,.06)';
      roundRect(ctx, ox - 4, oy - 4, cell * COLS + 8, cell * ROWS + 8, 10); ctx.fill();
      ctx.strokeStyle = 'rgba(243,238,228,.06)'; ctx.lineWidth = 1;
      for (let x = 1; x < COLS; x++) { ctx.beginPath(); ctx.moveTo(ox + x * cell, oy); ctx.lineTo(ox + x * cell, oy + ROWS * cell); ctx.stroke(); }
      for (let y = 0; y < ROWS; y++) for (let x = 0; x < COLS; x++) if (board[y][x]) drawCell(x, y, COLORS[board[y][x]]);
      if (cur && !over) {
        const g = ghostY();
        blocks(cur, 0, g).forEach(([x, y]) => y >= 0 && drawCell(x, y, COLORS[cur.k], 0.18));
        blocks(cur).forEach(([x, y]) => y >= 0 && drawCell(x, y, COLORS[cur.k]));
      }
      // line-clear glow
      flashes = flashes.filter((f) => now - f.t < 420);
      flashes.forEach((f) => {
        const a = 1 - (now - f.t) / 420;
        ctx.fillStyle = `rgba(255,252,240,${a * 0.75})`;
        ctx.fillRect(ox - 6 * a, oy + f.y * cell, cell * COLS + 12 * a, cell);
      });
      // side panel: next piece
      const px = ox + cell * COLS + 18, small = Math.max(10, Math.floor(cell * 0.7));
      if (next && px + small * 4 < size.w) {
        const py = oy + cell * ROWS - small * 4 - 26;
        ctx.fillStyle = 'rgba(243,238,228,.6)';
        ctx.font = `500 11px "DM Mono", monospace`;
        ctx.fillText('NEXT', px, py - 8);
        SHAPES[next.k].forEach(([x, y]) => {
          ctx.fillStyle = COLORS[next.k];
          roundRect(ctx, px + x * small, py + y * small, small - 2, small - 2, 3); ctx.fill();
        });
      }
      raf = requestAnimationFrame(frame);
    }

    // Touch: tap = rotate, drag sideways = move, flick down = drop.
    let touch = null;
    canvas.addEventListener('pointerdown', (e) => {
      if (paused || over) return;
      touch = { x: e.clientX, y: e.clientY, t: performance.now(), mx: 0, my: 0 };
      canvas.setPointerCapture(e.pointerId);
    });
    canvas.addEventListener('pointermove', (e) => {
      if (!touch) return;
      const step = Math.max(18, cell);
      const dx = e.clientX - touch.x - touch.mx * step;
      if (Math.abs(dx) >= step) { const d = Math.sign(dx); move(d); touch.mx += d; touch.moved = true; }
      const dy = e.clientY - touch.y - touch.my * step;
      if (dy >= step) { drop(); touch.my++; touch.moved = true; acc = 0; }
    });
    canvas.addEventListener('pointerup', (e) => {
      if (!touch) return;
      const dt = performance.now() - touch.t, dy = e.clientY - touch.y, dx = e.clientX - touch.x;
      if (dy > 60 && dt < 250 && Math.abs(dy) > Math.abs(dx) * 1.5) hardDrop();
      else if (!touch.moved && dt < 300) rotate();
      touch = null;
    });
    const onKey = (e) => {
      if (paused || over) return;
      const k = e.key;
      if (k === 'ArrowLeft') move(-1); else if (k === 'ArrowRight') move(1);
      else if (k === 'ArrowUp' || k === 'x') rotate(); else if (k === 'ArrowDown') { drop(); acc = 0; }
      else if (k === ' ') hardDrop(); else return;
      e.preventDefault();
    };
    window.addEventListener('keydown', onKey);

    const intro = overlay(host, `<b>Calm Tetris</b><span>Tap to turn · drag to move · flick down to drop</span><button class="btn sm pop">Start</button>`);
    intro.querySelector('button').onclick = () => { intro.remove(); reset(); };
    onHud('Tap start');
    raf = requestAnimationFrame(frame);

    return () => { cancelAnimationFrame(raf); window.removeEventListener('keydown', onKey); view.dispose(); host.innerHTML = ''; };
  }

  SC.games.tetris = { name: 'Tetris', start };
})();

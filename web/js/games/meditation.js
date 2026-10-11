/* Guided breathing meditation. Stress picks the pattern and length:
   high → long, slow exhales (4 in / 6 out) for 3 min; moderate → box breathing 2 min; low → even 5/5 for 1 min. */
(function () {
  const { fitCanvas, overlay, tone } = SC.games.util;

  const PLANS = {
    high: { name: 'Long exhale', min: 3, steps: [['Breathe in', 4], ['Breathe out', 6]] },
    moderate: { name: 'Box breathing', min: 2, steps: [['Breathe in', 4], ['Hold', 4], ['Breathe out', 4], ['Hold', 4]] },
    low: { name: 'Even breathing', min: 1, steps: [['Breathe in', 5], ['Breathe out', 5]] },
  };

  function start(host, { tier = 'moderate', onHud = () => {}, onDone } = {}) {
    const plan = PLANS[tier];
    let minutes = plan.min, running = false, t0 = 0, raf = 0, lastStep = -1, hum = null;
    const cycle = plan.steps.reduce((a, s) => a + s[1], 0);
    const view = fitCanvas(host);
    const { ctx, size } = view;

    function phaseAt(sec) {
      let t = sec % cycle;
      for (let i = 0; i < plan.steps.length; i++) {
        const [label, d] = plan.steps[i];
        if (t < d) return { i, label, p: t / d, left: d - t };
        t -= d;
      }
      return { i: 0, label: plan.steps[0][0], p: 0, left: plan.steps[0][1] };
    }

    // Radius follows the breath: grows on "in", holds, shrinks on "out".
    function radiusAt(ph, max, min) {
      const ease = (x) => 0.5 - 0.5 * Math.cos(Math.PI * x);
      if (ph.label === 'Breathe in') return min + (max - min) * ease(ph.p);
      if (ph.label === 'Breathe out') return max - (max - min) * ease(ph.p);
      const prev = plan.steps[(ph.i - 1 + plan.steps.length) % plan.steps.length][0];
      return prev === 'Breathe in' ? max : min;
    }

    function frame(now) {
      const w = size.w, h = size.h, cx = w * 0.45, cy = h * 0.5;
      const max = Math.min(w, h) * 0.36, min = max * 0.42;
      const sec = running ? (now - t0) / 1000 : 0;
      const total = minutes * 60;
      const ph = phaseAt(sec);
      const r = running ? radiusAt(ph, max, min) : min + (max - min) * 0.3;
      ctx.clearRect(0, 0, w, h);

      // soft halo rings
      for (let i = 3; i >= 1; i--) {
        ctx.fillStyle = `rgba(125,184,160,${0.05 * i})`;
        ctx.beginPath(); ctx.arc(cx, cy, r + i * 14, 0, Math.PI * 2); ctx.fill();
      }
      const g = ctx.createRadialGradient(cx - r * 0.3, cy - r * 0.3, r * 0.1, cx, cy, r);
      g.addColorStop(0, '#A8D5C2'); g.addColorStop(1, '#2F5D50');
      ctx.fillStyle = g;
      ctx.beginPath(); ctx.arc(cx, cy, r, 0, Math.PI * 2); ctx.fill();

      // session progress ring
      const prog = Math.min(1, sec / total);
      ctx.lineWidth = 4; ctx.lineCap = 'round';
      ctx.strokeStyle = 'rgba(243,238,228,.14)';
      ctx.beginPath(); ctx.arc(cx, cy, max + 34, 0, Math.PI * 2); ctx.stroke();
      ctx.strokeStyle = '#F3EEE4';
      ctx.beginPath(); ctx.arc(cx, cy, max + 34, -Math.PI / 2, -Math.PI / 2 + prog * Math.PI * 2); ctx.stroke();

      ctx.fillStyle = '#F3EEE4'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
      ctx.font = `700 ${Math.max(16, max * 0.17)}px "Bricolage Grotesque", sans-serif`;
      ctx.fillText(running ? ph.label : plan.name, cx, cy - (running ? 8 : 0));
      if (running) {
        ctx.font = `500 ${Math.max(12, max * 0.11)}px "DM Mono", monospace`;
        ctx.globalAlpha = 0.75; ctx.fillText(String(Math.ceil(ph.left)), cx, cy + max * 0.17); ctx.globalAlpha = 1;
      }

      if (running) {
        if (ph.i !== lastStep) { lastStep = ph.i; cue(ph.label); }
        const left = Math.max(0, total - sec);
        onHud(`${Math.floor(left / 60)}:${String(Math.floor(left % 60)).padStart(2, '0')} left`);
        if (sec >= total) return finish();
      }
      raf = requestAnimationFrame(frame);
    }

    function cue(label) {
      navigator.vibrate?.(label === 'Hold' ? 0 : 20);
      if (!hum) return;
      const f = label === 'Breathe in' ? 220 : label === 'Breathe out' ? 165 : 196;
      hum.osc.frequency.setTargetAtTime(f, hum.ctx.currentTime, 0.6);
    }

    function startHum() {
      try {
        const actx = new (window.AudioContext || window.webkitAudioContext)();
        const osc = actx.createOscillator(), gain = actx.createGain();
        osc.type = 'sine'; osc.frequency.value = 196;
        gain.gain.value = 0;
        gain.gain.setTargetAtTime(0.035, actx.currentTime, 1.5);
        osc.connect(gain).connect(actx.destination); osc.start();
        hum = { ctx: actx, osc, gain };
      } catch {}
    }
    function stopHum() {
      if (!hum) return;
      try { hum.gain.gain.setTargetAtTime(0, hum.ctx.currentTime, 0.4); const h = hum; setTimeout(() => h.ctx.close(), 1500); } catch {}
      hum = null;
    }

    function finish() {
      running = false; stopHum();
      tone(523, 0.6, 0.05); setTimeout(() => tone(784, 0.8, 0.04), 300);
      SC.services.tts.speak('Nicely done. Take a moment, then scan again to see how you feel.');
      const o = overlay(host, `<b>Session complete</b><span>${minutes} min of ${plan.name.toLowerCase()}</span>
        <div class="row" style="justify-content:center"><button class="btn sm ghost" data-again>Again</button><button class="btn sm pop" data-scan>Scan again</button></div>`);
      o.querySelector('[data-again]').onclick = () => { o.remove(); intro(); };
      o.querySelector('[data-scan]').onclick = () => onDone && onDone();
      raf = requestAnimationFrame(frame);
    }

    function intro() {
      running = false;
      const o = overlay(host, `<b>${plan.name}</b>
        <span>${plan.steps.map((s) => `${s[0].replace('Breathe ', '')} ${s[1]}s`).join(' · ')}</span>
        <div class="seg" role="group" aria-label="Length">${[1, 3, 5].map((m) => `<button data-min="${m}" aria-pressed="${m === minutes}">${m} min</button>`).join('')}</div>
        <label class="row tiny" style="justify-content:center;gap:8px"><input type="checkbox" data-hum checked> Soft tone</label>
        <button class="btn sm pop" data-go>Begin</button>`);
      o.querySelectorAll('[data-min]').forEach((b) => {
        b.onclick = () => { minutes = Number(b.dataset.min); o.querySelectorAll('[data-min]').forEach((x) => x.setAttribute('aria-pressed', x === b)); };
      });
      o.querySelector('[data-go]').onclick = () => {
        const useHum = o.querySelector('[data-hum]').checked;
        o.remove();
        SC.services.tts.speak(`Let's breathe together for ${minutes} minute${minutes > 1 ? 's' : ''}. Follow the circle.`);
        if (useHum) startHum();
        running = true; lastStep = -1; t0 = performance.now();
      };
      onHud(plan.name);
    }

    intro();
    raf = requestAnimationFrame(frame);
    return () => { cancelAnimationFrame(raf); stopHum(); SC.services.tts.stop(); view.dispose(); host.innerHTML = ''; };
  }

  SC.games.meditate = { name: 'Meditate', start };
})();

/* Home: steps, heart rate, stress, nutrition score. */
(function () {
  const { icon, esc, num, clamp, todayKey, statusChip, barChart, ago, sheet, toast } = SC.ui;

  const stepsLevel = (pct) => (pct >= 0.75 ? ['good', 'On track'] : pct >= 0.4 ? ['warn', 'Moderate'] : ['bad', 'Low']);
  const stressLevel = (s) => (s < 34 ? ['good', 'Low'] : s < 67 ? ['warn', 'Moderate'] : ['bad', 'High']);
  const nutriLevel = (s) => (s >= 70 ? ['good', 'Good'] : s >= 45 ? ['warn', 'Fair'] : ['bad', 'Poor']);
  const COLOR = { good: 'var(--good)', warn: 'var(--warn)', bad: 'var(--bad)' };

  function stepsCard(st) {
    const steps = st.steps[todayKey()] || 0;
    const goal = st.profile.stepGoal;
    const pct = clamp(steps / goal, 0, 1);
    const [lvl, word] = stepsLevel(pct);
    const C = 2 * Math.PI * 42;
    return `<button class="card stat" data-act="steps">
      <div class="eyebrow">${icon('steps', 'sm')}Steps</div>
      <div class="ring">
        <svg viewBox="0 0 100 100" aria-hidden="true">
          <circle cx="50" cy="50" r="42" fill="none" stroke="var(--card-2)" stroke-width="9"/>
          <circle cx="50" cy="50" r="42" fill="none" stroke="${COLOR[lvl]}" stroke-width="9" stroke-linecap="round"
            stroke-dasharray="${C * pct} ${C}"/>
        </svg>
        <div class="center"><b>${num(steps)}</b><span>/ ${num(goal)}</span></div>
      </div>
      <div class="foot">${statusChip(lvl, word)}${st.stepsReal ? '' : '<span class="chip demo">manual</span>'}</div>
    </button>`;
  }

  function heartCard(st) {
    const v = st.vitals[st.vitals.length - 1];
    const { hrLow, hrHigh } = st.profile;
    const lo = 40, hi = 140;
    const pos = (x) => clamp(((x - lo) / (hi - lo)) * 100, 0, 100);
    const a = pos(hrLow), b = pos(hrHigh);
    const arc = 'M12 62 A48 48 0 0 1 108 62';
    let dot = '', val, chip;
    if (v) {
      const t = pos(v.hr) / 100;
      const ang = Math.PI * (1 - t);
      const x = 60 + 48 * Math.cos(ang), y = 62 - 48 * Math.sin(ang);
      dot = `<circle cx="${x}" cy="${y}" r="7" fill="var(--ink)" stroke="var(--card)" stroke-width="3"/>`;
      const out = v.hr > hrHigh ? 'Above range' : v.hr < hrLow ? 'Below range' : null;
      chip = out ? statusChip('bad', out) : statusChip('good', 'In range');
      val = `<div class="big">${v.hr}<small>bpm</small></div>`;
    } else {
      val = `<div class="big muted">—</div>`;
      chip = `<span class="chip">${icon('camera')}Scan to measure</span>`;
    }
    return `<button class="card stat" data-act="heart">
      <div class="eyebrow">${icon('heart', 'sm')}Heart rate</div>
      <div>
        <svg class="gauge" viewBox="0 0 120 72" aria-hidden="true">
          <path d="${arc}" pathLength="100" fill="none" stroke="var(--bad)" stroke-width="9" stroke-dasharray="${a} 100" />
          <path d="${arc}" pathLength="100" fill="none" stroke="var(--good)" stroke-width="9" stroke-dasharray="0 ${a} ${b - a} 100" />
          <path d="${arc}" pathLength="100" fill="none" stroke="var(--bad)" stroke-width="9" stroke-dasharray="0 ${b} ${100 - b} 100" />
          ${dot}
        </svg>
        <div class="hr-val">${val}</div>
      </div>
      <div class="foot">${chip}${v ? `<span class="tiny muted">${ago(v.t)}</span>` : ''}</div>
    </button>`;
  }

  function stressCard(st) {
    const v = st.vitals[st.vitals.length - 1];
    if (!v) {
      return `<button class="card stat" data-act="heart">
        <div class="eyebrow">${icon('wave', 'sm')}Stress</div>
        <div class="big muted">—</div>
        <div class="foot"><span class="tiny muted">Comes from your camera scan</span></div></button>`;
    }
    const [lvl, word] = stressLevel(v.stress);
    return `<button class="card stat" data-act="heart">
      <div class="eyebrow">${icon('wave', 'sm')}Stress</div>
      <div><div class="big">${v.stress}<small>/ 100</small></div>
        <div class="tiny muted" style="margin-top:4px">Lower is calmer</div></div>
      <div class="meter ${lvl}"><i style="width:${v.stress}%"></i></div>
      <div class="foot">${statusChip(lvl, word)}</div>
    </button>`;
  }

  function nutritionCard(st) {
    const todays = st.foods.filter((f) => todayKey(new Date(f.t)) === todayKey());
    const score = SC.services.food.dayScore(todays);
    if (score == null) {
      return `<button class="card stat" data-act="food">
        <div class="eyebrow">${icon('leaf', 'sm')}Nutrition</div>
        <div class="big muted">—</div>
        <div class="foot"><span class="tiny muted">Log a meal to get a score</span></div></button>`;
    }
    const [lvl, word] = nutriLevel(score);
    return `<button class="card stat" data-act="food">
      <div class="eyebrow">${icon('leaf', 'sm')}Nutrition</div>
      <div><div class="big">${score}<small>/ 100</small></div>
        <div class="tiny muted" style="margin-top:4px">${todays.length} item${todays.length === 1 ? '' : 's'} today</div></div>
      <div class="meter ${lvl}"><i style="width:${score}%"></i></div>
      <div class="foot">${statusChip(lvl, word)}</div>
    </button>`;
  }

  function weekCard(st) {
    const data = [];
    for (let i = 6; i >= 0; i--) {
      const k = SC.store.daysAgo(i);
      const d = new Date(k + 'T12:00');
      const v = st.steps[k] || 0;
      data.push({ label: d.toLocaleDateString(undefined, { weekday: 'long' }), short: d.toLocaleDateString(undefined, { weekday: 'narrow' }), value: v, display: `${num(v)} steps` });
    }
    const avg = data.reduce((a, d) => a + d.value, 0) / 7;
    return `<div class="card">
      <div class="row" style="justify-content:space-between;margin-bottom:6px">
        <h3 style="font-size:16px">Steps this week</h3><span class="tiny muted mono">avg ${num(avg)}</span></div>
      ${barChart({ data, ref: st.profile.stepGoal, refLabel: `goal ${num(st.profile.stepGoal)}` })}
    </div>`;
  }

  function lastScanCard(st) {
    const v = st.vitals[st.vitals.length - 1];
    const body = v
      ? `<div class="row" style="gap:22px;flex-wrap:wrap">
          <div><div class="eyebrow">Heart</div><div class="big" style="font-size:26px">${v.hr}<small>bpm</small></div></div>
          <div><div class="eyebrow">Breathing</div><div class="big" style="font-size:26px">${v.br}<small>/min</small></div></div>
          <div><div class="eyebrow">Stress</div><div class="big" style="font-size:26px">${v.stress}</div></div>
        </div><p class="tiny muted" style="margin:10px 0 0">Measured ${ago(v.t)} · ${st.vitals.length} scan${st.vitals.length === 1 ? '' : 's'} saved</p>`
      : `<p class="muted" style="margin:6px 0 0">No scans yet. A 10-second face scan gives you heart rate, breathing and stress.</p>`;
    return `<div class="card" style="display:flex;flex-direction:column;justify-content:space-between;gap:14px">
      <div><h3 style="font-size:16px;margin-bottom:10px">Last camera scan</h3>${body}</div>
      <div><a class="btn sm" href="#camera">${icon('camera', 'sm')}${v ? 'Scan again' : 'Start a scan'}</a></div>
    </div>`;
  }

  function stepsSheet() {
    const st = SC.store.state;
    const sh = sheet({
      title: 'Steps',
      body: `<div class="stack">
        <label class="field">Daily goal<input class="input" type="number" min="1000" step="500" name="goal" value="${st.profile.stepGoal}"></label>
        <label class="field">Today's steps (manual for now)<input class="input" type="number" min="0" name="today" value="${st.steps[todayKey()] || 0}"></label>
        <div class="notice">${icon('info')}<span>${SC.native.on ? "Steps come from your phone's step sensor, counted from the first time SmartCam checks each day." : "Browsers can't read step counts. The Android app counts them automatically."}</span></div>
        <button class="btn block" data-save>Save</button></div>`,
    });
    sh.el.querySelector('[data-save]').onclick = () => {
      const goal = Math.max(1000, Number(sh.el.querySelector('[name=goal]').value) || 8000);
      const today = Math.max(0, Number(sh.el.querySelector('[name=today]').value) || 0);
      SC.store.update((s) => { s.profile.stepGoal = goal; s.steps[todayKey()] = today; });
      sh.close();
      toast('Steps updated');
    };
  }

  SC.views = SC.views || {};
  SC.views.home = {
    title: 'Today',
    render(el) {
      this.el = el;
      this.draw();
      this.off = SC.store.on(() => this.draw());
      SC.native.refreshSteps();
      el.onclick = (e) => {
        const act = e.target.closest('[data-act]')?.dataset.act;
        if (act === 'steps') stepsSheet();
        if (act === 'heart') location.hash = 'camera';
        if (act === 'food') location.hash = 'food';
      };
    },
    destroy() { this.off && this.off(); },
    draw() {
      const st = SC.store.state;
      const hour = new Date().getHours();
      const hello = hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening';
      this.el.innerHTML = `
        <p class="muted" style="margin:2px 0 14px">${esc(hello)} — ${new Date().toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' })}</p>
        <div class="home-grid">${stepsCard(st)}${heartCard(st)}${stressCard(st)}${nutritionCard(st)}</div>
        <div class="home-wide">${weekCard(st)}${lastScanCard(st)}</div>`;
    },
  };
})();

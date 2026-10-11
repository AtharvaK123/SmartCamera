/* Phone access: screen time, alarms, sleep lock, per-app limits.
   Screen time / app locking need the native build — the browser shows demo numbers. */
(function () {
  const { icon, esc, sheet, toast, fmtMin, time12, barChart, statusChip } = SC.ui;
  const DEMO_NOTE = `<div class="notice">${icon('info')}<span>Demo numbers. Open SmartCam on Android and allow Usage access to see your real screen time.</span></div>`;

  // Real screen time from the Android app when available, otherwise demo numbers.
  function D() {
    const n = SC.native.screenTime;
    if (n) return { real: true, appsToday: n.today.slice(0, 12), appsWeek: n.week.slice(0, 12), screenWeek: n.daily, sleepWeek: SC.demo.sleepWeek };
    return { real: false, ...SC.demo };
  }
  const note = () => (D().real ? '' : DEMO_NOTE);

  function weekData(values, fmt) {
    return values.map((v, i) => {
      const d = new Date(SC.store.daysAgo(6 - i) + 'T12:00');
      return { label: d.toLocaleDateString(undefined, { weekday: 'long' }), short: d.toLocaleDateString(undefined, { weekday: 'narrow' }), value: v, display: fmt(v) };
    });
  }

  function hbars(rows) {
    const max = Math.max(...rows.map((r) => r.min));
    return `<div class="hbars">${rows.map((r) => `
      <div class="hbar"><span>${esc(r.app)}</span><div class="track"><div class="fill" style="width:${(r.min / max) * 100}%"></div></div><span class="v">${fmtMin(r.min)}</span></div>`).join('')}</div>`;
  }

  function nextAlarm(alarms) {
    const now = new Date();
    const nowMin = now.getHours() * 60 + now.getMinutes();
    let best = null;
    for (const a of alarms.filter((x) => x.on)) {
      const [h, m] = a.time.split(':').map(Number);
      let diff = h * 60 + m - nowMin;
      if (diff <= 0) diff += 1440;
      if (!best || diff < best.diff) best = { ...a, diff };
    }
    return best;
  }

  function lockedApps(st) {
    return D().appsToday.filter((a) => st.appLimits[a.app] && a.min >= st.appLimits[a.app]);
  }

  /* ---- detail sheets ---- */
  function screenTimeSheet() {
    const total = D().appsToday.reduce((a, r) => a + r.min, 0);
    const wk = D().screenWeek;
    sheet({
      title: 'Screen time',
      size: 'wide',
      body: `<div class="stack">
        <div class="row" style="gap:28px;flex-wrap:wrap">
          <div><div class="eyebrow">Today</div><div class="big">${fmtMin(total)}</div></div>
          <div><div class="eyebrow">Daily average</div><div class="big">${fmtMin(wk.reduce((a, b) => a + b, 0) / 7)}</div></div></div>
        <h3 class="section-title" style="margin:8px 0 0">Today by app</h3>
        ${hbars(D().appsToday)}
        <h3 class="section-title" style="margin:8px 0 0">This week</h3>
        ${barChart({ data: weekData(wk, fmtMin), ref: 240, refLabel: '4h target' })}
        ${note()}</div>`,
    });
  }

  function alarmsSheet() {
    const sh = sheet({ title: 'Alarms', body: '' });
    function draw() {
      const alarms = [...SC.store.state.alarms].sort((a, b) => a.time.localeCompare(b.time));
      sh.setBody(`<div class="stack">
        <div class="list">${alarms.length ? alarms.map((a) => `
          <div class="list-row">
            <div class="grow"><div class="big" style="font-size:26px">${time12(a.time)}</div><span class="tiny muted">${esc(a.label || 'Alarm')}</span></div>
            <label class="switch" aria-label="Turn ${esc(a.label)} on or off"><input type="checkbox" data-toggle="${a.id}" ${a.on ? 'checked' : ''}><span></span></label>
            <button class="icon-btn" data-del="${a.id}" aria-label="Delete alarm">${icon('trash', 'sm')}</button>
          </div>`).join('') : '<p class="muted">No alarms yet.</p>'}</div>
        <form class="card stack" id="newAlarm" style="box-shadow:none">
          <b>New alarm</b>
          <div class="row"><input class="input" type="time" name="time" required style="max-width:150px">
            <input class="input grow" name="label" placeholder="Label (optional)" maxlength="40"></div>
          <button class="btn" type="submit">${icon('plus', 'sm')}Add alarm</button>
        </form>
        <div class="notice">${icon('info')}<span>${SC.native.on ? "New alarms are also added to your Clock app, so they ring even when SmartCam is closed. Turning one off here doesn't remove it from Clock." : 'In the browser, alarms ring only while SmartCam is open.'}</span></div>
        ${SC.native.on ? `<button class="btn ghost block" data-clock>${icon('alarm', 'sm')}Open Clock app</button>` : ''}</div>`);
      const clock = sh.el.querySelector('[data-clock]');
      if (clock) clock.onclick = () => SC.native.call('showAlarms').catch((e) => toast(e.message, 'bad'));
      sh.el.querySelectorAll('[data-toggle]').forEach((cb) => {
        cb.onchange = () => SC.store.update((s) => { const a = s.alarms.find((x) => x.id === cb.dataset.toggle); if (a) a.on = cb.checked; });
      });
      sh.el.querySelectorAll('[data-del]').forEach((b) => {
        b.onclick = () => { SC.store.update((s) => { s.alarms = s.alarms.filter((x) => x.id !== b.dataset.del); }); draw(); };
      });
      sh.el.querySelector('#newAlarm').onsubmit = (e) => {
        e.preventDefault();
        const fd = new FormData(e.target);
        const label = String(fd.get('label')).trim() || 'Alarm';
        SC.store.update((s) => { s.alarms.push({ id: 'a' + Date.now().toString(36), time: fd.get('time'), label, on: true }); });
        if (SC.native.on) {
          const [hour, minute] = String(fd.get('time')).split(':').map(Number);
          SC.native.call('setAlarm', { hour, minute, label }).catch((err) => toast(err.message, 'bad'));
        } else if ('Notification' in window && Notification.permission === 'default') Notification.requestPermission();
        toast(`Alarm set for ${time12(fd.get('time'))}`);
        draw();
      };
    }
    draw();
  }

  function sleepSheet() {
    const st = SC.store.state;
    const sh = sheet({
      title: 'Sleep time',
      body: `<div class="stack">
        <p class="muted" style="margin:0">Between these times SmartCam locks distracting apps so you can wind down.</p>
        <div class="row">
          <label class="field grow">Lock at<input class="input" type="time" name="lock" value="${st.sleep.lock}"></label>
          <label class="field grow">Unlock at<input class="input" type="time" name="unlock" value="${st.sleep.unlock}"></label></div>
        <button class="btn" data-save>${icon('check', 'sm')}Save times</button>
        <h3 class="section-title" style="margin:8px 0 0">Sleep, last 7 nights</h3>
        ${barChart({ data: weekData(D().sleepWeek, (h) => `${h.toFixed(1)} h`), ref: 8, refLabel: '8 h' })}
        <div class="notice">${icon('info')}<span>Sleep history is sample data for now. It will come from Health Connect.</span></div></div>`,
    });
    sh.el.querySelector('[data-save]').onclick = () => {
      const lock = sh.el.querySelector('[name=lock]').value, unlock = sh.el.querySelector('[name=unlock]').value;
      SC.store.update((s) => { s.sleep = { lock, unlock }; });
      toast(`Phone locks at ${time12(lock)}, unlocks at ${time12(unlock)}`);
    };
  }

  function limitsSheet() {
    let range = 'day';
    const sh = sheet({ title: 'Manage screen time', size: 'wide', body: '' });
    function draw() {
      const st = SC.store.state;
      const rows = range === 'day' ? D().appsToday : D().appsWeek;
      sh.setBody(`<div class="stack">
        <div class="seg" role="group" aria-label="Range">
          <button data-range="day" aria-pressed="${range === 'day'}">24 hours</button>
          <button data-range="week" aria-pressed="${range === 'week'}">7 days</button></div>
        <div class="list">${rows.filter((r) => r.app !== 'Other').map((r) => {
          const lim = st.appLimits[r.app];
          const usedToday = D().appsToday.find((x) => x.app === r.app)?.min || 0;
          const chip = !lim ? '' : usedToday >= lim ? statusChip('bad', 'Locked') : usedToday >= lim * 0.8 ? statusChip('warn', 'Near limit') : statusChip('good', 'Under limit');
          return `<div class="list-row">
            <div class="grow"><b>${esc(r.app)}</b><div class="tiny muted mono">${fmtMin(r.min)} ${range === 'day' ? 'today' : 'this week'}</div></div>
            ${chip}
            <select class="input" style="width:118px;height:38px" data-limit="${esc(r.app)}" aria-label="Daily limit for ${esc(r.app)}">
              <option value="">No limit</option>
              ${[15, 30, 45, 60, 90, 120].map((m) => `<option value="${m}" ${lim === m ? 'selected' : ''}>${fmtMin(m)}/day</option>`).join('')}
            </select></div>`;
        }).join('')}</div>
        <div class="notice">${icon('info')}<span>Limits are tracked here. Automatically locking other apps needs an extra Android permission, planned for a later version.</span></div>
        ${note()}</div>`);
      sh.el.querySelectorAll('[data-range]').forEach((b) => { b.onclick = () => { range = b.dataset.range; draw(); }; });
      sh.el.querySelectorAll('[data-limit]').forEach((s) => {
        s.onchange = () => {
          SC.store.update((st2) => { if (s.value) st2.appLimits[s.dataset.limit] = Number(s.value); else delete st2.appLimits[s.dataset.limit]; });
          draw();
        };
      });
    }
    draw();
  }

  SC.views = SC.views || {};
  SC.views.phone = {
    title: 'Phone',
    render(el) {
      this.el = el;
      this.draw();
      this.off = SC.store.on(() => this.draw());
      this.onNative = () => this.el && this.draw();
      window.addEventListener('sc:native', this.onNative);
      if (SC.native.on) SC.native.refreshScreenTime().then(this.onNative);
      el.onclick = (e) => {
        if (e.target.closest('[data-usage]')) return SC.native.call('openUsageAccessSettings');
        const a = e.target.closest('[data-open]')?.dataset.open;
        ({ screen: screenTimeSheet, alarms: alarmsSheet, sleep: sleepSheet, limits: limitsSheet })[a]?.();
      };
    },
    destroy() { this.off && this.off(); window.removeEventListener('sc:native', this.onNative); this.el = null; },
    draw() {
      const st = SC.store.state;
      const avg = D().screenWeek.reduce((a, b) => a + b, 0) / 7;
      const next = nextAlarm(st.alarms);
      const locked = lockedApps(st);
      this.el.innerHTML = `
        <div class="grid g2 phone-grid">
          <button class="card phone-card" data-open="screen">${icon('screen', 'lg')}
            <div><div class="big">${fmtMin(avg)}</div><div class="eyebrow" style="margin-top:6px">Avg screen time</div></div></button>
          <button class="card phone-card" data-open="alarms">${icon('alarm', 'lg')}
            <div><div class="big">${next ? fmtMin(next.diff) : 'Off'}</div>
            <div class="eyebrow" style="margin-top:6px">${next ? `Next alarm · ${time12(next.time)}` : 'No alarms on'}</div></div></button>
          <button class="card phone-card" data-open="sleep">${icon('moon', 'lg')}
            <div><div class="big">${time12(st.sleep.lock).replace(' ', '<small>')}</small></div>
            <div class="eyebrow" style="margin-top:6px">Sleep lock</div></div></button>
          <button class="card phone-card" data-open="limits">${icon('lock', 'lg')}
            <div><div class="big">${locked.length}<small>locked</small></div>
            <div class="eyebrow" style="margin-top:6px">Manage screen time</div></div></button>
        </div>
        ${SC.native.on && SC.native.usageGranted === false ? `<div class="notice warn" style="margin-top:16px">${icon('lock')}<span class="grow">To show your real screen time, allow SmartCam under <b>Usage access</b>.</span><button class="btn sm" data-usage>Allow</button></div>` : ''}
        <h3 class="section-title">Today's top apps ${D().real ? '' : '<span class="chip demo">demo</span>'}</h3>
        <div class="card">${hbars(D().appsToday.slice(0, 5))}</div>`;
    },
  };

  /* In-page alarm ringer for the browser version (the Android app uses real Clock alarms). */
  let lastFired = '';
  if (!SC.native.on) setInterval(() => {
    const now = new Date();
    const hhmm = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
    if (hhmm === lastFired) return;
    const a = SC.store.state.alarms.find((x) => x.on && x.time === hhmm);
    if (!a) return;
    lastFired = hhmm;
    toast(`Alarm: ${a.label} — ${time12(a.time)}`);
    if ('Notification' in window && Notification.permission === 'granted') new Notification('SmartCam alarm', { body: a.label });
    SC.services.tts.speak(`${a.label}. It's ${time12(a.time)}.`);
  }, 10000);
})();

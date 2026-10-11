/* Data store: saved on the device (localStorage) and, when the server has Tiger Data set up,
   synced to it a couple of seconds after each change. */
window.SC = window.SC || {};

(function () {
  const KEY = 'smartcam.v1';
  const { todayKey } = SC.ui;

  function daysAgo(n) {
    const d = new Date();
    d.setDate(d.getDate() - n);
    return todayKey(d);
  }

  function seed() {
    const steps = {};
    const demoSteps = [8420, 6110, 10230, 4380, 7950, 9120];
    demoSteps.forEach((v, i) => { steps[daysAgo(6 - i)] = v; });
    steps[todayKey()] = 5240;
    return {
      profile: { stepGoal: 8000, hrLow: 50, hrHigh: 100 },
      steps,
      vitals: [], // { t, hr, br, stress, source }
      foods: [], // see food.js
      alarms: [
        { id: 'a1', time: '07:00', label: 'Wake up', on: true },
        { id: 'a2', time: '13:30', label: 'Stretch break', on: false },
      ],
      sleep: { lock: '22:30', unlock: '06:45' },
      appLimits: { Instagram: 60, TikTok: 30, YouTube: 90 },
      orders: [],
      chat: [],
      shop: { query: '', results: [] },
      camera: { asked: false },
      deviceId: (crypto.randomUUID && crypto.randomUUID()) || 'd' + Date.now().toString(36) + Math.random().toString(36).slice(2),
      updatedAt: 0,
      settings: {
        speak: false,
        musicService: 'spotify',
        connections: { spotify: false, ytmusic: false },
        backendUrl: '',
        wallet: '',
        simulateHighHr: false,
        theme: 'auto',
      },
    };
  }

  function merge(base, saved) {
    if (!saved || typeof saved !== 'object' || Array.isArray(saved)) return saved ?? base;
    const out = { ...base };
    for (const k of Object.keys(saved)) {
      const b = base[k];
      out[k] = b && typeof b === 'object' && !Array.isArray(b) ? merge(b, saved[k]) : saved[k];
    }
    return out;
  }

  function load() {
    try {
      const raw = localStorage.getItem(KEY);
      return raw ? merge(seed(), JSON.parse(raw)) : seed();
    } catch {
      return seed();
    }
  }

  let state = load();
  const listeners = new Set();

  let syncTimer = null;
  function save() {
    state.updatedAt = Date.now();
    try {
      localStorage.setItem(KEY, JSON.stringify(state));
    } catch {
      SC.ui.toast('Storage is full — remove a few food photos.', 'bad');
    }
    clearTimeout(syncTimer);
    syncTimer = setTimeout(push, 2000);
  }

  // Tiger Data sync (via the server). Chat history stays on the device.
  async function push() {
    if (!SC.api?.status?.db) return;
    const { chat, ...rest } = state;
    try {
      await fetch(SC.api.base() + '/api/state', {
        method: 'PUT', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ device: state.deviceId, state: rest }),
      });
    } catch {}
  }
  // Foods live in a shared table, so barcode scans from the phone app show up here.
  async function pullFoods() {
    try {
      if (!SC.auth?.signedIn) return;
      const remote = await (await fetch(SC.api.base() + '/api/foods', { headers: SC.auth.headers() })).json();
      if (!Array.isArray(remote)) return;
      const local = new Map(state.foods.map((f) => [f.id, f]));
      remote.forEach((f) => local.set(f.id, { ...local.get(f.id), ...f }));
      state.foods = [...local.values()];
      localStorage.setItem(KEY, JSON.stringify(state));
      listeners.forEach((l) => l(state));
    } catch {}
  }

  async function pull() {
    if (!SC.api?.status?.db) return;
    pullFoods();
    try {
      const r = await (await fetch(`${SC.api.base()}/api/state?device=${encodeURIComponent(state.deviceId)}`)).json();
      if (r.state && (r.state.updatedAt || 0) > (state.updatedAt || 0)) {
        state = merge(seed(), { ...r.state, chat: state.chat, deviceId: state.deviceId });
        localStorage.setItem(KEY, JSON.stringify(state));
        listeners.forEach((l) => l(state));
      } else {
        push();
      }
    } catch {}
  }

  SC.store = {
    get state() { return state; },
    update(fn) {
      fn(state);
      save();
      listeners.forEach((l) => l(state));
    },
    on(fn) { listeners.add(fn); return () => listeners.delete(fn); },
    reset() { const id = state.deviceId; state = seed(); state.deviceId = id; save(); listeners.forEach((l) => l(state)); },
    pull,
    pullFoods,
    daysAgo,
  };

  /* Demo data for things a browser cannot read (screen time, sleep).
     A native Android/iOS build supplies the real numbers — see README. */
  SC.demo = {
    appsToday: [
      { app: 'Instagram', min: 74 },
      { app: 'YouTube', min: 58 },
      { app: 'TikTok', min: 41 },
      { app: 'Messages', min: 33 },
      { app: 'Chrome', min: 29 },
      { app: 'Maps', min: 12 },
      { app: 'Other', min: 23 },
    ],
    appsWeek: [
      { app: 'Instagram', min: 512 },
      { app: 'YouTube', min: 455 },
      { app: 'TikTok', min: 301 },
      { app: 'Messages', min: 240 },
      { app: 'Chrome', min: 198 },
      { app: 'Maps', min: 70 },
      { app: 'Other', min: 200 },
    ],
    screenWeek: [312, 280, 355, 298, 260, 401, 270],
    sleepWeek: [7.2, 6.4, 7.8, 5.9, 6.8, 8.1, 7.0],
  };
})();

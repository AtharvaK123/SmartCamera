/* Bridge to the Android app (android/.../SmartCamNativePlugin.java).
   In a normal browser SC.native.on is false and the app falls back to web features + demo data. */
window.SC = window.SC || {};

(function () {
  const cap = window.Capacitor;
  const on = !!cap?.isNativePlatform?.();
  const plugin = on ? (cap.registerPlugin ? cap.registerPlugin('SmartCamNative') : cap.Plugins?.SmartCamNative) : null;

  const call = async (method, args = {}) => {
    if (!plugin) throw new Error('Not running in the Android app');
    return plugin[method](args);
  };

  SC.native = {
    on,
    call,
    screenTime: null, // last real reading: { today:[{app,min}], week:[...], daily:[7 numbers] }

    async refreshScreenTime() {
      if (!on) return null;
      try {
        const r = await call('getScreenTime');
        SC.native.usageGranted = r.granted;
        SC.native.screenTime = r.granted ? r : null;
      } catch { SC.native.screenTime = null; }
      return SC.native.screenTime;
    },

    async refreshSteps() {
      if (!on) return;
      try {
        const { steps } = await call('getSteps');
        const k = SC.ui.todayKey();
        if (SC.store.state.steps[k] !== steps) SC.store.update((s) => { s.steps[k] = steps; s.stepsReal = true; });
      } catch {}
    },
  };

  if (!on) return;

  // Links that should leave the app: Spotify, YouTube Music, solana: wallet requests.
  document.addEventListener('click', (e) => {
    const a = e.target.closest('a[href]');
    if (!a) return;
    const href = a.getAttribute('href');
    if (a.target === '_blank' || /^solana:/i.test(href)) {
      e.preventDefault();
      call('openExternal', { url: a.href || href }).catch((err) => SC.ui.toast(err.message, 'bad'));
    }
  }, true);

  // Refresh phone data whenever the app comes back to the foreground.
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') {
      SC.native.refreshSteps();
      SC.native.refreshScreenTime().then(() => window.dispatchEvent(new Event('sc:native')));
    }
  });
})();

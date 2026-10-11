/* Settings (the ⋮ menu): account, permissions, voice, wallet, appearance. Connections run in the background. */
(function () {
  const { icon, esc, sheet, toast } = SC.ui;

  async function permState(name) {
    if (name === 'notifications' && 'Notification' in window) {
      return Notification.permission === 'default' ? 'prompt' : Notification.permission === 'denied' ? 'denied' : 'granted';
    }
    try { return (await navigator.permissions.query({ name })).state; } catch { return 'unknown'; }
  }

  async function requestPerm(name) {
    try {
      if (name === 'camera' || name === 'microphone') {
        const s = await navigator.mediaDevices.getUserMedia(name === 'camera' ? { video: true } : { audio: true });
        s.getTracks().forEach((t) => t.stop());
        if (name === 'camera') SC.store.update((st) => { st.camera.asked = true; });
      } else if (name === 'notifications') {
        await Notification.requestPermission();
      }
    } catch {
      toast('Blocked — change it in your browser\'s site settings.', 'bad');
    }
  }

  const statusDot = (on, onText = 'Connected', offText = 'Not set') => `<span class="status-dot ${on ? 'on' : ''}">${on ? onText : offText}</span>`;

  function open() {
    const sh = sheet({ title: 'Settings', size: 'wide', body: '' });

    async function draw() {
      const s = SC.store.state.settings;
      const perms = {};
      for (const p of ['camera', 'microphone', 'notifications']) perms[p] = await permState(p);
      const scroll = sh.el.scrollTop;
      const permRow = (key, label, why) => `
        <div class="set-row"><div class="grow"><b>${label}</b><span>${why}</span></div>
          ${perms[key] === 'granted' ? statusDot(true, 'Allowed') : `<button class="btn sm ghost" data-perm="${key}">${perms[key] === 'denied' ? 'Blocked' : 'Allow'}</button>`}</div>`;

      const who = SC.auth.user;
      sh.setBody(`
        <section class="set-section"><h3>Account</h3>
          <div class="set-row"><div class="grow"><b>${who?.username ? '@' + esc(who.username) : 'Guest'}</b><span>${who?.role === 'admin' ? 'Admin' : who?.username ? 'Your food log syncs with the phone app' : 'Nothing is synced'}</span></div>
            <button class="btn sm ghost" data-signout>${who?.username ? 'Sign out' : 'Sign in'}</button></div>
        </section>

        <section class="set-section"><h3>Phone permissions</h3>
          ${permRow('microphone', 'Microphone', 'Talk to Gemini')}
          ${permRow('camera', 'Camera', 'Vitals scan and barcode reading')}
          ${permRow('notifications', 'Notifications', 'Alarms and heart-rate warnings')}
        </section>

        <section class="set-section"><h3>Voice</h3>
          <div class="set-row"><div class="grow"><b>Read Gemini replies out loud</b><span>In the voice you picked when you set up SmartCam</span></div>
            <label class="switch"><input type="checkbox" data-speak ${s.speak ? 'checked' : ''}><span></span></label></div>
          <div class="set-row"><div class="grow"><b>Hear your voice</b></div>
            <button class="btn sm ghost" data-test="voice">Play</button></div>
        </section>

        <section class="set-section"><h3>Solana wallet</h3>
          <div class="set-row"><div class="grow"><b>Wallet</b><span>${s.wallet ? esc(s.wallet.slice(0, 4) + '…' + s.wallet.slice(-4)) : 'Phantom or any Solana Pay wallet'}</span></div>
            ${s.wallet ? statusDot(true) : `<button class="btn sm ghost" data-wallet>Connect</button>`}</div>
        </section>

        <section class="set-section"><h3>Privacy</h3>
          <div class="set-row"><div class="grow"><b>Privacy Policy</b><span>What SmartCam collects, who sees it and your choices</span></div>
            <a class="btn sm ghost" href="privacy.html" target="_blank" rel="noopener">Read</a></div>
        </section>

        <section class="set-section"><h3>Appearance</h3>
          <div class="set-row"><div class="grow"><b>Theme</b></div>
            <div class="seg" role="group" aria-label="Theme">${['auto', 'light', 'dark'].map((t) => `<button data-theme-set="${t}" aria-pressed="${s.theme === t}">${t[0].toUpperCase() + t.slice(1)}</button>`).join('')}</div></div>
        </section>`);
      sh.el.scrollTop = scroll;
      bind();
    }

    function bind() {
      const el = sh.el;
      el.querySelectorAll('[data-perm]').forEach((b) => { b.onclick = async () => { await requestPerm(b.dataset.perm); draw(); }; });
      el.querySelector('[data-signout]').onclick = () => SC.auth.signOut();
      el.querySelector('[data-speak]').onchange = (e) => SC.store.update((s) => { s.settings.speak = e.target.checked; });
      el.querySelectorAll('[data-theme-set]').forEach((b) => { b.onclick = () => { SC.store.update((s) => { s.settings.theme = b.dataset.themeSet; }); SC.applyTheme(); draw(); }; });
      const w = el.querySelector('[data-wallet]');
      if (w) w.onclick = async () => {
        try {
          const addr = await SC.services.solana.connect();
          SC.store.update((s) => { s.settings.wallet = addr; });
          draw();
        } catch (e) {
          if (e.message === 'NO_WALLET') { toast('No Phantom wallet found — opening phantom.com'); window.open('https://phantom.com', '_blank', 'noopener'); }
          else toast('Wallet connection cancelled');
        }
      };
      el.querySelector('[data-test]').onclick = () => SC.services.tts.speak("Hi, I'm your SmartCam assistant.");
    }
    draw();
  }

  SC.openSettings = open;
})();

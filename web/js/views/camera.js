/* Camera: A = live camera while Presage measures, then a game picked for your stress level.
   B = the camera moves into a small window once results arrive.
   C = heart-rate warning.  D = vitals + mood music. */
(function () {
  const { icon, esc, toast } = SC.ui;

  const tierOf = (stress) => (stress >= 67 ? 'high' : stress >= 34 ? 'moderate' : 'low');
  const PICK = { high: 'meditate', moderate: 'jigsaw', low: 'tetris' };
  const GAMES = ['tetris', 'jigsaw', 'meditate'];

  SC.views = SC.views || {};
  SC.views.camera = {
    title: 'Camera',
    render(el) {
      this.el = el;
      el.innerHTML = `
        <div class="cam">
          <div class="stage" id="stage">
            <div class="stage-a" id="stageA"></div>
            <video id="camVideo" class="cam-video in-a" playsinline muted autoplay hidden></video>
            <div id="stageOverlay"></div>
            <div class="cam-alert" id="camAlert" role="alert" hidden></div>
            <div class="stage-bar" id="stageBar" hidden></div>
          </div>
          <div class="cam-actions">
            <p class="tiny muted" style="margin:0;max-width:52ch">Readings are wellness estimates, not a medical diagnosis.
              ${SC.services.presage.isMock ? '<span class="chip demo">Presage demo mode</span>' : ''}</p>
            <button class="btn ghost sm" id="rescan" hidden>${icon('refresh', 'sm')}Scan again</button>
          </div>
        </div>`;
      this.video = el.querySelector('#camVideo');
      el.querySelector('#rescan').onclick = () => this.scan();
      this.begin();
    },

    async begin() {
      if (!navigator.mediaDevices?.getUserMedia) {
        return this.prompt('Camera not available', 'Open SmartCam over https:// or http://localhost to use the camera.', null);
      }
      let granted = false;
      try { granted = (await navigator.permissions.query({ name: 'camera' })).state === 'granted'; } catch {}
      if (granted || SC.store.state.camera.asked) return this.startCamera();
      this.prompt('Allow camera access', 'SmartCam reads tiny color changes in your face to estimate heart rate, breathing and stress. Video never leaves your device in this prototype.', 'Allow camera');
    },

    prompt(title, text, cta) {
      this.el.querySelector('#stageA').innerHTML = `
        <div class="cam-prompt">${icon('camera')}
          <h2>${esc(title)}</h2><p>${esc(text)}</p>
          ${cta ? `<button class="btn pop" id="allowCam">${esc(cta)}</button>` : ''}
        </div>`;
      const b = this.el.querySelector('#allowCam');
      if (b) b.onclick = () => this.startCamera();
    },

    async startCamera() {
      try {
        this.stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user', width: { ideal: 960 } }, audio: false });
      } catch (e) {
        SC.store.update((s) => { s.camera.asked = true; });
        return this.prompt('Camera blocked', 'Allow camera access for this site in your browser settings, then try again.', 'Try again');
      }
      if (!this.el) { this.stopStream(); return; }
      SC.store.update((s) => { s.camera.asked = true; });
      this.video.srcObject = this.stream;
      this.video.hidden = false;
      this.scan();
    },

    async scan() {
      this.abort?.abort();
      this.stopGame();
      this.abort = new AbortController();
      const $ = (s) => this.el?.querySelector(s);
      $('#stageA').innerHTML = '';
      $('#stage').classList.remove('playing');
      $('#camAlert').hidden = true;
      $('#stageBar').hidden = true;
      $('#rescan').hidden = true;
      this.video.className = 'cam-video in-a';
      $('#stageOverlay').innerHTML = `
        <div class="face-guide"></div>
        <div class="cam-label"><span class="dot"></span>Measuring</div>
        <div class="scan-meter"><div class="row" style="justify-content:space-between">
          <b>Hold still, face in the oval</b><span class="mono tiny" id="pct">0%</span></div>
          <div class="meter"><i id="bar" style="width:0%"></i></div></div>`;
      try {
        const v = await SC.services.presage.measure(this.video, {
          signal: this.abort.signal,
          onProgress: (p) => {
            const bar = $('#bar'), pct = $('#pct');
            if (bar) { bar.style.width = `${p * 100}%`; pct.textContent = `${Math.round(p * 100)}%`; }
          },
        });
        SC.store.update((s) => { s.vitals.push(v); s.vitals = s.vitals.slice(-200); });
        if (SC.api.status?.db) SC.api.post('/api/vitals', { device: SC.store.state.deviceId, ...v }).catch(() => {});
        if (this.el) this.showResult(v);
      } catch (e) {
        if (e.name !== 'AbortError') toast('Measurement failed — try again in better light.', 'bad');
      }
    },

    async showResult(v) {
      const $ = (s) => this.el?.querySelector(s);
      const { hrLow, hrHigh } = SC.store.state.profile;
      const tier = tierOf(v.stress);
      const rec = PICK[tier];
      $('#stage').classList.add('playing');
      $('#stageOverlay').innerHTML = `
        <div class="game-tabs" role="tablist" aria-label="Games">${GAMES.map((g) => `
          <button role="tab" data-game="${g}">${SC.games[g].name}${g === rec ? '<i title="Suggested for your stress level"></i>' : ''}</button>`).join('')}</div>
        <div class="game-hud" id="gameHud"></div>`;
      $('#stageOverlay').onclick = (e) => { const g = e.target.closest('[data-game]')?.dataset.game; if (g) this.playGame(g, tier); };
      this.video.className = 'cam-video in-b';
      this.playGame(rec, tier);
      $('#rescan').hidden = false;

      const out = v.hr > hrHigh ? `Heart rate looks high (${v.hr} bpm). Sit and breathe slowly.`
        : v.hr < hrLow ? `Heart rate looks low (${v.hr} bpm).` : null;
      if (out) {
        const a = $('#camAlert');
        a.innerHTML = `${icon('alert')}<span>${esc(out)}</span><button aria-label="Dismiss">${icon('close', 'sm')}</button>`;
        a.hidden = false;
        a.querySelector('button').onclick = () => { a.hidden = true; };
      }

      const bar = $('#stageBar');
      bar.innerHTML = `<span class="vital"><b>${v.hr}</b> bpm</span><span class="vital"><b>${v.br}</b> br/min</span>
        <span class="music-chip">${icon('music')}<span>Picking music…</span></span>`;
      bar.hidden = false;
      const m = await SC.services.music.suggest(v);
      const chip = bar.querySelector('.music-chip');
      if (chip) {
        chip.outerHTML = `<a class="music-chip" href="${esc(m.url)}" target="_blank" rel="noopener">${icon('music')}<span>${esc(m.title)}</span>${icon('external', 'sm')}</a>`;
      }
    },

    playGame(id, tier) {
      const $ = (s) => this.el?.querySelector(s);
      this.stopGame();
      $('#stageOverlay').querySelectorAll('[data-game]').forEach((b) => b.setAttribute('aria-selected', b.dataset.game === id));
      const host = document.createElement('div');
      host.className = 'game-host';
      $('#stageA').innerHTML = '';
      $('#stageA').appendChild(host);
      this.gameStop = SC.games[id].start(host, {
        tier,
        onHud: (t) => { const h = $('#gameHud'); if (h) h.textContent = t; },
        onDone: () => this.scan(),
      });
    },
    stopGame() { this.gameStop && this.gameStop(); this.gameStop = null; },
    stopStream() { this.stream?.getTracks().forEach((t) => t.stop()); this.stream = null; },
    destroy() {
      this.abort?.abort();
      this.stopGame();
      this.stopStream();
      this.el = null;
    },
  };
})();

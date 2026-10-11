/* Food: "+" adds by barcode or photo; every item becomes a tile. */
(function () {
  const { icon, esc, sheet, toast, todayKey, statusChip, num, resizeImage } = SC.ui;
  const F = () => SC.services.food;

  const TILE_COLORS = ['#F2C14E', '#F78154', '#9BC59D', '#B8D8E8', '#E7B7C8', '#D9C6A5'];
  function genTile(name) {
    let h = 0;
    for (const c of name) h = (h * 31 + c.charCodeAt(0)) >>> 0;
    const word = (name.split(/[\s—-]+/).find((w) => w.length > 2) || name || '?').slice(0, 7);
    return `<div class="gen" style="background:${TILE_COLORS[h % TILE_COLORS.length]};width:100%;height:100%;display:grid;place-items:center">${esc(word)}</div>`;
  }
  const level = (s) => (s >= 70 ? 'good' : s >= 45 ? 'warn' : 'bad');
  const word = (s) => (s >= 70 ? 'Good' : s >= 45 ? 'Fair' : 'Poor');

  function tile(f) {
    const face = f.image ? `<img src="${esc(f.image)}" alt="" loading="lazy">` : genTile(f.name);
    const s = F().score(f);
    return `<button class="tile" data-id="${esc(f.id)}">
      <div class="face">${face}<span class="badge">${s}</span></div>
      <span class="name">${esc(f.name)}</span>
      <span class="meta">${f.kcal != null ? `${num(f.kcal)} kcal` : '—'} · ${new Date(f.t).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}</span>
    </button>`;
  }

  const FIELDS = [
    ['kcal', 'Calories', 'kcal'], ['protein', 'Protein', 'g'], ['carbs', 'Carbs', 'g'], ['sugar', 'Sugar', 'g'],
    ['fat', 'Fat', 'g'], ['satFat', 'Sat. fat', 'g'], ['fiber', 'Fiber', 'g'], ['salt', 'Salt', 'g'],
  ];

  // Shared review/edit form used by barcode, photo and manual entry.
  function reviewForm(sh, item) {
    sh.setBody(`<form class="stack" id="foodForm">
      ${item.image ? `<img class="food-photo" src="${esc(item.image)}" alt="">` : ''}
      ${item.confidence === 'low' ? `<div class="notice warn">${icon('alert')}<span>Gemini isn't sure about this one — check the numbers.</span></div>` : ''}
      <label class="field">Name<input class="input" name="name" required value="${esc(item.name || '')}"></label>
      <div class="row tiny muted" style="justify-content:space-between"><span>Nutrition ${esc(item.basis || 'per serving')}</span>
        ${item.grade ? `<span class="chip">Nutri-Score ${esc(item.grade.toUpperCase())}</span>` : ''}</div>
      <div class="nutri">${FIELDS.map(([k, l, u]) => `<label class="field">${l} (${u})
        <input class="input" type="number" step="any" min="0" name="${k}" value="${item[k] ?? ''}"></label>`).join('')}</div>
      <button class="btn block" type="submit">${icon('check', 'sm')}Add to today</button>
    </form>`);
    sh.el.querySelector('#foodForm').onsubmit = (e) => {
      e.preventDefault();
      const fd = new FormData(e.target);
      const f = { ...item, id: 'f' + Date.now().toString(36), t: Date.now(), name: String(fd.get('name')).trim() };
      FIELDS.forEach(([k]) => { const v = fd.get(k); f[k] = v === '' ? null : Number(v); });
      delete f.confidence;
      SC.store.update((s) => { s.foods.push(f); });
      if (SC.api.status?.db && SC.auth.signedIn) SC.api.post('/api/foods', f).catch(() => {});
      sh.close();
      toast(`${f.name} added`);
    };
  }

  function addSheet() {
    const sh = sheet({
      title: 'Log food',
      body: `<div class="choice-grid">
          <button class="card choice" data-pick="barcode">${icon('barcode')}<b>Barcode</b><span class="tiny muted">Scan a packaged food</span></button>
          <button class="card choice" data-pick="photo">${icon('image')}<b>Photo</b><span class="tiny muted">Snap your plate${SC.services.gemini.available() ? '' : ' (needs Gemini)'}</span></button>
        </div>
        <button class="btn ghost block" style="margin-top:14px" data-pick="manual">${icon('pencil', 'sm')}Type it in</button>
        <input type="file" accept="image/*" capture="environment" hidden id="photoIn">`,
    });
    sh.el.onclick = (e) => {
      const p = e.target.closest('[data-pick]')?.dataset.pick;
      if (p === 'barcode') { sh.close(); barcodeSheet(); }
      if (p === 'photo') sh.el.querySelector('#photoIn').click();
      if (p === 'manual') { sh.setTitle('Type it in'); reviewForm(sh, { source: 'manual', basis: 'per serving' }); }
    };
    sh.el.querySelector('#photoIn').onchange = async (e) => {
      const file = e.target.files[0];
      if (!file) return;
      sh.setTitle('Photo');
      const big = await resizeImage(file, 768);
      const thumb = await resizeImage(file, 320, 0.75);
      if (!SC.services.gemini.available()) {
        reviewForm(sh, { source: 'photo', image: thumb, basis: 'per serving' });
        toast('Connect the SmartCam server to fill this in automatically.');
        return;
      }
      sh.setBody(`<div class="empty"><img class="food-photo" src="${big}" alt=""><p>Gemini is looking at your food…</p></div>`);
      try {
        const r = await F().analyzePhoto(big);
        if (!r?.name) { toast("That doesn't look like food — try another photo.", 'bad'); }
        reviewForm(sh, { ...r, source: 'photo', image: thumb, basis: r.basis || 'per serving' });
      } catch (err) {
        toast(err.message || 'Gemini failed', 'bad');
        reviewForm(sh, { source: 'photo', image: thumb, basis: 'per serving' });
      }
    };
  }

  function barcodeSheet() {
    let stream = null, timer = null;
    const stop = () => { clearInterval(timer); stream?.getTracks().forEach((t) => t.stop()); };
    const sh = sheet({
      title: 'Scan a barcode',
      onClose: stop,
      body: `<div class="stack">
        <div class="scanbox"><video playsinline muted autoplay></video><div class="scanline"></div></div>
        <p class="tiny muted" id="bcStatus" style="margin:0">Point the camera at the barcode.</p>
        <form class="row" id="bcForm"><input class="input grow" name="code" inputmode="numeric" autocomplete="off" placeholder="Or type the barcode number">
          <button class="btn" type="submit">Look up</button></form></div>`,
    });
    const status = (t) => { const s = sh.el.querySelector('#bcStatus'); if (s) s.textContent = t; };

    async function lookup(code) {
      stop();
      sh.setBody(`<div class="empty"><p>Looking up <span class="mono">${esc(code)}</span>…</p></div>`);
      try {
        const item = await F().lookupBarcode(code);
        if (!item) {
          toast('Not in Open Food Facts — enter it by hand.');
          return reviewForm(sh, { source: 'barcode', barcode: code, basis: 'per serving' });
        }
        reviewForm(sh, item);
      } catch {
        toast('Lookup failed — check your connection.', 'bad');
        reviewForm(sh, { source: 'barcode', barcode: code, basis: 'per serving' });
      }
    }

    sh.el.querySelector('#bcForm').onsubmit = (e) => {
      e.preventDefault();
      const code = new FormData(e.target).get('code').toString().replace(/\D/g, '');
      if (code.length >= 6) lookup(code); else toast('Enter the full number under the barcode.');
    };

    (async () => {
      let det = null;
      try { if ('BarcodeDetector' in window) det = new BarcodeDetector({ formats: ['ean_13', 'ean_8', 'upc_a', 'upc_e'] }); } catch {}
      if (!navigator.mediaDevices?.getUserMedia) return status('Camera unavailable here — type the number instead.');
      try {
        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } });
      } catch {
        return status('Camera blocked — type the number instead.');
      }
      const video = sh.el.querySelector('video');
      if (!video) return stop();
      video.srcObject = stream;
      if (!det) return status("This browser can't read barcodes live (try Chrome on Android). Type the number below.");
      timer = setInterval(async () => {
        try {
          const codes = await det.detect(video);
          if (codes[0]?.rawValue) lookup(codes[0].rawValue);
        } catch {}
      }, 350);
    })();
  }

  function detailSheet(f) {
    const s = F().score(f);
    const sh = sheet({
      title: f.name,
      body: `<div class="stack">
        ${f.image ? `<img class="food-photo" src="${esc(f.image)}" alt="">` : ''}
        <div class="row" style="justify-content:space-between;flex-wrap:wrap">
          <div><div class="eyebrow">Food score</div><div class="big">${s}<small>/ 100</small></div></div>
          ${statusChip(level(s), word(s))}</div>
        <div class="list card" style="box-shadow:none;padding:4px 14px">
          ${FIELDS.map(([k, l, u]) => `<div class="list-row"><span class="grow">${l}</span><span class="mono">${f[k] ?? '—'} ${f[k] != null ? u : ''}</span></div>`).join('')}
        </div>
        <p class="tiny muted" style="margin:0">Values ${esc(f.basis || 'per serving')} · from ${esc(f.source)}${f.barcode ? ` · ${esc(f.barcode)}` : ''} · ${new Date(f.t).toLocaleString()}</p>
        <button class="btn danger block" data-del>${icon('trash', 'sm')}Remove</button></div>`,
    });
    sh.el.querySelector('[data-del]').onclick = () => {
      SC.store.update((st) => { st.foods = st.foods.filter((x) => x.id !== f.id); });
      if (SC.api.status?.db && SC.auth.signedIn) fetch(`${SC.api.base()}/api/foods?id=${encodeURIComponent(f.id)}`, { method: 'DELETE', headers: SC.auth.headers() }).catch(() => {});
      sh.close();
      toast('Removed');
    };
  }

  SC.views = SC.views || {};
  SC.views.food = {
    title: 'Food',
    render(el) {
      this.el = el;
      this.draw();
      this.off = SC.store.on(() => this.draw());
      if (SC.api.status?.db) SC.store.pullFoods();
      el.onclick = (e) => {
        if (e.target.closest('[data-add]')) return addSheet();
        const id = e.target.closest('.tile[data-id]')?.dataset.id;
        const f = id && SC.store.state.foods.find((x) => x.id === id);
        if (f) detailSheet(f);
      };
    },
    destroy() { this.off && this.off(); },
    draw() {
      const foods = [...SC.store.state.foods].sort((a, b) => b.t - a.t);
      const todays = foods.filter((f) => todayKey(new Date(f.t)) === todayKey());
      const score = F().dayScore(todays);
      const kcal = todays.reduce((a, f) => a + (f.basis === 'per 100 g' ? 0 : f.kcal || 0), 0);
      this.el.innerHTML = `
        <div class="food-summary">
          <div><div class="eyebrow">Today's score</div><div class="big">${score ?? '—'}${score != null ? '<small>/ 100</small>' : ''}</div></div>
          ${score != null ? statusChip(level(score), word(score)) : ''}
          <div style="margin-left:auto;text-align:right"><div class="eyebrow" style="justify-content:flex-end">Logged</div>
            <div class="mono">${todays.length} today · ${kcal ? `${num(kcal)} kcal*` : '—'}</div></div>
        </div>
        <div class="food-grid">
          <button class="tile add" data-add aria-label="Log food"><div class="face">${icon('plus')}</div><span class="name">Add food</span><span class="meta">barcode / photo</span></button>
          ${foods.map(tile).join('')}
        </div>
        ${foods.length ? '<p class="tiny muted" style="margin-top:16px">*Calories count servings only — barcode items are per 100 g until you add a portion size.</p>'
          : `<p class="muted" style="margin-top:22px">Tap <b>+</b> to log your first meal. Barcodes come from Open Food Facts; photos are read by Gemini.</p>`}`;
    },
  };
})();

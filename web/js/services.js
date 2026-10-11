/* Integrations: SmartCam server (Gemini, ElevenLabs, Tiger Data), Presage, Open Food Facts, music, Solana.
   API keys live on the server (server/.env), never in the app. With no server, everything runs in demo mode. */
window.SC = window.SC || {};

(function () {
  const S = () => SC.store.state.settings;
  const { clamp } = SC.ui;

  /* ---------------- SmartCam server ---------------- */
  const api = {
    status: null, // { gemini, tts, db, solanaMerchant, solanaNetwork } once connected
    base() {
      const b = (S().backendUrl || SC.config.backend || '').replace(/\/$/, '');
      return b;
    },
    async init() {
      // A page opened straight from disk has no server behind it.
      if (!api.base() && !/^https?:$/.test(location.protocol)) { api.status = null; return null; }
      try {
        const res = await fetch(api.base() + '/api/config', { signal: AbortSignal.timeout(5000) });
        api.status = res.ok ? await res.json() : null;
      } catch {
        api.status = null;
      }
      document.dispatchEvent(new CustomEvent('sc:api'));
      return api.status;
    },
    async post(path, body, { raw = false } = {}) {
      const res = await fetch(api.base() + path, {
        method: 'POST', headers: SC.auth.headers(), body: JSON.stringify(body),
      });
      if (!res.ok) {
        let msg = `Server error ${res.status}`;
        try { msg = (await res.json()).error || msg; } catch {}
        throw new Error(msg);
      }
      return raw ? res : res.json();
    },
  };

  /* ---------------- Gemini (through the server) ---------------- */
  const gemini = {
    available: () => !!api.status?.gemini,
    async generate(prompt, { system, json = false, image, history = [] } = {}) {
      if (!gemini.available()) throw new Error('NO_KEY');
      const { text } = await api.post('/api/gemini', { prompt, system, json, image, history });
      if (!json) return text;
      return JSON.parse(text.replace(/^```(?:json)?|```$/g, '').trim());
    },
  };

  /* ---------------- Voice: ElevenLabs via the server, falling back to the device voice ---------------- */
  let currentAudio = null;
  const tts = {
    async speak(text) {
      if (!text) return;
      tts.stop();
      if (api.status?.tts) {
        try {
          const res = await api.post('/api/tts', { text }, { raw: true });
          const url = URL.createObjectURL(await res.blob());
          currentAudio = new Audio(url);
          currentAudio.onended = () => URL.revokeObjectURL(url);
          await currentAudio.play();
          return;
        } catch (e) {
          SC.ui.toast('ElevenLabs failed — using the built-in voice.', 'bad');
        }
      }
      if (SC.native?.on) return SC.native.call('speak', { text }).catch(() => {});
      if ('speechSynthesis' in window) {
        const u = new SpeechSynthesisUtterance(text);
        u.rate = 1.02;
        speechSynthesis.speak(u);
      }
    },
    stop() {
      if (currentAudio) { currentAudio.pause(); currentAudio = null; }
      if (SC.native?.on) SC.native.call('stopSpeaking').catch(() => {});
      else if ('speechSynthesis' in window) speechSynthesis.cancel();
    },
  };

  /* ---------------- Presage (camera vitals) ----------------
     Presage's SmartSpectra SDK runs on Android / iOS / C++, not in a browser tab.
     This mock keeps the same shape: start with a live video, report progress, resolve with vitals.
     In the native build, replace measure() with the SDK's callbacks. */
  const presage = {
    isMock: true,
    measure(video, { onProgress, signal, duration = 9000 } = {}) {
      return new Promise((resolve, reject) => {
        const t0 = performance.now();
        const timer = setInterval(() => {
          if (signal?.aborted) { clearInterval(timer); reject(new DOMException('aborted', 'AbortError')); return; }
          const p = Math.min(1, (performance.now() - t0) / duration);
          onProgress && onProgress(p);
          if (p >= 1) {
            clearInterval(timer);
            const high = S().simulateHighHr;
            const hr = high ? 104 + Math.round(Math.random() * 14) : 62 + Math.round(Math.random() * 22);
            const br = 12 + Math.round(Math.random() * 6);
            const stress = clamp(Math.round((hr - 55) * 1.1 + (br - 12) * 3 + Math.random() * 10), 5, 95);
            resolve({ t: Date.now(), hr, br, stress, source: 'demo' });
          }
        }, 100);
      });
    },
  };

  /* ---------------- Food: Open Food Facts + Gemini vision ---------------- */
  const GRADE = { a: 92, b: 76, c: 58, d: 38, e: 20 };
  const food = {
    async lookupBarcode(code) {
      const fields = 'product_name,brands,image_front_small_url,nutriments,nutriscore_grade,serving_size';
      const res = await fetch(`https://world.openfoodfacts.org/api/v2/product/${encodeURIComponent(code)}.json?fields=${fields}`);
      if (!res.ok && res.status !== 404) throw new Error('Lookup failed');
      const data = await res.json();
      if (data.status !== 1 || !data.product) return null;
      const p = data.product, n = p.nutriments || {};
      const g = (k) => (n[k] == null ? null : Math.round(Number(n[k]) * 10) / 10);
      return {
        name: [p.brands?.split(',')[0], p.product_name].filter(Boolean).join(' — ') || `Product ${code}`,
        image: p.image_front_small_url || '',
        basis: 'per 100 g',
        kcal: g('energy-kcal_100g'), protein: g('proteins_100g'), carbs: g('carbohydrates_100g'),
        sugar: g('sugars_100g'), fat: g('fat_100g'), satFat: g('saturated-fat_100g'),
        fiber: g('fiber_100g'), salt: g('salt_100g'),
        grade: (p.nutriscore_grade || '').toLowerCase().replace(/[^a-e]/g, '') || null,
        barcode: code, source: 'barcode',
      };
    },

    async analyzePhoto(dataUrl) {
      const prompt = `Identify the food in this photo and estimate nutrition for the portion shown.
Return JSON: {"name": string, "basis": "per serving", "kcal": number, "protein": number, "carbs": number,
"sugar": number, "fat": number, "satFat": number, "fiber": number, "salt": number, "confidence": "low"|"medium"|"high"}.
Grams for macros, grams for salt. If it is not food, return {"name": "", "confidence": "low"}.`;
      return gemini.generate(prompt, { json: true, image: dataUrl });
    },

    // 0–100: Nutri-Score when we have it, otherwise a simple nutrient heuristic.
    score(item) {
      if (item.grade && GRADE[item.grade]) return GRADE[item.grade];
      const v = (k) => Number(item[k]) || 0;
      const s = 62 + Math.min(v('protein'), 25) * 0.8 + Math.min(v('fiber'), 10) * 1.6
        - Math.min(v('sugar'), 40) * 0.8 - Math.min(v('satFat'), 15) * 1.5 - Math.min(v('salt'), 3) * 5;
      return Math.round(clamp(s, 5, 98));
    },

    dayScore(items) {
      if (!items.length) return null;
      return Math.round(items.reduce((a, i) => a + food.score(i), 0) / items.length);
    },
  };

  /* ---------------- Music picked from mood ---------------- */
  const music = {
    link(query) {
      return S().musicService === 'ytmusic'
        ? `https://music.youtube.com/search?q=${encodeURIComponent(query)}`
        : `https://open.spotify.com/search/${encodeURIComponent(query)}`;
    },
    fallback(v) {
      if (v.stress >= 67 || v.hr > 100) return { title: 'Slow-breath ambient', query: 'calm ambient breathing' };
      if (v.stress >= 34) return { title: 'Easy lo-fi', query: 'lofi chill beats' };
      return { title: 'Bright focus mix', query: 'upbeat focus playlist' };
    },
    async suggest(v) {
      let pick = music.fallback(v);
      if (gemini.available()) {
        try {
          const r = await gemini.generate(
            `Someone just measured heart rate ${v.hr} bpm, breathing ${v.br}/min, stress ${v.stress}/100. ` +
            'Suggest one short playlist mood to help them feel balanced. Return JSON {"title": "max 4 words", "query": "search terms"}.',
            { json: true });
          if (r?.title && r?.query) pick = r;
        } catch {}
      }
      return { ...pick, url: music.link(pick.query) };
    },
  };

  /* ---------------- Solana Pay ---------------- */
  const solana = {
    provider: () => window.phantom?.solana || (window.solana?.isPhantom ? window.solana : null),
    async connect() {
      const p = solana.provider();
      if (!p) throw new Error('NO_WALLET');
      const r = await p.connect();
      return r.publicKey.toString();
    },
    async solPrice() {
      const res = await fetch('https://api.coingecko.com/api/v3/simple/price?ids=solana&vs_currencies=usd');
      if (!res.ok) throw new Error('price');
      return (await res.json()).solana.usd;
    },
    // Solana Pay transfer-request URL — any Solana Pay wallet can open it.
    payUrl({ recipient, amountSol, label, message, memo }) {
      const q = new URLSearchParams({ amount: amountSol.toFixed(6), label, message, memo });
      return `solana:${recipient}?${q.toString()}`;
    },
  };

  SC.api = api;
  SC.services = { gemini, tts, presage, food, music, solana };
})();

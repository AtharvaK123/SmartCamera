/* Ask Gemini: drawer on phones, permanent right column on wide screens. */
(function () {
  const { icon, esc, toast, todayKey, fmtMin, time12 } = SC.ui;

  function context() {
    const st = SC.store.state;
    const v = st.vitals[st.vitals.length - 1];
    const foods = st.foods.filter((f) => todayKey(new Date(f.t)) === todayKey());
    return {
      date: new Date().toString(),
      steps: st.steps[todayKey()] || 0,
      stepGoal: st.profile.stepGoal,
      lastScan: v ? { heartRate: v.hr, breathing: v.br, stress: v.stress, when: new Date(v.t).toString() } : null,
      foodToday: foods.map((f) => ({ name: f.name, kcal: f.kcal, basis: f.basis, score: SC.services.food.score(f) })),
      nutritionScore: SC.services.food.dayScore(foods),
      screenTimeTodayMin: SC.demo.appsToday.reduce((a, r) => a + r.min, 0),
      sleepLock: st.sleep.lock, sleepUnlock: st.sleep.unlock,
      alarms: st.alarms.filter((a) => a.on).map((a) => a.time),
    };
  }

  const SYSTEM = 'You are Gemini, the assistant inside the SmartCam app. Only help with SmartCam: how to use its features ' +
    '(face scan and vitals, games, mood music, food logging, nutrition limits, plans, steps, heart rate, screen time, sleep, alarms, ' +
    'screen lock, the Solana shop, accounts, settings) and the user\'s own health, food, activity and sleep data. If a question is about ' +
    'anything else, do not answer it; say in one friendly sentence that you can only help with SmartCam and their health data here. ' +
    'Plain text, no markdown, under 90 words. Data: ';

  function demoReply(q) {
    const c = context();
    const t = q.toLowerCase();
    if (/step|walk/.test(t)) return `You're at ${c.steps.toLocaleString()} of ${c.stepGoal.toLocaleString()} steps. A 15-minute walk adds roughly 1,500–2,000.`;
    if (/heart|hr|pulse|stress|breath/.test(t)) return c.lastScan
      ? `Your last scan: ${c.lastScan.heartRate} bpm, ${c.lastScan.breathing} breaths/min, stress ${c.lastScan.stress}/100. Try four slow breaths in the Camera tab's game if you feel tense.`
      : 'No scan yet — open the Camera tab and hold still for about 10 seconds.';
    if (/food|eat|meal|nutri|calor/.test(t)) return c.foodToday.length
      ? `You've logged ${c.foodToday.length} item(s) today with a nutrition score of ${c.nutritionScore}. Adding vegetables or a protein source usually lifts it.`
      : 'Nothing logged yet today. Tap + in the Food tab to scan a barcode or snap a photo.';
    if (/screen|phone|app|sleep/.test(t)) return `Screen time today is ${fmtMin(c.screenTimeTodayMin)}. Your phone locks at ${time12(c.sleepLock)}.`;
    return "I can help with SmartCam and your health data — try asking about your steps, scans, food or screen time.";
  }

  function mount() {
    const root = document.getElementById('chat');
    root.innerHTML = `
      <header class="chat-head"><span class="gmark">G</span><h2>Gemini</h2>
        <button class="icon-btn" id="chatSpeak"></button>
        <button class="icon-btn chat-close" id="chatClose" aria-label="Close">${icon('close')}</button></header>
      <div class="chat-log" id="chatLog" aria-live="polite"></div>
      <form class="chat-form" id="chatForm">
        <input id="chatInput" placeholder="Ask about your day…" autocomplete="off" aria-label="Message Gemini">
        <button type="button" class="icon-btn" id="chatMic" aria-label="Speak">${icon('mic')}</button>
        <button type="submit" class="icon-btn" aria-label="Send">${icon('send')}</button>
      </form>`;

    const log = root.querySelector('#chatLog');
    const input = root.querySelector('#chatInput');
    const speakBtn = root.querySelector('#chatSpeak');
    let busy = false;

    function drawSpeak() {
      const on = SC.store.state.settings.speak;
      speakBtn.innerHTML = icon(on ? 'speaker' : 'speakerOff');
      speakBtn.setAttribute('aria-label', on ? 'Voice replies on' : 'Voice replies off');
      speakBtn.title = on ? 'Voice replies on' : 'Voice replies off';
    }
    speakBtn.onclick = () => {
      SC.store.update((s) => { s.settings.speak = !s.settings.speak; });
      if (!SC.store.state.settings.speak) SC.services.tts.stop();
      drawSpeak();
    };
    drawSpeak();

    function draw(pending) {
      const msgs = SC.store.state.chat;
      log.innerHTML = msgs.length ? msgs.map((m) => `<div class="msg ${m.role}">${esc(m.text)}</div>`).join('')
        : `<div class="msg model">Hi! I can see your steps, scans, food and screen time. What would you like to know?</div>
           <div class="chat-suggest">${['How am I doing today?', 'Why is my stress high?', 'What should I eat next?', 'Help me cut screen time'].map((s) => `<button type="button">${s}</button>`).join('')}</div>`;
      if (pending) log.insertAdjacentHTML('beforeend', '<div class="msg model thinking">Thinking…</div>');
      log.scrollTop = log.scrollHeight;
    }
    log.onclick = (e) => { const b = e.target.closest('.chat-suggest button'); if (b) send(b.textContent); };

    async function send(text) {
      text = text.trim();
      if (!text || busy) return;
      busy = true;
      input.value = '';
      SC.store.update((s) => { s.chat.push({ role: 'user', text }); s.chat = s.chat.slice(-40); });
      draw(true);
      let reply;
      try {
        if (SC.services.gemini.available()) {
          const history = SC.store.state.chat.slice(-13, -1).map((m) => ({ role: m.role, parts: [{ text: m.text }] }));
          reply = await SC.services.gemini.generate(text, { system: SYSTEM + JSON.stringify(context()), history });
        } else {
          await new Promise((r) => setTimeout(r, 450));
          reply = demoReply(text);
        }
      } catch (e) {
        reply = `Sorry, I couldn't reach Gemini (${e.message}).`;
      }
      SC.store.update((s) => { s.chat.push({ role: 'model', text: reply }); });
      busy = false;
      draw();
      if (SC.store.state.settings.speak) SC.services.tts.speak(reply);
    }

    root.querySelector('#chatForm').onsubmit = (e) => { e.preventDefault(); send(input.value); };

    // Voice input via the browser's speech recognition (Chrome, Edge, Safari).
    const Rec = window.SpeechRecognition || window.webkitSpeechRecognition;
    const mic = root.querySelector('#chatMic');
    let rec = null;
    mic.onclick = async () => {
      if (SC.native.on) {
        if (mic.classList.contains('rec')) { SC.native.call('stopListening'); return; }
        mic.classList.add('rec');
        try { const { text } = await SC.native.call('listen'); if (text) send(text); }
        catch (e) { toast(e.message || 'Microphone error', 'bad'); }
        mic.classList.remove('rec');
        return;
      }
      if (!Rec) return toast('Voice input is not supported in this browser.');
      if (rec) { rec.stop(); return; }
      rec = new Rec();
      rec.lang = navigator.language || 'en-US';
      rec.interimResults = true;
      mic.classList.add('rec');
      rec.onresult = (e) => {
        const r = e.results[e.results.length - 1];
        input.value = r[0].transcript;
        if (r.isFinal) send(input.value);
      };
      rec.onerror = (e) => { if (e.error !== 'aborted') toast('Microphone: ' + e.error, 'bad'); };
      rec.onend = () => { mic.classList.remove('rec'); rec = null; };
      rec.start();
    };

    const app = document.getElementById('app');
    const close = () => { app.classList.remove('chat-open'); SC.services.tts.stop(); };
    root.querySelector('#chatClose').onclick = close;
    document.getElementById('chatBackdrop').onclick = close;
    document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && app.classList.contains('chat-open') && !document.querySelector('.sheet-wrap')) close(); });

    SC.openChat = () => {
      app.classList.add('chat-open');
      setTimeout(() => input.focus(), 250);
    };
    draw();
  }

  SC.mountChat = mount;
})();

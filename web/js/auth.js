/* Accounts on the website: the same usernames as the phone app (stored in Tiger Data).
   Signed-in users see their own food log; guests can look around with nothing synced. */
window.SC = window.SC || {};

(function () {
  const KEY = 'smartcam.auth';
  const saved = (() => { try { return JSON.parse(localStorage.getItem(KEY)) || null; } catch { return null; } })();

  const auth = {
    user: saved, // { token, username, role } | { guest: true } | null
    headers() {
      const h = { 'Content-Type': 'application/json' };
      if (auth.user?.token) h.Authorization = 'Bearer ' + auth.user.token;
      return h;
    },
    get signedIn() { return !!auth.user?.token; },
    save(u) {
      auth.user = u;
      try { u ? localStorage.setItem(KEY, JSON.stringify(u)) : localStorage.removeItem(KEY); } catch {}
    },
    signOut() { auth.save(null); location.reload(); },

    /** Shows the sign-in screen until the person signs in or picks guest. */
    async gate() {
      if (auth.user?.guest) return;
      if (auth.user?.token) {
        try {
          const me = await (await fetch(SC.api.base() + '/api/me', { headers: auth.headers() })).json();
          if (me.username) return;
        } catch { return; } // server unreachable: keep the saved login
        auth.save(null);
      }
      await new Promise((done) => show(done));
    },
  };

  function show(done) {
    const el = document.createElement('div');
    el.className = 'auth';
    let mode = 'signin';
    const draw = () => {
      el.innerHTML = `
        <div>
          <h1>Smart<b>Cam</b></h1>
          <p class="tag">Your camera, your calm.</p>
          <form class="auth-card card">
            <div class="seg" role="group">
              <button type="button" data-mode="signin" aria-pressed="${mode === 'signin'}">Sign in</button>
              <button type="button" data-mode="signup" aria-pressed="${mode === 'signup'}">Create account</button>
            </div>
            <label class="field">Username<input class="input" name="u" autocomplete="username" required></label>
            <label class="field">Password<input class="input" name="p" type="password" autocomplete="${mode === 'signup' ? 'new-password' : 'current-password'}" required></label>
            <p class="err" aria-live="polite"></p>
            <button class="btn primary block">${mode === 'signup' ? 'Create account' : 'Sign in'}</button>
            <button class="btn ghost block" type="button" data-guest>Continue as guest</button>
            <p class="tiny muted" style="margin:4px 0 0;text-align:center">${mode === 'signup' ? 'By creating an account you agree to the' : 'How we handle your data:'} <a href="privacy.html" target="_blank" rel="noopener">Privacy Policy</a></p>
          </form>
        </div>`;
      el.querySelectorAll('[data-mode]').forEach((b) => { b.onclick = () => { mode = b.dataset.mode; draw(); }; });
      el.querySelector('[data-guest]').onclick = () => { auth.save({ guest: true }); el.remove(); done(); };
      el.querySelector('form').onsubmit = async (e) => {
        e.preventDefault();
        const f = e.target;
        const err = el.querySelector('.err');
        err.textContent = '';
        try {
          const res = await fetch(SC.api.base() + (mode === 'signup' ? '/api/signup' : '/api/login'), {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: f.u.value, password: f.p.value }),
          });
          const body = await res.json();
          if (!res.ok) throw new Error(body.error || 'Something went wrong');
          auth.save(body);
          el.remove();
          done();
        } catch (x) {
          err.textContent = x.message === 'Failed to fetch' ? 'The SmartCam server isn’t reachable.' : x.message;
        }
      };
    };
    draw();
    document.body.appendChild(el);
  }

  SC.auth = auth;
})();

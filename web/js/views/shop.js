/* Shop with Gemini: describe what you want, Gemini suggests products, pay with Solana Pay. */
(function () {
  const { icon, esc, sheet, toast } = SC.ui;

  function demoResults(q) {
    const base = q.trim().replace(/\s+/g, ' ');
    return [
      { name: `${base} — everyday pick`, price: 24.99, why: 'Well-reviewed, middle of the price range.', query: base },
      { name: `${base} — budget option`, price: 12.5, why: 'Cheapest option that still covers the basics.', query: `${base} budget` },
      { name: `${base} — premium`, price: 59, why: 'Better materials and a longer warranty.', query: `${base} premium` },
    ];
  }

  async function search(q) {
    if (!SC.services.gemini.available()) return { items: demoResults(q), demo: true };
    const r = await SC.services.gemini.generate(
      `Shopping request: "${q}". Suggest 3–5 specific real products that fit. Return JSON: {"items":[{"name": string, "price": number (typical USD), "why": "one short sentence"}]}`,
      { json: true, system: 'You are a careful shopping helper. Prefer well-reviewed, widely available products. Prices are estimates.' });
    return { items: (r.items || []).slice(0, 5), demo: false };
  }

  function buySheet(item) {
    const st = { solanaRecipient: SC.api.status?.solanaMerchant || '', solanaNetwork: SC.api.status?.solanaNetwork || 'devnet' };
    const sh = sheet({ title: 'Checkout', body: `<div class="empty"><p>Getting the SOL price…</p></div>` });
    (async () => {
      let rate = null;
      try { rate = await SC.services.solana.solPrice(); } catch {}
      const sol = rate ? item.price / rate : null;
      const ready = !!st.solanaRecipient;
      const url = ready && sol ? SC.services.solana.payUrl({
        recipient: st.solanaRecipient, amountSol: sol, label: 'SmartCam Shop', message: item.name.slice(0, 60), memo: 'sc-' + Date.now().toString(36),
      }) : null;
      sh.setBody(`<div class="stack">
        <div class="card" style="box-shadow:none"><b>${esc(item.name)}</b>
          <div class="row" style="justify-content:space-between;margin-top:8px">
            <span class="big" style="font-size:26px">$${item.price.toFixed(2)}</span>
            <span class="mono">${sol ? `≈ ${sol.toFixed(4)} SOL` : 'SOL price unavailable'}</span></div></div>
        ${ready ? '' : `<div class="notice warn">${icon('alert')}<span>The SmartCam server isn't connected, so this is a demo checkout — nothing is charged.</span></div>`}
        <p class="tiny muted" style="margin:0">Network: ${esc(st.solanaNetwork)}. Prices are Gemini estimates; confirm with the seller before paying.</p>
        ${url ? `<a class="btn pop block" href="${esc(url)}" data-paid>${icon('wallet', 'sm')}Pay with Solana wallet</a>` : ''}
        <button class="btn ${url ? 'ghost' : 'pop'} block" data-demo>${icon('check', 'sm')}${url ? "I've paid — save order" : 'Place demo order'}</button>
      </div>`);
      sh.el.querySelector('[data-demo]').onclick = () => {
        SC.store.update((s) => { s.orders.unshift({ id: 'o' + Date.now().toString(36), t: Date.now(), name: item.name, usd: item.price, sol, demo: !url }); });
        sh.close();
        toast(url ? 'Order saved' : 'Demo order placed');
      };
    })();
  }

  SC.views = SC.views || {};
  SC.views.shop = {
    title: 'Shop',
    render(el) {
      this.el = el;
      const st = SC.store.state;
      el.innerHTML = `
        <form class="shop-input" id="shopForm">
          <input name="q" placeholder="Shop with Gemini" value="${esc(st.shop.query)}" aria-label="What are you shopping for?" autocomplete="off">
          <button type="submit" aria-label="Search"><span class="gmark">G</span></button>
        </form>
        <div id="results"></div>
        <div id="orders"></div>`;
      el.querySelector('#shopForm').onsubmit = async (e) => {
        e.preventDefault();
        const q = new FormData(e.target).get('q').toString().trim();
        if (!q) return;
        el.querySelector('#results').innerHTML = `<div class="empty"><p>Gemini is finding options…</p></div>`;
        try {
          const r = await search(q);
          SC.store.update((s) => { s.shop = { query: q, results: r.items, demo: r.demo }; });
        } catch (err) {
          toast(err.message || 'Search failed', 'bad');
        }
        if (this.el) this.draw();
      };
      el.onclick = (e) => {
        const i = e.target.closest('[data-buy]')?.dataset.buy;
        if (i != null) buySheet(SC.store.state.shop.results[Number(i)]);
      };
      this.draw();
    },
    draw() {
      const { shop, orders } = SC.store.state;
      const res = this.el.querySelector('#results');
      res.innerHTML = !shop.results.length
        ? `<div class="empty">${icon('bag')}<h3>Nothing to show right now</h3><p>Try “a quiet desk fan under $40” or “high-protein snacks”.</p></div>`
        : `${shop.demo ? `<p class="tiny muted" style="margin:0 0 12px"><span class="chip demo">demo results</span> Connect the SmartCam server for real suggestions.</p>` : ''}
          <div class="products">${shop.results.map((p, i) => `
            <div class="card product">
              <h3>${esc(p.name)}</h3>
              <span class="price">$${Number(p.price || 0).toFixed(2)}<small class="tiny muted" style="font-family:var(--f-body);font-weight:400"> est.</small></span>
              <p class="muted" style="margin:0">${esc(p.why)}</p>
              <div class="acts">
                <button class="btn sm pop" data-buy="${i}">${icon('wallet', 'sm')}Buy with Solana</button>
              </div></div>`).join('')}</div>`;
      this.el.querySelector('#orders').innerHTML = orders.length ? `
        <h3 class="section-title">Recent orders</h3>
        <div class="card list" style="padding:2px 14px">${orders.slice(0, 6).map((o) => `
          <div class="list-row"><div class="grow"><b>${esc(o.name)}</b><div class="tiny muted">${new Date(o.t).toLocaleString()}</div></div>
          <span class="mono">$${o.usd.toFixed(2)}</span>${o.demo ? '<span class="chip demo">demo</span>' : ''}</div>`).join('')}</div>` : '';
    },
    destroy() { this.el = null; },
  };
})();

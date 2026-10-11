/* Shared UI helpers: icons, sheets, toasts, formatting, tiny charts. */
window.SC = window.SC || {};

(function () {
  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  const PATHS = {
    home: '<path d="M3 11.5 12 4l9 7.5"/><path d="M5.5 10v9.5h13V10"/><path d="M10 19.5v-5h4v5"/>',
    camera: '<path d="M4 8h3l1.6-2.5h6.8L17 8h3v11H4z"/><circle cx="12" cy="13.5" r="3.5"/>',
    food: '<path d="M12 7.5c-1.6-1.2-4.8-1.4-6.2 1.2-1.6 3 .2 8 2.4 9.8 1.3 1 2.4.6 3.8 0 1.4.6 2.5 1 3.8 0 2.2-1.8 4-6.8 2.4-9.8-1.4-2.6-4.6-2.4-6.2-1.2z"/><path d="M12 7.5c0-2 1-3.5 3-4"/>',
    phone: '<rect x="7" y="2.5" width="10" height="19" rx="2.5"/><path d="M11 18.5h2"/>',
    bag: '<path d="M5 8h14l-1 12.5H6z"/><path d="M9 10V6.5a3 3 0 0 1 6 0V10"/>',
    dots: '<circle cx="12" cy="5" r="1.4" fill="currentColor"/><circle cx="12" cy="12" r="1.4" fill="currentColor"/><circle cx="12" cy="19" r="1.4" fill="currentColor"/>',
    mic: '<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21"/>',
    send: '<path d="M4 12 20 4l-6 16-2.5-6.5z"/><path d="M11.5 13.5 20 4"/>',
    speaker: '<path d="M4 9.5h3.5L12 5.5v13l-4.5-4H4z"/><path d="M15.5 9a4 4 0 0 1 0 6M18 6.5a7.5 7.5 0 0 1 0 11"/>',
    speakerOff: '<path d="M4 9.5h3.5L12 5.5v13l-4.5-4H4z"/><path d="m16 9.5 5 5m0-5-5 5"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    barcode: '<path d="M4 6v12M7 6v12M10.5 6v12M13 6v12M16.5 6v12M20 6v12"/>',
    image: '<rect x="3.5" y="5" width="17" height="14" rx="2"/><circle cx="9" cy="10" r="1.6"/><path d="m4 17 5-4.5 4 3.5 2.5-2 4.5 3.5"/>',
    pencil: '<path d="M4 20h4L19 9l-4-4L4 16z"/><path d="m13.5 6.5 4 4"/>',
    close: '<path d="M6 6l12 12M18 6 6 18"/>',
    alert: '<path d="M12 4 2.8 19.5h18.4z"/><path d="M12 10v4.5M12 17v.01"/>',
    heart: '<path d="M12 20s-7.5-4.6-7.5-10A4.3 4.3 0 0 1 12 7.4 4.3 4.3 0 0 1 19.5 10c0 5.4-7.5 10-7.5 10z"/>',
    moon: '<path d="M19 14.5A7.5 7.5 0 0 1 9.5 5a7.5 7.5 0 1 0 9.5 9.5z"/>',
    alarm: '<circle cx="12" cy="13" r="7"/><path d="M12 9.5V13l2.5 2M4 5.5 7 3M20 5.5 17 3"/>',
    screen: '<rect x="3" y="4.5" width="18" height="12" rx="2"/><path d="M8.5 20h7M12 16.5V20"/>',
    lock: '<rect x="5" y="10.5" width="14" height="10" rx="2"/><path d="M8 10.5V8a4 4 0 0 1 8 0v2.5"/>',
    music: '<path d="M9 18V6l10-2v12"/><circle cx="6.5" cy="18" r="2.5"/><circle cx="16.5" cy="16" r="2.5"/>',
    trash: '<path d="M5 7h14M10 7V4.5h4V7M6.5 7l1 13h9l1-13"/>',
    check: '<path d="m5 12.5 4.5 4.5L19 7.5"/>',
    chevron: '<path d="m9 6 6 6-6 6"/>',
    refresh: '<path d="M19.5 12a7.5 7.5 0 1 1-2.2-5.3M19.5 4.5v4h-4"/>',
    steps: '<path d="M8 3.5c1.7 0 2.5 2 2.5 4.5S9.7 12 8 12 5.5 10.5 5.5 8 6.3 3.5 8 3.5zM6 14.5h4l-.3 2.5a2 2 0 0 1-3.4 0zM16 7.5c1.7 0 2.5 2 2.5 4.5S17.7 16 16 16s-2.5-1.5-2.5-4 .8-4.5 2.5-4.5zM14 18.5h4l-.3 1.5a2 2 0 0 1-3.4 0z"/>',
    wave: '<path d="M3 12h3l2-5 3.5 10 2.5-7 2 4h5"/>',
    leaf: '<path d="M5 19c0-8 5-13 14-14 0 9-5 14-13 14z"/><path d="M5 19 13 11"/>',
    wallet: '<rect x="3.5" y="6" width="17" height="13" rx="2"/><path d="M16 12.5h4.5M3.5 9.5h17"/>',
    external: '<path d="M14 4.5h5.5V10M19.5 4.5 11 13M17 14v5.5H4.5V7H10"/>',
    game: '<rect x="3" y="8" width="18" height="10" rx="4"/><path d="M8 11v4M6 13h4M15.5 12.5v.01M17.5 14.5v.01"/>',
    info: '<circle cx="12" cy="12" r="8.5"/><path d="M12 11v5M12 8v.01"/>',
  };

  function icon(name, cls = '') {
    return `<svg class="ic ${cls}" viewBox="0 0 24 24" aria-hidden="true">${PATHS[name] || ''}</svg>`;
  }

  function toast(msg, kind = '') {
    const root = document.getElementById('toasts');
    const t = document.createElement('div');
    t.className = 'toast ' + kind;
    t.textContent = msg;
    root.appendChild(t);
    setTimeout(() => t.remove(), 3800);
  }

  // Bottom sheet on phones, centered dialog on wide screens.
  function sheet({ title, body = '', onClose, size = '' }) {
    const root = document.getElementById('sheetRoot');
    const wrap = document.createElement('div');
    wrap.className = 'sheet-wrap';
    wrap.innerHTML = `
      <div class="sheet-backdrop" data-close></div>
      <section class="sheet ${size}" role="dialog" aria-modal="true" aria-label="${esc(title)}">
        <header class="sheet-head"><h2>${esc(title)}</h2>
          <button class="icon-btn" data-close aria-label="Close">${icon('close')}</button></header>
        <div class="sheet-body">${body}</div>
      </section>`;
    root.appendChild(wrap);
    requestAnimationFrame(() => wrap.classList.add('open'));
    const bodyEl = wrap.querySelector('.sheet-body');

    let closed = false;
    const onKey = (e) => { if (e.key === 'Escape' && root.lastElementChild === wrap) close(); };
    function close() {
      if (closed) return;
      closed = true;
      document.removeEventListener('keydown', onKey);
      onClose && onClose();
      wrap.classList.remove('open');
      setTimeout(() => wrap.remove(), 240);
    }
    wrap.addEventListener('click', (e) => { if (e.target.closest('[data-close]')) close(); });
    document.addEventListener('keydown', onKey);
    setTimeout(() => wrap.querySelector('.sheet-head .icon-btn').focus({ preventScroll: true }), 50);
    return {
      el: bodyEl,
      close,
      setBody(html) { bodyEl.innerHTML = html; },
      setTitle(t) { wrap.querySelector('.sheet-head h2').textContent = t; },
    };
  }

  const pad = (n) => String(n).padStart(2, '0');
  const todayKey = (d = new Date()) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;

  function fmtMin(m) {
    m = Math.round(m);
    const h = Math.floor(m / 60), r = m % 60;
    return h ? (r ? `${h}h ${r}m` : `${h}h`) : `${r}m`;
  }
  function time12(hhmm) {
    if (!hhmm) return '—';
    const [h, m] = hhmm.split(':').map(Number);
    const ap = h >= 12 ? 'pm' : 'am';
    return `${((h + 11) % 12) + 1}:${pad(m)} ${ap}`;
  }
  function ago(ts) {
    const s = (Date.now() - ts) / 1000;
    if (s < 60) return 'just now';
    if (s < 3600) return `${Math.floor(s / 60)} min ago`;
    if (s < 86400) return `${Math.floor(s / 3600)} h ago`;
    return new Date(ts).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  }
  const num = (n) => Math.round(n).toLocaleString();
  const clamp = (v, a, b) => Math.max(a, Math.min(b, v));

  // Status is always color + icon + word, never color alone.
  function statusChip(level, text) {
    const ic = level === 'good' ? 'check' : 'alert';
    return `<span class="chip ${level}">${icon(ic)}${esc(text)}</span>`;
  }

  // Single-series vertical bars with per-bar hover/focus tooltip and an optional reference line.
  function barChart({ data, max, ref, refLabel, highlightLast = true }) {
    const top = Math.max(max || 0, ref || 0, ...data.map((d) => d.value)) * 1.08 || 1;
    const cols = data.map((d, i) => {
      const h = (d.value / top) * 100;
      const dim = highlightLast && i !== data.length - 1 ? 'dim' : '';
      return `<button class="col ${dim}" type="button" aria-label="${esc(d.label)}: ${esc(d.display)}">
        <span class="tip">${esc(d.label)} · ${esc(d.display)}</span>
        <div class="bar" style="height:${h}%"></div></button>`;
    }).join('');
    const refEl = ref ? `<div class="ref" style="bottom:${(ref / top) * 100}%"><span>${esc(refLabel || '')}</span></div>` : '';
    return `<div class="bars">${refEl}${cols}</div>
      <div class="bar-labels">${data.map((d) => `<span>${esc(d.short || d.label)}</span>`).join('')}</div>`;
  }

  function resizeImage(file, maxSide = 768, quality = 0.82) {
    return new Promise((resolve, reject) => {
      const img = new Image();
      const url = URL.createObjectURL(file);
      img.onload = () => {
        const s = Math.min(1, maxSide / Math.max(img.width, img.height));
        const c = document.createElement('canvas');
        c.width = Math.round(img.width * s);
        c.height = Math.round(img.height * s);
        c.getContext('2d').drawImage(img, 0, 0, c.width, c.height);
        URL.revokeObjectURL(url);
        resolve(c.toDataURL('image/jpeg', quality));
      };
      img.onerror = reject;
      img.src = url;
    });
  }

  SC.ui = { esc, icon, toast, sheet, fmtMin, time12, ago, num, clamp, todayKey, statusChip, barChart, resizeImage };
})();

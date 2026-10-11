/* App shell: tab routing (#home, #camera, …), theme, chat, settings. */
(function () {
  const { icon } = SC.ui;
  const TABS = [
    { id: 'home', label: 'Home', icon: 'home' },
    { id: 'camera', label: 'Camera', icon: 'camera' },
    { id: 'food', label: 'Food', icon: 'food' },
    { id: 'phone', label: 'Phone', icon: 'phone' },
    { id: 'shop', label: 'Shop', icon: 'bag' },
  ];

  const app = document.getElementById('app');
  const view = document.getElementById('view');
  const navItems = document.getElementById('navItems');
  let current = null;

  SC.applyTheme = () => {
    const t = SC.store.state.settings.theme;
    if (t === 'auto') document.documentElement.removeAttribute('data-theme');
    else document.documentElement.setAttribute('data-theme', t);
    const bg = getComputedStyle(document.documentElement).getPropertyValue('--paper').trim();
    document.querySelector('meta[name=theme-color]').setAttribute('content', bg);
  };

  navItems.innerHTML = TABS.map((t) => `<a class="nav-item" href="#${t.id}" aria-label="${t.label}">${icon(t.icon)}<span>${t.label}</span></a>`).join('');
  document.getElementById('openSettings').innerHTML = icon('dots');
  document.getElementById('openSettings2').innerHTML = `${icon('dots')}<span>Settings</span>`;
  document.getElementById('openSettings').onclick = () => SC.openSettings();
  document.getElementById('openSettings2').onclick = () => SC.openSettings();
  document.getElementById('askBar').onclick = () => SC.openChat();

  function route() {
    const id = location.hash.slice(1);
    const tab = TABS.find((t) => t.id === id) || TABS[0];
    if (current && current === SC.views[tab.id]) return;
    if (current?.destroy) current.destroy();
    current = SC.views[tab.id];
    view.onclick = null;
    view.innerHTML = '';
    view.scrollTop = 0;
    app.dataset.tab = tab.id;
    document.getElementById('pageTitle').textContent = current.title;
    document.title = tab.id === 'home' ? 'SmartCam' : `${current.title} · SmartCam`;
    navItems.querySelectorAll('.nav-item').forEach((a) => {
      if (a.getAttribute('href') === '#' + tab.id) a.setAttribute('aria-current', 'page');
      else a.removeAttribute('aria-current');
    });
    current.render(view);
  }

  SC.applyTheme();
  matchMedia('(prefers-color-scheme: dark)').addEventListener?.('change', SC.applyTheme);
  SC.mountChat();
  // Sign in first (or continue as a guest), then load this person's data.
  SC.api.init().then(() => SC.auth.gate()).then(() => SC.store.pull());
  window.addEventListener('hashchange', route);
  route();
})();

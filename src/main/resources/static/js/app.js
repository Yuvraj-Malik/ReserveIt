// Shared helpers: CSRF-aware fetch, STOMP connection, sidebar, notifications panel, live counters.
window.ResrvIt = (function () {
  const token = document.querySelector('meta[name="_csrf"]')?.content;
  const header = document.querySelector('meta[name="_csrf_header"]')?.content;

  function post(url, body, json) {
    const headers = {};
    if (token && header) headers[header] = token;
    let payload = body;
    if (json) {
      headers['Content-Type'] = 'application/json';
      payload = JSON.stringify(body);
    }
    return fetch(url, { method: 'POST', headers, body: payload, credentials: 'same-origin' });
  }

  let client = null;
  const pending = [];
  function stomp(onConnect) {
    if (!window.StompJs) return null;
    if (client) {
      if (client.connected) onConnect(client); else pending.push(onConnect);
      return client;
    }
    const proto = location.protocol === 'https:' ? 'wss' : 'ws';
    client = new StompJs.Client({ brokerURL: proto + '://' + location.host + '/ws', reconnectDelay: 4000 });
    pending.push(onConnect);
    client.onConnect = () => pending.forEach(fn => fn(client));
    client.activate();
    return client;
  }

  return { post, stomp };
})();

// Confirm dangerous forms with data-confirm.
document.addEventListener('submit', (e) => {
  const msg = e.target.getAttribute('data-confirm');
  if (msg && !window.confirm(msg)) e.preventDefault();
});

document.addEventListener('DOMContentLoaded', () => {
  // Page title in the top bar comes from <title>.
  const pt = document.getElementById('pageTitle');
  if (pt) pt.textContent = document.title.replace(/\s*·\s*ResrvIt\s*$/, '');

  // Highlight the current sidebar link (longest matching path wins).
  const path = location.pathname, query = location.search;
  let best = null, bestLen = -1;
  document.querySelectorAll('.sidebar .nav-item-link').forEach(a => {
    const url = new URL(a.href);
    const prefixes = [url.pathname].concat((a.dataset.also || '').split(',').filter(Boolean));
    prefixes.forEach(p => {
      const exact = a.hasAttribute('data-exact');
      const hit = exact ? path === p : (path === p || path.startsWith(p.endsWith('/') ? p : p + '/'));
      const q = a.dataset.query;
      if (hit && (!q || query.includes(q)) && p.length > bestLen) { best = a; bestLen = p.length; }
    });
  });
  if (best) { best.classList.add('active'); best.setAttribute('aria-current', 'page'); }

  // ---- notifications panel
  const bell = document.getElementById('bellBtn');
  const panel = document.getElementById('notifPanel');
  const list = document.getElementById('notifList');

  function setCount(kind, n) {
    document.querySelectorAll('[data-counter="' + kind + '"]').forEach(el => {
      el.textContent = n > 99 ? '99+' : n;
      el.classList.toggle('d-none', !n);
    });
  }

  function render(items) {
    list.innerHTML = '';
    if (!items.length) {
      list.innerHTML = '<li class="notif-empty">You\'re all caught up.</li>';
      return;
    }
    items.forEach(n => {
      const li = document.createElement('li');
      li.className = 'notif-item' + (n.read ? '' : ' unread');
      const a = document.createElement('a');
      a.href = '/notifications/' + n.id + '/open';
      a.className = 'notif-text';
      const msg = document.createElement('span'); msg.textContent = n.message;
      const at = document.createElement('small'); at.textContent = n.at;
      a.append(msg, at);
      const actions = document.createElement('div');
      actions.className = 'notif-actions';
      if (!n.read) {
        const r = document.createElement('button');
        r.type = 'button'; r.className = 'btn icon-btn sm'; r.title = 'Mark as read'; r.setAttribute('aria-label', 'Mark as read');
        r.innerHTML = '<i class="bi bi-check2"></i>';
        r.onclick = async () => { const res = await ResrvIt.post('/api/notifications/' + n.id + '/read'); if (res.ok) { setCount('notifications', (await res.json()).unread); load(); } };
        actions.append(r);
      }
      const d = document.createElement('button');
      d.type = 'button'; d.className = 'btn icon-btn sm'; d.title = 'Delete'; d.setAttribute('aria-label', 'Delete');
      d.innerHTML = '<i class="bi bi-x-lg"></i>';
      d.onclick = async () => { const res = await ResrvIt.post('/api/notifications/' + n.id + '/delete'); if (res.ok) { setCount('notifications', (await res.json()).unread); load(); } };
      actions.append(d);
      li.append(a, actions);
      list.append(li);
    });
  }

  async function load() {
    const res = await fetch('/api/notifications', { credentials: 'same-origin' });
    if (!res.ok) return;
    const data = await res.json();
    render(data.items);
    setCount('notifications', data.unread);
  }

  async function refreshCounters() {
    const res = await fetch('/api/counters', { credentials: 'same-origin' });
    if (!res.ok) return;
    const c = await res.json();
    setCount('notifications', c.notifications);
    setCount('chat', c.chat);
  }

  window.ResrvItRefresh = refreshCounters;

  if (bell && panel) {
    bell.addEventListener('click', (e) => {
      e.stopPropagation();
      const open = panel.hidden;
      panel.hidden = !open;
      bell.setAttribute('aria-expanded', String(open));
      if (open) load();
    });
    document.addEventListener('click', (e) => { if (!panel.hidden && !panel.contains(e.target)) { panel.hidden = true; bell.setAttribute('aria-expanded', 'false'); } });
    document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && !panel.hidden) { panel.hidden = true; bell.focus(); } });
    document.getElementById('notifReadAll').onclick = async () => { await ResrvIt.post('/api/notifications/read-all'); load(); };
    document.getElementById('notifDeleteAll').onclick = async () => {
      if (!confirm('Delete all notifications?')) return;
      await ResrvIt.post('/api/notifications/delete-all'); load();
    };
  }

  // Live counters: the server pings /topic/user.{id} when something changes; poll as a fallback.
  const userId = document.querySelector('meta[name="user-id"]')?.content;
  if (userId) {
    ResrvIt.stomp(c => c.subscribe('/topic/user.' + userId, () => {
      refreshCounters();
      if (panel && !panel.hidden) load();
      document.dispatchEvent(new CustomEvent('resrvit:user-changed'));
    }));
    setInterval(refreshCounters, 60000);
  }
});

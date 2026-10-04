'use strict';
const T = 16;                 // tile size in source px
const ACT = ['sleep', 'eat', 'work', 'socialize', 'relax', 'shop', 'wander'];
const ACT_COLOR = {sleep: '#5B8DEF', eat: '#E8743B', work: '#8E8E96', socialize: '#E86FA8', relax: '#B58CF0', shop: '#5CC38A', wander: '#D9C7A0'};
const NEED_COLOR = {hunger: '#E8743B', energy: '#5B8DEF', social: '#E86FA8', fun: '#B58CF0', money: '#5CC38A'};
const SKIN = ['#F1C9A0', '#D9A474', '#B97A4E', '#8A5A3A'];
const CLOTH = ['#E85D5D', '#4C7BD9', '#F2C14E', '#59B36B', '#9B6BD6', '#E88A3D', '#3FB2B2', '#D9D9E2'];
const HAIR = ['#3A2A20', '#1E1E26', '#C9A25A', '#8A4A2A'];
const ROOF = {home: ['#A64B4B', '#8A3C3C'], restaurant: ['#C8963E', '#A87A2C'], shop: ['#3E9B7A', '#2E7A5E'], work: ['#6F7380', '#565A66']};

const $ = id => document.getElementById(id);
const canvas = $('town'), ctx = canvas.getContext('2d');
let mapData = null, base = null;
let snap = {running: false};
let people = [];              // {id, x, y, tx, ty, act, inside, moving, tone}
let selected = -1, hoverId = -1;
let zoom = 2, panX = 0, panY = 0, drag = null;
let viewDecisionIdx = 0, lastPerson = null;

// ---------- deterministic rng for decoration ----------
function mulberry(a) { return () => { a |= 0; a = a + 0x6D2B79F5 | 0; let t = Math.imul(a ^ a >>> 15, 1 | a); t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296; }; }

function buildBase(map) {
  const c = document.createElement('canvas');
  c.width = map.w * T; c.height = map.h * T;
  const g = c.getContext('2d');
  const R = mulberry(7);
  const rect = (x, y, w, h, f) => { g.fillStyle = f; g.fillRect(x, y, w, h); };
  const tile = (x, y, w, h, f) => rect(x * T, y * T, w * T, h * T, f);
  tile(0, 0, map.w, map.h, '#7DB35A');
  for (let i = 0; i < 260; i++) rect(Math.floor(R() * map.w) * T + Math.floor(R() * 12), Math.floor(R() * map.h) * T + Math.floor(R() * 12), 4, 4, '#72A852');
  tile(0, 22, map.w, 3, '#8E8E96'); tile(38, 0, 3, map.h, '#8E8E96');
  for (let x = 0; x < map.w; x += 3) rect(x * T + 4, 23 * T + 10, 14, 3, '#A8A8B0');
  for (let y = 0; y < map.h; y += 3) rect(39 * T + 7, y * T + 4, 3, 14, '#A8A8B0');
  [[6, 14, 30], [44, 14, 30], [6, 32, 30], [44, 32, 30]].forEach(p => tile(p[0], p[1], p[2], 1, '#D9C7A0'));
  tile(3, 3, 11, 8, '#4F9BD6'); tile(5, 5, 3, 2, '#6FB3E6');
  map.buildings.forEach(b => {
    const [c1, c2] = ROOF[b.type];
    tile(b.x, b.y, b.w, b.h, c2);
    rect(b.x * T + 4, b.y * T + 4, b.w * T - 8, b.h * T - 8, c1);
    rect(b.x * T + b.w * T / 2 - 5, (b.y + b.h) * T - 6, 10, 6, '#3A2A20');
  });
  for (let i = 0; i < 26; i++) { const x = (46 + Math.floor(R() * 30)) * T, y = (35 + Math.floor(R() * 10)) * T; rect(x, y, 22, 22, '#3F7432'); rect(x + 3, y + 3, 16, 16, '#4E8A3E'); }
  for (let i = 0; i < 18; i++) { const x = (2 + Math.floor(R() * 32)) * T, y = (35 + Math.floor(R() * 10)) * T; rect(x, y, 22, 22, '#3F7432'); rect(x + 3, y + 3, 16, 16, '#4E8A3E'); }
  return c;
}

function resize() {
  canvas.width = window.innerWidth; canvas.height = window.innerHeight;
  clampPan();
}
window.addEventListener('resize', resize);

function clampPan() {
  if (!base) return;
  const w = base.width * zoom, h = base.height * zoom, top = 48;
  const minX = Math.min(0, canvas.width - w), minY = Math.min(0, canvas.height - h);
  panX = w <= canvas.width ? (canvas.width - w) / 2 : Math.max(minX, Math.min(0, panX));
  panY = h <= canvas.height - top ? top + (canvas.height - top - h) / 2 : Math.max(minY, Math.min(top, panY));
}

function toScreen(tx, ty) { return [panX + tx * T * zoom, panY + ty * T * zoom]; }

// ---------- people ----------
function syncPeople(list) {
  if (people.length !== list.length) {
    people = list.map(p => ({id: p[0], x: p[1], y: p[2], tx: p[1], ty: p[2], act: p[3], inside: p[4] === 1, moving: false,
      skin: SKIN[p[0] % 4], cloth: CLOTH[(p[0] * 7) % 8], hair: HAIR[(p[0] * 3) % 4]}));
    return;
  }
  list.forEach((p, i) => { const q = people[i]; q.tx = p[1]; q.ty = p[2]; q.act = p[3]; q.inside = p[4] === 1; });
}

function nightAlpha(minute) {
  const h = (minute / 60) % 24;
  if (h >= 22 || h < 5) return 0.55;
  if (h >= 20) return 0.55 * (h - 20) / 2;
  if (h < 7) return 0.55 * (7 - h) / 2;
  return 0;
}

function drawPerson(q, t) {
  const [sx, sy] = toScreen(q.x, q.y);
  const k = zoom, fx = Math.round(sx - 6 * k), fy = Math.round(sy - 20 * k);
  const r = (x, y, w, h, f) => { ctx.fillStyle = f; ctx.fillRect(fx + x * k, fy + y * k, w * k, h * k); };
  ctx.strokeStyle = ACT_COLOR[ACT[q.act]]; ctx.lineWidth = 2;
  ctx.beginPath(); ctx.ellipse(sx, sy + k, 7 * k, 3 * k, 0, 0, Math.PI * 2); ctx.stroke();
  r(2, 0, 8, 8, q.skin); r(2, 0, 8, 3, q.hair);
  r(0, 8, 12, 8, q.cloth);
  const step = q.moving && Math.floor(t / 180 + q.id) % 2 === 0;
  r(2, 16, 3, step ? 3 : 4, '#3A3F55'); r(7, 16, 3, step ? 4 : 3, '#3A3F55');
}

function draw(t) {
  requestAnimationFrame(draw);
  ctx.imageSmoothingEnabled = false;
  ctx.fillStyle = '#14161F'; ctx.fillRect(0, 0, canvas.width, canvas.height);
  if (!base) return;
  ctx.drawImage(base, panX, panY, base.width * zoom, base.height * zoom);
  const na = snap.running ? nightAlpha(snap.minute) : 0;
  if (na > 0) { ctx.fillStyle = 'rgba(27,35,71,' + na + ')'; ctx.fillRect(panX, panY, base.width * zoom, base.height * zoom); }
  const order = people.filter(q => !q.inside).sort((a, b) => a.y - b.y);
  for (const q of people) { const dx = q.tx - q.x, dy = q.ty - q.y; q.moving = Math.abs(dx) + Math.abs(dy) > 0.02; q.x += dx * 0.35; q.y += dy * 0.35; }
  for (const q of order) drawPerson(q, t);
  if (selected >= 0 && people[selected]) {
    const q = people[selected];
    const [sx, sy] = toScreen(q.x, q.y);
    const pulse = performance.now() - pulseAt < 300 && !reduceMotion ? 3 : 0;
    ctx.strokeStyle = '#fff'; ctx.lineWidth = 2;
    ctx.beginPath(); ctx.arc(sx, sy - 10 * zoom, 17 * zoom + pulse, 0, Math.PI * 2); ctx.stroke();
  }
}
let pulseAt = 0;
const reduceMotion = window.matchMedia && matchMedia('(prefers-reduced-motion: reduce)').matches;

// ---------- picking ----------
function pick(mx, my) {
  let best = -1, bd = (14 * zoom) ** 2;
  for (const q of people) {
    if (q.inside) continue;
    const [sx, sy] = toScreen(q.x, q.y);
    const d = (sx - mx) ** 2 + (sy - 10 * zoom - my) ** 2;
    if (d < bd) { bd = d; best = q.id; }
  }
  return best;
}

canvas.addEventListener('mousedown', e => { drag = {x: e.clientX, y: e.clientY, px: panX, py: panY, moved: false}; });
window.addEventListener('mouseup', e => {
  if (drag && !drag.moved && e.target === canvas) select(pick(e.clientX, e.clientY));
  drag = null;
});
window.addEventListener('mousemove', e => {
  if (drag) {
    if (Math.abs(e.clientX - drag.x) + Math.abs(e.clientY - drag.y) > 4) drag.moved = true;
    if (drag.moved) { panX = drag.px + e.clientX - drag.x; panY = drag.py + e.clientY - drag.y; clampPan(); }
  }
  if (e.target !== canvas || !snap.running) { $('hover').hidden = true; return; }
  const id = pick(e.clientX, e.clientY);
  if (id < 0) { $('hover').hidden = true; hoverId = -1; return; }
  hoverId = id;
  const h = $('hover');
  h.hidden = false; h.style.left = e.clientX + 12 + 'px'; h.style.top = e.clientY + 12 + 'px';
  h.textContent = (names[id] || ('Person ' + id)) + ' · ' + ACT[people[id].act];
});
canvas.addEventListener('wheel', e => {
  e.preventDefault();
  const nz = Math.max(1, Math.min(3, zoom + (e.deltaY < 0 ? 1 : -1)));
  if (nz === zoom) return;
  const wx = (e.clientX - panX) / zoom, wy = (e.clientY - panY) / zoom;
  zoom = nz; panX = e.clientX - wx * zoom; panY = e.clientY - wy * zoom; clampPan();
}, {passive: false});

// ---------- inspector ----------
const names = {};
function select(id) {
  selected = id; viewDecisionIdx = 0; lastDecisionKey = '';
  $('inspector').hidden = id < 0;
  if (id >= 0) refreshInspector();
}
let lastDecisionKey = '';
async function refreshInspector() {
  if (selected < 0) return;
  try {
    const r = await fetch('/api/person/' + selected);
    if (!r.ok) return;
    const p = await r.json();
    names[p.id] = p.name; lastPerson = p;
    renderInspector(p);
  } catch (e) { /* ignore */ }
}
setInterval(refreshInspector, 400);

function renderInspector(p) {
  $('i-name').textContent = p.name;
  $('i-place').textContent = p.place + ' · ' + p.activity;
  $('i-needs').innerHTML = Object.entries(p.needs).map(([k, v]) =>
    `<div class="need"><span>${k[0].toUpperCase() + k.slice(1)}</span><div class="bar"><i style="width:${v}%;background:${NEED_COLOR[k]}"></i></div><em class="mono">${v}</em></div>`).join('');
  const d = viewDecisionIdx === 0 ? p.last : p.history[viewDecisionIdx - 1];
  const key = p.id + '|' + (d ? d.time + d.state : '');
  if (d && viewDecisionIdx === 0 && key !== lastDecisionKey) { lastDecisionKey = key; pulseAt = performance.now(); }
  if (!d) { $('i-decision').innerHTML = '<span class="muted">No decision yet.</span>'; }
  else {
    const esc = s => s.replace(/[&<>]/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;'}[c]));
    const ms = d.batchMs >= 1000 ? (d.batchMs / 1000).toFixed(1) + ' s' : d.batchMs + ' ms';
    $('i-decision').innerHTML =
      `<div class="muted">State sent to Laya (${d.time})</div><div class="state mono">${esc(d.state)}</div>
       <div class="muted" style="margin-bottom:6px">What should ${esc(p.name)} do next?</div>` +
      d.options.map((o, i) => `<div class="pr${o.label === d.chosen ? ' top1' : ''}"><span>${o.label}</span><div class="pb"><i style="width:${(o.p * 100).toFixed(0)}%;background:${ACT_COLOR[o.label]}"></i></div><em class="mono">${o.p.toFixed(2)}</em></div>`).join('') +
      `<div class="mono" style="margin-top:8px"><span class="amber">Decided in ${ms}</span> (batch of ${d.batchSize}) · answer confidence ${isNaN(d.confidence) || d.confidence == null ? '–' : d.confidence.toFixed(2)}</div>`;
  }
  $('i-history').innerHTML = p.history.map((h, i) => {
    const top = h.options.find(o => o.label === h.chosen);
    return `<span class="muted mono click" data-i="${i + 1}">${h.time}</span><span class="click" data-i="${i + 1}">${h.chosen}</span><span class="mono click" style="text-align:right" data-i="${i + 1}">${top ? top.p.toFixed(2) : ''}</span>`;
  }).join('');
}
$('i-history').addEventListener('click', e => {
  const i = e.target.dataset && e.target.dataset.i; if (!i || !lastPerson) return;
  viewDecisionIdx = viewDecisionIdx === +i ? 0 : +i; renderInspector(lastPerson);
});
$('i-close').addEventListener('click', () => select(-1));

// ---------- top bar & controls ----------
function post(path) { return fetch('/api/' + path, {method: 'POST'}); }
$('btn-pause').addEventListener('click', togglePause);
$('btn-step').addEventListener('click', () => post('step'));
document.querySelectorAll('.speed').forEach(b => b.addEventListener('click', () => post('speed/' + b.dataset.speed)));
$('btn-new').addEventListener('click', async () => {
  if (!confirm('Discard this town and start over?')) return;
  await post('stop'); select(-1); people = []; showStart();
});
function togglePause() { post(snap.paused ? 'resume' : 'pause'); }
window.addEventListener('keydown', e => {
  if (e.target.tagName === 'INPUT') return;
  if (e.code === 'Space' && snap.running) { e.preventDefault(); togglePause(); }
  if (e.key === 'Escape') select(-1);
});

function fmtMs(ms) { return ms < 0 ? '–' : ms >= 1000 ? (ms / 1000).toFixed(1) + ' s' : ms + ' ms'; }
function onSnapshot(s) {
  snap = s;
  if (!s.running) return;
  if (!$('start').hidden) { $('start').hidden = true; $('top').hidden = false; }
  syncPeople(s.p);
  $('btn-pause').textContent = s.paused ? 'Resume' : 'Pause';
  $('btn-step').disabled = !s.paused;
  document.querySelectorAll('.speed').forEach(b => b.classList.toggle('primary', +b.dataset.speed === s.speed));
  $('clock').textContent = s.paused ? s.clock + ' · Paused.' : s.clock;
  $('pop').textContent = s.n + ' people';
  $('m-dps').textContent = s.dps >= 10 ? Math.round(s.dps) : s.dps.toFixed(1);
  $('m-batch').textContent = s.batch || '–';
  $('m-ms').textContent = s.msg ? '…' : fmtMs(s.ms);
  $('msg').textContent = s.msg || '';
}

// ---------- start screen ----------
function showStart() { $('start').hidden = false; $('top').hidden = true; $('inspector').hidden = true; snap = {running: false}; checkLaya(); }
async function checkLaya() {
  const st = $('f-status'); st.className = 'mono muted'; st.textContent = 'Checking Laya…'; $('f-start').disabled = true;
  try {
    const r = await (await fetch('/api/check?url=' + encodeURIComponent($('f-url').value))).json();
    if (r.ok) { st.textContent = 'Laya is reachable.'; $('f-start').disabled = false; }
    else { st.className = 'mono bad'; st.textContent = 'Laya is not reachable at ' + $('f-url').value + '.'; }
  } catch (e) { st.className = 'mono bad'; st.textContent = 'Could not check Laya.'; }
}
$('f-check').addEventListener('click', checkLaya);
$('f-url').addEventListener('change', checkLaya);
$('f-pop').addEventListener('input', () => { $('f-pop-v').textContent = $('f-pop').value; });
$('f-start').addEventListener('click', async () => {
  $('f-start').disabled = true;
  const r = await fetch('/api/start', {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({
    population: +$('f-pop').value, decisionInterval: +$('f-int').value, seed: +$('f-seed').value, layaUrl: $('f-url').value})});
  if (!r.ok) { checkLaya(); return; }
  people = []; selected = -1;
  $('start').hidden = true; $('top').hidden = false;
  $('hint').textContent = 'Click a person to see how they decide.';
  setTimeout(() => { $('hint').textContent = ''; }, 8000);
});

// ---------- boot ----------
(async function boot() {
  resize();
  mapData = await (await fetch('/api/map')).json();
  base = buildBase(mapData); zoom = Math.max(1, Math.min(3, Math.floor(window.innerWidth / (mapData.w * T)) || 1));
  resize();
  const d = await (await fetch('/api/defaults')).json();
  $('f-url').value = d.layaUrl;
  new EventSource('/api/events').onmessage = e => onSnapshot(JSON.parse(e.data));
  requestAnimationFrame(draw);
  checkLaya();
})();

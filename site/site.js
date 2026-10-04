'use strict';
// Decorative pixel walkers along the bottom of the hero. They are an illustration only:
// the real decisions happen in the app, not on this page.
(function walkers() {
  const c = document.getElementById('walkers');
  if (!c) return;
  const ctx = c.getContext('2d');
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const SKIN = ['#F1C9A0', '#D9A474', '#B97A4E', '#8A5A3A'];
  const CLOTH = ['#E85D5D', '#4C7BD9', '#F2C14E', '#59B36B', '#9B6BD6', '#E88A3D', '#3FB2B2', '#D9D9E2'];
  const HAIR = ['#3A2A20', '#1E1E26', '#C9A25A', '#8A4A2A'];
  const RING = ['#5B8DEF', '#E8743B', '#8E8E96', '#E86FA8', '#B58CF0', '#5CC38A', '#D9C7A0'];
  let W = 0, H = 0, people = [];
  const S = 2; // pixel scale

  function resize() {
    W = c.width = Math.ceil(c.clientWidth / S);
    H = c.height = Math.ceil(c.clientHeight / S);
    const n = Math.max(8, Math.round(W / 22));
    people = Array.from({length: n}, (_, i) => ({
      x: Math.random() * W, lane: Math.random() < .5 ? H - 26 : H - 12,
      v: (Math.random() * .25 + .12) * (Math.random() < .5 ? 1 : -1),
      skin: SKIN[i % 4], cloth: CLOTH[(i * 5) % 8], hair: HAIR[(i * 3) % 4], ring: RING[i % 7], t: Math.random() * 100
    })).sort((a, b) => a.lane - b.lane);
  }

  function px(x, y, w, h, f) { ctx.fillStyle = f; ctx.fillRect(Math.round(x), Math.round(y), w, h); }

  function frame() {
    ctx.clearRect(0, 0, W, H);
    px(0, H - 16, W, 1, 'rgba(217,199,160,.18)');
    for (const p of people) {
      p.x += reduce ? 0 : p.v; p.t += reduce ? 0 : 1;
      if (p.x > W + 8) p.x = -8; if (p.x < -8) p.x = W + 8;
      const y = p.lane - 20, x = p.x - 6, step = Math.floor(p.t / 12) % 2 === 0;
      ctx.strokeStyle = p.ring; ctx.globalAlpha = .7;
      ctx.beginPath(); ctx.ellipse(p.x, p.lane + 1, 6, 2, 0, 0, Math.PI * 2); ctx.stroke(); ctx.globalAlpha = 1;
      px(x + 2, y, 8, 8, p.skin); px(x + 2, y, 8, 3, p.hair); px(x, y + 8, 12, 8, p.cloth);
      px(x + 2, y + 16, 3, step ? 3 : 4, '#3A3F55'); px(x + 7, y + 16, 3, step ? 4 : 3, '#3A3F55');
    }
    if (!reduce) requestAnimationFrame(frame);
  }
  addEventListener('resize', resize);
  resize(); frame();
})();

// Reveal sections and animate the speed bars when they scroll into view.
(function reveal() {
  const els = document.querySelectorAll('section:not(.hero) .wrap > *, .steps li, .card, .feature > *');
  els.forEach(e => e.classList.add('reveal'));
  const bars = document.querySelector('.bars');
  if (!('IntersectionObserver' in window)) { els.forEach(e => e.classList.add('in')); bars && bars.classList.add('on'); return; }
  const io = new IntersectionObserver(entries => entries.forEach(en => {
    if (!en.isIntersecting) return;
    en.target.classList.add('in');
    if (en.target === bars) bars.classList.add('on');
    io.unobserve(en.target);
  }), {threshold: .15});
  els.forEach(e => io.observe(e));
  if (bars) io.observe(bars);
})();

// Copy buttons for the run commands.
document.querySelectorAll('[data-copy]').forEach(btn => btn.addEventListener('click', async () => {
  const text = document.getElementById(btn.dataset.copy).textContent;
  try { await navigator.clipboard.writeText(text); btn.textContent = 'Copied'; }
  catch (e) { btn.textContent = 'Select & copy'; }
  setTimeout(() => { btn.textContent = 'Copy'; }, 1600);
}));

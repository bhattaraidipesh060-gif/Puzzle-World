/* =========================================================
   GearGame — gear_alignment: rotate gears to align
   mechanic: { gears: [{ angle, target }] }  (target is 0)
   Drag a gear to spin it; every gear must point straight up.
   ========================================================= */
(function () {
  'use strict';
  const W = (window.PuzzleWorld = window.PuzzleWorld || {});
  (W.games = W.games || {}).gear_alignment = function (ctx) {
    const m = ctx.mechanic || {};
    const gears = Array.isArray(m.gears) ? m.gears : [];
    const norm = (n) => ((n % 360) + 360) % 360;

    const { el } = ctx;
    ctx.root.innerHTML = '';
    const wrap = el('div', 'g-wrap g-col');
    wrap.appendChild(el('div', 'g-hint', '⚙️ Drag each gear so its marker points straight up.'));

    const rack = el('div', 'gears');
    const status = el('div', 'g-status', '');
    wrap.appendChild(rack);
    wrap.appendChild(status);

    const states = gears.map((g) => norm(g.angle || 0));
    const targets = gears.map((g) => norm(g.target || 0));
    const gearEls = [];
    const markEls = [];

    const refresh = () => {
      let aligned = 0;
      states.forEach((a, i) => {
        const ok = Math.abs(norm(a - targets[i])) < 0.5;
        if (ok) aligned++;
        gearEls[i].classList.toggle('is-aligned', ok);
        markEls[i].style.transform = `rotate(${states[i]}deg)`;
      });
      status.textContent = `${aligned} / ${states.length} aligned`;
      status.className = 'g-status' + (aligned === states.length ? ' ok' : '');
    };

    const checkWin = () => {
      if (states.every((a, i) => Math.abs(norm(a - targets[i])) < 0.5)) {
        ctx.sfx.win();
        ctx.win();
      }
    };

    const bindDrag = (node, i) => {
      let dragging = false, last = 0;
      node.addEventListener('pointerdown', (e) => {
        dragging = true;
        node.setPointerCapture(e.pointerId);
        const r = node.getBoundingClientRect();
        const cx = r.left + r.width / 2, cy = r.top + r.height / 2;
        last = Math.atan2(e.clientY - cy, e.clientX - cx) * 180 / Math.PI;
        ctx.sfx.click();
        e.preventDefault();
      });
      node.addEventListener('pointermove', (e) => {
        if (!dragging) return;
        const r = node.getBoundingClientRect();
        const cx = r.left + r.width / 2, cy = r.top + r.height / 2;
        const a = Math.atan2(e.clientY - cy, e.clientX - cx) * 180 / Math.PI;
        let d = a - last;
        if (d > 180) d -= 360; else if (d < -180) d += 360;
        last = a;
        states[i] = norm(states[i] + d);
        refresh();
      });
      const end = () => { if (!dragging) return; dragging = false; setTimeout(checkWin, 30); };
      node.addEventListener('pointerup', end);
      node.addEventListener('pointercancel', end);
    };

    states.forEach((_, i) => {
      const g = el('div', 'gear');
      const mark = el('div', 'gear__mark');
      const hub = el('div', 'gear__hub');
      g.append(mark, hub);
      gearEls.push(g);
      markEls.push(mark);
      bindDrag(g, i);
      rack.appendChild(g);
    });

    refresh();
    ctx.root.appendChild(wrap);
  };
})();

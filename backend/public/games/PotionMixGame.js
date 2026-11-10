/* =========================================================
   PotionMixGame — potion_mix: jug puzzle
   mechanic: { beakers:[{id,cap,vol}], target }
   Fill / empty / pour between beakers until one holds target.
   ========================================================= */
(function () {
  'use strict';
  const W = (window.PuzzleWorld = window.PuzzleWorld || {});
  (W.games = W.games || {}).potion_mix = function (ctx) {
    const m = ctx.mechanic || {};
    const beakers = Array.isArray(m.beakers) ? m.beakers.map((b) => ({ id: b.id, cap: b.cap, vol: b.vol || 0 })) : [];
    const target = m.target;

    const { el } = ctx;
    ctx.root.innerHTML = '';
    const wrap = el('div', 'g-wrap g-col');
    wrap.appendChild(el('div', 'g-hint', `⚗️ Pour between beakers until one holds exactly ${target} units.`));

    const row = el('div', 'beakers');
    const fillEls = [];
    const valEls = [];

    const renderBeakers = () => {
      row.innerHTML = '';
      fillEls.length = 0; valEls.length = 0;
      beakers.forEach((b) => {
        const col = el('div', 'beaker');
        const body = el('div', 'beaker__body');
        const fill = el('div', 'beaker__fill');
        body.appendChild(fill);
        const val = el('div', 'beaker__val', String(b.vol));
        const cap = el('div', 'beaker__cap', `cap ${b.cap}`);
        col.append(body, val, cap);
        fillEls.push(fill);
        valEls.push(val);
        row.appendChild(col);
      });
    };

    const sync = () => {
      beakers.forEach((b, i) => {
        const pct = Math.max(0, Math.min(100, (b.vol / b.cap) * 100));
        fillEls[i].style.height = pct + '%';
        valEls[i].textContent = String(b.vol);
      });
      if (beakers.some((b) => b.vol === target)) { ctx.sfx.win(); ctx.win(); }
    };

    const actions = el('div', 'g-row');
    const makeBtn = (label, fn, cls) => {
      const b = el('button', 'btn ' + (cls || 'btn--ghost') + ' g-btn', `<span class="btn__label">${label}</span>`);
      b.addEventListener('click', () => { fn(); ctx.sfx.click(); sync(); });
      return b;
    };

    // fill / empty per beaker
    const fe = el('div', 'g-row');
    beakers.forEach((b, i) => {
      fe.appendChild(makeBtn(`Fill ${i}`, () => { beakers[i].vol = beakers[i].cap; }, 'btn--ghost'));
      fe.appendChild(makeBtn(`Empty ${i}`, () => { beakers[i].vol = 0; }, 'btn--ghost'));
    });

    // pour between each ordered pair
    const pour = el('div', 'g-row');
    beakers.forEach((a, i) => {
      beakers.forEach((b, j) => {
        if (i === j) return;
        pour.appendChild(makeBtn(`${i}→${j}`, () => {
          const amt = Math.min(beakers[i].vol, beakers[j].cap - beakers[j].vol);
          beakers[i].vol -= amt; beakers[j].vol += amt;
        }, 'btn--primary'));
      });
    });

    renderBeakers();
    wrap.appendChild(row);
    wrap.appendChild(fe);
    wrap.appendChild(pour);
    sync();
    ctx.root.appendChild(wrap);
  };
})();

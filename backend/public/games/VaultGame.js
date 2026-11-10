/* =========================================================
   VaultGame — vault_dial: rotary safe combination
   mechanic: { digits, range, combination:[int], max_attempts }
   ========================================================= */
(function () {
  'use strict';
  const W = (window.PuzzleWorld = window.PuzzleWorld || {});
  (W.games = W.games || {}).vault_dial = function (ctx) {
    const m = ctx.mechanic || {};
    const digits = m.digits || 2;
    const combo = Array.isArray(m.combination) ? m.combination : [];
    const maxAttempts = m.max_attempts || 6;
    const state = new Array(digits).fill(0);
    let attemptsLeft = maxAttempts;

    const { el } = ctx;
    ctx.root.innerHTML = '';
    const wrap = el('div', 'g-wrap g-col');
    wrap.appendChild(el('div', 'g-hint', '🔒 Spin each ring until the numbers line up.'));

    const dial = el('div', 'dial');
    const digitsEls = [];
    for (let i = 0; i < digits; i++) {
      const ring = el('div', 'dial__ring');
      const up = el('button', 'dial__spin', '▲');
      const d = el('div', 'dial__digit', '0');
      const down = el('button', 'dial__spin', '▼');
      up.addEventListener('click', () => { state[i] = (state[i] + 1) % 10; d.textContent = state[i]; ctx.sfx.tick(); });
      down.addEventListener('click', () => { state[i] = (state[i] + 9) % 10; d.textContent = state[i]; ctx.sfx.tick(); });
      ring.append(up, d, down);
      dial.appendChild(ring);
      digitsEls.push(d);
    }
    wrap.appendChild(dial);

    const status = el('div', 'g-status', `Attempts left: ${attemptsLeft}`);
    wrap.appendChild(status);

    const tryBtn = el('button', 'btn btn--primary g-btn', '<span class="btn__label">🔓 Try combination</span>');
    tryBtn.addEventListener('click', () => {
      const ok = state.every((v, i) => v === ((combo[i] ?? 0) % 10));
      if (ok) { status.textContent = '✅ Vault opens!'; status.className = 'g-status ok'; ctx.sfx.win(); ctx.win(); return; }
      attemptsLeft--;
      if (attemptsLeft > 0) {
        status.textContent = `❌ Wrong — ${attemptsLeft} attempt${attemptsLeft === 1 ? '' : 's'} left`;
        status.className = 'g-status bad';
        ctx.sfx.error();
      } else {
        status.textContent = '❌ Locked out!';
        status.className = 'g-status bad';
        tryBtn.disabled = true;
        ctx.sfx.loss();
        setTimeout(() => ctx.lose(), 600);
      }
    });
    wrap.appendChild(tryBtn);
    ctx.root.appendChild(wrap);
  };
})();

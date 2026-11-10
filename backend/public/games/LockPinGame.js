/* =========================================================
   LockPinGame — lock_pin: pin-and-tumbler
   mechanic: { pins:[depths], max_attempts }  depths are 1..4
   Click a pin to cycle its depth; test the lock to check.
   ========================================================= */
(function () {
  'use strict';
  const W = (window.PuzzleWorld = window.PuzzleWorld || {});
  (W.games = W.games || {}).lock_pin = function (ctx) {
    const m = ctx.mechanic || {};
    const pins = Array.isArray(m.pins) ? m.pins : [];
    const maxAttempts = m.max_attempts || 5;
    const DEPTHS = 4;
    const state = new Array(pins.length).fill(1);
    let attemptsLeft = maxAttempts;

    const { el } = ctx;
    ctx.root.innerHTML = '';
    const wrap = el('div', 'g-wrap g-col');
    wrap.appendChild(el('div', 'g-hint', '🔑 Click each pin (left to right) to set its depth, then test the lock.'));

    const rack = el('div', 'pins');
    const segs = [];
    pins.forEach((_, i) => {
      const col = el('div', 'pin');
      const stack = el('div', 'pin__column');
      const row = [];
      for (let d = 0; d < DEPTHS; d++) {
        const seg = el('div', 'pin__seg');
        row.push(seg);
        stack.appendChild(seg);
      }
      stack.addEventListener('click', () => {
        state[i] = (state[i] % DEPTHS) + 1;
        ctx.sfx.tick();
        sync();
      });
      col.append(stack, el('div', 'pin__label', String(i + 1)));
      rack.appendChild(col);
      segs.push(row);
    });
    wrap.appendChild(rack);

    const status = el('div', 'g-status', `Attempts left: ${attemptsLeft}`);
    wrap.appendChild(status);

    const testBtn = el('button', 'btn btn--primary g-btn', '<span class="btn__label">🔓 Test the lock</span>');
    testBtn.addEventListener('click', () => {
      const correct = pins.reduce((n, p, i) => n + (state[i] === p ? 1 : 0), 0);
      if (correct === pins.length) {
        status.textContent = '✅ The lock springs open!';
        status.className = 'g-status ok';
        ctx.sfx.win(); ctx.win();
        return;
      }
      attemptsLeft--;
      if (attemptsLeft > 0) {
        status.textContent = `❌ ${correct} / ${pins.length} pins set — ${attemptsLeft} attempt${attemptsLeft === 1 ? '' : 's'} left`;
        status.className = 'g-status bad';
        ctx.sfx.error();
      } else {
        status.textContent = '❌ The lock jammed!';
        status.className = 'g-status bad';
        testBtn.disabled = true;
        ctx.sfx.loss();
        setTimeout(() => ctx.lose(), 600);
      }
    });
    wrap.appendChild(testBtn);

    const sync = () => {
      pins.forEach((_, i) => {
        for (let d = 0; d < DEPTHS; d++) {
          segs[i][d].classList.toggle('on', d < state[i]);
        }
      });
    };
    sync();
    ctx.root.appendChild(wrap);
  };
})();

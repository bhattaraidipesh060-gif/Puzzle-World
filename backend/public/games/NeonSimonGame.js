/* =========================================================
   NeonSimonGame — neon_simon: replay the flashing sequence
   mechanic: { colors, sequence:[ints], speed_ms }
   ========================================================= */
(function () {
  'use strict';
  const W = (window.PuzzleWorld = window.PuzzleWorld || {});
  (W.games = W.games || {}).neon_simon = function (ctx) {
    const m = ctx.mechanic || {};
    const colors = m.colors || 4;
    const seq = Array.isArray(m.sequence) ? m.sequence : [];
    const speed = m.speed_ms || 900;
    const litMs = Math.max(180, Math.round(speed * 0.55));
    const gapMs = Math.max(120, Math.round(speed * 0.45));

    const { el } = ctx;
    ctx.root.innerHTML = '';
    const wrap = el('div', 'g-wrap g-col');
    wrap.appendChild(el('div', 'g-hint', '🕹️ Watch the colours flash, then replay the sequence.'));

    const simon = el('div', 'simon');
    const pads = [];
    for (let i = 0; i < colors; i++) {
      const p = el('button', `simon__pad simon__pad--${i}`);
      p.setAttribute('aria-label', 'Colour ' + (i + 1));
      pads.push(p);
      simon.appendChild(p);
    }
    wrap.appendChild(simon);

    const status = el('div', 'g-status', 'Watch…');
    const replayBtn = el('button', 'btn btn--ghost g-btn', '<span class="btn__label">🔁 Replay</span>');
    wrap.appendChild(status);
    wrap.appendChild(replayBtn);

    let progress = 0;
    let accepting = false;
    let timers = [];

    const setPad = (i, on) => {
      pads[i].classList.toggle('on', on);
      if (on) ctx.sfx.tone ? ctx.sfx.tone(400 + i * 120, 0.12, 'sine', 0.18) : ctx.sfx.tick();
    };

    const clearTimers = () => { timers.forEach(clearTimeout); timers = []; };

    const playSequence = () => {
      clearTimers();
      accepting = false;
      progress = 0;
      status.textContent = 'Watch…';
      status.className = 'g-status';
      seq.forEach((idx, i) => {
        timers.push(setTimeout(() => setPad(idx, true), 350 + i * (litMs + gapMs)));
        timers.push(setTimeout(() => setPad(idx, false), 350 + i * (litMs + gapMs) + litMs));
      });
      timers.push(setTimeout(() => { accepting = true; status.textContent = 'Your turn'; }, 350 + seq.length * (litMs + gapMs)));
    };

    pads.forEach((p, i) => {
      p.addEventListener('click', () => {
        if (!accepting) return;
        setPad(i, true);
        setTimeout(() => setPad(i, false), 180);
        if (seq[progress] === i) {
          progress++;
          if (progress === seq.length) {
            accepting = false;
            status.textContent = '✅ Beat the machine!';
            status.className = 'g-status ok';
            ctx.sfx.win(); ctx.win();
          }
        } else {
          accepting = false;
          status.textContent = '❌ Wrong colour — watch again';
          status.className = 'g-status bad';
          ctx.sfx.error();
          setTimeout(playSequence, 500);
        }
      });
    });

    replayBtn.addEventListener('click', () => { ctx.sfx.click(); playSequence(); });
    playSequence();
    ctx.root.appendChild(wrap);
  };
})();

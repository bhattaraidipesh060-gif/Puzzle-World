/* =========================================================
   TerminalHackGame — terminal_hack
   mode "bin_to_hex": { input (binary), answer (hex) }
   mode "memory_path": { nodes:[ints] }  replay node lights
   ========================================================= */
(function () {
  'use strict';
  const W = (window.PuzzleWorld = window.PuzzleWorld || {});
  (W.games = W.games || {}).terminal_hack = function (ctx) {
    const m = ctx.mechanic || {};
    if (m.mode === 'memory_path') return renderMemory(ctx, m);
    return renderBinToHex(ctx, m);
  };

  function renderBinToHex(ctx, m) {
    const input = m.input || '';
    const answer = m.answer || '';
    const { el } = ctx;
    ctx.root.innerHTML = '';
    const wrap = el('div', 'g-wrap g-col');
    wrap.appendChild(el('div', 'g-hint', '💻 Convert the binary stream into hexadecimal to pass the firewall.'));

    // display binary grouped into 4-bit nibbles
    const bin = el('div', 'term__bin');
    bin.innerHTML = input.replace(/(.{4})/g, '<b>$1</b> ');
    wrap.appendChild(bin);

    const row = el('div', 'g-row');
    const inputEl = el('input', 'term__input');
    inputEl.placeholder = 'hex…';
    inputEl.spellcheck = false;
    inputEl.autocomplete = 'off';
    const submit = el('button', 'btn btn--primary g-btn', '<span class="btn__label">Submit</span>');
    row.append(inputEl, submit);
    wrap.appendChild(row);

    const status = el('div', 'g-status', '');
    wrap.appendChild(status);

    const norm = (s) => String(s).replace(/\s+/g, '').toUpperCase();
    const check = () => {
      if (norm(inputEl.value) === norm(answer)) {
        status.textContent = '✅ Access granted';
        status.className = 'g-status ok';
        ctx.sfx.win(); ctx.win();
      } else {
        status.textContent = '❌ Rejected — try again';
        status.className = 'g-status bad';
        ctx.sfx.error();
      }
    };
    submit.addEventListener('click', check);
    inputEl.addEventListener('keydown', (e) => { if (e.key === 'Enter') check(); });

    ctx.root.appendChild(wrap);
    setTimeout(() => inputEl.focus(), 50);
  }

  function renderMemory(ctx, m) {
    const nodes = Array.isArray(m.nodes) ? m.nodes : [];
    const nodeCount = 9; // generator uses 0..8
    const { el } = ctx;
    ctx.root.innerHTML = '';
    const wrap = el('div', 'g-wrap g-col');
    wrap.appendChild(el('div', 'g-hint', '💻 Watch the node path light up, then replay it in order.'));

    const grid = el('div', 'nodes');
    const nodeEls = [];
    for (let i = 0; i < nodeCount; i++) {
      const n = el('button', 'node', String(i));
      nodeEls.push(n);
      grid.appendChild(n);
    }
    wrap.appendChild(grid);

    const status = el('div', 'g-status', 'Watch…');
    const replayBtn = el('button', 'btn btn--ghost g-btn', '<span class="btn__label">🔁 Replay path</span>');
    wrap.appendChild(status);
    wrap.appendChild(replayBtn);

    let progress = 0;
    let accepting = false;

    const light = (idx, on) => nodeEls[idx].classList.toggle('on', on);

    const playSequence = () => {
      accepting = false;
      progress = 0;
      status.textContent = 'Watch…';
      status.className = 'g-status';
      nodes.forEach((idx, i) => {
        setTimeout(() => { light(idx, true); ctx.sfx.tick(); }, 300 + i * 480);
        setTimeout(() => light(idx, false), 300 + i * 480 + 320);
      });
      setTimeout(() => { accepting = true; status.textContent = 'Your turn — click the nodes'; }, 300 + nodes.length * 480);
    };

    nodeEls.forEach((n, idx) => {
      n.addEventListener('click', () => {
        if (!accepting) return;
        light(idx, true);
        ctx.sfx.click();
        setTimeout(() => light(idx, false), 160);
        if (nodes[progress] === idx) {
          progress++;
          if (progress === nodes.length) {
            status.textContent = '✅ Access granted';
            status.className = 'g-status ok';
            accepting = false;
            ctx.sfx.win(); ctx.win();
          }
        } else {
          status.textContent = '❌ Wrong node — restarting';
          status.className = 'g-status bad';
          ctx.sfx.error();
          accepting = false;
          playSequence();
        }
      });
    });

    replayBtn.addEventListener('click', () => { ctx.sfx.click(); playSequence(); });
    playSequence();
    ctx.root.appendChild(wrap);
  }
})();

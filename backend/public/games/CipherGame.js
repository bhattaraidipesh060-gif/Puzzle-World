/* =========================================================
   CipherGame — cipher_wheel: caesar rotor
   mechanic: { cipher_type, cipher_text, shift, answer, hint }
   Rotate the wheel (shift back) until the decode == answer.
   ========================================================= */
(function () {
  'use strict';
  const W = (window.PuzzleWorld = window.PuzzleWorld || {});
  (W.games = W.games || {}).cipher_wheel = function (ctx) {
    const m = ctx.mechanic || {};
    const cipherText = m.cipher_text || '';
    const answer = m.answer || '';
    const hint = m.hint || 'Rotate the wheel until the letters snap into place.';

    const shiftChar = (c, delta) => {
      const u = c.toUpperCase();
      if (u >= 'A' && u <= 'Z') {
        const base = c === u ? 65 : 97;
        return String.fromCharCode(base + ((((c.charCodeAt(0) - base) + delta) % 26) + 26) % 26);
      }
      return c;
    };
    const decode = (offset) => cipherText.split('').map((c) => shiftChar(c, -offset)).join('');

    const { el } = ctx;
    ctx.root.innerHTML = '';
    const wrap = el('div', 'g-wrap g-col');
    wrap.appendChild(el('div', 'g-hint', '🔐 ' + hint));

    const cipher = el('div', 'cipher');
    cipher.appendChild(el('div', 'g-hint', 'Cipher text'));
    cipher.appendChild(el('div', 'cipher__text', cipherText));
    wrap.appendChild(cipher);

    let offset = 0;
    const rotor = el('div', 'rotor');
    const left = el('button', 'rotor__btn', '◀');
    const offEl = el('div', 'rotor__off', '0');
    const right = el('button', 'rotor__btn', '▶');
    const decodedEl = el('div', 'decoded', decode(0));

    const apply = () => {
      offEl.textContent = offset;
      decodedEl.textContent = decode(offset);
    };
    const check = () => {
      const norm = (s) => String(s).replace(/\s+/g, '').toUpperCase();
      if (norm(decode(offset)) === norm(answer)) {
        decodedEl.style.color = 'var(--emerald)';
        ctx.sfx.win();
        ctx.win();
      }
    };
    left.addEventListener('click', () => { offset = (offset + 25) % 26; apply(); ctx.sfx.tick(); check(); });
    right.addEventListener('click', () => { offset = (offset + 1) % 26; apply(); ctx.sfx.tick(); check(); });

    rotor.append(left, offEl, right);
    wrap.appendChild(rotor);

    const decodedWrap = el('div', 'g-col');
    decodedWrap.appendChild(el('div', 'g-hint', 'Decoded'));
    decodedWrap.appendChild(decodedEl);
    wrap.appendChild(decodedWrap);

    ctx.root.appendChild(wrap);
  };
})();

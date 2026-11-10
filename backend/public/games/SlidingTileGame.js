/* =========================================================
   SlidingTileGame — sliding_tile: 15-puzzle
   mechanic: { size, tiles:[ints] }  0 = blank, order 1..N
   ========================================================= */
(function () {
  'use strict';
  const W = (window.PuzzleWorld = window.PuzzleWorld || {});
  (W.games = W.games || {}).sliding_tile = function (ctx) {
    const m = ctx.mechanic || {};
    const size = m.size || 3;
    let tiles = Array.isArray(m.tiles) && m.tiles.length === size * size
      ? m.tiles.slice()
      : [...Array(size * size - 1).keys()].map((x) => x + 1).concat(0);

    const { el } = ctx;
    ctx.root.innerHTML = '';
    const wrap = el('div', 'g-wrap g-col');
    wrap.appendChild(el('div', 'g-hint', '📚 Slide tiles into the blank space to order them 1…' + (size * size - 1) + '.'));

    const board = el('div', 'board');
    board.style.gridTemplateColumns = `repeat(${size}, 1fr)`;
    wrap.appendChild(board);
    wrap.appendChild(el('div', 'g-status', 'Moves: 0'));

    let moves = 0;
    const solved = () => tiles.every((t, i) => t === (i === tiles.length - 1 ? 0 : i + 1));

    const render = () => {
      board.innerHTML = '';
      tiles.forEach((t, i) => {
        const cell = el('button', 'tile' + (t === 0 ? ' tile--blank' : ''), t === 0 ? '' : String(t));
        cell.addEventListener('click', () => {
          if (t === 0) return;
          const blank = tiles.indexOf(0);
          const r = (x) => [Math.floor(x / size), x % size];
          const [br, bc] = r(blank), [tr, tc] = r(i);
          const adjacent = (Math.abs(br - tr) + Math.abs(bc - tc)) === 1;
          if (!adjacent) return;
          tiles[blank] = t; tiles[i] = 0;
          moves++;
          wrap.querySelector('.g-status').textContent = 'Moves: ' + moves;
          ctx.sfx.move();
          render();
          if (solved()) { ctx.sfx.win(); ctx.win(); }
        });
        board.appendChild(cell);
      });
    };
    render();
    ctx.root.appendChild(wrap);
  };
})();

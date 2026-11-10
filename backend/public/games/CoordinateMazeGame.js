/* =========================================================
   CoordinateMazeGame — coordinate_maze: navigate S -> E
   mechanic: { grid:[strings] }  # wall, . open, S start, E end
   ========================================================= */
(function () {
  'use strict';
  const W = (window.PuzzleWorld = window.PuzzleWorld || {});
  (W.games = W.games || {}).coordinate_maze = function (ctx) {
    const m = ctx.mechanic || {};
    const grid = (Array.isArray(m.grid) ? m.grid : []).map((r) => String(r));
    if (!grid.length) { ctx.root.innerHTML = ''; ctx.root.appendChild(ctx.el('div', 'g-hint', 'No maze data.')); return; }

    const rows = grid.length;
    const cols = Math.max(...grid.map((r) => r.length));
    let pr = 0, pc = 0;
    grid.forEach((row, r) => { const c = row.indexOf('S'); if (c >= 0) { pr = r; pc = c; } });

    const { el } = ctx;
    ctx.root.innerHTML = '';
    const wrap = el('div', 'g-wrap g-col');
    wrap.appendChild(el('div', 'g-hint', '🧭 Move from S to E using the compass.'));

    const maze = el('div', 'maze');
    maze.style.gridTemplateColumns = `repeat(${cols}, 32px)`;
    wrap.appendChild(maze);

    const render = () => {
      maze.innerHTML = '';
      for (let r = 0; r < rows; r++) {
        for (let c = 0; c < cols; c++) {
          const ch = grid[r][c] || '#';
          const cell = el('div', 'maze__cell');
          if (r === pr && c === pc) { cell.classList.add('maze__cell--player'); cell.textContent = '●'; }
          else if (ch === '#') cell.classList.add('maze__cell--wall');
          else if (ch === 'E') { cell.classList.add('maze__cell--end'); cell.textContent = '★'; }
          else cell.classList.add('maze__cell--open');
          maze.appendChild(cell);
        }
      }
    };

    let won = false;
    const move = (dr, dc) => {
      if (won) return;
      const nr = pr + dr, nc = pc + dc;
      if (nr < 0 || nc < 0 || nr >= rows || nc >= cols) return;
      const ch = grid[nr][nc] || '#';
      if (ch === '#') return;
      pr = nr; pc = nc;
      ctx.sfx.move();
      if (ch === 'E') { won = true; render(); ctx.sfx.win(); ctx.win(); return; }
      render();
    };

    // dpad
    const dpad = el('div', 'dpad');
    const mk = (cls, label, dr, dc) => {
      const b = el('button', cls, label);
      b.addEventListener('click', () => move(dr, dc));
      dpad.appendChild(b);
    };
    mk('dpad__up', '↑', -1, 0);
    mk('dpad__left', '←', 0, -1);
    mk('dpad__right', '→', 0, 1);
    mk('dpad__down', '↓', 1, 0);
    wrap.appendChild(dpad);

    const onKey = (e) => {
      if (!ctx.root.isConnected) { window.removeEventListener('keydown', onKey); return; }
      const map = { ArrowUp: [-1, 0], ArrowDown: [1, 0], ArrowLeft: [0, -1], ArrowRight: [0, 1] };
      const d = map[e.key];
      if (d) { e.preventDefault(); move(d[0], d[1]); }
    };
    window.addEventListener('keydown', onKey);

    render();
    ctx.root.appendChild(wrap);
  };
})();

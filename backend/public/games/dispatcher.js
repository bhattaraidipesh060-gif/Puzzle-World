/* =========================================================
   PuzzleWorld — minigame dispatcher
   Maps puzzle_type -> render(ctx). Each game module registers
   itself on window.PuzzleWorld.games from its own file.
   ctx shape: { mechanic, level, room, root, el, esc, $, $$,
                sfx, toast, banner, win, lose }
   ========================================================= */
(function () {
  'use strict';
  const W = (window.PuzzleWorld = window.PuzzleWorld || {});
  W.games = W.games || {};

  W.dispatch = function (puzzleType) {
    return W.games[puzzleType] || null;
  };
})();

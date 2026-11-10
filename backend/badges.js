// badges.js — Rank badge tier system.
// Each badge is evaluated on demand from the player's stored progress
// (player_progress) and stats (players). Unlocks are therefore always consistent
// with the live data — there is no denormalized "unlocked" flag to drift out of sync.

const { getDb } = require('./database');

const BADGES = [
  // Global completion tiers
  { id: 'novice_solver', name: 'Novice Solver', icon: '🧩', metric: 'total', target: 1, description: 'Solve your first puzzle' },
  { id: 'puzzle_apprentice', name: 'Puzzle Apprentice', icon: '🎯', metric: 'total', target: 10, description: 'Solve 10 puzzles' },
  { id: 'puzzle_veteran', name: 'Puzzle Veteran', icon: '🧠', metric: 'total', target: 50, description: 'Solve 50 puzzles' },
  { id: 'escape_master', name: 'Escape Master', icon: '👑', metric: 'total', target: 90, description: 'Clear all 9 rooms (90 puzzles)' },

  // Per-room badges (5 levels each)
  { id: 'vault_cracker', name: 'Vault Cracker', icon: '🏦', metric: 'room', room: 'room_01_vault', target: 5, description: 'Complete 5 levels in The First Vault' },
  { id: 'master_cryptographer', name: 'Master Cryptographer', icon: '🔐', metric: 'room', room: 'room_02_cipher_lab', target: 5, description: 'Complete 5 levels in The Cipher Laboratory' },
  { id: 'clockwork_genius', name: 'Clockwork Genius', icon: '⚙️', metric: 'room', room: 'room_03_observatory', target: 5, description: 'Complete 5 levels in The Clockwork Observatory' },
  { id: 'arcane_scholar', name: 'Arcane Scholar', icon: '📚', metric: 'room', room: 'room_04_study', target: 5, description: "Complete 5 levels in The Puzzle Master's Study" },
  { id: 'code_breaker', name: 'Code Breaker', icon: '💻', metric: 'room', room: 'room_05_crypto', target: 5, description: "Complete 5 levels in The Cryptographer's Cache" },
  { id: 'master_alchemist', name: 'Master Alchemist', icon: '⚗️', metric: 'room', room: 'room_06_alchemy', target: 5, description: "Complete 5 levels in The Alchemist's Laboratory" },
  { id: 'master_cartographer', name: 'Master Cartographer', icon: '🗺️', metric: 'room', room: 'room_07_cartography', target: 5, description: "Complete 5 levels in The Cartographer's Vault" },
  { id: 'master_locksmith', name: 'Master Locksmith', icon: '🔑', metric: 'room', room: 'room_08_locksmith', target: 5, description: "Complete 5 levels in The Locksmith's Forge" },
  { id: 'arcade_legend', name: 'Arcade Legend', icon: '🕹️', metric: 'room', room: 'room_09_arcade', target: 5, description: 'Complete 5 levels in The Neon Arcade' },

  // Score & streak
  { id: 'high_scorer', name: 'High Scorer', icon: '🏆', metric: 'score', target: 500, description: 'Reach 500 points' },
  { id: 'streak_runner', name: 'Streak Runner', icon: '🔥', metric: 'best_streak', target: 5, description: 'Reach a 5-win streak' },
];

function currentFor(badge, ctx) {
  switch (badge.metric) {
    case 'room': return ctx.perRoom[badge.room] || 0;
    case 'score': return ctx.score;
    case 'best_streak': return ctx.bestStreak;
    default: return ctx.total;
  }
}

// Compute the full profile (stats + badge list) for a user. Returns a profile
// object even when the player has no row yet (all badges locked, zero stats) so
// brand-new users render gracefully instead of 404ing.
function evaluate(userId) {
  const db = getDb();
  const player = db.prepare('SELECT * FROM players WHERE user_id = ?').get(userId);

  const total = player
    ? db.prepare('SELECT COUNT(*) AS n FROM player_progress WHERE user_id = ? AND is_solved = 1').get(userId).n
    : 0;

  const perRoom = {};
  if (player) {
    db.prepare(`
      SELECT l.room_id AS room_id, COUNT(*) AS n
      FROM player_progress pp JOIN levels l ON l.level_id = pp.level_id
      WHERE pp.user_id = ? AND pp.is_solved = 1
      GROUP BY l.room_id
    `).all(userId).forEach(r => { perRoom[r.room_id] = r.n; });
  }

  const ctx = {
    total,
    perRoom,
    score: player ? player.score : 0,
    bestStreak: player ? player.best_streak : 0,
  };

  const badges = BADGES.map(b => {
    const current = currentFor(b, ctx);
    return {
      id: b.id, name: b.name, icon: b.icon, description: b.description,
      unlocked: current >= b.target,
      progress: { current, target: b.target },
    };
  });

  return {
    user_id: userId,
    username: player ? player.username : userId,
    stats: {
      solved_count: total,
      total_score: ctx.score,
      streak: player ? player.streak : 0,
      best_streak: ctx.bestStreak,
      hints_used: player ? player.hints_used : 0,
      total_attempts: player ? player.total_attempts : 0,
    },
    badges,
    unlocked_count: badges.filter(b => b.unlocked).length,
    total_badges: badges.length,
  };
}

module.exports = { evaluate, BADGES };

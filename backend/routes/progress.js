const express = require('express');
const router = express.Router();
const wrap = require('./wrap');
const { getDb } = require('../database');

// POST /api/progress — sync player progress + stats
router.post('/', wrap((req, res) => {
  const db = getDb();
  const { user_id, username, solved_levels, score, streak, hints_used, total_attempts, current_room } = req.body;
  if (!user_id) return res.status(400).json({ error: 'user_id required' });

  // 1. Ensure the player row exists (fresh insert defaults username)
  db.prepare('INSERT OR IGNORE INTO players (user_id, username) VALUES (?, ?)')
    .run(user_id, username || 'Player');

  // 2. Apply only the fields that were actually sent (preserve others)
  const sets = ['last_sync = datetime(\'now\')'];
  const args = [];
  if (username) { sets.push('username = ?'); args.push(username); }
  if (score !== undefined && score !== null) { sets.push('score = ?'); args.push(score); }
  if (streak !== undefined && streak !== null) { sets.push('streak = ?'); args.push(streak); sets.push('best_streak = MAX(best_streak, ?)'); args.push(streak); }
  if (hints_used !== undefined && hints_used !== null) { sets.push('hints_used = ?'); args.push(hints_used); }
  if (total_attempts !== undefined && total_attempts !== null) { sets.push('total_attempts = ?'); args.push(total_attempts); }
  if (current_room !== undefined && current_room !== null) { sets.push('current_room = ?'); args.push(current_room); }
  args.push(user_id);
  db.prepare(`UPDATE players SET ${sets.join(', ')} WHERE user_id = ?`).run(...args);

  // 3. Merge solved levels (plain object only, reject arrays)
  if (solved_levels && typeof solved_levels === 'object' && !Array.isArray(solved_levels)) {
    const upsert = db.prepare(`INSERT INTO player_progress (user_id, level_id, is_solved, solved_at)
      VALUES (?, ?, 1, datetime('now')) ON CONFLICT(user_id, level_id) DO UPDATE SET is_solved = 1, solved_at = COALESCE(excluded.solved_at, solved_at)`);
    db.transaction(() => { for (const [lid] of Object.entries(solved_levels)) upsert.run(user_id, lid); })();
  }

  // 4. Read back the canonical state
  const p = db.prepare('SELECT * FROM players WHERE user_id = ?').get(user_id);
  const rows = db.prepare('SELECT level_id, is_solved FROM player_progress WHERE user_id = ?').all(user_id);
  const solved = {}; rows.forEach(r => { solved[r.level_id] = !!r.is_solved; });

  res.json({
    user_id, username: p.username, solved_levels: solved,
    score: p.score, streak: p.streak, best_streak: p.best_streak,
    hints_used: p.hints_used, total_attempts: p.total_attempts,
    current_room: p.current_room || null,
    last_sync: new Date().toISOString()
  });
}));

// GET /api/progress/:userId
router.get('/:userId', wrap((req, res) => {
  const db = getDb();
  const p = db.prepare('SELECT * FROM players WHERE user_id = ?').get(req.params.userId);
  if (!p) return res.status(404).json({ error: 'Player not found' });
  const rows = db.prepare('SELECT level_id, is_solved FROM player_progress WHERE user_id = ?').all(req.params.userId);
  const solved = {}; rows.forEach(r => { solved[r.level_id] = !!r.is_solved; });
  res.json({
    user_id: p.user_id, username: p.username, solved_levels: solved,
    score: p.score, streak: p.streak, best_streak: p.best_streak,
    hints_used: p.hints_used, total_attempts: p.total_attempts,
    current_room: p.current_room || null, last_sync: p.last_sync
  });
}));

// GET /api/progress (leaderboard)
router.get('/', wrap((req, res) => {
  const db = getDb();
  const lb = db.prepare(`SELECT p.user_id, p.username, COUNT(pp.level_id) AS solved_count, p.score, p.last_sync
    FROM players p LEFT JOIN player_progress pp ON pp.user_id = p.user_id AND pp.is_solved = 1
    GROUP BY p.user_id ORDER BY solved_count DESC, p.score DESC LIMIT 50`).all();
  res.json({ leaderboard: lb });
}));

// DELETE /api/progress/:userId — reset a player (remove progress + player row)
router.delete('/:userId', wrap((req, res) => {
  const db = getDb();
  const p = db.prepare('SELECT user_id FROM players WHERE user_id = ?').get(req.params.userId);
  if (!p) return res.status(404).json({ error: 'Player not found' });

  db.transaction(() => {
    db.prepare('DELETE FROM player_progress WHERE user_id = ?').run(req.params.userId);
    db.prepare('DELETE FROM players WHERE user_id = ?').run(req.params.userId);
  })();

  res.json({ user_id: req.params.userId, deleted: true });
}));

module.exports = router;
const express = require('express');
const router = express.Router();
const wrap = require('./wrap');
const { getDb } = require('../database');

function dailySeed(dateStr) { let h=0; for(let i=0;i<dateStr.length;i++)h=((h<<5)-h+dateStr.charCodeAt(i))|0; return Math.abs(h); }

function todayStr() { return new Date().toISOString().slice(0,10).replace(/-/g,''); }

// Resolve the challenge for a date, generating + persisting it on first use.
function getOrCreateChallenge(db, date) {
  const seed = dailySeed(date);
  let c = db.prepare('SELECT * FROM daily_challenges WHERE challenge_date = ?').get(date);
  if (!c) {
    const levels = db.prepare(`
      SELECT level_id, room_id FROM levels
      WHERE json_extract(mechanic, '$.mechanic_type') = 'cipher_wheel'
         OR (json_extract(mechanic, '$.mechanic_type') = 'terminal_hack' AND json_extract(mechanic, '$.mode') = 'bin_to_hex')
      ORDER BY sort_order
    `).all();
    if (!levels.length) return { error: 'No gradable levels' };
    const pick = levels[seed % levels.length];
    db.prepare('INSERT INTO daily_challenges VALUES (?,?,?,?)').run(date, seed, pick.room_id, pick.level_id);
    c = { challenge_date: date, seed, room_id: pick.room_id, level_id: pick.level_id };
  }
  return { seed: c.seed, level_id: c.level_id, room_id: c.room_id };
}

function challengeResponse(db, c, date) {
  const l = db.prepare('SELECT l.*, r.room_name, r.theme FROM levels l JOIN rooms r ON r.room_id = l.room_id WHERE l.level_id = ?').get(c.level_id);
  if (!l) return null;
  return { challenge_date: date, seed: c.seed, level: { level_id: l.level_id, room_id: l.room_id, room_name: l.room_name, theme: l.theme, title: l.title, difficulty: l.difficulty, mechanic: JSON.parse(l.mechanic), hints: JSON.parse(l.hints || '[]') } };
}

// GET /api/daily-challenge
router.get('/', wrap((req, res) => {
  const db = getDb();
  const date = todayStr();
  const c = getOrCreateChallenge(db, date);
  if (c.error) return res.status(404).json({ error: c.error });
  const out = challengeResponse(db, c, date);
  if (!out) return res.status(404).json({ error: 'Level not found' });
  res.json(out);
}));

// GET /api/daily-challenge/:date
router.get('/:date', wrap((req, res) => {
  const db = getDb();
  const c = getOrCreateChallenge(db, req.params.date);
  if (c.error) return res.status(404).json({ error: c.error });
  const out = challengeResponse(db, c, req.params.date);
  if (!out) return res.status(404).json({ error: 'Level not found' });
  res.json(out);
}));

// POST /api/daily-challenge/complete — submit a solved daily challenge
router.post('/complete', wrap((req, res) => {
  const db = getDb();
  const { user_id, challenge_date, score, streak, hints_used, total_attempts } = req.body;
  if (!user_id) return res.status(400).json({ error: 'user_id required' });

  const date = challenge_date || todayStr();
  const c = getOrCreateChallenge(db, date);
  if (c.error) return res.status(404).json({ error: c.error });

  // Atomic: ensure player, mark solved, and apply stats in one transaction.
  const sets = ['last_sync = datetime(\'now\')'];
  const args = [];
  if (score !== undefined && score !== null) { sets.push('score = ?'); args.push(score); }
  if (streak !== undefined && streak !== null) { sets.push('streak = ?'); args.push(streak); sets.push('best_streak = MAX(best_streak, ?)'); args.push(streak); }
  if (hints_used !== undefined && hints_used !== null) { sets.push('hints_used = ?'); args.push(hints_used); }
  if (total_attempts !== undefined && total_attempts !== null) { sets.push('total_attempts = ?'); args.push(total_attempts); }
  args.push(user_id);
  db.transaction(() => {
    db.prepare('INSERT OR IGNORE INTO players (user_id, username) VALUES (?, ?)').run(user_id, 'Player');
    db.prepare(`INSERT INTO player_progress (user_id, level_id, is_solved, solved_at)
      VALUES (?, ?, 1, datetime('now'))
      ON CONFLICT(user_id, level_id) DO UPDATE SET is_solved = 1, solved_at = COALESCE(excluded.solved_at, solved_at)`)
      .run(user_id, c.level_id);
    db.prepare(`UPDATE players SET ${sets.join(', ')} WHERE user_id = ?`).run(...args);
  })();

  // Build response: challenge + updated stats
  const p = db.prepare('SELECT * FROM players WHERE user_id = ?').get(user_id);
  const solved = db.prepare('SELECT COUNT(*) AS n FROM player_progress WHERE user_id = ? AND is_solved = 1').get(user_id);

  res.json({
    challenge_date: date,
    level_id:       c.level_id,
    solved:         true,
    solved_count:   solved.n,
    total_score:    p.score,
    best_streak:    p.best_streak,
    streak:         p.streak,
    hints_used:     p.hints_used,
    total_attempts: p.total_attempts
  });
}));

module.exports = router;
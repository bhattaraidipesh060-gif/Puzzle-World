'use strict';
const express = require('express');
const router = express.Router();
const wrap = require('./wrap');
const { getDb } = require('../database');
const { requireAuth, requireSelf } = require('../middleware/auth');
const { t } = require('../i18n');

// GET /api/stats/:userId (auth + self only)
router.get('/:userId', requireAuth, requireSelf((req) => req.params.userId), wrap((req, res) => {
  const db = getDb();
  const p = db.prepare('SELECT * FROM players WHERE user_id = ?').get(req.params.userId);
  if (!p) return res.status(404).json({ error: t(req, 'player_not_found') });

  const solved = db.prepare('SELECT COUNT(*) AS n FROM player_progress WHERE user_id = ? AND is_solved = 1').get(req.params.userId);

  res.json({
    user_id:        p.user_id,
    solved_count:   solved.n,
    total_score:    p.score,
    best_streak:    p.best_streak,
    hints_used:     p.hints_used,
    total_attempts: p.total_attempts
  });
}));

module.exports = router;

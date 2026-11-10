const express = require('express');
const router = express.Router();
const wrap = require('./wrap');
const { evaluate } = require('../badges');

// GET /api/user/profile?user_id=xxx  (query param)
// GET /api/user/profile/:userId    (path param)
// Returns the user's stats plus the full badge list with per-badge unlock status
// and progress, so clients can render locked vs unlocked badges.
function respond(req, res, userId) {
  const profile = evaluate(userId);
  res.json(profile);
}

router.get('/profile', wrap((req, res) => {
  const userId = String(req.query.user_id || '').trim();
  if (!userId) return res.status(400).json({ error: 'user_id required' });
  respond(req, res, userId);
}));

router.get('/profile/:userId', wrap((req, res) => {
  respond(req, res, String(req.params.userId).trim());
}));

module.exports = router;

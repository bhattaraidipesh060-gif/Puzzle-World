'use strict';
const express = require('express');
const router = express.Router();
const wrap = require('./wrap');
const { evaluate } = require('../badges');
const { requireAuth, requireSelf } = require('../middleware/auth');
const { t } = require('../i18n');

// GET /api/user/profile?user_id=xxx  (query param)  — auth + self only
// GET /api/user/profile/:userId    (path param)     — auth + self only
// Returns the user's stats plus the full badge list with per-badge unlock status.
function respond(req, res, userId) {
  res.json(evaluate(userId));
}

router.get('/profile', requireAuth, requireSelf((req) => req.query.user_id), wrap((req, res) => {
  const userId = String(req.query.user_id || '').trim();
  if (!userId) return res.status(400).json({ error: t(req, 'user_id_required') });
  respond(req, res, userId);
}));

router.get('/profile/:userId', requireAuth, requireSelf((req) => req.params.userId), wrap((req, res) => {
  respond(req, res, String(req.params.userId).trim());
}));

module.exports = router;

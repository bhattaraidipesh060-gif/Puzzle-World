'use strict';
const express = require('express');
const crypto = require('crypto');
const router = express.Router();
const wrap = require('./wrap');
const { getDb } = require('../database');
const {
  TOKEN_TTL_SECONDS, signToken, hashPassword, verifyPassword,
  legacySha256, isLegacyHash, generateTotpSecret, verifyTotp, otpauthUrl,
} = require('../lib/security');
const { requireAuth } = require('../middleware/auth');

// Issue a session: sign the JWT and, for the web portal, drop it into an
// httpOnly cookie so the token never touches client-side localStorage.
function issueSession(req, res, user) {
  const token = signToken(user);
  const secure = req.secure || String(req.headers['x-forwarded-proto']).toLowerCase() === 'https';
  res.cookie('token', token, {
    httpOnly: true,
    sameSite: 'strict',
    secure,
    maxAge: TOKEN_TTL_SECONDS * 1000,
    path: '/',
  });
  return token;
}

function authResponse(req, res, row) {
  const user = { user_id: row.user_id, username: row.username };
  return { token: issueSession(req, res, user), user, mfa_enabled: !!row.mfa_enabled };
}

// POST /api/auth/register
router.post('/register', wrap((req, res) => {
  const db = getDb();
  const username = String(req.body.username || '').trim();
  const password = String(req.body.password || '');

  if (username.length < 3 || username.length > 32) {
    return res.status(400).json({ error: 'Username must be 3–32 characters.' });
  }
  if (password.length < 8) {
    return res.status(400).json({ error: 'Password must be at least 8 characters.' });
  }
  if (!/[A-Z]/.test(password) || !/[a-z]/.test(password) || !/[0-9]/.test(password)) {
    return res.status(400).json({ error: 'Password must include an uppercase letter, a lowercase letter, and a number.' });
  }

  const existing = db.prepare('SELECT user_id FROM users WHERE username = ?').get(username);
  if (existing) return res.status(409).json({ error: 'That username is already taken.' });

  const password_hash = hashPassword(password); // scrypt; salt is embedded
  const user_id = 'u_' + crypto.randomBytes(9).toString('hex');

  try {
    db.transaction(() => {
      db.prepare('INSERT INTO users (user_id, username, password_hash, salt) VALUES (?, ?, ?, ?)')
        .run(user_id, username, password_hash, '');
      db.prepare('INSERT INTO players (user_id, username) VALUES (?, ?)').run(user_id, username);
    })();
  } catch (e) {
    if (e && e.code === 'SQLITE_CONSTRAINT_UNIQUE') {
      return res.status(409).json({ error: 'That username is already taken.' });
    }
    throw e;
  }

  const row = db.prepare('SELECT * FROM users WHERE user_id = ?').get(user_id);
  res.status(201).json(authResponse(req, res, row));
}));

// POST /api/auth/login  (optional mfa_code when MFA is enabled)
router.post('/login', wrap((req, res) => {
  const db = getDb();
  const username = String(req.body.username || '').trim();
  const password = String(req.body.password || '');
  const mfaCode = String(req.body.mfa_code || '').trim();

  const user = db.prepare('SELECT * FROM users WHERE username = ?').get(username);
  if (!user) {
    // Burn a hash to reduce username-enumeration timing differences.
    legacySha256(password, crypto.randomBytes(16).toString('hex'));
    return res.status(401).json({ error: 'Invalid username or password.' });
  }

  let ok = false;
  if (isLegacyHash(user.password_hash)) {
    ok = legacySha256(password, user.salt) === user.password_hash;
    if (ok) {
      // Opportunistic migration to scrypt.
      const upgraded = hashPassword(password);
      db.prepare('UPDATE users SET password_hash = ?, salt = ? WHERE user_id = ?')
        .run(upgraded, '', user.user_id);
      user.password_hash = upgraded;
    }
  } else {
    ok = verifyPassword(password, user.password_hash);
  }

  if (!ok) return res.status(401).json({ error: 'Invalid username or password.' });

  if (user.mfa_enabled) {
    if (!verifyTotp(user.mfa_secret, mfaCode)) {
      return res.status(401).json({ error: 'Invalid or missing MFA code.', mfa_required: true });
    }
  }

  db.prepare('INSERT OR IGNORE INTO players (user_id, username) VALUES (?, ?)')
    .run(user.user_id, user.username);

  res.json(authResponse(req, res, user));
}));

// POST /api/auth/logout — clear the httpOnly cookie (client also drops its token).
router.post('/logout', wrap((req, res) => {
  res.clearCookie('token', { path: '/' });
  res.json({ ok: true });
}));

// GET /api/auth/me — validates the Bearer/cookie token (auto-login / session check).
router.get('/me', requireAuth, wrap((req, res) => {
  const user = getDb().prepare('SELECT user_id, username, mfa_enabled FROM users WHERE user_id = ?').get(req.user.user_id);
  if (!user) return res.status(401).json({ error: 'Account not found.' });
  res.json({ user: { user_id: user.user_id, username: user.username, mfa_enabled: !!user.mfa_enabled } });
}));

// ── MFA (TOTP) ────────────────────────────────────────────────────────────
// POST /api/auth/mfa/enable — begin enrollment: generate a secret, return the
// otpauth URL for an authenticator app (mfa stays disabled until /verify).
router.post('/mfa/enable', requireAuth, wrap((req, res) => {
  const db = getDb();
  const user = db.prepare('SELECT user_id, username FROM users WHERE user_id = ?').get(req.user.user_id);
  if (!user) return res.status(404).json({ error: 'Account not found.' });
  const secret = generateTotpSecret();
  db.prepare('UPDATE users SET mfa_secret = ? WHERE user_id = ?').run(secret, user.user_id);
  res.json({ secret, otpauth_url: otpauthUrl(secret, user.username) });
}));

// POST /api/auth/mfa/verify — confirm a code and activate MFA.
router.post('/mfa/verify', requireAuth, wrap((req, res) => {
  const db = getDb();
  const user = db.prepare('SELECT mfa_secret FROM users WHERE user_id = ?').get(req.user.user_id);
  if (!user || !user.mfa_secret) return res.status(400).json({ error: 'MFA enrollment has not been started.' });
  if (!verifyTotp(user.mfa_secret, req.body.code)) {
    return res.status(400).json({ error: 'Invalid code.' });
  }
  db.prepare('UPDATE users SET mfa_enabled = 1 WHERE user_id = ?').run(req.user.user_id);
  res.json({ ok: true, mfa_enabled: true });
}));

// POST /api/auth/mfa/disable — require a valid code before turning MFA off.
router.post('/mfa/disable', requireAuth, wrap((req, res) => {
  const db = getDb();
  const user = db.prepare('SELECT mfa_secret FROM users WHERE user_id = ?').get(req.user.user_id);
  if (!user || !user.mfa_enabled) return res.status(400).json({ error: 'MFA is not enabled.' });
  if (!verifyTotp(user.mfa_secret, req.body.code)) {
    return res.status(400).json({ error: 'Invalid code.' });
  }
  db.prepare('UPDATE users SET mfa_enabled = 0, mfa_secret = NULL WHERE user_id = ?').run(req.user.user_id);
  res.json({ ok: true, mfa_enabled: false });
}));

module.exports = router;

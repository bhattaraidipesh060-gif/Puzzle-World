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
const { t, SUPPORTED } = require('../i18n');

// Usernames: 3–32 chars of letters / digits / underscore / dot / dash (no spaces).
function isValidUsername(v) {
  return typeof v === 'string' && /^[A-Za-z0-9_.-]{3,32}$/.test(v);
}

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

function publicUser(row) {
  return {
    user_id: row.user_id,
    username: row.username || null,
    email: row.email || null,
    has_username: !!row.username,
    language: row.language || 'en',
  };
}

function authResponse(req, res, row) {
  const user = publicUser(row);
  return { token: issueSession(req, res, user), user, mfa_enabled: !!row.mfa_enabled };
}

// ═══════════════════════════════════════════════════════════════════════════
//  REGISTRATION (username + password — no email, no OTP)
// ═══════════════════════════════════════════════════════════════════════════

// POST /api/auth/register — create an account that is active immediately.
router.post('/register', wrap((req, res) => {
  const db = getDb();
  const username = String(req.body.username || '').trim();
  const password = String(req.body.password || '');

  if (!isValidUsername(username)) {
    return res.status(400).json({ error: t(req, 'invalid_username') });
  }
  if (password.length < 8) {
    return res.status(400).json({ error: t(req, 'password_length') });
  }
  if (!/[A-Z]/.test(password) || !/[a-z]/.test(password) || !/[0-9]/.test(password)) {
    return res.status(400).json({ error: t(req, 'password_policy') });
  }

  const password_hash = hashPassword(password);
  const user_id = 'u_' + crypto.randomBytes(9).toString('hex');

  try {
    db.transaction(() => {
      db.prepare('INSERT INTO users (user_id, username, password_hash, salt) VALUES (?, ?, ?, ?)')
        .run(user_id, username, password_hash, '');
      db.prepare('INSERT OR IGNORE INTO players (user_id, username) VALUES (?, ?)')
        .run(user_id, username);
    })();
  } catch (e) {
    if (e && e.code === 'SQLITE_CONSTRAINT_UNIQUE') {
      return res.status(409).json({ error: t(req, 'username_taken') });
    }
    throw e;
  }

  const row = db.prepare('SELECT * FROM users WHERE user_id = ?').get(user_id);
  res.status(201).json(authResponse(req, res, row));
}));

// ═══════════════════════════════════════════════════════════════════════════
//  LOGIN (username + password, optional MFA code)
// ═══════════════════════════════════════════════════════════════════════════

// POST /api/auth/login — identify by username; verify password; optional MFA.
router.post('/login', wrap((req, res) => {
  const db = getDb();
  const username = String(req.body.username || '').trim();
  const password = String(req.body.password || '');
  const mfaCode = String(req.body.mfa_code || '').trim();

  const user = db.prepare('SELECT * FROM users WHERE username = ?').get(username);
  if (!user) {
    // Burn a hash so timing does not reveal whether the username exists.
    legacySha256(password, crypto.randomBytes(16).toString('hex'));
    return res.status(401).json({ error: t(req, 'invalid_login') });
  }

  let ok = false;
  if (isLegacyHash(user.password_hash)) {
    ok = legacySha256(password, user.salt) === user.password_hash;
    if (ok) {
      const upgraded = hashPassword(password);
      db.prepare('UPDATE users SET password_hash = ?, salt = ? WHERE user_id = ?')
        .run(upgraded, '', user.user_id);
      user.password_hash = upgraded;
    }
  } else {
    ok = verifyPassword(password, user.password_hash);
  }

  if (!ok) return res.status(401).json({ error: t(req, 'invalid_login') });

  if (user.mfa_enabled) {
    if (!verifyTotp(user.mfa_secret, mfaCode)) {
      return res.status(401).json({ error: t(req, 'mfa_invalid'), mfa_required: true });
    }
  }

  db.prepare('INSERT OR IGNORE INTO players (user_id, username) VALUES (?, ?)')
    .run(user.user_id, user.username || 'Player');

  res.json(authResponse(req, res, user));
}));

// POST /api/auth/logout — clear the httpOnly cookie (client also drops its token).
router.post('/logout', wrap((req, res) => {
  res.clearCookie('token', { path: '/' });
  res.json({ ok: true });
}));

// GET /api/auth/me — validates the Bearer/cookie token (auto-login / session check).
router.get('/me', requireAuth, wrap((req, res) => {
  const user = getDb().prepare('SELECT user_id, username, email, language, mfa_enabled FROM users WHERE user_id = ?').get(req.user.user_id);
  if (!user) return res.status(401).json({ error: t(req, 'account_not_found') });
  res.json({ user: { ...publicUser(user), mfa_enabled: !!user.mfa_enabled } });
}));

// POST /api/auth/language — store the user's preferred language ('en' or 'ko').
router.post('/language', requireAuth, wrap((req, res) => {
  const db = getDb();
  const language = String(req.body.language || '').toLowerCase();
  if (!SUPPORTED.includes(language)) {
    return res.status(400).json({ error: t(req, 'invalid_language') });
  }
  db.prepare('UPDATE users SET language = ? WHERE user_id = ?').run(language, req.user.user_id);
  res.json({ ok: true, language });
}));

// POST /api/auth/set-username — change the unique username (profile edit).
router.post('/set-username', requireAuth, wrap((req, res) => {
  const db = getDb();
  const username = String(req.body.username || '').trim();

  if (!isValidUsername(username)) {
    return res.status(400).json({ error: t(req, 'username_length') });
  }

  const existing = db.prepare('SELECT user_id FROM users WHERE username = ? AND user_id != ?')
    .get(username, req.user.user_id);
  if (existing) return res.status(409).json({ error: t(req, 'username_taken') });

  try {
    db.transaction(() => {
      db.prepare('UPDATE users SET username = ? WHERE user_id = ?').run(username, req.user.user_id);
      db.prepare('INSERT OR IGNORE INTO players (user_id, username) VALUES (?, ?)').run(req.user.user_id, username);
      db.prepare('UPDATE players SET username = ? WHERE user_id = ?').run(username, req.user.user_id);
    })();
  } catch (e) {
    if (e && e.code === 'SQLITE_CONSTRAINT_UNIQUE') {
      return res.status(409).json({ error: t(req, 'username_taken') });
    }
    throw e;
  }

  const row = db.prepare('SELECT * FROM users WHERE user_id = ?').get(req.user.user_id);
  res.json({ ok: true, user: publicUser(row) });
}));

// ═══════════════════════════════════════════════════════════════════════════
//  MFA (TOTP)
// ═══════════════════════════════════════════════════════════════════════════

// POST /api/auth/mfa/enable — begin enrollment: generate a secret, return the
// otpauth URL for an authenticator app (mfa stays disabled until /verify).
router.post('/mfa/enable', requireAuth, wrap((req, res) => {
  const db = getDb();
  const user = db.prepare('SELECT user_id, username FROM users WHERE user_id = ?').get(req.user.user_id);
  if (!user) return res.status(404).json({ error: t(req, 'account_not_found') });
  const secret = generateTotpSecret();
  db.prepare('UPDATE users SET mfa_secret = ? WHERE user_id = ?').run(secret, user.user_id);
  res.json({ secret, otpauth_url: otpauthUrl(secret, user.username || 'PuzzleWorld') });
}));

// POST /api/auth/mfa/verify — confirm a code and activate MFA.
router.post('/mfa/verify', requireAuth, wrap((req, res) => {
  const db = getDb();
  const user = db.prepare('SELECT mfa_secret FROM users WHERE user_id = ?').get(req.user.user_id);
  if (!user || !user.mfa_secret) return res.status(400).json({ error: t(req, 'mfa_not_started') });
  if (!verifyTotp(user.mfa_secret, req.body.code)) {
    return res.status(400).json({ error: t(req, 'mfa_invalid_code') });
  }
  db.prepare('UPDATE users SET mfa_enabled = 1 WHERE user_id = ?').run(req.user.user_id);
  res.json({ ok: true, mfa_enabled: true });
}));

// POST /api/auth/mfa/disable — require a valid code before turning MFA off.
router.post('/mfa/disable', requireAuth, wrap((req, res) => {
  const db = getDb();
  const user = db.prepare('SELECT mfa_secret FROM users WHERE user_id = ?').get(req.user.user_id);
  if (!user || !user.mfa_enabled) return res.status(400).json({ error: t(req, 'mfa_not_enabled') });
  if (!verifyTotp(user.mfa_secret, req.body.code)) {
    return res.status(400).json({ error: t(req, 'mfa_invalid_code') });
  }
  db.prepare('UPDATE users SET mfa_enabled = 0, mfa_secret = NULL WHERE user_id = ?').run(req.user.user_id);
  res.json({ ok: true, mfa_enabled: false });
}));

module.exports = router;

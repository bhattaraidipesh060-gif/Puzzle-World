const express = require('express');
const crypto = require('crypto');
const router = express.Router();
const wrap = require('./wrap');
const { getDb } = require('../database');

// JWT secret — supplied via PW_JWT_SECRET (see backend/.env.example). Never hardcoded.
const JWT_SECRET = process.env.PW_JWT_SECRET;
if (!JWT_SECRET) {
  throw new Error('PW_JWT_SECRET is not set — copy backend/.env.example to backend/.env and set it.');
}
const TOKEN_TTL_SECONDS = 60 * 60 * 24 * 30; // 30 days

// Salted SHA-256 (no plaintext passwords are ever stored or logged).
function hash(password, salt) {
  return crypto.createHash('sha256').update(`${salt}:${password}`).digest('hex');
}

// base64url encode/decode (Node-version agnostic — no reliance on 'base64url').
function b64url(buf) {
  return Buffer.from(buf).toString('base64')
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
function b64urlDecode(str) {
  return Buffer.from(str.replace(/-/g, '+').replace(/_/g, '/'), 'base64');
}

// HS256 JWT: header.payload.signature (dependency-free).
function signToken(user) {
  const header = { alg: 'HS256', typ: 'JWT' };
  const now = Math.floor(Date.now() / 1000);
  const payload = { sub: user.user_id, username: user.username, iat: now, exp: now + TOKEN_TTL_SECONDS };
  const enc = `${b64url(JSON.stringify(header))}.${b64url(JSON.stringify(payload))}`;
  const sig = b64url(crypto.createHmac('sha256', JWT_SECRET).update(enc).digest());
  return `${enc}.${sig}`;
}

// Returns the decoded payload on success, or null on invalid/expired signature.
function verifyToken(token) {
  if (!token) return null;
  const parts = String(token).split('.');
  if (parts.length !== 3) return null;
  const enc = `${parts[0]}.${parts[1]}`;
  const expect = b64url(crypto.createHmac('sha256', JWT_SECRET).update(enc).digest());
  const got = Buffer.from(parts[2]);
  const want = Buffer.from(expect);
  if (got.length !== want.length || !crypto.timingSafeEqual(got, want)) return null;
  try {
    const payload = JSON.parse(b64urlDecode(parts[1]).toString('utf8'));
    if (!payload.sub) return null;
    if (payload.exp && payload.exp < Math.floor(Date.now() / 1000)) return null;
    return payload;
  } catch {
    return null;
  }
}

function authResponse(row) {
  const user = { user_id: row.user_id, username: row.username };
  return { token: signToken(user), user };
}

// POST /api/auth/register
router.post('/register', wrap((req, res) => {
  const db = getDb();
  const username = String(req.body.username || '').trim();
  const password = String(req.body.password || '');

  if (username.length < 3 || username.length > 32) {
    return res.status(400).json({ error: 'Username must be 3–32 characters.' });
  }
  if (password.length < 4) {
    return res.status(400).json({ error: 'Password must be at least 4 characters.' });
  }

  const existing = db.prepare('SELECT user_id FROM users WHERE username = ?').get(username);
  if (existing) return res.status(409).json({ error: 'That username is already taken.' });

  const salt = crypto.randomBytes(16).toString('hex');
  const password_hash = hash(password, salt);
  const user_id = 'u_' + crypto.randomBytes(9).toString('hex');

  try {
    db.transaction(() => {
      db.prepare('INSERT INTO users (user_id, username, password_hash, salt) VALUES (?, ?, ?, ?)')
        .run(user_id, username, password_hash, salt);
      // Mirror into `players` so progress / stats / leaderboard work immediately.
      db.prepare('INSERT INTO players (user_id, username) VALUES (?, ?)').run(user_id, username);
    })();
  } catch (e) {
    // Concurrent registration of the same username slips past the SELECT check above
    // but is caught by the UNIQUE constraint — surface a 409 instead of a 500.
    if (e && e.code === 'SQLITE_CONSTRAINT_UNIQUE') {
      return res.status(409).json({ error: 'That username is already taken.' });
    }
    throw e;
  }

  const row = db.prepare('SELECT * FROM users WHERE user_id = ?').get(user_id);
  res.status(201).json(authResponse(row));
}));

// POST /api/auth/login
router.post('/login', wrap((req, res) => {
  const db = getDb();
  const username = String(req.body.username || '').trim();
  const password = String(req.body.password || '');

  const user = db.prepare('SELECT * FROM users WHERE username = ?').get(username);
  if (!user) return res.status(401).json({ error: 'No account found with that username.' });

  if (hash(password, user.salt) !== user.password_hash) {
    return res.status(401).json({ error: 'Incorrect password.' });
  }

  db.prepare('INSERT OR IGNORE INTO players (user_id, username) VALUES (?, ?)')
    .run(user.user_id, user.username);

  res.json(authResponse(user));
}));

// GET /api/auth/me — validates the Bearer token (auto-login / session check).
router.get('/me', wrap((req, res) => {
  const h = String(req.headers.authorization || '');
  const token = h.startsWith('Bearer ') ? h.slice(7) : h;
  const payload = verifyToken(token);
  if (!payload) return res.status(401).json({ error: 'Invalid or expired token.' });

  const user = getDb().prepare('SELECT user_id, username FROM users WHERE user_id = ?').get(payload.sub);
  if (!user) return res.status(401).json({ error: 'Account not found.' });

  res.json({ user: { user_id: user.user_id, username: user.username } });
}));

module.exports = router;
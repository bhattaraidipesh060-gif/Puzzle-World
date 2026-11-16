'use strict';
const express = require('express');
const crypto = require('crypto');
const nodemailer = require('nodemailer');
const { promises: dns } = require('dns');
const router = express.Router();
const wrap = require('./wrap');
const { getDb } = require('../database');
const {
  TOKEN_TTL_SECONDS, signToken, hashPassword, verifyPassword,
  legacySha256, isLegacyHash, generateTotpSecret, verifyTotp, otpauthUrl,
} = require('../lib/security');
const { requireAuth } = require('../middleware/auth');

// Basic but strict-enough email validation (used at registration).
function isValidEmail(v) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v);
}

// Registration requires a real Google (Gmail) address.
function isGoogleEmail(v) {
  return /^[^\s@]+@(gmail\.com|googlemail\.com)$/i.test(v);
}

// DNS MX lookup — verify the email domain can receive mail.
async function hasMxRecord(domain) {
  try {
    const records = await dns.resolveMx(domain);
    return Array.isArray(records) && records.length > 0;
  } catch {
    return false; // NXDOMAIN, SERVFAIL, timeout, etc.
  }
}

// Generate a secure 6-digit OTP using CSPRNG.
function generateOtp() {
  return crypto.randomInt(100000, 999999).toString();
}

// Send a verification code via Gmail SMTP.  Falls back to console logging when
// PW_EMAIL_USER / PW_EMAIL_PASS are not set (dev / demo mode — check the log).
async function sendVerificationEmail(email, otp) {
  console.log(`[email] Verification code for ${email}: ${otp}`);
  const user = process.env.PW_EMAIL_USER;
  const pass = process.env.PW_EMAIL_PASS;
  if (!user || !pass) {
    console.log('[email] SMTP not configured — set PW_EMAIL_USER + PW_EMAIL_PASS for real delivery.');
    return;
  }
  try {
    const transporter = nodemailer.createTransport({
      service: 'gmail',
      auth: { user, pass },
    });
    await transporter.sendMail({
      from: user,
      to: email,
      subject: 'PuzzleWorld – Verify your email',
      text: `Your PuzzleWorld verification code is: ${otp}\n\nThis code expires in 10 minutes.\n\nIf you didn't create a PuzzleWorld account, please ignore this email.`,
      html: `<div style="font-family:sans-serif;max-width:480px;margin:0 auto"><h2 style="color:#0f172a">🧩 PuzzleWorld</h2><p>Your verification code is:</p><h1 style="letter-spacing:8px;color:#0ea5e9">${otp}</h1><p style="color:#64748b;font-size:14px">This code expires in 10 minutes.</p><hr style="border:none;border-top:1px solid #e2e8f0"><p style="color:#94a3b8;font-size:12px">If you didn't create a PuzzleWorld account, please ignore this email.</p></div>`,
    });
    console.log(`[email] Verification code sent to ${email}.`);
  } catch (err) {
    console.error('[email] Failed to send:', err.message);
  }
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

// Shape the user object consistently: always include email and the current
// username status so clients can prompt for a handle while it is still unset.
function publicUser(row) {
  return {
    user_id: row.user_id,
    email: row.email,
    username: row.username || null,
    has_username: !!row.username,
  };
}

function authResponse(req, res, row) {
  const user = publicUser(row);
  return { token: issueSession(req, res, user), user, mfa_enabled: !!row.mfa_enabled };
}

// ═══════════════════════════════════════════════════════════════════════════
//  REGISTRATION (OTP flow)
// ═══════════════════════════════════════════════════════════════════════════

// POST /api/auth/register — create an account and log in immediately.
router.post('/register', wrap(async (req, res) => {
  const db = getDb();
  const email = String(req.body.email || '').trim().toLowerCase();
  const password = String(req.body.password || '');

  if (!isValidEmail(email)) {
    return res.status(400).json({ error: 'A valid email address is required.' });
  }
  if (!isGoogleEmail(email)) {
    return res.status(400).json({ error: 'Please use a Gmail address (…@gmail.com).' });
  }
  // Validate the domain can receive mail (DNS MX lookup).
  if (!(await hasMxRecord(email.split('@')[1]))) {
    return res.status(400).json({ error: 'Invalid email address' });
  }
  if (password.length < 8) {
    return res.status(400).json({ error: 'Password must be at least 8 characters.' });
  }
  if (!/[A-Z]/.test(password) || !/[a-z]/.test(password) || !/[0-9]/.test(password)) {
    return res.status(400).json({ error: 'Password must include an uppercase letter, a lowercase letter, and a number.' });
  }

  const existing = db.prepare('SELECT user_id FROM users WHERE email = ?').get(email);
  if (existing) return res.status(409).json({ error: 'An account with that email already exists.' });

  const password_hash = hashPassword(password);
  const user_id = 'u_' + crypto.randomBytes(9).toString('hex');

  try {
    db.transaction(() => {
      db.prepare('INSERT INTO users (user_id, email, username, password_hash, salt, is_verified) VALUES (?, ?, NULL, ?, ?, 1)')
        .run(user_id, email, password_hash, '');
      db.prepare('INSERT INTO players (user_id, username) VALUES (?, ?)')
        .run(user_id, email.split('@')[0] || 'Player');
    })();
  } catch (e) {
    if (e && e.code === 'SQLITE_CONSTRAINT_UNIQUE') {
      return res.status(409).json({ error: 'An account with that email already exists.' });
    }
    throw e;
  }

  const row = db.prepare('SELECT * FROM users WHERE user_id = ?').get(user_id);
  res.status(201).json(authResponse(req, res, row));
}));

// ═══════════════════════════════════════════════════════════════════════════
//  EMAIL VERIFICATION
// ═══════════════════════════════════════════════════════════════════════════

// POST /api/auth/verify-otp — verify the OTP, mark account, issue a JWT.
router.post('/verify-otp', wrap((req, res) => {
  const db = getDb();
  const email = String(req.body.email || '').trim().toLowerCase();
  const otp = String(req.body.otp || '').trim();

  if (!isValidEmail(email)) {
    return res.status(400).json({ error: 'A valid email address is required.' });
  }
  if (!/^\d{6}$/.test(otp)) {
    return res.status(400).json({ error: 'Please enter a valid 6‑digit code.' });
  }

  const user = db.prepare('SELECT * FROM users WHERE email = ?').get(email);
  if (!user) return res.status(400).json({ error: 'Invalid email or code.' });
  if (user.is_verified) return res.status(400).json({ error: 'Email already verified. Please log in.' });

  if (user.verification_otp !== otp || (user.otp_expires_at && new Date(user.otp_expires_at) < new Date())) {
    return res.status(400).json({ error: 'Invalid or expired verification code.' });
  }

  db.prepare('UPDATE users SET is_verified = 1, verification_otp = NULL, otp_expires_at = NULL WHERE user_id = ?')
    .run(user.user_id);
  user.is_verified = 1;

  res.json(authResponse(req, res, user));
}));

// POST /api/auth/resend-otp — regenerate a fresh code and send it again.
router.post('/resend-otp', wrap(async (req, res) => {
  const db = getDb();
  const email = String(req.body.email || '').trim().toLowerCase();

  if (!isValidEmail(email)) {
    return res.status(400).json({ error: 'A valid email address is required.' });
  }
  if (!(await hasMxRecord(email.split('@')[1]))) {
    return res.status(400).json({ error: 'Invalid email address' });
  }

  const user = db.prepare('SELECT * FROM users WHERE email = ?').get(email);
  if (!user) return res.status(400).json({ error: 'No account found for that email.' });
  if (user.is_verified) return res.status(400).json({ error: 'Email already verified. Please log in.' });

  const otp = generateOtp();
  const expires = new Date(Date.now() + 10 * 60 * 1000).toISOString();
  db.prepare('UPDATE users SET verification_otp = ?, otp_expires_at = ? WHERE user_id = ?')
    .run(otp, expires, user.user_id);

  sendVerificationEmail(email, otp).catch(() => {});

  res.json({ message: 'A new verification code has been sent to ' + email + '.', email });
}));

// ═══════════════════════════════════════════════════════════════════════════
//  LOGIN
// ═══════════════════════════════════════════════════════════════════════════

// POST /api/auth/login — identify by email (or legacy username); must be verified.
router.post('/login', wrap((req, res) => {
  const db = getDb();
  const email = String(req.body.email || '').trim().toLowerCase();
  const username = String(req.body.username || '').trim();
  const password = String(req.body.password || '');
  const mfaCode = String(req.body.mfa_code || '').trim();

  let user;
  if (email) {
    user = db.prepare('SELECT * FROM users WHERE email = ?').get(email);
  } else if (username) {
    user = db.prepare('SELECT * FROM users WHERE username = ?').get(username);
  }

  if (!user) {
    // Burn a hash to reduce identifier-enumeration timing differences.
    legacySha256(password, crypto.randomBytes(16).toString('hex'));
    return res.status(401).json({ error: 'Invalid email/username or password.' });
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

  if (!ok) return res.status(401).json({ error: 'Invalid email/username or password.' });

  // Require email verification before issuing a session.
  if (!user.is_verified) {
    return res.status(403).json({
      error: 'Email not verified. Please check your inbox for the verification code.',
      unverified: true,
      email: user.email,
    });
  }

  if (user.mfa_enabled) {
    if (!verifyTotp(user.mfa_secret, mfaCode)) {
      return res.status(401).json({ error: 'Invalid or missing MFA code.', mfa_required: true });
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
  const user = getDb().prepare('SELECT user_id, email, username, mfa_enabled FROM users WHERE user_id = ?').get(req.user.user_id);
  if (!user) return res.status(401).json({ error: 'Account not found.' });
  res.json({ user: { ...publicUser(user), mfa_enabled: !!user.mfa_enabled } });
}));

// POST /api/auth/set-username — choose or update the unique username (two-step setup).
router.post('/set-username', requireAuth, wrap((req, res) => {
  const db = getDb();
  const username = String(req.body.username || '').trim();

  if (username.length < 3 || username.length > 32) {
    return res.status(400).json({ error: 'Username must be 3–32 characters.' });
  }

  const existing = db.prepare('SELECT user_id FROM users WHERE username = ? AND user_id != ?')
    .get(username, req.user.user_id);
  if (existing) return res.status(409).json({ error: 'That username is already taken.' });

  try {
    db.transaction(() => {
      db.prepare('UPDATE users SET username = ? WHERE user_id = ?').run(username, req.user.user_id);
      db.prepare('INSERT OR IGNORE INTO players (user_id, username) VALUES (?, ?)').run(req.user.user_id, username);
      db.prepare('UPDATE players SET username = ? WHERE user_id = ?').run(username, req.user.user_id);
    })();
  } catch (e) {
    if (e && e.code === 'SQLITE_CONSTRAINT_UNIQUE') {
      return res.status(409).json({ error: 'That username is already taken.' });
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
  const user = db.prepare('SELECT user_id, username, email FROM users WHERE user_id = ?').get(req.user.user_id);
  if (!user) return res.status(404).json({ error: 'Account not found.' });
  const secret = generateTotpSecret();
  db.prepare('UPDATE users SET mfa_secret = ? WHERE user_id = ?').run(secret, user.user_id);
  res.json({ secret, otpauth_url: otpauthUrl(secret, user.username || user.email) });
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
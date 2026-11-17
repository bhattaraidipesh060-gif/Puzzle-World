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
const { t, SUPPORTED } = require('../i18n');

// Strict-enough RFC-ish email format check (any provider: Gmail, Naver, Yahoo,
// Daum, Outlook, iCloud, …).
function isValidEmail(v) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(v);
}

// DNS MX lookup — verify the email domain can receive mail.
// Fails CLOSED only when the domain definitively has no MX records
// (ENODATA/ENOTFOUND); fails OPEN on infrastructure errors (ECONNREFUSED,
// TIMEOUT, SERVFAIL) so registration isn't blocked by a DNS outage.
async function hasMxRecord(domain) {
  try {
    const records = await dns.resolveMx(domain);
    return Array.isArray(records) && records.length > 0;
  } catch (err) {
    const code = err && err.code;
    if (code === 'ENOTFOUND' || code === 'ENODATA' || code === 'NXDOMAIN') {
      return false;
    }
    return true; // can't determine — allow through
  }
}

// Generate a secure 6-digit OTP using CSPRNG.
function generateOtp() {
  return crypto.randomInt(100000, 999999).toString();
}

// ── SMTP / email delivery configuration ────────────────────────────────────
// Provider presets for common hosts. Override any field via PW_SMTP_* env vars
// so the same code serves Gmail, Naver, Daum, Yahoo, Outlook, etc. without
// source edits. PW_EMAIL_USER / PW_EMAIL_PASS carry the account credentials
// (for Gmail, a 16-character App Password — NOT the account password; requires
// 2-Step Verification enabled on the Google account).
const SMTP_PRESETS = {
  gmail:   { host: 'smtp.gmail.com',         port: 465, secure: true  },
  naver:   { host: 'smtp.naver.com',         port: 465, secure: true  },
  daum:    { host: 'smtp.daum.net',          port: 465, secure: true  },
  yahoo:   { host: 'smtp.mail.yahoo.com',    port: 465, secure: true  },
  outlook: { host: 'smtp-mail.outlook.com',  port: 587, secure: false }, // STARTTLS
};

function resolveSmtpConfig() {
  const user = process.env.PW_EMAIL_USER;
  const pass = process.env.PW_EMAIL_PASS;
  if (!user || !pass) return null;
  const provider = String(process.env.PW_SMTP_PROVIDER || 'gmail').toLowerCase();
  const preset = SMTP_PRESETS[provider] || SMTP_PRESETS.gmail;
  const host = process.env.PW_SMTP_HOST || preset.host;
  const port = parseInt(process.env.PW_SMTP_PORT || String(preset.port), 10);
  const secure = String(process.env.PW_SMTP_SECURE || 'true') !== 'false';
  return {
    host,
    port,
    secure,
    auth: { user, pass },
    from: process.env.PW_EMAIL_FROM || user,
    provider,
  };
}

// Send a verification code via SMTP. Falls back to console logging when SMTP
// is not configured (dev / demo mode — the code appears in the server log).
// Returns { sent, reason } so callers can surface delivery failures.
async function sendVerificationEmail(email, otp) {
  const cfg = resolveSmtpConfig();
  if (!cfg) {
    console.warn(
      `[email] SMTP not configured — set PW_EMAIL_USER + PW_EMAIL_PASS ` +
      `(optionally PW_SMTP_PROVIDER/HOST/PORT/SECURE) for real delivery. ` +
      `Console fallback code for ${email}: ${otp}`
    );
    return { sent: false, reason: 'not_configured' };
  }

  console.log(
    `[email] Sending code to ${email} via ${cfg.provider} ` +
    `(${cfg.host}:${cfg.port}, secure=${cfg.secure})…`
  );

  const transporter = nodemailer.createTransport({
    host: cfg.host,
    port: cfg.port,
    secure: cfg.secure,
    auth: cfg.auth,
    connectionTimeout: 15000,
    greetingTimeout: 15000,
    socketTimeout: 30000,
  });

  try {
    const info = await transporter.sendMail({
      from: cfg.from,
      to: email,
      subject: 'PuzzleWorld – 이메일 인증 / Email Verification',
      text: `Your PuzzleWorld verification code is: ${otp}\n\nThis code expires in 10 minutes.\n\nPuzzleWorld 인증 코드: ${otp} (10분 후 만료).`,
      html: `<div style="font-family:sans-serif;max-width:480px;margin:0 auto"><h2 style="color:#0f172a">🧩 PuzzleWorld</h2><p>Your verification code is / 인증 코드:</p><h1 style="letter-spacing:8px;color:#0ea5e9">${otp}</h1><p style="color:#64748b;font-size:14px">This code expires in 10 minutes. · 코드는 10분 후 만료됩니다.</p></div>`,
    });
    console.log(`[email] Delivered to ${email}: ${info.messageId || 'accepted'}`);
    return { sent: true, reason: 'ok' };
  } catch (err) {
    // Robust diagnostics — surface the exact failure reason in the server log.
    console.error('[email] Delivery FAILED:');
    console.error(`  target: ${cfg.host}:${cfg.port} secure=${cfg.secure} from=${cfg.from} to=${email}`);
    console.error(`  code=${err.code || 'n/a'} command=${err.command || 'n/a'} response=${err.response || err.responseCode || 'n/a'}`);
    console.error(`  message=${err.message}`);
    if (err.code === 'EAUTH') {
      console.error('  Hint: SMTP authentication rejected. Gmail requires a 16-char App Password (2-Step Verification ON), not the account password. Naver/Daum require SMTP enabled in account settings.');
    } else if (err.code === 'ESOCKET' || err.code === 'ETIMEDOUT' || err.code === 'ECONNECTION' || err.code === 'ECONNREFUSED') {
      console.error('  Hint: could not reach the SMTP host — check PW_SMTP_HOST/PORT/SECURE and outbound firewall (ports 465/587).');
    }
    if (err.stack) console.error(err.stack);
    return { sent: false, reason: err.code || 'error' };
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

function publicUser(row) {
  return {
    user_id: row.user_id,
    email: row.email || null,
    username: row.username || null,
    has_username: !!row.username,
    language: row.language || 'en',
  };
}

function authResponse(req, res, row) {
  const user = publicUser(row);
  return { token: issueSession(req, res, user), user, mfa_enabled: !!row.mfa_enabled };
}

// ═══════════════════════════════════════════════════════════════════════════
//  REGISTRATION (universal email + OTP)
// ═══════════════════════════════════════════════════════════════════════════

// POST /api/auth/register — validate email + password, create unverified account, send OTP.
router.post('/register', wrap(async (req, res) => {
  const db = getDb();
  const email = String(req.body.email || '').trim().toLowerCase();
  const password = String(req.body.password || '');

  if (!isValidEmail(email)) {
    return res.status(400).json({ error: t(req, 'invalid_email') });
  }
  // Verify the domain can receive mail (DNS MX lookup).
  if (!(await hasMxRecord(email.split('@')[1]))) {
    return res.status(400).json({ error: t(req, 'invalid_email') });
  }
  if (password.length < 8) {
    return res.status(400).json({ error: t(req, 'password_length') });
  }
  if (!/[A-Z]/.test(password) || !/[a-z]/.test(password) || !/[0-9]/.test(password)) {
    return res.status(400).json({ error: t(req, 'password_policy') });
  }

  const existing = db.prepare('SELECT user_id FROM users WHERE email = ?').get(email);
  if (existing) return res.status(409).json({ error: t(req, 'email_exists') });

  const password_hash = hashPassword(password);
  const user_id = 'u_' + crypto.randomBytes(9).toString('hex');
  const otp = generateOtp();
  const otpExpires = new Date(Date.now() + 10 * 60 * 1000).toISOString();

  try {
    db.transaction(() => {
      db.prepare('INSERT INTO users (user_id, email, username, password_hash, salt, is_verified, verification_otp, otp_expires_at) VALUES (?, ?, NULL, ?, ?, 0, ?, ?)')
        .run(user_id, email, password_hash, '', otp, otpExpires);
      db.prepare('INSERT INTO players (user_id, username) VALUES (?, ?)')
        .run(user_id, email.split('@')[0] || 'Player');
    })();
  } catch (e) {
    if (e && e.code === 'SQLITE_CONSTRAINT_UNIQUE') {
      return res.status(409).json({ error: t(req, 'email_exists') });
    }
    throw e;
  }

  // Await OTP email delivery so we can surface failures to the client.
  const delivery = await sendVerificationEmail(email, otp);

  res.status(201).json({
    message: t(req, 'otp_sent', { email }),
    email,
    delivery: delivery.sent ? 'ok' : (delivery.reason === 'not_configured' ? 'console' : 'failed'),
  });
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
    return res.status(400).json({ error: t(req, 'invalid_email') });
  }
  if (!/^\d{6}$/.test(otp)) {
    return res.status(400).json({ error: t(req, 'otp_format') });
  }

  const user = db.prepare('SELECT * FROM users WHERE email = ?').get(email);
  if (!user) return res.status(400).json({ error: t(req, 'otp_no_account') });
  if (user.is_verified) return res.status(400).json({ error: t(req, 'otp_already_verified') });

  // Strict character-for-character match + expiry check.
  if (user.verification_otp !== otp || (user.otp_expires_at && new Date(user.otp_expires_at) < new Date())) {
    return res.status(400).json({ error: t(req, 'otp_invalid') });
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
    return res.status(400).json({ error: t(req, 'invalid_email') });
  }
  if (!(await hasMxRecord(email.split('@')[1]))) {
    return res.status(400).json({ error: t(req, 'invalid_email') });
  }

  const user = db.prepare('SELECT * FROM users WHERE email = ?').get(email);
  if (!user) return res.status(400).json({ error: t(req, 'otp_no_account') });
  if (user.is_verified) return res.status(400).json({ error: t(req, 'otp_already_verified') });

  const otp = generateOtp();
  const expires = new Date(Date.now() + 10 * 60 * 1000).toISOString();
  db.prepare('UPDATE users SET verification_otp = ?, otp_expires_at = ? WHERE user_id = ?')
    .run(otp, expires, user.user_id);

  const delivery = await sendVerificationEmail(email, otp);

  res.json({
    message: t(req, 'otp_resent', { email }),
    email,
    delivery: delivery.sent ? 'ok' : (delivery.reason === 'not_configured' ? 'console' : 'failed'),
  });
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
    // Legacy fallback for accounts registered during the username-only period.
    user = db.prepare('SELECT * FROM users WHERE username = ?').get(username);
  }

  if (!user) {
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

  // Require email verification before issuing a session.
  if (!user.is_verified) {
    return res.status(403).json({
      error: t(req, 'unverified'),
      unverified: true,
      email: user.email,
    });
  }

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
  const user = getDb().prepare('SELECT user_id, email, username, language, mfa_enabled FROM users WHERE user_id = ?').get(req.user.user_id);
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

// POST /api/auth/set-username — choose or update the unique username (profile setup).
router.post('/set-username', requireAuth, wrap((req, res) => {
  const db = getDb();
  const username = String(req.body.username || '').trim();

  if (username.length < 3 || username.length > 32) {
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
  const user = db.prepare('SELECT user_id, username, email FROM users WHERE user_id = ?').get(req.user.user_id);
  if (!user) return res.status(404).json({ error: t(req, 'account_not_found') });
  const secret = generateTotpSecret();
  db.prepare('UPDATE users SET mfa_secret = ? WHERE user_id = ?').run(secret, user.user_id);
  res.json({ secret, otpauth_url: otpauthUrl(secret, user.username || user.email) });
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

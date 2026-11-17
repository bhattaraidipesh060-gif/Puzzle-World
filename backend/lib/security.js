'use strict';
// Shared security primitives: JWT (HS256), scrypt password hashing, TOTP (MFA).
// No external dependencies — everything uses Node's built-in `crypto`.
const crypto = require('crypto');

// ── JWT secret ────────────────────────────────────────────────────────────
function getJwtSecret() {
  const s = process.env.PW_JWT_SECRET;
  if (!s || s.length < 32) {
    throw new Error('PW_JWT_SECRET must be set to a value of at least 32 characters.');
  }
  return s;
}

const TOKEN_TTL_SECONDS = 60 * 60 * 24 * 30; // 30 days

// base64url encode/decode (no reliance on Node's 'base64url' encoding).
function b64url(buf) {
  return Buffer.from(buf).toString('base64')
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
function b64urlDecode(str) {
  return Buffer.from(String(str).replace(/-/g, '+').replace(/_/g, '/'), 'base64');
}

// HS256 JWT: header.payload.signature.
function signToken(user) {
  const header = { alg: 'HS256', typ: 'JWT' };
  const now = Math.floor(Date.now() / 1000);
  const payload = { sub: user.user_id, username: user.username || null, email: user.email || null, language: user.language || null, iat: now, exp: now + TOKEN_TTL_SECONDS };
  const enc = `${b64url(JSON.stringify(header))}.${b64url(JSON.stringify(payload))}`;
  const sig = b64url(crypto.createHmac('sha256', getJwtSecret()).update(enc).digest());
  return `${enc}.${sig}`;
}

// Returns decoded payload on success, or null on invalid/expired signature.
function verifyToken(token) {
  if (!token) return null;
  const parts = String(token).split('.');
  if (parts.length !== 3) return null;
  const enc = `${parts[0]}.${parts[1]}`;
  const expect = b64url(crypto.createHmac('sha256', getJwtSecret()).update(enc).digest());
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

// ── Password hashing (scrypt) ─────────────────────────────────────────────
// Format: scrypt$N$r$p$saltHex$hashHex  (salt embedded, no separate column needed).
const SCRYPT = { N: 16384, r: 8, p: 1, keylen: 64 };

function hashPassword(password) {
  const salt = crypto.randomBytes(16);
  const hash = crypto.scryptSync(String(password), salt, SCRYPT.keylen, SCRYPT);
  return `scrypt$${SCRYPT.N}$${SCRYPT.r}$${SCRYPT.p}$${salt.toString('hex')}$${hash.toString('hex')}`;
}

function verifyPassword(password, stored) {
  if (!stored || !stored.startsWith('scrypt$')) return false;
  const parts = String(stored).split('$');
  if (parts.length !== 6) return false;
  const [, N, r, p, saltHex, hashHex] = parts;
  const salt = Buffer.from(saltHex, 'hex');
  const expected = Buffer.from(hashHex, 'hex');
  const hash = crypto.scryptSync(String(password), salt, expected.length, { N: +N, r: +r, p: +p });
  return hash.length === expected.length && crypto.timingSafeEqual(hash, expected);
}

// Legacy salted SHA-256 (pre-scrypt accounts). Used only to verify + migrate.
function legacySha256(password, salt) {
  return crypto.createHash('sha256').update(`${salt}:${password}`).digest('hex');
}
function isLegacyHash(stored) {
  return !!stored && !stored.startsWith('scrypt$');
}

// ── TOTP (RFC 6238) — time-based MFA ──────────────────────────────────────
const B32 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';

function base32Encode(buf) {
  let bits = 0, value = 0, out = '';
  for (const byte of buf) {
    value = (value << 8) | byte; bits += 8;
    while (bits >= 5) { out += B32[(value >>> (bits - 5)) & 31]; bits -= 5; }
  }
  if (bits > 0) out += B32[(value << (5 - bits)) & 31];
  return out;
}

function base32Decode(str) {
  const clean = String(str).toUpperCase().replace(/[^A-Z2-7]/g, '');
  let bits = 0, value = 0;
  const out = [];
  for (const ch of clean) {
    value = (value << 5) | B32.indexOf(ch); bits += 5;
    if (bits >= 8) { out.push((value >>> (bits - 8)) & 0xff); bits -= 8; }
  }
  return Buffer.from(out);
}

function generateTotpSecret() {
  return base32Encode(crypto.randomBytes(20));
}

function totpAt(secretB32, counter, digits = 6) {
  const key = base32Decode(secretB32);
  const msg = Buffer.alloc(8);
  msg.writeBigUInt64BE(BigInt(counter));
  const hmac = crypto.createHmac('sha1', key).update(msg).digest();
  const off = hmac[hmac.length - 1] & 0x0f;
  const code = (hmac.readUInt32BE(off) & 0x7fffffff) % 10 ** digits;
  return String(code).padStart(digits, '0');
}

// Accepts ±1 time step (30s) to tolerate clock skew.
function verifyTotp(secretB32, code) {
  const want = String(code || '').trim();
  if (!/^\d{6}$/.test(want)) return false;
  const now = Math.floor(Date.now() / 1000 / 30);
  const wantBuf = Buffer.from(want);
  for (let i = -1; i <= 1; i++) {
    const cur = Buffer.from(totpAt(secretB32, now + i));
    if (cur.length === wantBuf.length && crypto.timingSafeEqual(cur, wantBuf)) return true;
  }
  return false;
}

function otpauthUrl(secretB32, username) {
  const label = encodeURIComponent(`PuzzleWorld:${username}`);
  return `otpauth://totp/${label}?secret=${secretB32}&issuer=PuzzleWorld`;
}

module.exports = {
  TOKEN_TTL_SECONDS,
  signToken,
  verifyToken,
  hashPassword,
  verifyPassword,
  legacySha256,
  isLegacyHash,
  generateTotpSecret,
  verifyTotp,
  totpAt,
  otpauthUrl,
};

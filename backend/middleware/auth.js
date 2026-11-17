'use strict';
// Auth middleware: reads the JWT from either the Authorization header (Android
// app) or the `token` httpOnly cookie (web portal), verifies it, and attaches
// `req.user` ({ user_id, username }). Also provides `requireSelf` to enforce
// object-level authorization (a caller may only act on their own user id).
const { verifyToken } = require('../lib/security');
const { t } = require('../i18n');

// Extract the raw token from Bearer header or cookie (dependency-free parse).
function readToken(req) {
  const h = String(req.headers.authorization || '');
  if (h.startsWith('Bearer ')) return h.slice(7).trim();
  const cookie = String(req.headers.cookie || '');
  const m = /(?:^|;\s*)token=([^;]+)/.exec(cookie);
  return m ? m[1] : null;
}

function requireAuth(req, res, next) {
  const token = readToken(req);
  const payload = verifyToken(token);
  if (!payload) {
    return res.status(401).json({ error: t(req, 'auth_required') });
  }
  req.user = { user_id: payload.sub, username: payload.username || null, email: payload.email || null, language: payload.language || null };
  next();
}

// Factory: build a middleware that requires the authenticated user to match a
// given user id (from a path param, query param, or body field).
function requireSelf(getUserId) {
  return (req, res, next) => {
    const target = getUserId(req);
    if (!target || target !== req.user.user_id) {
      return res.status(403).json({ error: t(req, 'forbidden') });
    }
    next();
  };
}

module.exports = { requireAuth, requireSelf, readToken };

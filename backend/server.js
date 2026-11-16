'use strict';
const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
const compression = require('compression');
const path = require('path');

// Load backend/.env (PW_JWT_SECRET, PW_DB_PATH, PORT, CORS_ORIGINS, …) if present.
// Node 20.12+ built-in — no dotenv dependency. Absent file is ignored so the
// shell/environment can supply these instead.
try { process.loadEnvFile(path.join(__dirname, '.env')); } catch { /* optional */ }

const app = express();
const PORT = process.env.PORT || 3000;
const { seedIfEmpty, seedDemoAccount, seedDemoLeaderboard } = require('./seed');

// Render terminates TLS and sets X-Forwarded-*; trust one proxy hop so
// `req.secure` / `req.ip` / `req.protocol` are correct.
app.set('trust proxy', 1);

// --- Compress all text responses (gzip/deflate/br) to shrink API payloads ---
app.use(compression());

// --- Security headers (CSP, X-Content-Type-Options, X-Frame-Options, HSTS…) ---
app.use(helmet({
  contentSecurityPolicy: {
    directives: {
      defaultSrc: ["'self'"],
      scriptSrc: ["'self'"],
      styleSrc: ["'self'", "'unsafe-inline'", 'https://fonts.googleapis.com'],
      fontSrc: ["'self'", 'https://fonts.gstatic.com', 'data:'],
      imgSrc: ["'self'", 'data:'],
      connectSrc: ["'self'"],
      objectSrc: ["'none'"],
      baseUri: ["'self'"],
      frameAncestors: ["'self'"],
    },
  },
  crossOriginResourcePolicy: { policy: 'same-origin' },
}));

// --- CORS: allow only known origins (native Android app sends no browser CORS).
const ALLOWED_ORIGINS = (process.env.CORS_ORIGINS
  || 'https://puzzleworld-backend.onrender.com,http://localhost:3000,http://127.0.0.1:3000')
  .split(',').map((s) => s.trim()).filter(Boolean);
app.use(cors({
  origin(origin, cb) { cb(null, !origin || ALLOWED_ORIGINS.includes(origin)); },
  credentials: true,
}));

// --- Body size limit (defense against abusive payloads; no file upload exists).
app.use(express.json({ limit: '100kb' }));

// --- Rate limiting: strict on auth (brute-force defense), lenient elsewhere.
const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, max: 30, standardHeaders: true, legacyHeaders: false,
  message: { error: 'Too many attempts. Please try again later.' },
});
const apiLimiter = rateLimit({
  windowMs: 60 * 1000, max: 300, standardHeaders: true, legacyHeaders: false,
  message: { error: 'Too many requests. Please slow down.' },
});
app.use('/api/auth', authLimiter);
app.use('/api', apiLimiter);

// --- Cache policy: catalogue + health are public & short-lived; user data is never cached.
app.use('/api', (req, res, next) => {
  if (req.method !== 'GET') { res.set('Cache-Control', 'no-store'); return next(); }
  const p = req.path;
  const cacheable = p === '/health' || p === '/rooms' || p.startsWith('/rooms/');
  res.set('Cache-Control', cacheable ? 'public, max-age=30, stale-while-revalidate=120' : 'no-store');
  next();
});

// --- Web portal (static frontend) ---
app.use(express.static(path.join(__dirname, 'public'), {
  setHeaders(res, filePath) {
    if (filePath.endsWith('.html')) {
      res.setHeader('Cache-Control', 'public, max-age=0, must-revalidate');
    } else if (/\.(css|js|png|jpe?g|svg|webp|woff2?)$/i.test(filePath)) {
      res.setHeader('Cache-Control', 'public, max-age=3600');
    }
  },
}));

app.get('/api/health', (_req, res) => res.json({ status: 'ok', service: 'puzzleworld-backend', version: '1.0.0' }));

app.use('/api/auth', require('./routes/auth'));
app.use('/api/rooms', require('./routes/rooms'));
app.use('/api/progress', require('./routes/progress'));
app.use('/api/daily-challenge', require('./routes/challenges'));
app.use('/api/stats', require('./routes/stats'));
app.use('/api/user', require('./routes/profile'));

app.use((_req, res) => res.status(404).json({ error: 'Not found' }));

// --- Error handling: clean JSON, never echo stack traces or raw internals to
// clients, and keep request bodies/tokens out of the logs.
app.use((err, _req, res, _next) => {
  const status = (err && (err.status || err.statusCode)) || 500;
  const msg = err && err.type === 'entity.parse.failed'
    ? 'Invalid JSON body'
    : (status === 500 ? 'Internal server error' : (err.message || 'Bad request'));
  if (process.env.NODE_ENV === 'production') {
    console.error('[error]', status, err && err.name);
  } else {
    console.error('[error]', status, err && err.stack ? err.stack : err);
  }
  res.status(status).json({ error: msg });
});

// Keep the process alive on async failures instead of crashing (Node 15+ exits on unhandledRejection).
process.on('unhandledRejection', (reason) => {
  console.error('[unhandledRejection]', process.env.NODE_ENV === 'production' ? 'suppressed' : reason);
});
process.on('uncaughtException', (err) => {
  console.error('[uncaughtException]', process.env.NODE_ENV === 'production' ? err && err.name : (err && err.stack));
  process.exit(1); // undefined state — log and restart rather than lingering as a zombie
});

// Populate rooms/levels if the database is empty (idempotent — never wipes progress).
seedIfEmpty();
// Ensure the demo login survives ephemeral DB wipes (idempotent).
seedDemoAccount();
// Populate a demo leaderboard on empty DBs (idempotent).
seedDemoLeaderboard();

app.listen(PORT, '0.0.0.0', () => {
  console.log(`PuzzleWorld backend → http://0.0.0.0:${PORT} (all interfaces)`);
  console.log(`  GET  /api/health`);
  console.log(`  POST /api/auth/register`);
  console.log(`  POST /api/auth/login`);
  console.log(`  POST /api/auth/logout`);
  console.log(`  GET  /api/auth/me`);
  console.log(`  POST /api/auth/mfa/enable|verify|disable`);
  console.log(`  GET  /api/rooms`);
  console.log(`  POST /api/progress`);
  console.log(`  GET  /api/progress  (leaderboard)`);
  console.log(`  GET  /api/daily-challenge`);
  console.log(`  GET  /api/stats/:userId`);
  console.log(`  GET  /api/user/profile`);
});

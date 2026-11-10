const express = require('express');
const cors = require('cors');
const path = require('path');

// Load backend/.env (PW_JWT_SECRET, PW_DB_PATH, PORT, …) if present.
// Node 20.12+ built-in — no dotenv dependency. Absent file is ignored so the
// shell/environment can supply these instead.
try { process.loadEnvFile(path.join(__dirname, '.env')); } catch { /* optional */ }

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

// --- Web portal (static frontend) ---
app.use(express.static(path.join(__dirname, 'public')));

app.get('/api/health', (_req, res) => res.json({ status: 'ok', service: 'puzzleworld-backend', version: '1.0.0' }));

app.use('/api/auth', require('./routes/auth'));
app.use('/api/rooms', require('./routes/rooms'));
app.use('/api/progress', require('./routes/progress'));
app.use('/api/daily-challenge', require('./routes/challenges'));
app.use('/api/stats', require('./routes/stats'));
app.use('/api/user', require('./routes/profile'));

app.use((_req, res) => res.status(404).json({ error: 'Not found' }));

// --- Error handling: return clean JSON instead of Express's HTML 500 + stack trace.
app.use((err, _req, res, _next) => {
  const status = (err && (err.status || err.statusCode)) || 500;
  const msg = err && err.type === 'entity.parse.failed'
    ? 'Invalid JSON body'
    : (status === 500 ? 'Internal server error' : (err.message || 'Bad request'));
  console.error('[error]', err && err.stack ? err.stack : err);
  res.status(status).json({ error: msg });
});

// Keep the process alive on async failures instead of crashing (Node 15+ exits on unhandledRejection).
process.on('unhandledRejection', (reason) => console.error('[unhandledRejection]', reason));
process.on('uncaughtException', (err) => {
  console.error('[uncaughtException]', err && err.stack ? err.stack : err);
  process.exit(1); // undefined state — log and restart rather than lingering as a zombie
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`PuzzleWorld backend → http://0.0.0.0:${PORT} (all interfaces)`);
  console.log(`  GET  /api/health`);
  console.log(`  POST /api/auth/register`);
  console.log(`  POST /api/auth/login`);
  console.log(`  GET  /api/auth/me`);
  console.log(`  GET  /api/rooms`);
  console.log(`  GET  /api/rooms/:roomId`);
  console.log(`  GET  /api/rooms/:roomId/levels/:levelId`);
  console.log(`  POST /api/progress`);
  console.log(`  GET  /api/progress/:userId`);
  console.log(`  GET  /api/progress  (leaderboard)`);
  console.log(`  GET  /api/daily-challenge`);
  console.log(`  GET  /api/daily-challenge/:date`);
  console.log(`  GET  /api/stats/:userId`);
  console.log(`  GET  /api/user/profile`);
});
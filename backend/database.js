const Database = require('better-sqlite3');
const path = require('path');

const DB_PATH = process.env.PW_DB_PATH || path.join(__dirname, 'puzzleworld.db');
let db;

function getDb() {
  if (!db) { db = new Database(DB_PATH); db.pragma('journal_mode = WAL'); db.pragma('foreign_keys = ON'); db.pragma('busy_timeout = 5000'); initSchema(); }
  return db;
}

function ensureColumn(table, col, def) {
  const cols = db.prepare(`PRAGMA table_info(${table})`).all();
  if (!cols.some(c => c.name === col)) {
    db.exec(`ALTER TABLE ${table} ADD COLUMN ${col} ${def}`);
  }
}

function initSchema() {
  db.exec(`
    CREATE TABLE IF NOT EXISTS rooms (
      room_id TEXT PRIMARY KEY, room_name TEXT NOT NULL, theme TEXT DEFAULT '',
      description TEXT DEFAULT '', room_graph TEXT DEFAULT '{}',
      puzzle_type TEXT DEFAULT ''
    );
    CREATE TABLE IF NOT EXISTS levels (
      level_id TEXT PRIMARY KEY, room_id TEXT NOT NULL REFERENCES rooms(room_id),
      puzzle_type TEXT DEFAULT '', title TEXT NOT NULL, difficulty TEXT NOT NULL,
      mechanic TEXT NOT NULL, hints TEXT DEFAULT '[]', sort_order INTEGER DEFAULT 0
    );
    CREATE TABLE IF NOT EXISTS players (
      user_id TEXT PRIMARY KEY, username TEXT NOT NULL,
      created_at TEXT DEFAULT (datetime('now')), last_sync TEXT DEFAULT (datetime('now')),
      score INTEGER DEFAULT 0, streak INTEGER DEFAULT 0, best_streak INTEGER DEFAULT 0,
      hints_used INTEGER DEFAULT 0, total_attempts INTEGER DEFAULT 0, current_room TEXT
    );
    CREATE TABLE IF NOT EXISTS player_progress (
      user_id TEXT NOT NULL REFERENCES players(user_id), level_id TEXT NOT NULL,
      is_solved INTEGER DEFAULT 0, attempts INTEGER DEFAULT 0, hints_used INTEGER DEFAULT 0, solved_at TEXT,
      PRIMARY KEY (user_id, level_id)
    );
    CREATE TABLE IF NOT EXISTS daily_challenges (
      challenge_date TEXT PRIMARY KEY, seed INTEGER NOT NULL, room_id TEXT NOT NULL,
      level_id TEXT NOT NULL REFERENCES levels(level_id)
    );
  `);

  db.exec(`
    CREATE TABLE IF NOT EXISTS users (
      user_id TEXT PRIMARY KEY,
      username TEXT NOT NULL UNIQUE,
      password_hash TEXT NOT NULL,
      salt TEXT NOT NULL,
      created_at TEXT DEFAULT (datetime('now'))
    );
  `);

  // Migration: drop the FK on player_progress.level_id so any app-reported level ID is accepted.
  // (level_id stays a column; solved_count = COUNT(rows) is unaffected.)
  const pp = db.prepare("SELECT sql FROM sqlite_master WHERE type='table' AND name='player_progress'").get();
  if (pp && /REFERENCES\s+levels/i.test(pp.sql)) {
    db.exec(`
      ALTER TABLE player_progress RENAME TO player_progress_old;
      CREATE TABLE player_progress (
        user_id TEXT NOT NULL REFERENCES players(user_id), level_id TEXT NOT NULL,
        is_solved INTEGER DEFAULT 0, attempts INTEGER DEFAULT 0, hints_used INTEGER DEFAULT 0, solved_at TEXT,
        PRIMARY KEY (user_id, level_id)
      );
      INSERT INTO player_progress SELECT * FROM player_progress_old;
      DROP TABLE player_progress_old;
    `);
  }

  // Migrations for pre-existing DBs (backward compatible)
  ensureColumn('rooms',  'puzzle_type', "TEXT DEFAULT ''");
  ensureColumn('levels', 'puzzle_type', "TEXT DEFAULT ''");
  ensureColumn('players', 'score',          'INTEGER DEFAULT 0');
  ensureColumn('players', 'streak',         'INTEGER DEFAULT 0');
  ensureColumn('players', 'best_streak',    'INTEGER DEFAULT 0');
  ensureColumn('players', 'hints_used',     'INTEGER DEFAULT 0');
  ensureColumn('players', 'total_attempts', 'INTEGER DEFAULT 0');
  ensureColumn('players', 'current_room',   'TEXT');

  // Performance indexes: per-room level lookups (routes/rooms.js) and leaderboard/progress
  // retrieval (routes/progress.js, routes/stats.js) filter on these columns on every query.
  db.exec(`
    CREATE INDEX IF NOT EXISTS idx_levels_room ON levels(room_id, sort_order);
    CREATE INDEX IF NOT EXISTS idx_progress_solved ON player_progress(user_id, is_solved);
  `);
}

module.exports = { getDb };
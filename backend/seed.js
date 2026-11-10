const { getDb } = require('./database');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

// The Android app's bundled assets are the single source of truth for room/level content.
// Override with ROOMS_ASSETS_DIR if the app lives elsewhere.
const ASSETS_DIR = process.env.ROOMS_ASSETS_DIR
  || path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'rooms');

function insertRooms(db) {
  const insRoom = db.prepare('INSERT INTO rooms (room_id, room_name, theme, description, puzzle_type, room_graph) VALUES (?,?,?,?,?,?)');
  const insLevel = db.prepare('INSERT INTO levels (level_id, room_id, puzzle_type, title, difficulty, mechanic, hints, sort_order) VALUES (?,?,?,?,?,?,?,?)');

  const files = fs.existsSync(ASSETS_DIR)
    ? fs.readdirSync(ASSETS_DIR).filter(f => f.endsWith('.json')).sort()
    : [];

  if (files.length === 0) {
    console.error(`No room asset files found in ${ASSETS_DIR}`);
    console.error('Point ROOMS_ASSETS_DIR at the app/src/main/assets/rooms directory.');
    return 0;
  }

  let roomCount = 0, levelCount = 0;
  for (const f of files) {
    const data = JSON.parse(fs.readFileSync(path.join(ASSETS_DIR, f), 'utf8'));
    const puzzleType = data.puzzle_type || (data.levels?.[0]?.mechanic?.mechanic_type) || '';
    insRoom.run(data.room_id, data.room_name, data.theme || '', data.description || '', puzzleType, JSON.stringify(data.room_graph || {}));
    roomCount++;
    (data.levels || []).forEach((lvl, i) => {
      insLevel.run(
        lvl.level_id,
        data.room_id,
        puzzleType,
        lvl.title,
        lvl.difficulty,
        JSON.stringify(lvl.mechanic || {}),
        JSON.stringify(lvl.hints || []),
        i + 1
      );
      levelCount++;
    });
  }
  return roomCount;
}

// Destructive: wipe rooms/levels/players/progress and reseed. Used by `npm run seed`.
function seed() {
  const db = getDb();
  db.exec('DELETE FROM daily_challenges; DELETE FROM player_progress; DELETE FROM players; DELETE FROM levels; DELETE FROM rooms;');
  const n = insertRooms(db);
  console.log(`Seeded ${n} rooms (mirrored from app assets: ${ASSETS_DIR}).`);
}

// Idempotent: only seeds when the rooms table is empty. Called on server startup so a
// fresh/empty database (e.g. a new cloud deploy) always has content without wiping progress.
function seedIfEmpty() {
  const db = getDb();
  const { c } = db.prepare('SELECT COUNT(*) AS c FROM rooms').get();
  if (c > 0) return 0;
  const n = insertRooms(db);
  if (n > 0) console.log(`[seed] Empty database — seeded ${n} rooms from app assets.`);
  return n;
}

// Idempotent: guarantee a known demo login exists on every startup. Render's free
// tier uses ephemeral SQLite, so this account would otherwise vanish on redeploy.
const DEMO_USERNAME = 'Dipesh';
const DEMO_PASSWORD = '12345678';

function seedDemoAccount() {
  const db = getDb();
  const exists = db.prepare('SELECT user_id FROM users WHERE username = ?').get(DEMO_USERNAME);
  if (exists) return false;
  const salt = crypto.randomBytes(16).toString('hex');
  const passwordHash = crypto.createHash('sha256').update(`${salt}:${DEMO_PASSWORD}`).digest('hex');
  const userId = 'u_demo_dipesh';
  db.transaction(() => {
    db.prepare('INSERT INTO users (user_id, username, password_hash, salt) VALUES (?, ?, ?, ?)')
      .run(userId, DEMO_USERNAME, passwordHash, salt);
    db.prepare('INSERT OR IGNORE INTO players (user_id, username) VALUES (?, ?)')
      .run(userId, DEMO_USERNAME);
  })();
  console.log(`[seed] Demo account "${DEMO_USERNAME}" created.`);
  return true;
}

module.exports = { seed, seedIfEmpty, seedDemoAccount };

if (require.main === module) seed();

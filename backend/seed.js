const { getDb } = require('./database');
const fs = require('fs');
const path = require('path');

// The Android app's bundled assets are the single source of truth for room/level content.
// Override with ROOMS_ASSETS_DIR if the app lives elsewhere.
const ASSETS_DIR = process.env.ROOMS_ASSETS_DIR
  || path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'rooms');

function seed() {
  const db = getDb();
  db.exec('DELETE FROM daily_challenges; DELETE FROM player_progress; DELETE FROM players; DELETE FROM levels; DELETE FROM rooms;');

  const insRoom = db.prepare('INSERT INTO rooms (room_id, room_name, theme, description, puzzle_type, room_graph) VALUES (?,?,?,?,?,?)');
  const insLevel = db.prepare('INSERT INTO levels (level_id, room_id, puzzle_type, title, difficulty, mechanic, hints, sort_order) VALUES (?,?,?,?,?,?,?,?)');

  const files = fs.existsSync(ASSETS_DIR)
    ? fs.readdirSync(ASSETS_DIR).filter(f => f.endsWith('.json')).sort()
    : [];

  if (files.length === 0) {
    console.error(`No room asset files found in ${ASSETS_DIR}`);
    console.error('Point ROOMS_ASSETS_DIR at the app/src/main/assets/rooms directory.');
    process.exit(1);
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

  console.log(`Seeded ${roomCount} rooms, ${levelCount} puzzles (mirrored from app assets: ${ASSETS_DIR}).`);
}

seed();
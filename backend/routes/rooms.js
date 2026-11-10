const express = require('express');
const router = express.Router();
const wrap = require('./wrap');
const { getDb } = require('../database');

// GET /api/rooms
router.get('/', wrap((req, res) => {
  const db = getDb();
  const rooms = db.prepare(`
    SELECT r.*, COUNT(l.level_id) AS level_count
    FROM rooms r LEFT JOIN levels l ON l.room_id = r.room_id
    GROUP BY r.room_id ORDER BY r.room_id
  `).all();
  res.json({ rooms: rooms.map(r => ({
    room_id: r.room_id, room_name: r.room_name, theme: r.theme,
    description: r.description, puzzle_type: r.puzzle_type, level_count: r.level_count,
    room_graph: JSON.parse(r.room_graph || '{}')
  })) });
}));

// GET /api/rooms/:roomId
router.get('/:roomId', wrap((req, res) => {
  const db = getDb();
  const room = db.prepare('SELECT * FROM rooms WHERE room_id = ?').get(req.params.roomId);
  if (!room) return res.status(404).json({ error: 'Room not found' });
  const levels = db.prepare('SELECT * FROM levels WHERE room_id = ? ORDER BY sort_order').all(req.params.roomId);
  res.json({
    room_id: room.room_id, room_name: room.room_name, theme: room.theme,
    description: room.description, puzzle_type: room.puzzle_type,
    room_graph: JSON.parse(room.room_graph || '{}'),
    levels: levels.map(l => ({
      level_id: l.level_id, puzzle_type: l.puzzle_type, title: l.title, difficulty: l.difficulty,
      mechanic: JSON.parse(l.mechanic), hints: JSON.parse(l.hints || '[]')
    }))
  });
}));

// GET /api/rooms/:roomId/levels/:levelId
router.get('/:roomId/levels/:levelId', wrap((req, res) => {
  const db = getDb();
  const level = db.prepare(`
    SELECT l.*, r.room_name FROM levels l JOIN rooms r ON r.room_id = l.room_id
    WHERE l.level_id = ? AND l.room_id = ?
  `).get(req.params.levelId, req.params.roomId);
  if (!level) return res.status(404).json({ error: 'Level not found' });
  res.json({
    level_id: level.level_id, room_id: level.room_id, room_name: level.room_name,
    puzzle_type: level.puzzle_type, title: level.title, difficulty: level.difficulty,
    mechanic: JSON.parse(level.mechanic), hints: JSON.parse(level.hints || '[]')
  });
}));

module.exports = router;
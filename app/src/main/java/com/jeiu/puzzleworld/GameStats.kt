package com.jeiu.puzzleworld

import android.content.Context
import android.content.SharedPreferences

/**
 * Unified cross-game stats tracker. Every puzzle solve in Escape Rooms, Sudoku,
 * or Word Descrambler records here, feeding the Home dashboard, achievements,
 * and the local leaderboard (top scores keyed by username).
 */
object GameStats {

    private const val PREFS = "puzzleworld_stats"
    private const val KEY_TOTAL_SOLVED = "total_solved"
    private const val KEY_TOTAL_SCORE = "total_score"
    private const val KEY_ESCAPE_ROOMS = "escape_rooms_solved"
    private const val KEY_SUDOKU = "sudoku_solved"
    private const val KEY_WORDS = "word_scramble_solved"
    private const val KEY_LEADERBOARD = "leaderboard"

    /** Record one solved puzzle worth [points] in game [game]. */
    fun recordSolve(ctx: Context, game: String, points: Int) {
        val p = prefs(ctx)
        p.edit()
            .putInt(KEY_TOTAL_SOLVED, p.getInt(KEY_TOTAL_SOLVED, 0) + 1)
            .putInt(KEY_TOTAL_SCORE, p.getInt(KEY_TOTAL_SCORE, 0) + points)
            .putInt(keyFor(game), count(ctx, game) + 1)
            .apply()
        // update this user's leaderboard total
        val user = currentUsername(ctx)
        val board = mutableMapOf<String, Int>()
        leaderboard(ctx).forEach { (n, s) -> board[n] = maxOf(board[n] ?: 0, s) }
        board[user] = (board[user] ?: 0) + points
        saveLeaderboard(p, board)
        Achievements.checkUnlocks(ctx)
    }

    fun totalSolved(ctx: Context) = prefs(ctx).getInt(KEY_TOTAL_SOLVED, 0)
    fun totalScore(ctx: Context) = prefs(ctx).getInt(KEY_TOTAL_SCORE, 0)

    fun count(ctx: Context, game: String) = prefs(ctx).getInt(keyFor(game), 0)

    private fun keyFor(game: String) = when (game) {
        "escape_room", "escape_rooms" -> KEY_ESCAPE_ROOMS
        "sudoku" -> KEY_SUDOKU
        "word_scramble", "words" -> KEY_WORDS
        else -> KEY_TOTAL_SOLVED
    }

    fun currentUsername(ctx: Context): String =
        ctx.getSharedPreferences("puzzleworld_account", Context.MODE_PRIVATE)
            .getString("username", "Guest") ?: "Guest"

    /** Sorted descending list of (username, total score). */
    fun leaderboard(ctx: Context): List<Pair<String, Int>> {
        val raw = prefs(ctx).getString(KEY_LEADERBOARD, null) ?: return emptyList()
        val map = mutableMapOf<String, Int>()
        raw.split("\n").forEach { line ->
            val i = line.lastIndexOf(':')
            if (i > 0) {
                val name = line.substring(0, i)
                val score = line.substring(i + 1).toIntOrNull() ?: 0
                map[name] = maxOf(map[name] ?: 0, score)
            }
        }
        return map.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    private fun saveLeaderboard(p: SharedPreferences, board: Map<String, Int>) {
        val serialized = board.entries
            .sortedByDescending { it.value }
            .take(50)
            .joinToString("\n") { "${it.key}:${it.value}" }
        p.edit().putString(KEY_LEADERBOARD, serialized).apply()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

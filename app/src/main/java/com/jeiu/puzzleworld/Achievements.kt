package com.jeiu.puzzleworld

import android.content.Context

/**
 * Achievement system. Each achievement has an id, emoji, title, description,
 * and a condition evaluated against [GameStats]. Unlocked achievements are
 * persisted so they unlock once and stay unlocked.
 */
object Achievements {

    private const val PREFS = "puzzleworld_achievements"

    data class Achievement(
        val id: String,
        val emoji: String,
        val title: String,
        val description: String,
        val condition: (Context) -> Boolean
    )

    val ALL = listOf(
        Achievement("first_steps", "🎯", "First Steps",
            "Solve your very first puzzle", { c -> GameStats.totalSolved(c) >= 1 }),
        Achievement("novice", "🧠", "Puzzle Novice",
            "Solve 10 puzzles", { c -> GameStats.totalSolved(c) >= 10 }),
        Achievement("master", "👑", "Puzzle Master",
            "Solve 50 puzzles", { c -> GameStats.totalSolved(c) >= 50 }),
        Achievement("score_chaser", "💎", "Score Chaser",
            "Earn 500 total points", { c -> GameStats.totalScore(c) >= 500 }),
        Achievement("high_roller", "🚀", "High Roller",
            "Earn 2,000 total points", { c -> GameStats.totalScore(c) >= 2000 }),
        Achievement("room_clearer", "🚪", "Room Clearer",
            "Complete an entire escape room (10 puzzles)", { c -> GameStats.count(c, "escape_room") >= 10 }),
        Achievement("sudoku_solver", "🔢", "Sudoku Solver",
            "Solve a Sudoku puzzle", { c -> GameStats.count(c, "sudoku") >= 1 }),
        Achievement("word_smith", "🔤", "Word Smith",
            "Unscramble 10 words", { c -> GameStats.count(c, "word_scramble") >= 10 })
    )

    fun isUnlocked(ctx: Context, id: String): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(id, false)

    fun unlockedCount(ctx: Context) = ALL.count { isUnlocked(ctx, it.id) }

    /** Evaluate every achievement and persist any newly satisfied ones. */
    fun checkUnlocks(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val edit = p.edit()
        for (a in ALL) {
            if (!p.getBoolean(a.id, false) && a.condition(ctx)) edit.putBoolean(a.id, true)
        }
        edit.apply()
    }
}

package com.jeiu.puzzleworld

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AlertDialog

/**
 * Tracks which mechanic tutorials the player has seen and shows inline help
 * dialogs on first encounter. Holds mechanic-specific instruction text.
 */
object TutorialEngine {

    private const val PREFS = "puzzleworld_tutorials"
    private const val KEY_PREFIX = "seen_"

    fun shouldShow(ctx: Context, mechanicType: String): Boolean {
        val p = prefs(ctx)
        val key = KEY_PREFIX + mechanicType
        if (p.getBoolean(key, false)) return false
        p.edit().putBoolean(key, true).apply()
        return true
    }

    fun show(ctx: Context, mechanicType: String) {
        val text = when (mechanicType) {
            "wire_maze"       -> ctx.getString(R.string.tutorial_wire_maze)
            "cipher"          -> ctx.getString(R.string.tutorial_cipher)
            "riddle"          -> ctx.getString(R.string.tutorial_riddle)
            "deduction_grid"  -> ctx.getString(R.string.tutorial_deduction)
            "mechanical_lock" -> ctx.getString(R.string.tutorial_mechanical_lock)
            "pattern_lock"    -> ctx.getString(R.string.tutorial_pattern_lock)
            "color_sequence"  -> ctx.getString(R.string.tutorial_color_sequence)
            "sudoku"          -> ctx.getString(R.string.tutorial_sudoku)
            "word_scramble"   -> ctx.getString(R.string.tutorial_word_scramble)
            else -> "Complete the puzzle to advance!"
        }
        AlertDialog.Builder(ctx, R.style.HintDialog)
            .setTitle("📖 How to Play")
            .setMessage(text)
            .setPositiveButton("Got it!", null)
            .show()
    }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
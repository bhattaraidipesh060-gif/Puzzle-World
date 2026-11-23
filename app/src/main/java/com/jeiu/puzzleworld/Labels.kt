package com.jeiu.puzzleworld

import android.content.Context

/**
 * Localization helpers for enum-like values that are stored as English strings
 * (difficulty levels, mechanic types) but must be displayed in the active locale.
 */
object Labels {

    fun difficulty(ctx: Context, value: String): String = when (value) {
        "Easy"   -> ctx.getString(R.string.difficulty_easy)
        "Medium" -> ctx.getString(R.string.difficulty_medium)
        "Hard"   -> ctx.getString(R.string.difficulty_hard)
        "Expert" -> ctx.getString(R.string.difficulty_expert)
        else     -> value
    }

    fun mechanic(ctx: Context, type: String): String = when (type) {
        "wire_maze"        -> ctx.getString(R.string.mechanic_wire_maze)
        "cipher"           -> ctx.getString(R.string.mechanic_cipher)
        "riddle"           -> ctx.getString(R.string.mechanic_riddle)
        "deduction_grid"   -> ctx.getString(R.string.mechanic_deduction_grid)
        "mechanical_lock"  -> ctx.getString(R.string.mechanic_mechanical_lock)
        "pattern_lock"     -> ctx.getString(R.string.mechanic_pattern_lock)
        "color_sequence"   -> ctx.getString(R.string.mechanic_color_sequence)
        "vault_dial"       -> ctx.getString(R.string.mechanic_vault_dial)
        "cipher_wheel"     -> ctx.getString(R.string.mechanic_cipher_wheel)
        "gear_alignment"   -> ctx.getString(R.string.mechanic_gear_alignment)
        "sliding_tile"     -> ctx.getString(R.string.mechanic_sliding_tile)
        "terminal_hack"    -> ctx.getString(R.string.mechanic_terminal_hack)
        "potion_mix"       -> ctx.getString(R.string.mechanic_potion_mix)
        "coordinate_maze"  -> ctx.getString(R.string.mechanic_coordinate_maze)
        "lock_pin"         -> ctx.getString(R.string.mechanic_lock_pin)
        "neon_simon"       -> ctx.getString(R.string.mechanic_neon_simon)
        else               -> type.replace('_', ' ')
    }
}

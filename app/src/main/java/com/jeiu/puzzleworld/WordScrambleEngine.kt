package com.jeiu.puzzleworld

import kotlin.random.Random

/**
 * Seed-based word scramble generator with difficulty tiers based on word length.
 * "Easy" = 3-5 letters, "Medium" = 5-7 letters, "Hard" = 7-9 letters.
 * Graded hints reveal letters progressively.
 */
object WordScrambleEngine {

    private val WORDS = listOf(
        // Easy (3-5 letters)
        "star", "moon", "fire", "wind", "time", "snap", "cube", "lens",
        "key", "map", "gem", "orb", "trap", "gate", "clue", "code", "hint",
        "lock", "door", "vault", "wand", "coin", "ring", "mask", "gold",
        // Medium (5-7 letters)
        "planet", "rocket", "puzzle", "riddle", "cipher", "galaxy", "cosmos",
        "nebula", "comet", "magnet", "energy", "signal", "circuit", "genius",
        "matrix", "vector", "quasar", "orbit", "sphere", "prism", "photon",
        "vortex", "wisdom", "castle", "bridge", "jungle", "garden",
        "silver", "golden", "crimson", "amber", "jigsaw", "logic", "trivia",
        "shadow", "candle", "secret", "mystic", "legend", "beacon", "lantern",
        "marble", "engine", "shield", "temple",
        // Hard (7-9 letters)
        "adventure", "mystery", "treasure", "paradox", "horizon", "volcano",
        "quantum", "gravity", "compass", "eclipse", "diamond", "artisan",
        "miracle", "crystal", "rainbow", "fortune", "machine", "obscure",
        "phantom", "revolve", "scatter", "cascade", "fortress", "cryptic",
        // Expert (9-12 letters)
        "labyrinth", "enigmatic", "conundrum", "perplexing", "brainteaser",
        "illumination", "kaleidoscope", "discovery", "challenge", "timekeeper",
        "cryptogram", "constellation"
    )

    fun wordByDifficulty(difficulty: String): List<String> {
        return WORDS.filter { w ->
            when (difficulty) {
                "Easy" -> w.length in 3..5
                "Medium" -> w.length in 5..7
                "Hard" -> w.length in 7..9
                "Expert" -> w.length in 9..12
                else -> true
            }
        }
    }

    data class Round(
        val word: String,
        val scrambled: String,
        val hintLayers: List<String>
    )

    fun generate(seed: Long, difficulty: String = "Medium"): Round {
        val rng = Random(seed)
        val pool = wordByDifficulty(difficulty)
        val word = if (pool.isNotEmpty()) pool[rng.nextInt(pool.size)].uppercase()
                   else WORDS.random(rng).uppercase()
        var scrambled = word
        var guard = 0
        while (scrambled == word && guard++ < 20) {
            scrambled = word.toList().shuffled(rng).joinToString("")
        }
        val hintLayers = listOf(0.25f, 0.5f, 0.75f, 1f).map { frac ->
            val reveal = (word.length * frac).toInt().coerceAtLeast(1)
            word.mapIndexed { i, ch -> if (i < reveal) ch else '_' }.joinToString("")
        }
        return Round(word, scrambled, hintLayers)
    }
}

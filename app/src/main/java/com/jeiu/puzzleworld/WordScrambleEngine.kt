package com.jeiu.puzzleworld

import kotlin.random.Random

/**
 * Seed-based word scramble generator with difficulty tiers based on word length.
 * "Easy" = 3-5 letters, "Medium" = 5-7 letters, "Hard" = 7-9 letters.
 * Graded hints reveal letters progressively. Each round carries a themed
 * category tag used by the HUD (cosmetic — does not affect solving).
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
        val hintLayers: List<String>,
        val category: String
    )

    /** Lightweight theme tag for the streak/category HUD — cosmetic only. */
    fun categoryFor(word: String): String {
        val w = word.lowercase()
        return when {
            w in setOf("star", "moon", "time", "planet", "rocket", "galaxy", "cosmos",
                "nebula", "comet", "orbit", "sphere", "quasar", "photon", "vortex",
                "eclipse", "constellation", "gravity", "energy", "signal", "beacon",
                "lantern", "horizon", "magnet") -> "Space"
            w in setOf("cipher", "code", "riddle", "puzzle", "logic", "matrix", "vector",
                "circuit", "cryptic", "cryptogram", "enigmatic", "conundrum", "perplexing",
                "brainteaser", "obscure", "phantom", "genius", "wisdom", "secret", "trivia") -> "Cipher"
            w in setOf("treasure", "adventure", "mystery", "legend", "compass", "map",
                "fortune", "discovery", "challenge", "timekeeper", "labyrinth", "kaleidoscope",
                "illumination", "miracle", "paradox", "clue", "hint") -> "Quest"
            w in setOf("castle", "bridge", "temple", "fortress", "machine", "engine", "gate",
                "door", "lock", "vault", "key", "shield", "mask", "marble", "gear", "wand") -> "Machinery"
            w in setOf("jungle", "garden", "volcano", "rainbow", "crystal", "diamond", "amber",
                "crimson", "silver", "golden", "gem", "fire", "wind", "lens", "orb", "gold",
                "snap", "cube", "jigsaw") -> "Nature"
            else -> "Mystery"
        }
    }

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
        return Round(word, scrambled, hintLayers, categoryFor(word))
    }
}

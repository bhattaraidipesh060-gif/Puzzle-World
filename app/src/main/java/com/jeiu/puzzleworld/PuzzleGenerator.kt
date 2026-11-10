package com.jeiu.puzzleworld

import kotlin.random.Random

class PuzzleGenerator {

    companion object {
        const val GRID_COLS = 8; const val GRID_ROWS = 8

        private val CIPHERS = listOf(
            Triple("WKH VHFUHW LV RXW", "caesar", "THE SECRET IS OUT"),
            Triple("EBIIL TLOIA", "caesar", "HELLO WORLD"),
            Triple("WKLV LV D WUDLQHU", "caesar", "THIS IS A TRAINER"),
            Triple("GRQRW JLYH XS", "caesar", "DONOT GIVE UP"))
        private val CIPHER_HINTS = mapOf(
            "WKH VHFUHW LV RXW" to listOf("🔐 An encrypted message lies before you.", "🔐 Same Caesar shift as before — shift 3.", "🔐 Each letter moves back 3: W→T, K→H, H→E... 'THE SECRET IS OUT'."),
            "EBIIL TLOIA"      to listOf("🔐 The letters look scrambled but follow a pattern.", "🔐 Caesar shift 3. What's 'E' shifted back by 3?", "🔐 E→B, H→E, L→I, L→I, O→L... 'HELLO WORLD'."),
            "WKLV LV D WUDLQHU" to listOf("🔐 This one is straightforward if you know the shift.", "🔐 Shift 3 as usual. Read each word individually.", "🔐 'WKLV' = 'THIS', 'LV' = 'IS', 'D' = 'A', 'WUDLQHU' = 'TRAINER'."),
            "GRQRW JLYH XS"     to listOf("🔐 A message of encouragement.", "🔐 Caesar shift 3. Two words.", "🔐 'GRQRW' = 'DONOT', 'JLYH' = 'GIVE', 'XS' = 'UP'.")
        )

        private val RIDDLES = listOf(
            Pair("I speak without a mouth and hear without ears. I have no body but come alive with the wind. What am I?", "echo"),
            Pair("The more you take, the more you leave behind. What am I?", "footsteps"),
            Pair("I have keys but no locks. I have space but no room. You can enter but can't go inside. What am I?", "keyboard"),
            Pair("What has a head and a tail but no body?", "coin"),
            Pair("I'm tall when I'm young and short when I'm old. What am I?", "candle"),
            Pair("What gets wetter the more it dries?", "towel"))
        private val RIDDLE_HINTS = mapOf(
            "echo"      to listOf("🤔 It repeats what you say.", "🤔 Found in canyons and empty halls.", "🤔 It's a sound that bounces back — an 'echo'."),
            "footsteps" to listOf("🤔 Think about walking somewhere.", "🤔 Every step you take leaves something behind.", "🤔 The answer is 'footsteps'."),
            "keyboard"  to listOf("🤔 You use it every single day.", "🤔 It has letter keys and a space bar.", "🤔 It's a 'keyboard'."),
            "coin"      to listOf("🤔 It has two distinct sides.", "🤔 You flip it to decide things.", "🤔 It's a 'coin'."),
            "candle"    to listOf("🤔 It provides light and melts over time.", "🤔 Tall at first, then gradually shrinks.", "🤔 It's a 'candle'."),
            "towel"     to listOf("🤔 Used after a shower or swim.", "🤔 It absorbs water but itself gets wet.", "🤔 It's a 'towel'.")
        )
    }

    fun generateRoom(roomId: String, roomName: String, theme: String, seed: String): PuzzleRoom {
        val levels = (1..10).map { n ->
            val rng = Random("${seed}_$n".hashCode().toLong())
            val diff = when (n) { in 1..3 -> "Easy"; in 4..6 -> "Medium"; in 7..8 -> "Hard"; else -> "Expert" }
            val (mechanic, hints) = generateMechanic(rng, n)
            PuzzleLevel("${roomId}_%02d".format(n), "$roomName — Puzzle $n", diff, mechanic, false, hints)
        }
        return PuzzleRoom(roomId, roomName, theme, "A procedurally generated $theme escape room.", levels)
    }

    private fun generateMechanic(rng: Random, n: Int): Pair<PuzzleMechanic, List<String>> = when {
        n <= 2  -> wireMaze(rng, n)
        n == 3  -> patternLock(rng)
        n <= 5  -> cipherWithHints(rng)
        n == 6  -> colorSequence(rng)
        n <= 8  -> riddleWithHints(rng)
        n == 9  -> deductionWithHints()
        else    -> lockWithHints(rng)
    }

    private fun wireMaze(rng: Random, lvl: Int): Pair<PuzzleMechanic.WireMaze, List<String>> {
        val cols = 8; val rows = 8
        val density = when (lvl) { in 1..2 -> 0.20f; in 3..4 -> 0.25f; in 5..6 -> 0.30f; else -> 0.35f }
        val start = Pair(0, rng.nextInt(rows))
        var end = Pair(cols - 1, rng.nextInt(rows))
        if (end == start) end = Pair(cols - 1, (end.second + 1) % rows)

        val obstacles = WireMazeEngine.generateObstacles(cols, rows, start, end, rng.nextLong(), density)
        val par = WireMazeEngine.shortestPathLength(cols, rows, start, end, obstacles)

        val items = mutableListOf<PuzzleItem>()
        items.add(PuzzleItem("start", "start", start.first, start.second))
        items.add(PuzzleItem("end", "end", end.first, end.second))
        for ((x, y) in obstacles) items.add(PuzzleItem("o_${x}_$y", "obstacle", x, y))

        val hints = listOf(
            "🔌 Drag from the power outlet to the bulb to complete the circuit.",
            "🔌 The dark wall tiles block power — route your wire around them.",
            "🔌 The shortest route is $par moves. Can you match it?"
        )
        return Pair(PuzzleMechanic.WireMaze(cols, rows, items), hints)
    }

    private fun cipherWithHints(rng: Random): Pair<PuzzleMechanic, List<String>> {
        val (ct, tp, ans) = CIPHERS[rng.nextInt(CIPHERS.size)]
        val hints = CIPHER_HINTS[ct] ?: listOf("🔐 Decode the cipher.", "🔐 Shift letters back by 3.", "🔐 Try Caesar shift 3.")
        return Pair(PuzzleMechanic.Cipher(ct, tp, hints.last(), ans), hints.dropLast(1))
    }

    private fun riddleWithHints(rng: Random): Pair<PuzzleMechanic, List<String>> {
        val (q, a) = RIDDLES[rng.nextInt(RIDDLES.size)]
        val hints = RIDDLE_HINTS[a] ?: listOf("🤔 Think carefully.", "🤔 Consider all the clues.", "🤔 The answer is '$a'.")
        return Pair(PuzzleMechanic.Riddle(q, hints.last(), a.lowercase()), hints.dropLast(1))
    }

    private fun deductionWithHints() = Pair(
        PuzzleMechanic.DeductionGrid(
            listOf("Color", "Drink", "Pet"),
            listOf("Red", "Blue", "Green", "Coffee", "Tea", "Water", "Dog", "Cat", "Bird"),
            listOf("The Red house drinks Coffee.", "The Dog lives in Blue.", "The Cat drinks Tea."),
            mapOf("Red" to "Coffee", "Blue" to "Dog", "Green" to "Water")),
        listOf(
            "🧩 Three houses, each with a color, drink, and pet. Use the clues to match them.",
            "🧩 The Red house drinks Coffee, so Red=Coffee. The Dog is in Blue, so Blue=Dog. The Cat drinks Tea — that leaves Green with Water.",
            "🧩 Enter your answer as: RedCoffee BlueDog GreenWater"
        )
    )

    private fun lockWithHints(rng: Random) = Pair(
        PuzzleMechanic.MechanicalLock("dial", (1..3).map { rng.nextInt(0, 10) }, 5),
        listOf(
            "🔒 A 3-digit combination lock blocks the way.",
            "🔒 Try common patterns or look for clues in earlier puzzles.",
            "🔒 Each digit is between 0 and 9. Try a few combinations — you have 5 attempts."
        )
    )

    private fun patternLock(rng: Random): Pair<PuzzleMechanic, List<String>> {
        // generate a random valid pattern: start at random dot, connect 4-5 dots
        val len = rng.nextInt(4, 6)
        val pattern = mutableListOf(rng.nextInt(9))
        val visited = mutableSetOf(pattern[0])
        for (i in 1 until len) {
            var next = rng.nextInt(9)
            var guard = 0
            while (next in visited && guard++ < 20) next = rng.nextInt(9)
            visited.add(next)
            pattern.add(next)
        }
        return Pair(
            PuzzleMechanic.PatternLock(3, pattern),
            listOf(
                "🔓 Drag your finger across the dots in the right order.",
                "🔓 Connect $len dots. The pattern is unique.",
                "🔓 Try starting from dot ${pattern[0] + 1} then look for the flow."
            )
        )
    }

    private fun colorSequence(rng: Random): Pair<PuzzleMechanic, List<String>> {
        val colors = listOf("#FF4081", "#00E676", "#448AFF", "#FFD740")
        val len = rng.nextInt(3, 6)
        val seq = (0 until len).map { rng.nextInt(4) }
        return Pair(
            PuzzleMechanic.ColorSequence(colors, seq, len, 800L),
            listOf(
                "🎨 Watch the panels light up, then tap them in the same order.",
                "🎨 $len colors will flash. Memorize the sequence.",
                "🎨 Pink=1, Green=2, Blue=3, Amber=4. Count the flashes."
            )
        )
    }
}
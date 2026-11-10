package com.jeiu.puzzleworld

import kotlin.random.Random

/**
 * Generation + solving helpers for the interactive wire-maze mechanic.
 *
 * The puzzle is now a Flow-Free-style circuit: the player drags a wire from the
 * power outlet to the bulb, routing around wall tiles. Obstacles are scattered
 * then BFS-verified so a path always exists.
 */
object WireMazeEngine {

    private val DIRS = listOf(Pair(0, -1), Pair(0, 1), Pair(-1, 0), Pair(1, 0))

    /** Scatter obstacle cells, then remove any that break start→end reachability. */
    fun generateObstacles(
        cols: Int, rows: Int,
        start: Pair<Int, Int>, end: Pair<Int, Int>,
        seed: Long, density: Float = 0.28f
    ): Set<Pair<Int, Int>> {
        val rng = Random(seed)
        val obstacles = mutableSetOf<Pair<Int, Int>>()
        for (x in 0 until cols) for (y in 0 until rows) {
            val p = Pair(x, y)
            if (p == start || p == end) continue
            if (rng.nextFloat() < density) obstacles.add(p)
        }
        var guard = 0
        while (!hasPath(cols, rows, start, end, obstacles) && obstacles.isNotEmpty() && guard++ < 500) {
            obstacles.remove(obstacles.random(rng))
        }
        return obstacles
    }

    fun hasPath(cols: Int, rows: Int, start: Pair<Int, Int>, end: Pair<Int, Int>, obstacles: Set<Pair<Int, Int>>): Boolean =
        shortestPathLength(cols, rows, start, end, obstacles) >= 0

    /** BFS shortest path length in moves; -1 if unreachable. Used as the "par". */
    fun shortestPathLength(
        cols: Int, rows: Int,
        start: Pair<Int, Int>, end: Pair<Int, Int>,
        obstacles: Set<Pair<Int, Int>>
    ): Int {
        val dist = HashMap<Pair<Int, Int>, Int>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        dist[start] = 0
        queue.add(start)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            if (cur == end) return dist[cur]!!
            for ((dx, dy) in DIRS) {
                val np = Pair(cur.first + dx, cur.second + dy)
                if (np.first !in 0 until cols || np.second !in 0 until rows) continue
                if (np in obstacles) continue
                if (dist.containsKey(np)) continue
                dist[np] = dist[cur]!! + 1
                queue.add(np)
            }
        }
        return -1
    }
}

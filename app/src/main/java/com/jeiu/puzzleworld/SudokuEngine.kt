package com.jeiu.puzzleworld

import kotlin.random.Random

/**
 * Seed-based 9x9 Sudoku generation + a solver used both to build puzzles and
 * to auto-check the player's board. Returns a solved grid and a puzzle grid
 * where empty cells are 0.
 */
object SudokuEngine {

    const val SIZE = 9
    const val BOX = 3

    /** A full solved board (1..9) generated from [seed]. */
    fun solvedBoard(seed: Long): Array<IntArray> {
        val rng = Random(seed)
        val board = Array(SIZE) { IntArray(SIZE) }
        fill(board, rng)
        return board
    }

    /**
     * Build a puzzle: solve a board, then blank out cells until [givens]
     * clues remain. The blanking order is shuffled so puzzles differ by seed.
     */
    fun generate(seed: Long, givens: Int = 36): Pair<Array<IntArray>, Array<IntArray>> {
        val solution = solvedBoard(seed)
        val puzzle = Array(SIZE) { r -> solution[r].copyOf() }
        val cells = (0 until SIZE * SIZE).toMutableList()
        cells.shuffle(Random(seed xor 0x5DEECE66DL))
        var remaining = SIZE * SIZE - givens
        for (idx in cells) {
            if (remaining <= 0) break
            val r = idx / SIZE; val c = idx % SIZE
            puzzle[r][c] = 0
            remaining--
        }
        return Pair(puzzle, solution)
    }

    /** Backtracking fill of an empty board (deterministic per rng). */
    private fun fill(board: Array<IntArray>, rng: Random): Boolean {
        for (r in 0 until SIZE) for (c in 0 until SIZE) {
            if (board[r][c] != 0) continue
            val candidates = (1..9).shuffled(rng)
            for (v in candidates) {
                if (isValid(board, r, c, v)) {
                    board[r][c] = v
                    if (fill(board, rng)) return true
                    board[r][c] = 0
                }
            }
            return false
        }
        return true
    }

    fun isValid(board: Array<IntArray>, row: Int, col: Int, value: Int): Boolean {
        for (i in 0 until SIZE) {
            if (board[row][i] == value) return false
            if (board[i][col] == value) return false
        }
        val br = row - row % BOX
        val bc = col - col % BOX
        for (r in br until br + BOX) for (c in bc until bc + BOX)
            if (board[r][c] == value) return false
        return true
    }

    /** True when the board is completely filled with no conflicts. */
    fun isSolved(board: Array<IntArray>): Boolean {
        for (r in 0 until SIZE) for (c in 0 until SIZE) {
            val v = board[r][c]
            if (v == 0) return false
            board[r][c] = 0
            val ok = isValid(board, r, c, v)
            board[r][c] = v
            if (!ok) return false
        }
        return true
    }
}

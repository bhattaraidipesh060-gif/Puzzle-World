package com.jeiu.puzzleworld

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Sudoku mini-game with pencil marks, undo, and timer.
 */
class SudokuActivity : AppCompatActivity() {

    companion object {
        private val DIFFICULTY_GIVENS = mapOf("Easy" to 45, "Medium" to 38, "Hard" to 32, "Expert" to 28)
    }

    private lateinit var boardView: SudokuView
    private lateinit var headerTitle: TextView
    private lateinit var statusView: TextView
    private lateinit var timerView: TextView
    private lateinit var btnNew: Button
    private lateinit var btnCheck: Button
    private lateinit var btnUndo: Button
    private lateinit var btnNotes: Button
    private lateinit var btnHint: Button
    private lateinit var celebration: CelebrationView

    private var solution: Array<IntArray> = Array(9) { IntArray(9) }
    private var difficulty = "Medium"
    private var finished = false
    private val undoStack = mutableListOf<Array<IntArray>>()
    private var elapsedSec = 0
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            if (!finished) {
                elapsedSec++
                updateTimer()
                handler.postDelayed(this, 1000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sudoku)
        SoundFx.init(this)
        AnimatedBackgroundDrawable.applyTo(this,
            Color.parseColor("#0A0E1E"), Color.parseColor("#001B33"), Color.parseColor("#448AFF"))

        boardView  = findViewById(R.id.sudoku_board)
        headerTitle = findViewById(R.id.sudoku_header_title)
        statusView  = findViewById(R.id.sudoku_status)
        timerView   = findViewById(R.id.sudoku_timer)
        btnNew      = findViewById(R.id.sudoku_btn_new)
        btnCheck    = findViewById(R.id.sudoku_btn_check)
        btnUndo     = findViewById(R.id.sudoku_btn_undo)
        btnNotes    = findViewById(R.id.sudoku_btn_notes)
        btnHint     = findViewById(R.id.sudoku_btn_hint)
        celebration = findViewById(R.id.sudoku_celebration)

        btnNew.setOnClickListener { chooseDifficulty() }
        btnCheck.setOnClickListener { checkBoard() }
        btnUndo.setOnClickListener { undo() }
        btnNotes.setOnClickListener {
            boardView.noteMode = !boardView.noteMode
            btnNotes.text = if (boardView.noteMode) "✎  NOTES ON" else "✎  NOTES"
            if (boardView.noteMode) {
                btnNotes.setTextColor(Color.parseColor("#FFD740"))
                statusView.text = "Note mode: tap digit to toggle pencil mark"
            } else {
                btnNotes.setTextColor(Color.WHITE)
                statusView.text = "Enter mode: tap digit to set cell value"
            }
        }
        btnHint.setOnClickListener { giveHint() }

        for (i in 1..9) {
            val id = resources.getIdentifier("sudoku_pad_$i", "id", packageName)
            val btn = findViewById<Button>(id)
            btn.setOnClickListener {
                saveUndo()
                boardView.inputDigit(i)
            }
        }
        findViewById<Button>(R.id.sudoku_pad_clear).setOnClickListener {
            saveUndo()
            boardView.clearSelected()
        }

        boardView.onBoardChanged = {
            statusView.setTextColor(Color.parseColor("#B0B8C4"))
            statusView.text = "Solving…"
        }
        boardView.onSolved = { onSolved() }

        if (TutorialEngine.shouldShow(this, "sudoku")) {
            boardView.postDelayed({ TutorialEngine.show(this, "sudoku") }, 500L)
        }

        newGame(difficulty)
    }

    private fun saveUndo() {
        if (finished) return
        undoStack.add(boardView.snapshot())
    }

    private fun undo() {
        if (finished || undoStack.isEmpty()) return
        boardView.restoreSnapshot(undoStack.removeAt(undoStack.lastIndex))
        statusView.setTextColor(Color.parseColor("#FFD740"))
        statusView.text = "🔄 Undo (${undoStack.size} steps left)"
    }

    private fun giveHint() {
        if (finished) return
        if (boardView.hint()) {
            boardView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            statusView.setTextColor(Color.parseColor("#FFD740"))
            statusView.text = "💡 Hint: a cell filled in"
        } else {
            statusView.text = "No empty cells left — tap CHECK!"
        }
    }

    private fun newGame(diff: String) {
        difficulty = diff
        finished = false
        elapsedSec = 0
        undoStack.clear()
        boardView.noteMode = false
        btnNotes.text = "✎  NOTES"
        btnNotes.setTextColor(Color.WHITE)
        val givens = DIFFICULTY_GIVENS[diff] ?: 36
        val (puzzle, sol) = SudokuEngine.generate(System.currentTimeMillis(), givens)
        solution = sol
        boardView.puzzle = puzzle
        boardView.solution = sol
        headerTitle.text = "🧩 Sudoku — $diff"
        statusView.setTextColor(Color.parseColor("#B0B8C4"))
        statusView.text = "Tap a cell, then enter a number"
        updateTimer()
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, 1000L)
    }

    private fun chooseDifficulty() {
        handler.removeCallbacks(ticker)
        val items = arrayOf("Easy", "Medium", "Hard", "Expert")
        AlertDialog.Builder(this, R.style.HintDialog)
            .setTitle("🎯 Difficulty")
            .setItems(items) { _, i -> newGame(items[i]) }
            .setNegativeButton("Cancel") { _, _ -> handler.postDelayed(ticker, 1000L) }
            .show()
    }

    private fun checkBoard() {
        if (finished) return
        val board = boardView.board()
        var empty = 0; var conflicts = 0
        for (r in 0 until 9) for (c in 0 until 9) {
            val v = board[r][c]
            if (v == 0) { empty++; continue }
            if (v != solution[r][c]) conflicts++
        }
        when {
            SudokuEngine.isSolved(board) -> onSolved()
            empty == 0 -> {
                statusView.setTextColor(Color.parseColor("#FF4081"))
                statusView.text = "❌ $conflicts cell(s) wrong — fix the red cells"
            }
            else -> {
                statusView.setTextColor(Color.parseColor("#FFD740"))
                statusView.text = "⚠️ $empty empty, $conflicts wrong — keep going!"
                SoundFx.error()
            }
        }
    }

    private fun onSolved() {
        if (finished) return
        finished = true
        handler.removeCallbacks(ticker)
        updateTimer()
        val points = when (difficulty) { "Easy" -> 50; "Medium" -> 100; "Hard" -> 150; else -> 200 }
        GameStats.recordSolve(this, "sudoku", points)
        statusView.setTextColor(Color.parseColor("#00E676"))
        statusView.text = "🎉 Solved in ${formatTime(elapsedSec)}!"
        boardView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        boardView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pop))
        celebration.burst(70)
        SoundFx.win()
        Toast.makeText(this, "🏆 Sudoku complete!", Toast.LENGTH_SHORT).show()
    }

    private fun updateTimer() {
        timerView.text = "⏱ ${formatTime(elapsedSec)}"
    }

    private fun formatTime(s: Int): String {
        val m = s / 60; val sec = s % 60
        return "$m:${sec.toString().padStart(2, '0')}"
    }
}

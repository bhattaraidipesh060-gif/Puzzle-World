package com.jeiu.puzzleworld

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Interactive 9x9 Sudoku board with pencil-mark (candidate notes) support.
 * Tap a cell to select; use an external number pad to enter digits or toggle
 * notes when note-mode is on. Given clues are locked; conflicts are tinted red.
 */
class SudokuView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var puzzle: Array<IntArray> = Array(9) { IntArray(9) }
        set(v) { field = v; given = Array(9) { r -> BooleanArray(9) { v[r][it] != 0 } }; notes = Array(9) { Array(9) { mutableSetOf<Int>() } }; invalidate() }

    var solution: Array<IntArray> = Array(9) { IntArray(9) }
        set(v) { field = v; invalidate() }

    var noteMode = false
        set(v) { field = v; invalidate() }

    var onBoardChanged: ((Array<IntArray>) -> Unit)? = null
    var onSolved: (() -> Unit)? = null

    private lateinit var given: Array<BooleanArray>
    private var notes = Array(9) { Array(9) { mutableSetOf<Int>() } }
    private var selected: Pair<Int, Int>? = null

    // ── paints ───────────────────────────────────────────────────────────
    private val bgP = Paint().apply { color = Color.parseColor("#161B22") }
    private val minorGridP = Paint().apply { color = Color.parseColor("#2A3442"); strokeWidth = 1f }
    private val majorGridP = Paint().apply { color = Color.parseColor("#00E5FF"); strokeWidth = 3f }
    private val cellSelected = Color.parseColor("#1E3A5C")
    private val cellSame = Color.parseColor("#16303F")
    private val cellConflict = Color.parseColor("#3A1A1A")
    private val givenText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6EDF3"); textAlign = Paint.Align.CENTER
        textSize = 40f; typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val entryText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF"); textAlign = Paint.Align.CENTER
        textSize = 40f; typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val conflictText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4081"); textAlign = Paint.Align.CENTER
        textSize = 40f; typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val noteText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6E7A8A"); textAlign = Paint.Align.CENTER
        textSize = 14f; typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }
    private val selectedStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD740"); strokeWidth = 4f; style = Paint.Style.STROKE
    }

    init { given = Array(9) { BooleanArray(9) } }

    fun board(): Array<IntArray> = puzzle

    /** Snapshot for undo: a deep copy of the current puzzle board. */
    fun snapshot(): Array<IntArray> = Array(9) { r -> puzzle[r].copyOf() }

    /** Restore a saved snapshot. */
    fun restoreSnapshot(snap: Array<IntArray>) {
        for (r in 0 until 9) for (c in 0 until 9) {
            if (!given[r][c]) puzzle[r][c] = snap[r][c]
        }
        onBoardChanged?.invoke(puzzle); invalidate()
    }

    /** Enter a digit (1..9) into the selected cell; 0 clears it.
     *  In noteMode, toggles a pencil-mark note instead. */
    fun inputDigit(d: Int) {
        val (r, c) = selected ?: return
        if (given[r][c]) return
        if (noteMode) {
            if (d == 0) notes[r][c].clear()
            else {
                if (notes[r][c].contains(d)) notes[r][c].remove(d)
                else notes[r][c].add(d)
            }
            invalidate(); return
        }
        notes[r][c].clear()
        puzzle[r][c] = d
        onBoardChanged?.invoke(puzzle)
        if (d != 0 && SudokuEngine.isSolved(puzzle)) onSolved?.invoke()
        invalidate()
    }

    fun clearSelected() = inputDigit(0)

    fun selectCell(r: Int, c: Int) {
        selected = Pair(r, c); invalidate()
    }

    /** Reveal one empty cell with its correct solution value (hint).
     *  The revealed cell is locked in as a given. Returns true if revealed. */
    fun hint(): Boolean {
        for (r in 0 until 9) for (c in 0 until 9) {
            if (puzzle[r][c] == 0) {
                puzzle[r][c] = solution[r][c]
                given[r][c] = true
                notes[r][c].clear()
                selected = Pair(r, c)
                onBoardChanged?.invoke(puzzle)
                if (SudokuEngine.isSolved(puzzle)) onSolved?.invoke()
                invalidate()
                return true
            }
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        val cw = w / 9f; val ch = h / 9f
        canvas.drawRect(0f, 0f, w, h, bgP)

        // selection & same-value shading
        val sel = selected
        if (sel != null) {
            val sv = puzzle[sel.first][sel.second]
            for (r in 0 until 9) for (cc in 0 until 9) {
                val same = sv != 0 && puzzle[r][cc] == sv
                val inBand = r == sel.first || cc == sel.second ||
                        (r / 3 == sel.first / 3 && cc / 3 == sel.second / 3)
                val conflict = puzzle[r][cc] != 0 && !given[r][cc] &&
                        solution[r][cc] != 0 && puzzle[r][cc] != solution[r][cc]
                when {
                    r == sel.first && cc == sel.second ->
                        canvas.drawRect(cc * cw, r * ch, (cc + 1) * cw, (r + 1) * ch,
                            Paint().apply { color = cellSelected })
                    conflict -> canvas.drawRect(cc * cw, r * ch, (cc + 1) * cw, (r + 1) * ch,
                        Paint().apply { color = cellConflict })
                    same || inBand -> canvas.drawRect(cc * cw, r * ch, (cc + 1) * cw, (r + 1) * ch,
                        Paint().apply { color = cellSame })
                }
            }
        }

        // grid lines
        for (i in 0..9) {
            val major = i % 3 == 0
            val p = if (major) majorGridP else minorGridP
            canvas.drawLine(i * cw, 0f, i * cw, h, p)
            canvas.drawLine(0f, i * ch, w, i * ch, p)
        }

        // digits
        for (r in 0 until 9) for (cc in 0 until 9) {
            val v = puzzle[r][cc]
            if (v != 0) {
                val conflict = !given[r][cc] && solution[r][cc] != 0 && v != solution[r][cc]
                val p = when { conflict -> conflictText; given[r][cc] -> givenText; else -> entryText }
                val cx = (cc + 0.5f) * cw
                val cy = (r + 0.5f) * ch - (p.ascent() + p.descent()) / 2f
                canvas.drawText(v.toString(), cx, cy, p)
            }
        }

        // pencil marks (notes) for empty cells
        for (r in 0 until 9) for (cc in 0 until 9) {
            if (puzzle[r][cc] != 0 || notes[r][cc].isEmpty()) continue
            val nset = notes[r][cc]
            // draw in 3x3 mini-grid inside the cell
            val n3 = 3
            val nx = cw / n3.toFloat(); val ny = ch / n3.toFloat()
            for (d in 1..9) {
                if (d !in nset) continue
                val mr = (d - 1) / n3; val mc = (d - 1) % n3
                val cx = cc * cw + (mc + 0.5f) * nx
                val cy = r * ch + (mr + 0.5f) * ny - (noteText.ascent() + noteText.descent()) / 2f
                canvas.drawText(d.toString(), cx, cy, noteText)
            }
        }

        if (sel != null) {
            val (r, cc) = sel
            canvas.drawRect(cc * cw, r * ch, (cc + 1) * cw, (r + 1) * ch, selectedStroke)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            val r = (e.y / height * 9).toInt().coerceIn(0, 8)
            val cc = (e.x / width * 9).toInt().coerceIn(0, 8)
            selectCell(r, cc)
            return true
        }
        return super.onTouchEvent(e)
    }
}

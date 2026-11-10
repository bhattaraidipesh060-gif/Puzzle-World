package com.jeiu.puzzleworld

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/** Coordinate maze — navigate the marker S → E through the grid. */
class CoordinateMazeView(context: Context) : View(context) {
    var onSolved: (() -> Unit)? = null

    private var grid = arrayOf<String>()
    private var mr = 0; private var mc = 0
    private var er = 0; private var ec = 0
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    fun setGrid(grid: List<String>) {
        this.grid = grid.toTypedArray()
        for (r in grid.indices) for (c in grid[r].indices) when (grid[r][c]) {
            'S' -> { mr = r; mc = c }
            'E' -> { er = r; ec = c }
        }
        invalidate()
    }

    private fun open(r: Int, c: Int): Boolean =
        r in grid.indices && c in grid[r].indices && grid[r][c] != '#'

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (grid.isEmpty()) return
        val rows = grid.size
        val cols = grid.maxOf { it.length }
        val areaH = height * 0.78f
        val cell = minOf(width / cols.toFloat(), areaH / rows)
        val ox = (width - cols * cell) / 2f
        val oy = (height * 0.04f)
        for (r in 0 until rows) for (c in 0 until cols) {
            val ch = if (c < grid[r].length) grid[r][c] else '#'
            p.color = when (ch) {
                '#' -> Color.parseColor("#334155")
                'S' -> Color.parseColor("#10B981")
                'E' -> Color.parseColor("#F43F5E")
                else -> Color.parseColor("#1E293B")
            }
            canvas.drawRect(ox + c * cell + 2, oy + r * cell + 2, ox + (c + 1) * cell - 2, oy + (r + 1) * cell - 2, p)
            if (ch == 'E') { tp.textSize = cell * 0.5f; tp.color = Color.WHITE; canvas.drawText("E", ox + c * cell + cell / 2, oy + r * cell + cell * 0.68f, tp) }
        }
        // player marker
        tp.textSize = cell * 0.55f; tp.color = Color.parseColor("#22D3EE")
        canvas.drawText("●", ox + mc * cell + cell / 2, oy + mr * cell + cell * 0.68f, tp)
        // arrows
        val by = height * 0.84f; val bh = height * 0.12f; val bw = width / 4f
        val dirs = arrayOf("◀", "▲", "▼", "▶")
        for (i in dirs.indices) {
            p.color = Color.parseColor("#1E293B")
            canvas.drawRoundRect(i * bw + 6, by, (i + 1) * bw - 6, by + bh, 18f, 18f, p)
            tp.textSize = 22f; tp.color = Color.parseColor("#22D3EE")
            canvas.drawText(dirs[i], i * bw + bw / 2, by + bh / 2 + 8f, tp)
        }
        if (mr == er && mc == ec) {
            tp.textSize = 16f; tp.color = Color.parseColor("#10B981")
            canvas.drawText("✓ Coordinates reached!", width / 2f, height * 0.80f, tp)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_DOWN) return true
        val h = height.toFloat()
        if (e.y > h * 0.82f) {
            val bw = width / 4f
            val (dr, dc) = when ((e.x / bw).toInt()) {
                0 -> 0 to -1; 1 -> -1 to 0; 2 -> 1 to 0; 3 -> 0 to 1
                else -> 0 to 0
            }
            if (open(mr + dr, mc + dc)) { mr += dr; mc += dc; invalidate() }
            if (mr == er && mc == ec) onSolved?.invoke()
        }
        return true
    }
}
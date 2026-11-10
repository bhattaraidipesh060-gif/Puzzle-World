package com.jeiu.puzzleworld

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/** Classic 15-puzzle — slide tiles into the blank space to order them. */
class SlidingTileView(context: Context) : View(context) {
    var onSolved: (() -> Unit)? = null

    private var size = 3
    private var tiles = intArrayOf()
    private var blank = 0
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    fun setPuzzle(size: Int, tiles: IntArray) {
        this.size = size; this.tiles = tiles.copyOf(); this.blank = this.tiles.indexOf(0)
        invalidate()
    }

    private fun solved(): Boolean {
        for (i in 0 until tiles.size - 1) if (tiles[i] != i + 1) return false
        return tiles[tiles.size - 1] == 0
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (tiles.isEmpty()) return
        val cell = minOf(width, height) / size.toFloat()
        val ox = (width - cell * size) / 2f
        val oy = (height - cell * size) / 2f
        for (i in tiles.indices) {
            val v = tiles[i]
            if (v == 0) continue
            val r = i / size; val c = i % size
            val x = ox + c * cell; val y = oy + r * cell
            p.color = Color.parseColor(if (v == i + 1) "#10B981" else "#1E293B")
            canvas.drawRoundRect(x + 4, y + 4, x + cell - 4, y + cell - 4, 14f, 14f, p)
            tp.textSize = cell * 0.42f
            tp.color = if (v == i + 1) Color.parseColor("#064E3B") else Color.WHITE
            canvas.drawText(v.toString(), x + cell / 2, y + cell / 2 + cell * 0.15f, tp)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_DOWN) return true
        val cell = minOf(width, height) / size.toFloat()
        val ox = (width - cell * size) / 2f
        val oy = (height - cell * size) / 2f
        val c = ((e.x - ox) / cell).toInt().coerceIn(0, size - 1)
        val r = ((e.y - oy) / cell).toInt().coerceIn(0, size - 1)
        val idx = r * size + c
        val blankR = blank / size; val blankC = blank % size
        val adjacent = (r == blankR && Math.abs(c - blankC) == 1) || (c == blankC && Math.abs(r - blankR) == 1)
        if (adjacent) {
            tiles[blank] = tiles[idx]; tiles[idx] = 0; blank = idx
            invalidate()
            if (solved()) onSolved?.invoke()
        }
        return true
    }
}
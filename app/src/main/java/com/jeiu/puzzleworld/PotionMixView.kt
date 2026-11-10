package com.jeiu.puzzleworld

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/** Potion mixing (jug) — fill / empty / pour beakers to hit the target volume. */
class PotionMixView(context: Context) : View(context) {
    var onSolved: (() -> Unit)? = null

    private var caps = intArrayOf()
    private var vols = intArrayOf()
    private var target = 0
    private var selected = 0
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    fun setPuzzle(caps: IntArray, target: Int) {
        this.caps = caps; this.vols = IntArray(caps.size); this.target = target; this.selected = 0
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = caps.size; if (n == 0) return
        val w = width.toFloat(); val h = height.toFloat()
        val bw = w / n
        for (i in 0 until n) {
            val cx = i * bw + bw / 2
            val fillH = if (caps[i] == 0) 0f else (h * 0.5f) * vols[i] / caps[i]
            p.color = Color.parseColor(if (i == selected) "#334155" else "#1E293B")
            canvas.drawRoundRect(i * bw + 10, h * 0.12f, (i + 1) * bw - 10, h * 0.62f, 14f, 14f, p)
            p.color = Color.parseColor("#22D3EE")
            canvas.drawRoundRect(i * bw + 14, h * 0.62f - fillH, (i + 1) * bw - 14, h * 0.62f, 8f, 8f, p)
            tp.textSize = 18f; tp.color = Color.WHITE
            canvas.drawText("${vols[i]} / ${caps[i]}", cx, h * 0.68f, tp)
            tp.textSize = 13f; tp.color = if (vols[i] == target) Color.parseColor("#10B981") else Color.parseColor("#94A3B8")
            canvas.drawText("beaker $i", cx, h * 0.72f, tp)
        }
        // action buttons
        val buttons = arrayOf("FILL", "EMPTY", "POUR →")
        val by = h * 0.84f; val bhh = h * 0.12f
        val bww = w / 3f
        for (i in buttons.indices) {
            p.color = Color.parseColor("#1E293B")
            canvas.drawRoundRect(i * bww + 8, by, (i + 1) * bww - 8, by + bhh, 20f, 20f, p)
            tp.textSize = 16f; tp.color = Color.parseColor("#22D3EE")
            canvas.drawText(buttons[i], i * bww + bww / 2, by + bhh / 2 + 6f, tp)
        }
        if (vols.any { it == target }) {
            tp.textSize = 16f; tp.color = Color.parseColor("#10B981")
            canvas.drawText("✓ Target $target reached!", w / 2, h * 0.06f, tp)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_DOWN) return true
        val n = caps.size; if (n == 0) return true
        val w = width.toFloat(); val h = height.toFloat()
        // action buttons first
        if (e.y > h * 0.80f) {
            val bww = w / 3f
            when ((e.x / bww).toInt()) {
                0 -> { vols[selected] = caps[selected] }
                1 -> { vols[selected] = 0 }
                2 -> {
                    var dest = -1
                    for (i in 0 until n) if (i != selected) dest = i
                    if (dest >= 0) {
                        val space = caps[dest] - vols[dest]
                        val amount = minOf(vols[selected], space)
                        vols[selected] -= amount; vols[dest] += amount
                    }
                }
            }
            invalidate()
            if (vols.any { it == target }) onSolved?.invoke()
            return true
        }
        // select beaker
        val idx = (e.x / (w / n)).toInt().coerceIn(0, n - 1)
        if (idx != selected) { selected = idx; invalidate() }
        return true
    }
}
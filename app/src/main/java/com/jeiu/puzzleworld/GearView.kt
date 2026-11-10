package com.jeiu.puzzleworld

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/** Interlocking gears — rotate each gear until every tooth-align angle hits target. */
class GearView(context: Context) : View(context) {
    var onSolved: (() -> Unit)? = null

    private var gears = intArrayOf()      // current angle per gear
    private var targets = intArrayOf()    // 0 = aligned
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    fun setGears(angles: IntArray, targets: IntArray) {
        this.gears = angles.copyOf(); this.targets = targets.copyOf()
        invalidate()
    }

    private fun aligned(): Boolean = gears.indices.all { Math.abs(norm(gears[it] - targets[it])) <= 8 }

    private fun norm(a: Int): Int { var x = a % 360; if (x < 0) x += 360; return if (x > 180) x - 360 else x }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = gears.size; if (n == 0) return
        val w = width.toFloat(); val h = height.toFloat()
        val r = minOf(w / (n * 2f), h * 0.26f)
        val cy = h * 0.45f
        for (i in 0 until n) {
            val cx = (i + 0.5f) * w / n
            // teeth
            p.style = Paint.Style.STROKE; p.strokeWidth = 6f
            for (k in 0 until 12) {
                val ang = Math.toRadians((k * 30 + gears[i]).toDouble())
                val a = Math.toRadians((k * 30 + gears[i] + 30).toDouble())
                p.color = Color.parseColor("#334155")
                canvas.drawLine(
                    cx + (r - 6) * cos(ang).toFloat(), cy + (r - 6) * sin(ang).toFloat(),
                    cx + (r + 10) * cos(a).toFloat(), cy + (r + 10) * sin(a).toFloat(), p)
                canvas.drawLine(
                    cx + (r - 6) * cos(ang).toFloat(), cy + (r - 6) * sin(ang).toFloat(),
                    cx + (r + 10) * cos(ang).toFloat(), cy + (r + 10) * sin(ang).toFloat(), p)
            }
            p.style = Paint.Style.FILL
            val ok = Math.abs(norm(gears[i] - targets[i])) <= 8
            p.color = Color.parseColor(if (ok) "#10B981" else "#1E293B")
            canvas.drawCircle(cx, cy, r * 0.82f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 3f
            p.color = Color.parseColor(if (ok) "#34D399" else "#22D3EE")
            canvas.drawCircle(cx, cy, r * 0.82f, p)
            p.style = Paint.Style.FILL
            tp.textSize = 13f; tp.color = Color.WHITE
            canvas.drawText("${gears[i]}°", cx, cy + r + 22f, tp)
        }
        if (aligned()) {
            tp.textSize = 18f; tp.color = Color.parseColor("#10B981")
            canvas.drawText("✓ Mechanism aligned — tap to complete", w / 2, h - 20f, tp)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_DOWN) return true
        val n = gears.size; if (n == 0) return true
        val w = width.toFloat()
        val i = (e.x / (w / n)).toInt().coerceIn(0, n - 1)
        if (e.y < height * 0.75f) { gears[i] = (gears[i] + 15) % 360; invalidate() }
        if (aligned()) { onSolved?.invoke() }
        return true
    }
}
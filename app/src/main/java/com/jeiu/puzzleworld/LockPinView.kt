package com.jeiu.puzzleworld

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/** Pick-and-tumbler lock — set each pin's depth, left to right, to open. */
class LockPinView(context: Context) : View(context) {
    var onSolved: (() -> Unit)? = null

    private var pins = intArrayOf()
    private var current = intArrayOf()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    fun setPins(pins: IntArray) {
        this.pins = pins; this.current = IntArray(pins.size)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = pins.size; if (n == 0) return
        val w = width.toFloat(); val h = height.toFloat()
        val slot = w / n
        for (i in 0 until n) {
            val cx = i * slot + slot / 2
            val ok = current[i] == pins[i]
            p.color = Color.parseColor(if (ok) "#10B981" else "#1E293B")
            canvas.drawRoundRect(i * slot + 10, h * 0.10f, (i + 1) * slot - 10, h * 0.66f, 14f, 14f, p)
            // depth indicator: 4 tick marks, filled up to current[i]
            val maxDepth = 4
            for (d in 0 until maxDepth) {
                val yb = h * 0.60f - d * (h * 0.50f / maxDepth)
                val yt = yb - h * 0.09f
                p.color = if (d < current[i]) Color.parseColor("#22D3EE") else Color.parseColor("#334155")
                canvas.drawRoundRect(i * slot + 18, yt, (i + 1) * slot - 18, yb, 6f, 6f, p)
            }
            tp.textSize = 14f; tp.color = if (ok) Color.parseColor("#10B981") else Color.WHITE
            canvas.drawText("pin ${i + 1}", cx, h * 0.72f, tp)
            tp.textSize = 12f; tp.color = Color.parseColor("#94A3B8")
            canvas.drawText("depth ${current[i]}", cx, h * 0.77f, tp)
        }
        if (current.contentEquals(pins)) {
            tp.textSize = 16f; tp.color = Color.parseColor("#10B981")
            canvas.drawText("✓ Lock picked!", w / 2, h * 0.05f, tp)
        } else {
            tp.textSize = 14f; tp.color = Color.parseColor("#94A3B8")
            canvas.drawText("tap pins to set depth (1–4)", w / 2, h * 0.05f, tp)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_DOWN) return true
        val n = pins.size; if (n == 0) return true
        val slot = width / n.toFloat()
        val i = (e.x / slot).toInt().coerceIn(0, n - 1)
        if (e.y < height * 0.68f) {
            current[i] = (current[i] + 1) % 5
            invalidate()
            if (current.contentEquals(pins)) onSolved?.invoke()
        }
        return true
    }
}
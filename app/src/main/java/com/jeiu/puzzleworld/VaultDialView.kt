package com.jeiu.puzzleworld

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/** Rotary combination dial — spin each ring until it matches the code. */
class VaultDialView(context: Context) : View(context) {
    var onSolved: (() -> Unit)? = null

    private var combination = intArrayOf()
    private var current = intArrayOf()
    private var solved = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    fun setDial(combination: IntArray) {
        this.combination = combination
        this.current = IntArray(combination.size)
        this.solved = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = combination.size
        if (n == 0) return
        val w = width.toFloat(); val h = height.toFloat()
        val slot = w / n
        val ringH = h * 0.28f
        for (i in 0 until n) {
            val cx = i * slot + slot / 2
            // up button
            p.color = if (solved) Color.parseColor("#10B981") else Color.parseColor("#1E293B")
            canvas.drawRoundRect(i * slot + 8, 8f, (i + 1) * slot - 8, ringH - 8, 22f, 22f, p)
            tp.textSize = 30f; tp.color = Color.WHITE
            canvas.drawText("▲", cx, (ringH - 8) / 2 + 10f, tp)
            // digit ring
            p.color = Color.parseColor("#0F172A")
            canvas.drawRoundRect(i * slot + 8, ringH, (i + 1) * slot - 8, h - ringH, 22f, 22f, p)
            tp.textSize = 46f
            tp.color = if (solved) Color.parseColor("#10B981") else Color.parseColor("#22D3EE")
            canvas.drawText(current[i].toString(), cx, ringH + (h - 2 * ringH) / 2 + 16f, tp)
            // down button
            p.color = if (solved) Color.parseColor("#10B981") else Color.parseColor("#1E293B")
            canvas.drawRoundRect(i * slot + 8, h - ringH + 8, (i + 1) * slot - 8, h - 8, 22f, 22f, p)
            tp.textSize = 30f; tp.color = Color.WHITE
            canvas.drawText("▼", cx, h - (ringH - 8) / 2 - 8f, tp)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_DOWN || solved) return true
        val n = combination.size
        if (n == 0) return true
        val slot = width / n.toFloat()
        val i = (e.x / slot).toInt().coerceIn(0, n - 1)
        val h = height.toFloat()
        val delta = if (e.y < h * 0.28f) 1 else if (e.y > h - h * 0.28f) -1 else 0
        if (delta != 0) {
            current[i] = (current[i] + delta + 10) % 10
            if (current.contentEquals(combination)) {
                solved = true
                invalidate()
                performClick()
                onSolved?.invoke()
            } else invalidate()
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
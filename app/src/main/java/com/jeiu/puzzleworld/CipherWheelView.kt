package com.jeiu.puzzleworld

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/** Interactive cypher wheel — rotate the ring to shift letters and decode. */
class CipherWheelView(context: Context) : View(context) {
    var onSolved: (() -> Unit)? = null

    private var cipherText = ""
    private var answer = ""
    private var shift = 0
    private var cur = 0
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    fun setPuzzle(cipherText: String, answer: String, shift: Int) {
        this.cipherText = cipherText; this.answer = answer; this.shift = shift
        this.cur = 0
        invalidate()
    }

    private fun decode(): String = cipherText.map {
        val c = it.uppercaseChar()
        if (c in 'A'..'Z') ((c - 'A' - cur + 26) % 26 + 'A'.code).toChar() else it
    }.joinToString("")

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2; val cy = h * 0.34f; val r = minOf(w, h) * 0.30f
        // outer ring
        p.style = Paint.Style.STROKE; p.strokeWidth = 26f
        p.color = Color.parseColor("#1E293B"); canvas.drawCircle(cx, cy, r, p)
        p.color = Color.parseColor("#22D3EE"); canvas.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.FILL
        // letters around ring
        for (i in 0 until 26) {
            val ang = Math.toRadians((i * 360.0 / 26 - 90).toDouble())
            val lx = cx + (r - 26f) * Math.cos(ang).toFloat()
            val ly = cy + (r - 26f) * Math.sin(ang).toFloat()
            tp.textSize = 22f; tp.color = Color.WHITE
            canvas.drawText(('A' + i).toString(), lx, ly + 8f, tp)
        }
        // decoded text
        tp.textSize = 20f; tp.color = Color.parseColor("#E2E8F0")
        canvas.drawText("Shift: $cur", cx, cy + r + 44f, tp)
        tp.textSize = 26f; tp.color = Color.parseColor("#22D3EE")
        canvas.drawText(decode(), cx, cy + r + 80f, tp)
        if (cur == shift) {
            tp.textSize = 18f; tp.color = Color.parseColor("#10B981")
            canvas.drawText("✓ MATCHED — tap to unlock", cx, h - 30f, tp)
        } else {
            tp.textSize = 15f; tp.color = Color.parseColor("#94A3B8")
            canvas.drawText("drag ◀ ▶ to rotate the wheel", cx, h - 30f, tp)
        }
    }

    private var dx = 0f
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> { dx = e.x; return true }
            MotionEvent.ACTION_UP -> {
                if (cur == shift) { onSolved?.invoke() }
            }
            MotionEvent.ACTION_MOVE -> {
                val delta = ((e.x - dx) / 24f).toInt()
                if (delta != 0) { dx += delta * 24f; cur = (cur + delta + 26) % 26; invalidate() }
            }
        }
        return true
    }
}
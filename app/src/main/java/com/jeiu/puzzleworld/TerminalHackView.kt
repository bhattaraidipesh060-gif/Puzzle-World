package com.jeiu.puzzleworld

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View

/** Terminal hacking — binary→hex answer pick, or node-memory-path replay. */
class TerminalHackView(context: Context) : View(context) {
    var onSolved: (() -> Unit)? = null

    private var mode = "bin_to_hex"
    private var input = ""
    private var answer = ""
    private var nodes = intArrayOf()
    private var options = arrayOf<String>()
    // memory state
    private var step = -1
    private var inputPointer = 0
    private var showFlash = -1
    private val handler = Handler(Looper.getMainLooper())
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    fun setPuzzle(mode: String, input: String, answer: String, nodes: IntArray) {
        this.mode = mode; this.input = input; this.answer = answer; this.nodes = nodes
        this.inputPointer = 0; this.step = -1; this.showFlash = -1
        if (mode == "bin_to_hex") {
            val len = if (answer.length <= 0) 2 else answer.length
            val a = answer
            val d1 = "F".repeat(len)
            val d2 = "0".repeat(len)
            var d3 = a.map { c -> if (c == '0') '1' else '0' }.joinToString("")
            if (d3 == a) d3 = "A".repeat(len)
            options = arrayOf(a, d1, d2, d3).toList().shuffled().toTypedArray()
        } else {
            run { revealStep(0) }
        }
        invalidate()
    }

    private fun revealStep(k: Int) {
        if (k >= nodes.size) { step = 0; showFlash = -1; invalidate(); return }
        showFlash = nodes[k]
        invalidate()
        handler.postDelayed({
            if (!isAttachedToWindow) return@postDelayed
            showFlash = -1; invalidate()
            handler.postDelayed({ if (isAttachedToWindow) revealStep(k + 1) }, 180L)
        }, 380L)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (mode == "bin_to_hex") {
            tp.textSize = 15f; tp.color = Color.parseColor("#94A3B8")
            canvas.drawText("Binary stream:", w / 2, h * 0.12f, tp)
            tp.textSize = 24f; tp.color = Color.parseColor("#22D3EE")
            val grouped = input.chunked(4).joinToString(" ")
            canvas.drawText(grouped, w / 2, h * 0.24f, tp)
            tp.textSize = 15f; tp.color = Color.parseColor("#94A3B8")
            canvas.drawText("Convert to hex:", w / 2, h * 0.44f, tp)
            for (i in options.indices) {
                val y = h * (0.56f + i * 0.11f)
                p.color = Color.parseColor("#1E293B")
                canvas.drawRoundRect(w * 0.2f, y - h * 0.04f, w * 0.8f, y + h * 0.04f, 20f, 20f, p)
                tp.textSize = 22f; tp.color = Color.parseColor("#22D3EE")
                canvas.drawText(options[i], w * 0.5f, y + 8f, tp)
            }
        } else {
            tp.textSize = 15f; tp.color = Color.parseColor("#94A3B8")
            canvas.drawText(if (step < 0) "Watch the node path…" else "Replay the node path", w / 2, h * 0.08f, tp)
            val cell = w / 3f
            for (k in 0 until 9) {
                val cx = (k % 3) * cell + cell / 2
                val cy = h * 0.22f + (k / 3) * cell
                val lit = showFlash == k
                p.color = Color.parseColor(if (lit) "#22D3EE" else "#1E293B")
                canvas.drawCircle(cx, cy, cell * 0.36f, p)
                tp.textSize = 20f; tp.color = Color.WHITE
                canvas.drawText(k.toString(), cx, cy + 7f, tp)
            }
            if (step >= 0 && step < nodes.size) {
                tp.textSize = 15f; tp.color = Color.parseColor("#94A3B8")
                canvas.drawText("Tapped ${step}/${nodes.size}", w / 2, h * 0.92f, tp)
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_DOWN) return true
        if (mode == "bin_to_hex") {
            val w = width.toFloat(); val h = height.toFloat()
            val y = e.y
            for (i in options.indices) {
                val yy = h * (0.56f + i * 0.11f)
                if (Math.abs(y - yy) < h * 0.05f) {
                    if (options[i] == answer) onSolved?.invoke()
                    return true
                }
            }
        } else if (step >= 0 && step < nodes.size) {
            val cell = width / 3f
            val c = (e.x / cell).toInt().coerceIn(0, 2)
            val r = ((e.y - height * 0.22f) / cell).toInt().coerceIn(0, 2)
            val k = r * 3 + c
            if (k == nodes[step]) {
                step++
                if (step == nodes.size) onSolved?.invoke()
            } else { step = 0; inputPointer = 0; handler.post { revealStep(0) } }
        }
        return true
    }
}
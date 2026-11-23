package com.jeiu.puzzleworld

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

/**
 * Terminal hacking — binary→hex answer pick, or node-memory-path replay.
 *
 * Solve contract is unchanged (`setPuzzle(mode, input, answer, nodes)` +
 * `onSolved`); this pass only raises the type for comfortable reading and
 * weaves a deterministic, contextual computing fact into each screen so the
 * challenge reads as a fun terminal rather than dry arithmetic.
 */
class TerminalHackView(context: Context) : View(context) {
    var onSolved: (() -> Unit)? = null

    private var mode = "bin_to_hex"
    private var input = ""
    private var answer = ""
    private var nodes = intArrayOf()
    private var options = arrayOf<String>()
    private var fact = ""

    // memory state
    private var step = -1
    private var inputPointer = 0
    private var showFlash = -1
    private val handler = Handler(Looper.getMainLooper())
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private val binFacts = listOf(
        "4 binary bits = 1 hex digit — a “nibble”.",
        "Hex digits run 0–9, then A–F.",
        "Two hex digits = one byte = 8 bits.",
        "Computers only understand 0s and 1s.",
        "1111 in binary = F in hex — the max nibble.",
        "Hexadecimal makes long binary readable to humans."
    )
    private val nodeFacts = listOf(
        "RAM is volatile — it forgets when power dies.",
        "Data travels the internet node-by-node.",
        "CPU cache can be ~100× faster than main memory.",
        "Packets hop through routers to reach your screen.",
        "A 3×3 grid = 9 possible memory addresses here."
    )

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
            fact = binFacts[Math.floorMod((input + answer).hashCode(), binFacts.size)]
        } else {
            fact = nodeFacts[Math.floorMod((input + answer).hashCode(), nodeFacts.size)]
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
            // Header
            tp.textSize = 12f; tp.color = Color.parseColor("#64748B")
            canvas.drawText("CRYPTO TERMINAL · bin→hex", w / 2, h * 0.06f, tp)

            tp.textSize = 16f; tp.color = Color.parseColor("#94A3B8")
            canvas.drawText("Binary stream:", w / 2, h * 0.13f, tp)
            tp.textSize = 30f; tp.color = Color.parseColor("#22D3EE")
            val grouped = input.chunked(4).joinToString(" ")
            canvas.drawText(grouped, w / 2, h * 0.25f, tp)
            tp.textSize = 16f; tp.color = Color.parseColor("#94A3B8")
            canvas.drawText("Convert to hex:", w / 2, h * 0.44f, tp)
            for (i in options.indices) {
                val y = h * (0.55f + i * 0.10f)
                p.color = Color.parseColor("#1E293B")
                canvas.drawRoundRect(w * 0.18f, y - h * 0.038f, w * 0.82f, y + h * 0.038f, 22f, 22f, p)
                tp.textSize = 26f; tp.color = Color.parseColor("#22D3EE")
                canvas.drawText(options[i], w * 0.5f, y + 9f, tp)
            }
        } else {
            tp.textSize = 12f; tp.color = Color.parseColor("#64748B")
            canvas.drawText("CRYPTO TERMINAL · memory trace", w / 2, h * 0.06f, tp)
            tp.textSize = 16f; tp.color = Color.parseColor("#94A3B8")
            canvas.drawText(if (step < 0) "Watch the node path…" else "Replay the node path", w / 2, h * 0.13f, tp)
            val cell = w / 3f
            for (k in 0 until 9) {
                val cx = (k % 3) * cell + cell / 2
                val cy = h * 0.24f + (k / 3) * cell
                val lit = showFlash == k
                p.color = Color.parseColor(if (lit) "#22D3EE" else "#1E293B")
                canvas.drawCircle(cx, cy, cell * 0.36f, p)
                tp.textSize = 24f; tp.color = Color.WHITE
                canvas.drawText(k.toString(), cx, cy + 8f, tp)
            }
            if (step >= 0 && step < nodes.size) {
                tp.textSize = 16f; tp.color = Color.parseColor("#94A3B8")
                canvas.drawText("Tapped ${step}/${nodes.size}", w / 2, h * 0.88f, tp)
            }
        }

        // Contextual fun / scientific fact
        tp.textSize = 13f; tp.color = Color.parseColor("#F59E0B")
        canvas.drawText("💡 $fact", w / 2, h * 0.955f, tp)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_DOWN) return true
        if (mode == "bin_to_hex") {
            val w = width.toFloat(); val h = height.toFloat()
            val y = e.y
            for (i in options.indices) {
                val yy = h * (0.55f + i * 0.10f)
                if (Math.abs(y - yy) < h * 0.05f) {
                    SoundFx.click()
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (options[i] == answer) onSolved?.invoke()
                    else performHapticFeedback(HapticFeedbackConstants.REJECT)
                    return true
                }
            }
        } else if (step >= 0 && step < nodes.size) {
            val cell = width / 3f
            val c = (e.x / cell).toInt().coerceIn(0, 2)
            val r = ((e.y - height * 0.24f) / cell).toInt().coerceIn(0, 2)
            val k = r * 3 + c
            SoundFx.click()
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            if (k == nodes[step]) {
                step++
                if (step == nodes.size) onSolved?.invoke()
            } else { step = 0; inputPointer = 0; handler.post { revealStep(0) } }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

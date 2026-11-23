package com.jeiu.puzzleworld

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

/**
 * Pick-and-tumbler lock — set each pin's depth (0–4), left to right, to open.
 *
 * The solve contract is unchanged (`setPins(pins)`, tap to cycle depth,
 * `onSolved` when `current` matches `pins`). This pass renders a realistic
 * pin-tumbler cutaway: housing, springs, brass driver pins, steel key pins, a
 * dashed shear line and a keyway. Each key pin's top rides up/down with depth
 * and aligns flush with the shear line at the correct depth, with a satisfying
 * click + set pulse when a pin lands on its target.
 */
class LockPinView(context: Context) : View(context) {
    var onSolved: (() -> Unit)? = null

    private var pins = intArrayOf()
    private var current = intArrayOf()
    private var setAt = LongArray(0)

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val handler = Handler(Looper.getMainLooper())

    private fun dp(v: Float) = v * resources.displayMetrics.density

    fun setPins(pins: IntArray) {
        this.pins = pins
        this.current = IntArray(pins.size)
        this.setAt = LongArray(pins.size)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = pins.size; if (n == 0) return
        val w = width.toFloat(); val h = height.toFloat()
        val now = SystemClock.uptimeMillis()

        val lockLeft = w * 0.06f
        val lockRight = w * 0.94f
        val housingTop = h * 0.10f
        val shearY = h * 0.46f
        val plugBottom = h * 0.58f
        val keywayBottom = h * 0.66f

        // Lock casing — housing (top) + plug (bottom)
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#1E293B")
        canvas.drawRoundRect(lockLeft, housingTop, lockRight, shearY, dp(12f), dp(12f), p)
        p.color = Color.parseColor("#263449")
        canvas.drawRoundRect(lockLeft, shearY, lockRight, plugBottom, dp(10f), dp(10f), p)

        // Keyway slot
        p.color = Color.parseColor("#0B1220")
        canvas.drawRoundRect(lockLeft, plugBottom, lockRight, keywayBottom, dp(6f), dp(6f), p)
        // keyhole hint
        val khx = lockLeft + dp(16f)
        val khy = (plugBottom + keywayBottom) / 2f
        p.color = Color.parseColor("#0B1220")
        p.style = Paint.Style.STROKE
        p.strokeWidth = dp(2f)
        p.color = Color.parseColor("#334155")
        canvas.drawCircle(khx, khy, dp(5f), p)
        canvas.drawLine(khx, khy + dp(4f), khx, keywayBottom - dp(2f), p)

        // Shear line (dashed)
        p.strokeWidth = dp(1.5f)
        p.color = Color.parseColor("#64748B")
        val dash = dp(6f)
        var x = lockLeft
        while (x < lockRight) {
            canvas.drawLine(x, shearY, minOf(x + dash, lockRight), shearY, p)
            x += dash * 2f
        }

        val slot = (lockRight - lockLeft) / n
        val pinW = slot * 0.52f
        val driverTop = housingTop + (shearY - housingTop) * 0.50f
        val unit = (plugBottom - shearY) * 0.25f

        for (i in 0 until n) {
            val cx = lockLeft + (i + 0.5f) * slot
            val ok = current[i] == pins[i]

            // Spring (zigzag)
            p.strokeWidth = dp(1.5f)
            p.color = Color.parseColor("#64748B")
            val sy0 = housingTop + dp(3f)
            val sy1 = driverTop - dp(2f)
            var px = cx - pinW / 2f
            val segs = 4
            for (k in 0 until segs) {
                val yy = sy0 + (sy1 - sy0) * (k / segs.toFloat())
                val ny = sy0 + (sy1 - sy0) * ((k + 1) / segs.toFloat())
                val nx = if (k % 2 == 0) cx + pinW / 2f else cx - pinW / 2f
                canvas.drawLine(px, yy, nx, ny, p)
                px = nx
            }

            // Driver pin (fixed in housing)
            p.style = Paint.Style.FILL
            p.color = Color.parseColor(if (ok) "#10B981" else "#C9A227")
            canvas.drawRoundRect(cx - pinW / 2f, driverTop, cx + pinW / 2f, shearY, dp(4f), dp(4f), p)

            // Key pin (rides with depth; flush with shear line when correct)
            val keyTop = (shearY + (current[i] - pins[i]) * unit)
                .coerceIn(housingTop + dp(4f), plugBottom - dp(3f))
            p.color = Color.parseColor(if (ok) "#34D399" else "#94A3B8")
            canvas.drawRoundRect(cx - pinW / 2f, keyTop, cx + pinW / 2f, plugBottom - dp(1f), dp(4f), dp(4f), p)

            // Set pulse at the shear line
            if (now - setAt[i] < 350L) {
                p.color = Color.parseColor("#34D399")
                canvas.drawCircle(cx, shearY, dp(7f), p)
            }

            // Labels
            tp.textSize = 14f
            tp.color = if (ok) Color.parseColor("#10B981") else Color.WHITE
            canvas.drawText("pin ${i + 1}", cx, keywayBottom + dp(18f), tp)
            tp.textSize = 12f
            tp.color = Color.parseColor("#94A3B8")
            canvas.drawText("depth ${current[i]}", cx, keywayBottom + dp(34f), tp)
        }

        if (current.contentEquals(pins)) {
            tp.textSize = 16f; tp.color = Color.parseColor("#10B981")
            canvas.drawText("✓ Lock picked!", w / 2, h * 0.045f, tp)
        } else {
            tp.textSize = 14f; tp.color = Color.parseColor("#94A3B8")
            canvas.drawText("tap pins to set depth (0–4)", w / 2, h * 0.045f, tp)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_DOWN) return true
        val n = pins.size; if (n == 0) return true
        val slot = width / n.toFloat()
        val i = (e.x / slot).toInt().coerceIn(0, n - 1)
        if (e.y < height * 0.70f) {
            current[i] = (current[i] + 1) % 5
            if (current[i] == pins[i]) {
                // satisfying "set" click
                setAt[i] = SystemClock.uptimeMillis()
                SoundFx.click()
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                handler.postDelayed({ invalidate() }, 380)
            } else {
                SoundFx.move()
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            invalidate()
            if (current.contentEquals(pins)) {
                SoundFx.success()
                onSolved?.invoke()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

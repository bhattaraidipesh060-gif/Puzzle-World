package com.jeiu.puzzleworld

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Mechanical vault dial — a rotary tumbler instead of tap-buttons.
 *
 * Each ring is drawn as a circular dial with 10 tick marks, a top alignment
 * notch, and a small "tumbler strip" (previous / current / next digit) that
 * reads like a real combination lock. Spin a ring by dragging vertically
 * (up = +1, down = -1) or tap the top/bottom half for single steps. Every step
 * gives a click + haptic tick and flashes the ring that just moved, and the
 * whole dial glows green once the combination lines up.
 *
 * The solve contract is unchanged: `setDial(combination)` + `onSolved`, with the
 * same modulo-10 digit ring as before — so every existing vault solution works.
 */
class VaultDialView(context: Context) : View(context) {
    var onSolved: (() -> Unit)? = null

    private var combination = intArrayOf()
    private var current = intArrayOf()
    private var solved = false

    // Drag + flash state
    private var dragRing = -1
    private var lastDragY = 0f
    private var dragAccum = 0f
    private var moved = false
    private var flashRing = -1
    private var flashUntil = 0L

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val marker = Path()

    private fun dp(v: Float) = v * resources.displayMetrics.density

    fun setDial(combination: IntArray) {
        this.combination = combination
        this.current = IntArray(combination.size)
        this.solved = false
        this.flashRing = -1
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = combination.size
        if (n == 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        val slot = w / n
        val now = System.currentTimeMillis()

        // Header cue
        if (solved) {
            tp.textSize = dp(16f)
            tp.color = Color.parseColor("#10B981")
            canvas.drawText("✓ Vault cracked!", w / 2, h * 0.055f, tp)
        } else {
            tp.textSize = dp(12.5f)
            tp.color = Color.parseColor("#94A3B8")
            canvas.drawText("Spin the dials — align each digit to the notch", w / 2, h * 0.055f, tp)
        }

        for (i in 0 until n) {
            val cx = i * slot + slot / 2
            val cy = h * 0.58f
            val r = minOf(slot, h * 0.40f) * 0.5f
            drawDial(canvas, cx, cy, r, i, now)
        }
    }

    private fun drawDial(canvas: Canvas, cx: Float, cy: Float, r: Float, i: Int, now: Long) {
        val digit = current[i]
        val flashing = flashRing == i && now < flashUntil
        val accent = when {
            solved -> Color.parseColor("#10B981")
            flashing -> Color.parseColor("#67E8F9")
            else -> Color.parseColor("#22D3EE")
        }

        // Outer ring (glows when solved, brightens on flash)
        p.style = Paint.Style.STROKE
        p.strokeWidth = dp(3f)
        p.color = if (solved) Color.parseColor("#10B981")
        else if (flashing) Color.parseColor("#22D3EE")
        else Color.parseColor("#334155")
        canvas.drawCircle(cx, cy, r, p)

        // Dial face
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#0B1220")
        canvas.drawCircle(cx, cy, r - dp(3f), p)

        // Ten tick marks around the rim
        p.style = Paint.Style.STROKE
        p.strokeWidth = dp(1.5f)
        val rOut = r - dp(6f)
        val rIn = r - dp(12f)
        for (k in 0 until 10) {
            val ang = (-90.0 + k * 36.0) * PI / 180.0
            val x1 = cx + (rOut * cos(ang)).toFloat()
            val y1 = cy + (rOut * sin(ang)).toFloat()
            val x2 = cx + (rIn * cos(ang)).toFloat()
            val y2 = cy + (rIn * sin(ang)).toFloat()
            p.color = if (k == digit) accent else Color.parseColor("#334155")
            canvas.drawLine(x1, y1, x2, y2, p)
        }

        // Top alignment notch (the read position)
        val topY = cy - r
        marker.reset()
        marker.moveTo(cx - dp(8f), topY - dp(2f))
        marker.lineTo(cx + dp(8f), topY - dp(2f))
        marker.lineTo(cx, topY + dp(10f))
        marker.close()
        p.style = Paint.Style.FILL
        p.color = if (solved) Color.parseColor("#10B981") else Color.parseColor("#F59E0B")
        canvas.drawPath(marker, p)

        // Tumbler strip: previous / current / next digit stacked under the notch
        val wy = cy - r * 0.55f
        tp.textAlign = Paint.Align.CENTER
        tp.textSize = r * 0.30f
        tp.color = Color.parseColor("#475569")
        canvas.drawText(((digit + 9) % 10).toString(), cx, wy - r * 0.30f, tp)
        tp.textSize = r * 0.62f
        tp.color = accent
        canvas.drawText(digit.toString(), cx, wy + r * 0.08f, tp)
        tp.textSize = r * 0.30f
        tp.color = Color.parseColor("#475569")
        canvas.drawText(((digit + 1) % 10).toString(), cx, wy + r * 0.44f, tp)
    }

    private fun spin(i: Int, delta: Int) {
        current[i] = (current[i] + delta + 10) % 10
        flashRing = i
        flashUntil = System.currentTimeMillis() + 160L
        SoundFx.click()
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        if (current.contentEquals(combination)) {
            solved = true
            SoundFx.success()
            invalidate()
            performClick()
            onSolved?.invoke()
        } else {
            invalidate()
            postInvalidateDelayed(200)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (solved) return true
        val n = combination.size
        if (n == 0) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val slot = width / n.toFloat()
                dragRing = (e.x / slot).toInt().coerceIn(0, n - 1)
                lastDragY = e.y
                dragAccum = 0f
                moved = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragRing < 0) return true
                val dy = e.y - lastDragY
                lastDragY = e.y
                dragAccum += dy
                val step = (dragAccum / dp(30f)).toInt()
                if (step != 0) {
                    dragAccum -= step * dp(30f)
                    moved = true
                    spin(dragRing, -step) // swipe up = +1, swipe down = -1
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragRing >= 0 && !moved) {
                    val h = height.toFloat()
                    val delta = if (e.y < h * 0.5f) 1 else -1
                    spin(dragRing, delta)
                }
                dragRing = -1
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

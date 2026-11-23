package com.jeiu.puzzleworld

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Clockwork Observatory — align the telescope's brass focus gears to bring a
 * constellation into view through the eyepiece.
 *
 * The solve contract is unchanged: `setGears(angles, targets)` and the ±8°
 * tolerance check, with a tap still rotating a gear by +15° (drag = ±15°).
 * The new layer is purely visual/atmospheric: a night sky behind a circular
 * eyepiece, and a "celestial clock" constellation whose stars are scattered
 * while the gears are misaligned and snap into a glowing figure once they line
 * up — so "matching the target constellation" is expressed as focusing it.
 */
class GearView(context: Context) : View(context) {
    var onSolved: (() -> Unit)? = null

    private var gears = intArrayOf()      // current angle per gear
    private var targets = intArrayOf()    // target angle per gear
    private var solved = false

    // Drag state
    private var dragRing = -1
    private var lastDragY = 0f
    private var dragAccum = 0f
    private var moved = false

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    // "Celestial clock" constellation: 8 outer stars + a centre star.
    private val outerStars: List<FloatArray> = (0 until 8).map { k ->
        val a = Math.toRadians((k * 45.0 - 90.0))
        floatArrayOf(0.5f + 0.34f * cos(a).toFloat(), 0.5f + 0.34f * sin(a).toFloat())
    }
    private val centreStar = floatArrayOf(0.5f, 0.5f)

    private fun dp(v: Float) = v * resources.displayMetrics.density

    fun setGears(angles: IntArray, targets: IntArray) {
        this.gears = angles.copyOf()
        this.targets = targets.copyOf()
        this.solved = false
        invalidate()
    }

    private fun aligned(): Boolean = gears.indices.all { abs(norm(gears[it] - targets[it])) <= 8 }

    private fun norm(a: Int): Int { var x = a % 360; if (x < 0) x += 360; return if (x > 180) x - 360 else x }

    /** Deterministic hash in [0,1) for star positions / scatter directions. */
    private fun hash(i: Int): Float {
        var x = i * 2654435761.toInt()
        x = x xor (x ushr 13)
        x *= 0x5bd1e995
        x = x xor (x ushr 15)
        return (x and 0x7fffffff) / 2147483647f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val n = gears.size

        // Night-sky backdrop
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#0A0E1A")
        canvas.drawRect(0f, 0f, w, h, p)

        // Ambient starfield (deterministic)
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#64748B")
        for (i in 0 until 60) {
            val x = hash(i * 2 + 1) * w
            val y = hash(i * 2 + 2) * h
            val rr = 1f + hash(i * 2 + 3) * 1.8f
            canvas.drawCircle(x, y, rr, p)
        }

        // Eyepiece (upper)
        val ey = h * 0.30f
        val er = min(w * 0.42f, h * 0.28f)

        // misalignment drives the "focus": 0 = sharp, 1 = fully scattered
        val misalign = if (n == 0) 0f else
            (gears.indices.maxOf { abs(norm(gears[it] - targets[it])).toFloat() } / 180f).coerceIn(0f, 1f)
        val focus = 1f - misalign

        // Eyepiece body
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#0B1220")
        canvas.drawCircle(w / 2, ey, er, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = dp(4f)
        p.color = if (solved) Color.parseColor("#10B981") else Color.parseColor("#334155")
        canvas.drawCircle(w / 2, ey, er, p)
        p.strokeWidth = dp(1.5f)
        p.color = Color.parseColor("#263449")
        canvas.drawCircle(w / 2, ey, er - dp(7f), p)

        // Constellation stars (scattered by misalignment)
        val scale = er * 0.72f
        val starAlpha = (0.25f + 0.75f * focus)
        val starColor = if (solved) "#FBBF24" else "#F59E0B"
        p.color = adjustAlpha(starColor, starAlpha)
        p.style = Paint.Style.FILL

        fun scatterPt(base: FloatArray, idx: Int): FloatArray {
            val dx = (hash(idx * 3 + 1) * 2f - 1f) * misalign * er * 0.26f
            val dy = (hash(idx * 3 + 2) * 2f - 1f) * misalign * er * 0.26f
            return floatArrayOf(
                w / 2 + (base[0] - 0.5f) * 2f * scale + dx,
                ey + (base[1] - 0.5f) * 2f * scale + dy
            )
        }

        // Constellation edges (spokes + ring), drawn first so stars sit on top
        p.style = Paint.Style.STROKE
        p.strokeWidth = dp(1.5f)
        p.color = adjustAlpha("#FBBF24", starAlpha * 0.55f)
        val centrePt = scatterPt(centreStar, 0)
        val outerPts = outerStars.mapIndexed { i, s -> scatterPt(s, i + 1) }
        for (i in outerPts.indices) {
            canvas.drawLine(centrePt[0], centrePt[1], outerPts[i][0], outerPts[i][1], p)
            val j = (i + 1) % outerPts.size
            canvas.drawLine(outerPts[i][0], outerPts[i][1], outerPts[j][0], outerPts[j][1], p)
        }

        // Stars
        p.style = Paint.Style.FILL
        canvas.drawCircle(centrePt[0], centrePt[1], dp(3.5f), p)
        outerPts.forEach { canvas.drawCircle(it[0], it[1], dp(2.8f), p) }

        // Gears (lower) — brass focus mechanism
        if (n == 0) return
        val r = min(w / (n * 2f), h * 0.22f)
        val cy = h * 0.74f
        for (i in 0 until n) {
            val cx = (i + 0.5f) * w / n
            drawBrassGear(canvas, cx, cy, r, i)
            tp.textSize = 12f
            tp.color = Color.parseColor("#94A3B8")
            canvas.drawText("${norm(gears[i] - targets[i])}°", cx, cy + r + dp(16f), tp)
        }

        // Status
        if (solved) {
            tp.textSize = 16f
            tp.color = Color.parseColor("#10B981")
            canvas.drawText("✓ Constellation locked!", w / 2, h * 0.96f, tp)
        } else if (n > 0) {
            tp.textSize = 12.5f
            tp.color = Color.parseColor("#94A3B8")
            canvas.drawText("Turn the brass gears to focus the constellation", w / 2, h * 0.96f, tp)
        }
    }

    private fun drawBrassGear(canvas: Canvas, cx: Float, cy: Float, r: Float, i: Int) {
        val ok = abs(norm(gears[i] - targets[i])) <= 8
        val brass = if (ok) "#34D399" else "#C9A227"
        val dark = if (ok) "#1A3320" else "#3A2E14"

        // Teeth
        p.style = Paint.Style.STROKE
        p.strokeWidth = dp(4f)
        p.color = Color.parseColor(brass)
        for (k in 0 until 12) {
            val ang = Math.toRadians((k * 30 + gears[i]).toDouble())
            val a2 = Math.toRadians((k * 30 + gears[i] + 15).toDouble())
            canvas.drawLine(
                cx + (r - 4) * cos(ang).toFloat(), cy + (r - 4) * sin(ang).toFloat(),
                cx + (r + 7) * cos(a2).toFloat(), cy + (r + 7) * sin(a2).toFloat(), p
            )
        }

        // Body
        p.style = Paint.Style.FILL
        p.color = Color.parseColor(dark)
        canvas.drawCircle(cx, cy, r * 0.85f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = dp(3f)
        p.color = Color.parseColor(brass)
        canvas.drawCircle(cx, cy, r * 0.85f, p)

        // Spokes + hub
        p.strokeWidth = dp(2.5f)
        for (k in 0 until 3) {
            val ang = Math.toRadians((k * 120 + gears[i]).toDouble())
            canvas.drawLine(
                cx + r * 0.16f * cos(ang).toFloat(), cy + r * 0.16f * sin(ang).toFloat(),
                cx + r * 0.80f * cos(ang).toFloat(), cy + r * 0.80f * sin(ang).toFloat(), p
            )
        }
        p.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, r * 0.16f, p)
    }

    private fun adjustAlpha(hex: String, alpha: Float): Int {
        val c = Color.parseColor(hex)
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        return Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))
    }

    private fun spin(i: Int, delta: Int) {
        gears[i] = (gears[i] + delta * 15 + 360) % 360
        SoundFx.click()
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        if (aligned()) {
            solved = true
            SoundFx.success()
            invalidate()
            performClick()
            onSolved?.invoke()
        } else {
            invalidate()
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (solved) return true
        val n = gears.size
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
                val step = (dragAccum / dp(34f)).toInt()
                if (step != 0) {
                    dragAccum -= step * dp(34f)
                    moved = true
                    spin(dragRing, -step)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragRing >= 0 && !moved) {
                    spin(dragRing, 1) // tap still rotates +15°, preserving the original solve
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

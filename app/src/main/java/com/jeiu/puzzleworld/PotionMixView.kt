package com.jeiu.puzzleworld

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.sin

/**
 * Potion mixing (jug) — fill / empty / pour beakers to hit the target volume.
 *
 * The solve contract is unchanged (`setPuzzle(caps, target)`, FILL/EMPTY/POUR
 * into the last non-selected beaker, target match fires `onSolved`). This pass
 * adds realistic liquid feedback: smoothly animated levels, a pouring stream
 * with droplets, a sloshing meniscus, bubbles, per-beaker colours and a
 * selection glow — without touching the logic.
 */
class PotionMixView(context: Context) : View(context) {
    var onSolved: (() -> Unit)? = null

    private var caps = intArrayOf()
    private var vols = intArrayOf()
    private var target = 0
    private var selected = 0

    // display state (animated)
    private var displayVols = FloatArray(0)
    private var anim: ValueAnimator? = null
    private var pourFrom = -1
    private var pourTo = -1
    private var pourFrac = 0f

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private val liquidColors = intArrayOf(
        Color.parseColor("#22D3EE"),
        Color.parseColor("#E879F9"),
        Color.parseColor("#FBBF24"),
        Color.parseColor("#34D399")
    )

    private fun dp(v: Float) = v * resources.displayMetrics.density

    fun setPuzzle(caps: IntArray, target: Int) {
        anim?.cancel()
        this.caps = caps
        this.vols = IntArray(caps.size)
        this.target = target
        this.selected = 0
        this.displayVols = FloatArray(caps.size)
        this.pourFrom = -1; this.pourTo = -1; this.pourFrac = 0f
        invalidate()
    }

    private fun color(i: Int) = liquidColors[i % liquidColors.size]

    private fun lighten(c: Int, f: Float): Int = Color.rgb(
        (Color.red(c) + (255 - Color.red(c)) * f).toInt(),
        (Color.green(c) + (255 - Color.green(c)) * f).toInt(),
        (Color.blue(c) + (255 - Color.blue(c)) * f).toInt()
    )

    private fun withAlpha(c: Int, a: Int) = Color.argb(a.coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

    private fun destOf(from: Int): Int {
        var d = -1
        for (i in caps.indices) if (i != from) d = i
        return d
    }

    private fun animateTo(newVols: IntArray, from: Int, to: Int) {
        anim?.cancel()
        val start = displayVols.copyOf()
        val end = FloatArray(newVols.size) { newVols[it].toFloat() }
        pourFrom = from
        pourTo = to
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (from >= 0 && to >= 0) 500L else 320L
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                pourFrac = va.animatedFraction
                val f = va.animatedFraction
                for (i in displayVols.indices) {
                    displayVols[i] = start[i] + (end[i] - start[i]) * f
                }
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    pourFrom = -1; pourTo = -1; pourFrac = 0f
                    invalidate()
                }
            })
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = caps.size; if (n == 0) return
        val w = width.toFloat(); val h = height.toFloat()
        val bw = w / n
        val topY = h * 0.14f
        val botY = h * 0.62f
        val beakerH = botY - topY

        drawStream(canvas, bw, topY)

        for (i in 0 until n) {
            val cx = i * bw + bw / 2
            val x0 = i * bw + dp(10f)
            val x1 = (i + 1) * bw - dp(10f)
            val level = (displayVols[i] / caps[i].toFloat()).coerceIn(0f, 1f)
            val fillH = beakerH * level
            val surfaceY = botY - fillH

            // sloshing surface while this beaker pours / receives
            val wobble = if (pourFrom == i || pourTo == i)
                sin(pourFrac * 3.14159f * 3f) * (1f - pourFrac) * dp(3.5f) else 0f
            val liqTop = surfaceY + wobble

            if (level > 0f) {
                // liquid body
                p.style = Paint.Style.FILL
                p.color = withAlpha(color(i), 210)
                canvas.drawRoundRect(x0 + dp(2f), liqTop, x1 - dp(2f), botY - dp(2f), dp(8f), dp(8f), p)
                // meniscus highlight
                p.color = lighten(color(i), 0.45f)
                canvas.drawRoundRect(x0 + dp(3f), liqTop, x1 - dp(3f), liqTop + dp(4.5f), dp(3f), dp(3f), p)
                // bubbles
                p.color = Color.argb(95, 255, 255, 255)
                canvas.drawCircle(cx - dp(9f), botY - fillH * 0.55f, dp(2.2f), p)
                canvas.drawCircle(cx + dp(7f), botY - fillH * 0.3f, dp(1.6f), p)
                canvas.drawCircle(cx - dp(2f), botY - fillH * 0.78f, dp(1.3f), p)
            }

            // glass outline
            p.style = Paint.Style.STROKE
            p.strokeWidth = dp(2.5f)
            p.color = if (i == selected) Color.parseColor("#22D3EE") else Color.parseColor("#475569")
            canvas.drawRoundRect(x0, topY, x1, botY, dp(14f), dp(14f), p)
            // rim
            canvas.drawRoundRect(x0 - dp(3f), topY - dp(7f), x1 + dp(3f), topY + dp(4f), dp(7f), dp(7f), p)

            // labels
            tp.textSize = 16f; tp.color = Color.WHITE
            canvas.drawText("${vols[i]} / ${caps[i]}", cx, botY + dp(24f), tp)
            tp.textSize = 12f
            tp.color = if (vols[i] == target) Color.parseColor("#10B981") else Color.parseColor("#94A3B8")
            canvas.drawText("beaker $i", cx, botY + dp(40f), tp)
        }

        // action buttons
        val buttons = arrayOf("FILL", "EMPTY", "POUR →")
        val by = h * 0.82f; val bhh = h * 0.12f
        val bww = w / 3f
        for (i in buttons.indices) {
            p.color = Color.parseColor("#1E293B")
            canvas.drawRoundRect(i * bww + dp(8f), by, (i + 1) * bww - dp(8f), by + bhh, dp(20f), dp(20f), p)
            tp.textSize = 16f; tp.color = Color.parseColor("#22D3EE")
            canvas.drawText(buttons[i], i * bww + bww / 2, by + bhh / 2 + dp(6f), tp)
        }
        if (vols.any { it == target }) {
            tp.textSize = 15f; tp.color = Color.parseColor("#10B981")
            canvas.drawText("✓ Target $target reached!", w / 2, h * 0.07f, tp)
        }
    }

    private fun drawStream(canvas: Canvas, bw: Float, topY: Float) {
        if (pourFrom < 0 || pourTo < 0 || pourFrac <= 0f) return
        val x1 = (pourFrom + 0.5f) * bw
        val x2 = (pourTo + 0.5f) * bw
        val dir = if (x2 >= x1) 1f else -1f
        val srcX = x1 + dir * dp(8f)
        val dstX = x2 - dir * dp(8f)
        val midX = (srcX + dstX) / 2f
        val ctrlY = topY - dp(30f)

        val path = Path()
        path.moveTo(srcX, topY - dp(2f))
        path.quadTo(midX, ctrlY, dstX, topY - dp(2f))

        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = dp(6.5f) * (0.55f + 0.45f * sin(pourFrac * 3.14159f))
        p.color = withAlpha(color(pourFrom), 150)
        canvas.drawPath(path, p)

        // droplets along the stream arc
        p.style = Paint.Style.FILL
        for (k in 0 until 6) {
            val t = (k + 0.5f) / 6f
            val u = 1f - t
            val px = u * u * srcX + 2 * u * t * midX + t * t * dstX
            val py = u * u * (topY - dp(2f)) + 2 * u * t * ctrlY + t * t * (topY - dp(2f))
            val alpha = (100 + 90 * sin(pourFrac * 3.14159f + k)).toInt()
            p.color = withAlpha(color(pourFrom), alpha)
            canvas.drawCircle(px, py, dp(2.3f), p)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_DOWN) return true
        val n = caps.size; if (n == 0) return true
        val w = width.toFloat(); val h = height.toFloat()

        if (e.y > h * 0.78f) {
            val bww = w / 3f
            val newVols = vols.copyOf()
            var from = -1; var to = -1
            when ((e.x / bww).toInt().coerceIn(0, 2)) {
                0 -> newVols[selected] = caps[selected]                 // FILL
                1 -> newVols[selected] = 0                               // EMPTY
                2 -> {                                                   // POUR
                    val dest = destOf(selected)
                    if (dest >= 0) {
                        val space = caps[dest] - newVols[dest]
                        val amount = minOf(newVols[selected], space)
                        newVols[selected] -= amount
                        newVols[dest] += amount
                        from = selected; to = dest
                    }
                }
            }
            SoundFx.click()
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            vols = newVols
            animateTo(newVols, from, to)
            if (vols.any { it == target }) onSolved?.invoke()
            return true
        }

        val idx = (e.x / (w / n)).toInt().coerceIn(0, n - 1)
        if (idx != selected) {
            selected = idx
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            invalidate()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

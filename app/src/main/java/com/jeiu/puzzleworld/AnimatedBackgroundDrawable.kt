package com.jeiu.puzzleworld

import android.graphics.*
import android.graphics.drawable.Drawable
import kotlin.math.sin
import kotlin.random.Random

/**
 * Animated room background: a slowly breathing gradient with drifting, twinkling
 * energy motes. Self-animates via [scheduleSelf]/[invalidateSelf] so it can be
 * set directly as a View's background.
 */
class AnimatedBackgroundDrawable(
    private val topColor: Int,
    private val bottomColor: Int,
    private val accentColor: Int
) : Drawable() {

    private data class Particle(
        val x: Float, val y: Float, val radius: Float,
        val speed: Float, val phase: Float, val alpha: Float
    )

    private val particles = mutableListOf<Particle>()
    private val rng = Random(System.currentTimeMillis())

    private val gradientPaint = Paint()
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val tick = Runnable { invalidateSelf() }

    init {
        for (i in 0 until 26) {
            particles.add(
                Particle(
                    x = rng.nextFloat(),
                    y = rng.nextFloat(),
                    radius = rng.nextFloat() * 2.6f + 0.9f,
                    speed = rng.nextFloat() * 0.35f + 0.08f,
                    phase = rng.nextFloat() * 6.283f,
                    alpha = rng.nextFloat() * 0.5f + 0.18f
                )
            )
        }
    }

    override fun draw(canvas: Canvas) {
        val t = System.currentTimeMillis() / 1000f
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return

        // Breathing gradient — the vertical extent gently shifts.
        val shift = (sin(t * 0.28f) * 0.5f + 0.5f) * h * 0.35f
        gradientPaint.shader = LinearGradient(
            0f, -shift, 0f, h + shift,
            topColor, bottomColor, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, gradientPaint)

        // Drifting, twinkling energy motes (rise slowly, wrap around).
        for (p in particles) {
            val yy = ((p.y * h - t * p.speed * h) % h).let { if (it < 0) it + h else it }
            val xx = p.x * w + sin(t * 0.6f + p.phase) * 14f
            val twinkle = 0.5f + 0.5f * sin(t * 1.5f + p.phase)
            particlePaint.color = accentColor
            particlePaint.alpha = (p.alpha * (0.35f + 0.65f * twinkle) * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(xx, yy, p.radius, particlePaint)
        }

        // Keep the animation going (~20fps ambient effect).
        unscheduleSelf(tick)
        scheduleSelf(tick, 50L)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        /**
         * Replaces the given activity's content-root background with a themed
         * animated background, so every screen feels alive. Call it in onCreate
         * after setContentView. Falls back gracefully if the root can't be found.
         */
        fun applyTo(activity: android.app.Activity, top: Int, bottom: Int, accent: Int) {
            val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            val root = content?.getChildAt(0) ?: return
            root.background = AnimatedBackgroundDrawable(top, bottom, accent)
        }
    }
}

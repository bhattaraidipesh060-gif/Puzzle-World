package com.jeiu.puzzleworld

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Full-screen celebration overlay: emits a burst of colored confetti particles
 * from the centre that fly outward and fade away. Designed to be placed as a
 * topmost child in a FrameLayout, shown with `burst()`, and hidden when done.
 */
class CelebrationView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class Particle(
        val color: Int,
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var radius: Float,
        var alpha: Float,
        var life: Float = 1f
    )

    private val particles = mutableListOf<Particle>()
    private var animator: ValueAnimator? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val colors = listOf(
        Color.parseColor("#FF4081"), Color.parseColor("#00E676"),
        Color.parseColor("#448AFF"), Color.parseColor("#FFD740"),
        Color.parseColor("#00E5FF"), Color.parseColor("#B388FF"),
        Color.parseColor("#FF6D00"), Color.parseColor("#EA80FC")
    )
    private val rng = Random(System.currentTimeMillis())

    init { visibility = GONE }

    /** Burst [count] particles outward from the centre of the view. */
    fun burst(count: Int = 60) {
        particles.clear()
        animator?.cancel()
        val cx = width / 2f; val cy = height / 2f
        if (cx <= 0f) {
            // view not measured yet; defer
            post { burst(count) }; return
        }
        for (i in 0 until count) {
            val angle = rng.nextFloat() * 360f
            val speed = rng.nextFloat() * 600f + 200f
            val rad = angle * Math.PI.toFloat() / 180f
            particles.add(Particle(
                color = colors[rng.nextInt(colors.size)],
                x = cx, y = cy,
                vx = speed * cos(rad),
                vy = speed * sin(rad),
                radius = rng.nextFloat() * 10f + 4f,
                alpha = 1f
            ))
        }
        visibility = VISIBLE
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val dt = 0.016f // ~60fps step
                for (p in particles) {
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                    p.vy += 200f * dt  // gravity
                    p.life -= dt / 1.8f
                    p.alpha = p.life.coerceIn(0f, 1f) * 0.85f
                }
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(anim: android.animation.Animator, isReverse: Boolean) {
                    visibility = GONE; particles.clear()
                }
            })
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (p in particles) {
            if (p.alpha <= 0f) continue
            paint.color = p.color
            paint.alpha = (p.alpha * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(p.x, p.y, p.radius, paint)
        }
    }
}

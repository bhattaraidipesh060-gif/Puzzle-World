package com.jeiu.puzzleworld

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator

/**
 * Simon-says style memory game. Four colored panels blink in a sequence; the
 * player must tap them back in the same order. Plays the reveal animation then
 * waits for input. Highlights feedback on each tap.
 */
class ColorSequenceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var sequence: List<Int> = emptyList()
        set(v) { field = v; playerIndex = 0; phase = Phase.Ready; invalidate() }
    var panelColors: List<Int> = listOf(
        Color.parseColor("#FF4081"),
        Color.parseColor("#00E676"),
        Color.parseColor("#448AFF"),
        Color.parseColor("#FFD740")
    )
    var revealDelayMs: Long = 800L
    var onSequenceComplete: ((Boolean) -> Unit)? = null
    var onPhaseChange: ((String) -> Unit)? = null

    enum class Phase { Ready, Revealing, Input, Success, Fail }

    private var phase = Phase.Ready
    private var playerIndex = 0
    private var revealIndex = 0
    private var highlightedIndex = -1
    private var blinkAlpha = 1f
    private var blinkAnim: ValueAnimator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val panels = mutableListOf<RectF>()

    private val bg = Paint().apply { color = Color.parseColor("#161B22") }
    private val borderP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E2A3A"); style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val dimOverlay = Paint().apply {
        color = Color.parseColor("#AA0D1117"); style = Paint.Style.FILL
    }

    fun startReveal() {
        if (sequence.isEmpty()) return
        phase = Phase.Revealing
        revealIndex = 0
        playerIndex = 0
        onPhaseChange?.invoke("Watch the pattern…")
        flashNext()
    }

    private fun flashNext() {
        if (phase != Phase.Revealing || revealIndex >= sequence.size) {
            phase = Phase.Input
            highlightedIndex = -1
            onPhaseChange?.invoke("Your turn — tap the panels")
            invalidate()
            return
        }
        highlightedIndex = sequence[revealIndex]
        blinkIn()
    }

    private fun blinkIn() {
        blinkAnim?.cancel()
        blinkAnim = ValueAnimator.ofFloat(0.3f, 1f).apply {
            duration = 250L; interpolator = OvershootInterpolator(1.2f)
            addUpdateListener { blinkAlpha = animatedFraction; invalidate() }
            start()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(anim: android.animation.Animator, isReverse: Boolean) {
                    handler.postDelayed({ blinkOut() }, revealDelayMs - 250L)
                }
            })
        }
    }

    private fun blinkOut() {
        blinkAnim?.cancel()
        blinkAnim = ValueAnimator.ofFloat(1f, 0.3f).apply {
            duration = 200L
            addUpdateListener { blinkAlpha = animatedFraction; invalidate() }
            start()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(anim: android.animation.Animator, isReverse: Boolean) {
                    revealed()
                }
            })
        }
    }

    private fun revealed() {
        highlightedIndex = -1
        revealIndex++
        flashNext()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bg)

        val pad = 8f; val cols = 2; val rows = 2
        val cw = (w - pad * (cols + 1)) / cols
        val ch = (h - pad * (rows + 1)) / rows
        panels.clear()
        for (i in 0 until 4) {
            val cx = pad + (i % cols) * (cw + pad)
            val cy = pad + (i / cols) * (ch + pad)
            val rect = RectF(cx, cy, cx + cw, cy + ch)
            panels.add(rect)

            val c = panelColors[i]
            val bright = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = c; style = Paint.Style.FILL
            }
            val dim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                val alpha = (0x80 + (0x7F * 0.3f)).toInt()
                color = Color.argb(alpha, Color.red(c), Color.green(c), Color.blue(c))
                style = Paint.Style.FILL
            }
            val paint = if (highlightedIndex == i) {
                val a = (blinkAlpha * 255).toInt().coerceIn(0, 255)
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))
                    style = Paint.Style.FILL
                }
            } else dim
            canvas.drawRoundRect(rect, 16f, 16f, paint)
            canvas.drawRoundRect(rect, 16f, 16f, borderP)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_DOWN || phase != Phase.Input) return true
        val px = e.x; val py = e.y
        for (i in panels.indices) {
            if (panels[i].contains(px, py)) {
                checkTap(i)
                return true
            }
        }
        return true
    }

    private fun checkTap(idx: Int) {
        if (playerIndex >= sequence.size) return
        if (idx == sequence[playerIndex]) {
            highlightedIndex = idx
            blinkAlpha = 1f
            invalidate()
            handler.postDelayed({
                highlightedIndex = -1
                invalidate()
                playerIndex++
                if (playerIndex >= sequence.size) {
                    phase = Phase.Success
                    onPhaseChange?.invoke("✅ Perfect!")
                    onSequenceComplete?.invoke(true)
                }
            }, 300L)
        } else {
            phase = Phase.Fail
            onPhaseChange?.invoke("❌ Wrong — watch again")
            onSequenceComplete?.invoke(false)
            // reveal again after a delay
            handler.postDelayed({ startReveal() }, 1200L)
        }
    }

    fun reset() {
        blinkAnim?.cancel()
        phase = Phase.Ready
        highlightedIndex = -1
        playerIndex = 0
        handler.removeCallbacksAndMessages(null)
        invalidate()
    }
}

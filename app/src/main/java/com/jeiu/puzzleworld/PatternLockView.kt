package com.jeiu.puzzleworld

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.pow

/**
 * Interactive pattern-lock grid: a 3×3 array of dots. The player drags their
 * finger across the dots in the correct order to unlock. Accepted patterns are
 * compared against the [correctPattern] list of dot indices (0..8). Visual
 * feedback lights up connected dots when the drag intersects them.
 *
 * Dot positions follow the standard Android lock-screen layout:
 *   0 1 2
 *   3 4 5
 *   6 7 8
 */
class PatternLockView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var gridSize = 3
        set(v) { field = v; rebuildDots(); invalidate() }
    var correctPattern: List<Int> = emptyList()
    var onPatternComplete: ((Boolean) -> Unit)? = null

    private var dots = listOf<Pair<Float, Float>>()
    private val entered = mutableListOf<Int>()
    private var dragging = false
    private var fingerX = 0f; private var fingerY = 0f
    private var finished = false

    // ── paints ───────────────────────────────────────────────────────────
    private val bg = Paint().apply { color = Color.parseColor("#161B22") }
    private val dotOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A3442"); style = Paint.Style.FILL
    }
    private val dotInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#555E6E"); style = Paint.Style.FILL
    }
    private val dotSelected = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF"); style = Paint.Style.FILL
    }
    private val dotError = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4081"); style = Paint.Style.FILL
    }
    private val dotSuccess = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676"); style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF"); style = Paint.Style.STROKE
        strokeWidth = 8f; strokeCap = Paint.Cap.ROUND
    }
    private val fingerLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF"); style = Paint.Style.STROKE
        strokeWidth = 5f; strokeCap = Paint.Cap.ROUND; alpha = 160
    }

    init { rebuildDots() }

    private fun rebuildDots() {
        dots = emptyList()
        // will compute on first layout
    }

    private fun computeDots() {
        val w = width.toFloat(); val h = height.toFloat()
        val margin = minOf(w, h) * 0.15f
        val stepX = (w - margin * 2) / (gridSize - 1).coerceAtLeast(1)
        val stepY = (h - margin * 2) / (gridSize - 1).coerceAtLeast(1)
        dots = (0 until gridSize * gridSize).map { idx ->
            val col = idx % gridSize; val row = idx / gridSize
            Pair(margin + col * stepX, margin + row * stepY)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()

        if (dots.size != gridSize * gridSize) computeDots()
        canvas.drawRect(0f, 0f, w, h, bg)

        val dotR = minOf(w, h) / (gridSize * 4.5f)

        // draw connecting lines between entered dots
        for (i in 0 until entered.size - 1) {
            val a = dots[entered[i]]; val b = dots[entered[i + 1]]
            canvas.drawLine(a.first, a.second, b.first, b.second,
                if (finished && !isCorrect()) dotError else linePaint)
        }
        // finger-follow line while dragging
        if (dragging && entered.isNotEmpty()) {
            val last = dots[entered.last()]
            canvas.drawLine(last.first, last.second, fingerX, fingerY, fingerLine)
        }

        // draw dots
        val conflict = finished && !isCorrect()
        for (i in 0 until gridSize * gridSize) {
            val (cx, cy) = dots[i]
            val inPath = entered.contains(i)

            val outer = when {
                conflict && inPath -> dotError
                finished && isCorrect() && inPath -> dotSuccess
                inPath -> dotSelected
                else -> dotOuter
            }
            canvas.drawCircle(cx, cy, dotR, outer)
            canvas.drawCircle(cx, cy, dotR * 0.62f,
                if (conflict && inPath) dotError else dotInner)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (finished) return true
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            dragging = true
            entered.clear()
            fingerX = e.x; fingerY = e.y
            hitTest()
            invalidate()
            return true
        }
        if (e.actionMasked == MotionEvent.ACTION_MOVE && dragging) {
            fingerX = e.x; fingerY = e.y
            hitTest()
            invalidate()
            return true
        }
        if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
            dragging = false
            fingerX = 0f; fingerY = 0f
            if (entered.isNotEmpty()) {
                finished = true
                val correct = isCorrect()
                onPatternComplete?.invoke(correct)
            }
            invalidate()
            return true
        }
        return super.onTouchEvent(e)
    }

    fun reset() {
        entered.clear()
        dragging = false
        finished = false
        invalidate()
    }

    private fun hitTest() {
        if (dots.isEmpty()) computeDots()
        val threshold = minOf(width, height).toFloat() / (gridSize * 2.8f)
        for (i in 0 until gridSize * gridSize) {
            val (dx, dy) = dots[i].let { it.first - fingerX to it.second - fingerY }
            val dist = (dx.pow(2) + dy.pow(2)).pow(0.5f)
            if (dist < threshold && i !in entered) {
                entered.add(i)
                return
            }
        }
    }

    private fun isCorrect(): Boolean =
        entered.size == correctPattern.size && entered.zip(correctPattern).all { (a, b) -> a == b }
}

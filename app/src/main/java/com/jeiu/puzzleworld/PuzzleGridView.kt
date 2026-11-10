package com.jeiu.puzzleworld

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.abs

/**
 * Interactive wire-maze surface (Flow-Free style).
 *
 * The player drags a finger from the power outlet (start) to the bulb (end),
 * tracing a path through empty cells while avoiding wall tiles. Reaching the
 * bulb completes the circuit: the path lights up from outlet→bulb (power flow)
 * and the bulb glows.
 */
class PuzzleGridView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var gridCols = 8; var gridRows = 8

    var items: List<PuzzleItem> = emptyList()
        set(v) {
            field = v
            parseItems()
            path.clear(); tracing = false
            flowAnimator?.cancel(); flowProgress = 0f
            bulbGlowAnimator?.cancel(); bulbGlowAlpha = 0f
            glowAlpha = 0f
            completed = false
            onPathChange?.invoke(0)
            startGlow()
            invalidate()
        }

    var obstacles: Set<Pair<Int, Int>> = emptySet()
        set(v) { field = v; invalidate() }

    /** When true the bulb glows and power flows along the traced path. */
    var completed = false
        set(v) {
            field = v
            if (v) { startBulbGlow(); startFlow() }
            else { bulbGlowAnimator?.cancel(); bulbGlowAlpha = 0f; flowProgress = 0f }
            invalidate()
        }

    /** Minimum move count (shortest path) — shown as "par". */
    var par: Int = 0
    var onCircuitComplete: (() -> Unit)? = null
    var onPathChange: ((Int) -> Unit)? = null

    private var startPos = Pair(0, 0)
    private var endPos = Pair(0, 0)
    private val path = mutableListOf<Pair<Int, Int>>()
    private var tracing = false

    private var glowAlpha = 0f
    private var glowAnimator: ValueAnimator? = null
    private var bulbGlowAlpha = 0f
    private var bulbGlowAnimator: ValueAnimator? = null
    private var flowProgress = 0f
    private var flowAnimator: ValueAnimator? = null

    // ── Paints ──────────────────────────────────────────────────────────
    private val bgP = Paint().apply { color = Color.parseColor("#161B22") }
    private val gridP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E2A3A"); strokeWidth = 1.5f; style = Paint.Style.STROKE
    }
    private val glowP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val startFill = Color.parseColor("#00E676")
    private val startGlow = Color.parseColor("#4400E676")
    private val slotFill  = Color.parseColor("#0B3D2E")

    private val bulbGlassUnlit = Color.parseColor("#3A414A")
    private val bulbGlassLit   = Color.parseColor("#FFEB3B")
    private val bulbGlowLit    = Color.parseColor("#66FFEB3B")
    private val bulbFilament   = Color.parseColor("#FFF9C4")
    private val bulbBase       = Color.parseColor("#78909C")
    private val bulbThread     = Color.parseColor("#455A64")

    private val wireFill      = Color.parseColor("#FFD740")
    private val wireDim       = Color.parseColor("#6B5A1E")
    private val obstacleFill  = Color.parseColor("#2A3240")
    private val obstacleEdge  = Color.parseColor("#141A22")

    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f
    }

    private val shaderCache = mutableMapOf<String, RadialGradient>()

    // ═══════════════════════════════════════════════════════════════════
    // Drawing
    // ═══════════════════════════════════════════════════════════════════

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val cw = width / gridCols.toFloat(); val ch = height / gridRows.toFloat()
        val r  = minOf(cw, ch) * 0.35f

        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgP)

        gridP.alpha = 120
        for (i in 0..gridCols) c.drawLine(i * cw, 0f, i * cw, height.toFloat(), gridP)
        for (i in 0..gridRows) c.drawLine(0f, i * ch, width.toFloat(), i * ch, gridP)

        drawObstacles(c, cw, ch, r)
        drawPath(c, cw, ch, r)

        for (it in items) {
            val t = normalizeType(it.type)
            if (t != "start" && t != "end") continue
            val cx = (it.xGrid + 0.5f) * cw; val cy = (it.yGrid + 0.5f) * ch
            if (t == "start") drawOutlet(c, cx, cy, r) else drawBulb(c, cx, cy, r)
        }
    }

    private fun drawObstacles(c: Canvas, cw: Float, ch: Float, r: Float) {
        val wall = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = obstacleFill; style = Paint.Style.FILL }
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = obstacleEdge; style = Paint.Style.STROKE; strokeWidth = 2f }
        for ((x, y) in obstacles) {
            val cx = (x + 0.5f) * cw; val cy = (y + 0.5f) * ch
            val rect = RectF(cx - cw * 0.44f, cy - ch * 0.44f, cx + cw * 0.44f, cy + ch * 0.44f)
            c.drawRoundRect(rect, r * 0.25f, r * 0.25f, wall)
            c.drawRoundRect(rect, r * 0.25f, r * 0.25f, edge)
        }
    }

    private fun drawPath(c: Canvas, cw: Float, ch: Float, r: Float) {
        if (path.size < 2) return
        val litSegs = if (completed) (flowProgress * (path.size - 1)).toInt().coerceAtMost(path.size - 1)
                      else path.size - 1
        val litPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = wireFill; style = Paint.Style.STROKE; strokeWidth = r * 0.95f
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = wireDim; style = Paint.Style.STROKE; strokeWidth = r * 0.95f
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        for (i in 0 until path.size - 1) {
            val a = path[i]; val b = path[i + 1]
            val paint = if (i < litSegs) litPaint else dimPaint
            c.drawLine(
                (a.first + 0.5f) * cw, (a.second + 0.5f) * ch,
                (b.first + 0.5f) * cw, (b.second + 0.5f) * ch, paint
            )
        }
        val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = wireFill; style = Paint.Style.FILL }
        for (i in 1 until path.size - 1) {
            val p = path[i]
            c.drawCircle((p.first + 0.5f) * cw, (p.second + 0.5f) * ch, r * 0.38f, nodePaint)
        }
    }

    private fun drawOutlet(c: Canvas, cx: Float, cy: Float, r: Float) {
        drawGlow(c, cx, cy, r, startGlow, glowAlpha)
        val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = startFill; style = Paint.Style.FILL }
        c.drawCircle(cx, cy, r, plate)
        c.drawCircle(cx, cy, r, strokeP)
        val slot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = slotFill; style = Paint.Style.FILL }
        val sw = r * 0.20f; val sh = r * 0.72f; val gap = r * 0.52f
        c.drawRoundRect(RectF(cx - gap - sw, cy - sh / 2, cx - gap, cy + sh / 2), sw * 0.6f, sw * 0.6f, slot)
        c.drawRoundRect(RectF(cx + gap, cy - sh / 2, cx + gap + sw, cy + sh / 2), sw * 0.6f, sw * 0.6f, slot)
        c.drawCircle(cx, cy + r * 0.62f, r * 0.15f, slot)
    }

    private fun drawBulb(c: Canvas, cx: Float, cy: Float, r: Float) {
        val lit = completed
        // The bulb only glows once the circuit is complete (power connected).
        if (lit) drawGlow(c, cx, cy - r * 0.2f, r * 2.2f, bulbGlowLit, bulbGlowAlpha)

        val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (lit) bulbGlassLit else bulbGlassUnlit; style = Paint.Style.FILL
        }
        c.drawCircle(cx, cy - r * 0.2f, r * 0.62f, glass)
        c.drawCircle(cx, cy - r * 0.2f, r * 0.62f, strokeP)

        // Filament — bright when lit, dim when the bulb is off.
        val fil = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (lit) bulbFilament else Color.parseColor("#4A535C")
            style = Paint.Style.STROKE; strokeWidth = r * 0.18f; strokeCap = Paint.Cap.ROUND
        }
        c.drawLine(cx - r * 0.28f, cy - r * 0.15f, cx + r * 0.28f, cy - r * 0.15f, fil)
        c.drawLine(cx, cy - r * 0.48f, cx, cy + r * 0.05f, fil)

        val base = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bulbBase; style = Paint.Style.FILL }
        c.drawRoundRect(RectF(cx - r * 0.28f, cy + r * 0.32f, cx + r * 0.28f, cy + r * 0.72f), r * 0.08f, r * 0.08f, base)

        val thr = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bulbThread; style = Paint.Style.STROKE; strokeWidth = 1f }
        for (i in 0..1) {
            val ty = cy + r * 0.45f + i * r * 0.11f
            c.drawLine(cx - r * 0.28f, ty, cx + r * 0.28f, ty, thr)
        }
    }

    private fun drawGlow(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, alpha: Float) {
        if (alpha <= 0f || color == Color.TRANSPARENT) return
        val radius = r * 2.6f
        val shader = shaderCache.getOrPut("${color}_${r.toInt()}") {
            RadialGradient(0f, 0f, radius, color, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        c.save(); c.translate(cx, cy)
        glowP.shader = shader
        glowP.alpha = (alpha * 140).toInt()
        c.drawCircle(0f, 0f, radius, glowP)
        c.restore()
    }

    // ═══════════════════════════════════════════════════════════════════
    // Interaction
    // ═══════════════════════════════════════════════════════════════════

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (completed) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val cell = touchToCell(e.x, e.y)
                if (cell == startPos) {
                    path.clear(); path.add(startPos); tracing = true
                    onPathChange?.invoke(path.size - 1); invalidate()
                } else if (path.isNotEmpty() && cell == path.last()) {
                    tracing = true
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tracing) return true
                val cell = touchToCell(e.x, e.y)
                val last = path.last()
                if (cell == last) return true
                // backtrack: drag back over the previous cell
                if (path.size >= 2 && cell == path[path.size - 2]) {
                    path.removeAt(path.size - 1)
                    onPathChange?.invoke(path.size - 1); invalidate(); return true
                }
                // extend: orthogonal adjacent, walkable, not already visited
                if (isAdjacent(cell, last) && isWalkable(cell) && cell !in path) {
                    path.add(cell)
                    onPathChange?.invoke(path.size - 1); invalidate()
                    if (cell == endPos) completeCircuit()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracing = false
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    /** Clear the traced path (RESET button). */
    fun resetPath() {
        path.clear(); tracing = false
        flowAnimator?.cancel(); flowProgress = 0f
        onPathChange?.invoke(0)
        invalidate()
    }

    private fun completeCircuit() {
        if (completed) return
        tracing = false
        completed = true
        postDelayed({ onCircuitComplete?.invoke() }, 750L)
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private fun touchToCell(px: Float, py: Float): Pair<Int, Int> =
        Pair((px / width * gridCols).toInt().coerceIn(0, gridCols - 1),
             (py / height * gridRows).toInt().coerceIn(0, gridRows - 1))

    private fun isAdjacent(a: Pair<Int, Int>, b: Pair<Int, Int>): Boolean =
        abs(a.first - b.first) + abs(a.second - b.second) == 1

    private fun isWalkable(cell: Pair<Int, Int>): Boolean =
        cell.first in 0 until gridCols && cell.second in 0 until gridRows && cell !in obstacles

    private fun parseItems() {
        for (it in items) {
            when (normalizeType(it.type)) {
                "start" -> startPos = Pair(it.xGrid, it.yGrid)
                "end"   -> endPos = Pair(it.xGrid, it.yGrid)
            }
        }
    }

    private fun normalizeType(t: String): String = when {
        t.contains("start") -> "start"
        t.contains("end")   -> "end"
        t.contains("node")  -> "node"
        t.contains("wire")  -> "wire"
        t == "obstacle"     -> "obstacle"
        else -> t
    }

    private fun startGlow() {
        glowAnimator?.cancel()
        glowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600L; interpolator = OvershootInterpolator(1.2f)
            addUpdateListener { glowAlpha = animatedFraction; invalidate() }
            start()
        }
    }

    private fun startBulbGlow() {
        bulbGlowAnimator?.cancel()
        bulbGlowAnimator = ValueAnimator.ofFloat(0.35f, 1f).apply {
            duration = 700L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { bulbGlowAlpha = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun startFlow() {
        flowAnimator?.cancel()
        flowProgress = 0f
        flowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 650L
            addUpdateListener { flowProgress = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        glowAnimator?.cancel()
        bulbGlowAnimator?.cancel()
        flowAnimator?.cancel()
    }

    fun pixelToGridX(px: Float) = (px / width * gridCols).toInt().coerceIn(0, gridCols - 1)
    fun pixelToGridY(px: Float) = (px / height * gridRows).toInt().coerceIn(0, gridRows - 1)
    fun gridToPixelX(c: Int) = ((c + 0.5f) / gridCols) * width
    fun gridToPixelY(r: Int) = ((r + 0.5f) / gridRows) * height
}

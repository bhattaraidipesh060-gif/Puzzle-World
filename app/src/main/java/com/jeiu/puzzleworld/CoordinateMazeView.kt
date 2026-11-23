package com.jeiu.puzzleworld

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

/**
 * Coordinate maze — navigate the marker S → E through the grid with the four
 * compass arrows.
 *
 * The solve contract is unchanged (`setGrid(grid)`, `open()` walkability,
 * `onSolved` when the player reaches `E`). This pass only enriches the
 * visuals: raised 3D walls, recessed floor tiles, a glowing player with a
 * fading trail, a pulsing exit, wall-bump feedback (red flash + thud) and a
 * live coordinate readout.
 */
class CoordinateMazeView(context: Context) : View(context) {
    var onSolved: (() -> Unit)? = null

    private var grid = arrayOf<String>()
    private var mr = 0; private var mc = 0
    private var er = 0; private var ec = 0
    private var sr = 0; private var sc = 0

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private val trail = ArrayDeque<IntArray>()
    private var bumpR = -1; private var bumpC = -1
    private var bumpUntil = 0L
    private val handler = Handler(Looper.getMainLooper())

    private fun dp(v: Float) = v * resources.displayMetrics.density

    fun setGrid(grid: List<String>) {
        this.grid = grid.toTypedArray()
        trail.clear()
        bumpR = -1; bumpC = -1
        for (r in grid.indices) for (c in grid[r].indices) when (grid[r][c]) {
            'S' -> { sr = r; sc = c; mr = r; mc = c; trail.addLast(intArrayOf(r, c)) }
            'E' -> { er = r; ec = c }
        }
        invalidate()
    }

    private fun open(r: Int, c: Int): Boolean =
        r in grid.indices && c in grid[r].indices && grid[r][c] != '#'

    private fun withAlpha(color: Int, alpha: Int) =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun drawGlow(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        p.shader = RadialGradient(cx, cy, radius, withAlpha(color, 110), withAlpha(color, 0), Shader.TileMode.CLAMP)
        p.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, radius, p)
        p.shader = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (grid.isEmpty()) return
        val rows = grid.size
        val cols = grid.maxOf { it.length }
        val areaH = height * 0.72f
        val cell = minOf(width / cols.toFloat(), areaH / rows)
        val ox = (width - cols * cell) / 2f
        val oy = (height * 0.05f)
        val now = SystemClock.uptimeMillis()

        for (r in 0 until rows) for (c in 0 until cols) {
            val ch = if (c < grid[r].length) grid[r][c] else '#'
            val x0 = ox + c * cell; val y0 = oy + r * cell
            val x1 = x0 + cell; val y1 = y0 + cell
            when (ch) {
                '#' -> drawWall(canvas, x0, y0, x1, y1, cell)
                else -> drawFloor(canvas, x0, y0, x1, y1, cell)
            }
        }

        // Start origin marker
        val sx = ox + sc * cell + cell / 2f
        val sy = oy + sr * cell + cell / 2f
        p.style = Paint.Style.STROKE
        p.strokeWidth = dp(2f)
        p.color = Color.parseColor("#10B981")
        canvas.drawCircle(sx, sy, cell * 0.28f, p)
        p.style = Paint.Style.FILL

        // Exit target (pulsing glow + ring)
        val ex = ox + ec * cell + cell / 2f
        val ey = oy + er * cell + cell / 2f
        val pulse = 0.5f + 0.5f * kotlin.math.sin((now % 1400L) / 1400f * 6.283f).toFloat()
        drawGlow(canvas, ex, ey, cell * (0.55f + 0.25f * pulse), Color.parseColor("#F43F5E"))
        p.color = Color.parseColor("#F43F5E")
        p.style = Paint.Style.FILL
        canvas.drawCircle(ex, ey, cell * 0.22f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = dp(2f)
        p.color = Color.parseColor("#FCA5A5")
        canvas.drawCircle(ex, ey, cell * (0.34f + 0.08f * pulse), p)

        // Trail (fading)
        val n = trail.size
        for (i in 0 until n - 1) {
            val pos = trail[i]
            val tc = ox + pos[1] * cell + cell / 2f
            val tr = oy + pos[0] * cell + cell / 2f
            val alpha = (70 * (i + 1) / n.toFloat()).toInt()
            p.style = Paint.Style.FILL
            p.color = withAlpha(Color.parseColor("#22D3EE"), alpha)
            canvas.drawCircle(tc, tr, cell * 0.10f, p)
        }

        // Wall-bump flash
        if (now < bumpUntil && bumpR >= 0 && bumpC >= 0) {
            val bx0 = ox + bumpC * cell; val by0 = oy + bumpR * cell
            p.color = withAlpha(Color.parseColor("#F43F5E"), 120)
            p.style = Paint.Style.FILL
            canvas.drawRect(bx0, by0, bx0 + cell, by0 + cell, p)
        }

        // Player marker
        val px = ox + mc * cell + cell / 2f
        val py = oy + mr * cell + cell / 2f
        drawGlow(canvas, px, py, cell * 0.55f, Color.parseColor("#22D3EE"))
        p.color = Color.parseColor("#22D3EE")
        p.style = Paint.Style.FILL
        canvas.drawCircle(px, py, cell * 0.22f, p)
        p.color = Color.parseColor("#0B1220")
        canvas.drawCircle(px, py, cell * 0.08f, p)

        // Coordinate readout / status
        tp.textSize = 14f
        if (mr == er && mc == ec) {
            tp.color = Color.parseColor("#10B981")
            canvas.drawText("✓ Coordinates reached!", width / 2f, height * 0.80f, tp)
        } else {
            tp.color = Color.parseColor("#94A3B8")
            canvas.drawText("🧭 x = $mc   y = $mr", width / 2f, height * 0.80f, tp)
        }

        // Compass arrows
        val by = height * 0.85f; val bh = height * 0.11f; val bw = width / 4f
        val dirs = arrayOf("◀", "▲", "▼", "▶")
        val cards = arrayOf("W", "N", "S", "E")
        for (i in dirs.indices) {
            p.color = Color.parseColor("#1E293B")
            canvas.drawRoundRect(i * bw + dp(6f), by, (i + 1) * bw - dp(6f), by + bh, dp(18f), dp(18f), p)
            tp.textSize = 22f; tp.color = Color.parseColor("#22D3EE")
            canvas.drawText(dirs[i], i * bw + bw / 2, by + bh / 2 + dp(6f), tp)
            tp.textSize = 11f; tp.color = Color.parseColor("#64748B")
            canvas.drawText(cards[i], i * bw + bw / 2, by + bh - dp(5f), tp)
        }
    }

    private fun drawWall(canvas: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, cell: Float) {
        val inset = cell * 0.03f
        val b = cell * 0.14f
        val l = x0 + inset; val t = y0 + inset; val r = x1 - inset; val bot = y1 - inset
        // base
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#3E4C59")
        canvas.drawRect(l, t, r, bot, p)
        // top + left highlight
        p.color = Color.parseColor("#64748B")
        canvas.drawRect(l, t, r, t + b, p)
        canvas.drawRect(l, t, l + b, bot, p)
        // bottom + right shadow
        p.color = Color.parseColor("#1B2430")
        canvas.drawRect(l, bot - b, r, bot, p)
        canvas.drawRect(r - b, t, r, bot, p)
    }

    private fun drawFloor(canvas: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, cell: Float) {
        val inset = cell * 0.03f
        val l = x0 + inset; val t = y0 + inset; val r = x1 - inset; val bot = y1 - inset
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#111B27")
        canvas.drawRect(l, t, r, bot, p)
        // recessed edge
        p.style = Paint.Style.STROKE
        p.strokeWidth = dp(1f)
        p.color = Color.parseColor("#1B2836")
        canvas.drawRect(l + dp(1f), t + dp(1f), r - dp(1f), bot - dp(1f), p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_DOWN) return true
        val h = height.toFloat()
        if (e.y > h * 0.82f) {
            val bw = width / 4f
            val (dr, dc) = when ((e.x / bw).toInt()) {
                0 -> 0 to -1; 1 -> -1 to 0; 2 -> 1 to 0; 3 -> 0 to 1
                else -> 0 to 0
            }
            val nr = mr + dr; val nc = mc + dc
            if (open(nr, nc)) {
                mr = nr; mc = nc
                trail.addLast(intArrayOf(nr, nc))
                if (trail.size > 9) trail.removeFirst()
                SoundFx.move()
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                invalidate()
            } else {
                // boundary / wall feedback
                SoundFx.error()
                performHapticFeedback(HapticFeedbackConstants.REJECT)
                if (nr in grid.indices && nc in grid[0].indices) {
                    bumpR = nr; bumpC = nc
                    bumpUntil = SystemClock.uptimeMillis() + 200
                    invalidate()
                    handler.postDelayed({ bumpR = -1; bumpC = -1; invalidate() }, 220)
                }
            }
            if (mr == er && mc == ec) onSolved?.invoke()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

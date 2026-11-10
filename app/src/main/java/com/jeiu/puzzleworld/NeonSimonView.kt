package com.jeiu.puzzleworld

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View

/** Simon-says — watch the flashing pads, then replay the sequence. */
class NeonSimonView(context: Context) : View(context) {
    var onSolved: (() -> Unit)? = null

    private var seq = intArrayOf()
    private var speedMs = 700L
    private var flash = -1
    private var pointer = 0
    private var listening = false
    private val handler = Handler(Looper.getMainLooper())
    private val padColors = intArrayOf(
        Color.parseColor("#22D3EE"), Color.parseColor("#FF40C4"),
        Color.parseColor("#FFD740"), Color.parseColor("#10E676")
    )
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    fun setSequence(seq: IntArray, speedMs: Long) {
        this.seq = seq; this.speedMs = speedMs
        this.pointer = 0; this.flash = -1; this.listening = false
        reveal(0)
    }

    private fun reveal(k: Int) {
        if (k >= seq.size) { listening = true; flash = -1; invalidate(); return }
        flash = seq[k]; invalidate()
        handler.postDelayed({
            if (!isAttachedToWindow) return@postDelayed
            flash = -1; invalidate()
            handler.postDelayed({ if (isAttachedToWindow) reveal(k + 1) }, 120L)
        }, speedMs)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        val padH = h * 0.40f
        val padW = w * 0.44f
        val ox = w * 0.5f - padW
        // 2x2 pads
        val pos = arrayOf(
            floatArrayOf(ox, h * 0.10f), floatArrayOf(ox + w * 0.48f, h * 0.10f),
            floatArrayOf(ox, h * 0.52f), floatArrayOf(ox + w * 0.48f, h * 0.52f)
        )
        for (i in 0 until 4) {
            val lit = flash == i
            p.color = if (lit) Color.WHITE else padColors[i]
            canvas.drawRoundRect(pos[i][0], pos[i][1], pos[i][0] + padW, pos[i][1] + padH, 24f, 24f, p)
        }
        tp.textSize = 14f; tp.color = Color.parseColor("#94A3B8")
        canvas.drawText(
            if (listening) "Replay the sequence (${pointer}/${seq.size})"
            else if (pointer > 0) "Watch…" else "Watch the sequence…",
            w / 2, h * 0.96f, tp)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_DOWN || !listening) return true
        val w = width.toFloat(); val h = height.toFloat()
        val padH = h * 0.40f; val padW = w * 0.44f; val ox = w * 0.5f - padW
        val pos = arrayOf(
            floatArrayOf(ox, h * 0.10f), floatArrayOf(ox + w * 0.48f, h * 0.10f),
            floatArrayOf(ox, h * 0.52f), floatArrayOf(ox + w * 0.48f, h * 0.52f)
        )
        for (i in 0 until 4) {
            if (e.x >= pos[i][0] && e.x <= pos[i][0] + padW && e.y >= pos[i][1] && e.y <= pos[i][1] + padH) {
                if (i == seq[pointer]) {
                    pointer++
                    if (pointer == seq.size) { listening = false; onSolved?.invoke() }
                } else {
                    pointer = 0; listening = false; reveal(0)
                }
                invalidate()
                return true
            }
        }
        return true
    }
}
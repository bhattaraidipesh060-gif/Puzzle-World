package com.jeiu.puzzleworld

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.sin

/**
 * Lightweight synthesized sound effects — no audio assets, no SoundPool files.
 * Short sine/square PCM tones are generated in memory and played through a
 * static [AudioTrack], mirroring the web portal's Web Audio SFX.
 *
 * Mute state is persisted in SharedPreferences so it survives restarts.
 */
object SoundFx {

    private const val RATE = 22050
    private const val PREFS = "puzzleworld_sound"
    private const val KEY_MUTED = "muted"

    private var muted = false
    private var ready = false
    private val handler = Handler(Looper.getMainLooper())

    fun init(ctx: Context) {
        if (ready) return
        muted = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_MUTED, false)
        ready = true
    }

    fun isMuted(): Boolean = muted

    fun setMuted(ctx: Context, value: Boolean) {
        muted = value
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_MUTED, value).apply()
    }

    // ------------------------------------------------------------------ SFX
    fun click() = tone(880.0, 0.05, 0.35)

    fun nav() {
        tone(620.0, 0.04, 0.30)
        later(55) { tone(920.0, 0.06, 0.25) }
    }

    fun move() = tone(520.0, 0.04, 0.25)

    fun success() {
        tone(660.0, 0.09, 0.35)
        later(80) { tone(990.0, 0.13, 0.30) }
    }

    fun win() {
        val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.5)
        notes.forEachIndexed { i, f -> later(i * 85L) { tone(f, 0.18, 0.35) } }
    }

    fun loss() {
        sweep(380.0, 120.0, 0.40, 0.30)
        later(40) { tone(140.0, 0.30, 0.20) }
    }

    fun error() {
        tone(220.0, 0.10, 0.30)
        later(90) { tone(170.0, 0.14, 0.30) }
    }

    // -------------------------------------------------------------- synthesis
    private fun later(ms: Long, block: () -> Unit) {
        handler.postDelayed(block, ms)
    }

    private fun tone(freq: Double, dur: Double, vol: Double) {
        if (muted) return
        val n = (RATE * dur).toInt().coerceAtLeast(1)
        val buf = ShortArray(n)
        val w = 2.0 * PI * freq / RATE
        for (i in 0 until n) {
            val env = envelope(i.toDouble() / n)
            buf[i] = (sin(w * i) * Short.MAX_VALUE * vol * env).toInt().toShort()
        }
        play(buf)
    }

    private fun sweep(f1: Double, f2: Double, dur: Double, vol: Double) {
        if (muted) return
        val n = (RATE * dur).toInt().coerceAtLeast(1)
        val buf = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / n
            val f = f1 + (f2 - f1) * t
            phase += 2.0 * PI * f / RATE
            val env = envelope(t)
            buf[i] = (sin(phase) * Short.MAX_VALUE * vol * env).toInt().toShort()
        }
        play(buf)
    }

    /** Attack/decay envelope so tones are crisp, not clicky. */
    private fun envelope(t: Double): Double = when {
        t < 0.08 -> t / 0.08
        t > 0.82 -> (1.0 - t) / 0.18
        else -> 1.0
    }

    private fun play(buf: ShortArray) {
        if (muted || buf.isEmpty()) return
        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(buf.size * 2)
                .build()

            track.write(buf, 0, buf.size)
            track.play()

            val ms = (buf.size * 1000L / RATE) + 80
            handler.postDelayed({
                try { track.stop() } catch (_: Exception) {}
                try { track.release() } catch (_: Exception) {}
            }, ms)
        } catch (_: Exception) {
            // Audio is best-effort — never let a failed tone crash the app.
        }
    }
}

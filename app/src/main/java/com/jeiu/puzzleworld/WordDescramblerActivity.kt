package com.jeiu.puzzleworld

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Word Descrambler with timer, difficulty tiers, and graded hints.
 */
class WordDescramblerActivity : AppCompatActivity() {

    companion object {
        private const val PREFS = "word_scramble_prefs"
        private const val KEY_SCORE = "score"
        private const val KEY_STREAK = "streak"
        private const val KEY_ROUND = "round"
    }

    private lateinit var headerTitle: TextView
    private lateinit var tilesContainer: LinearLayout
    private lateinit var statusView: TextView
    private lateinit var hintView: TextView
    private lateinit var timerView: TextView
    private lateinit var answerInput: EditText
    private lateinit var btnCheck: Button
    private lateinit var btnHint: Button
    private lateinit var btnNext: Button
    private lateinit var btnDifficulty: Button
    private lateinit var celebration: CelebrationView

    private var currentRound: WordScrambleEngine.Round? = null
    private var difficulty = "Medium"
    private var hintIndex = 0
    private var solved = false
    private var score = 0
    private var streak = 0
    private var roundNum = 1
    private var elapsedSec = 0
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            if (!solved) { elapsedSec++; updateTimer(); handler.postDelayed(this, 1000L) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_word_descrambler)
        SoundFx.init(this)
        AnimatedBackgroundDrawable.applyTo(this,
            Color.parseColor("#1A0A14"), Color.parseColor("#140A1E"), Color.parseColor("#FF4081"))

        headerTitle  = findViewById(R.id.wd_header_title)
        tilesContainer = findViewById(R.id.wd_tiles)
        statusView   = findViewById(R.id.wd_status)
        hintView     = findViewById(R.id.wd_hint)
        timerView    = findViewById(R.id.wd_timer)
        answerInput  = findViewById(R.id.wd_answer_input)
        btnCheck     = findViewById(R.id.wd_btn_check)
        btnHint      = findViewById(R.id.wd_btn_hint)
        btnNext      = findViewById(R.id.wd_btn_next)
        btnDifficulty = findViewById(R.id.wd_btn_difficulty)
        celebration  = findViewById(R.id.wd_celebration)

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        score = prefs.getInt(KEY_SCORE, 0)
        streak = prefs.getInt(KEY_STREAK, 0)
        roundNum = prefs.getInt(KEY_ROUND, 1)

        btnCheck.setOnClickListener { checkAnswer() }
        btnHint.setOnClickListener { showHint() }
        btnNext.setOnClickListener { nextRound() }
        btnDifficulty.setOnClickListener { chooseDifficulty() }

        if (TutorialEngine.shouldShow(this, "word_scramble")) {
            tilesContainer.postDelayed({ TutorialEngine.show(this, "word_scramble") }, 400L)
        }

        updateHud()
        newRound()
    }

    private fun newRound() {
        currentRound = WordScrambleEngine.generate(System.currentTimeMillis() xor roundNum.toLong(), difficulty)
        hintIndex = 0
        solved = false
        elapsedSec = 0
        renderTiles(currentRound!!.scrambled)
        hintView.text = ""
        hintView.visibility = android.view.View.GONE
        answerInput.text.clear()
        answerInput.isEnabled = true
        btnCheck.isEnabled = true
        statusView.setTextColor(Color.parseColor("#B0B8C4"))
        statusView.text = "Round $roundNum — ${currentRound!!.word.length} letters [$difficulty]"
        updateTimer()
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, 1000L)
    }

    private fun renderTiles(word: String) {
        tilesContainer.removeAllViews()
        val tileH = dp(56)
        for (i in word.indices) {
            val tile = TextView(this).apply {
                text = word[i].toString()
                textSize = when {
                    word.length <= 4 -> 30f
                    word.length <= 7 -> 26f
                    word.length <= 10 -> 22f
                    else -> 18f
                }
                setTextColor(Color.WHITE)
                typeface = Typeface.create("monospace", Typeface.BOLD)
                gravity = android.view.Gravity.CENTER
                setBackgroundResource(R.drawable.word_tile)
                alpha = 0f
                scaleX = 0.3f
                scaleY = 0.3f
            }
            val lp = LinearLayout.LayoutParams(0, tileH, 1f)
            lp.marginStart = if (i == 0) 0 else dp(4)
            lp.marginEnd = if (i == word.length - 1) 0 else dp(4)
            tile.layoutParams = lp
            tilesContainer.addView(tile)
            tile.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay((i * 70).toLong())
                .setDuration(220).start()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun checkAnswer() {
        val round = currentRound ?: return
        if (solved) return
        val input = answerInput.text.toString().trim().uppercase()
        if (input.isEmpty()) {
            statusView.setTextColor(Color.parseColor("#B0B8C4"))
            statusView.text = "Type a word first"
            return
        }
        if (input == round.word) {
            solved = true
            handler.removeCallbacks(ticker)
            val base = when { hintIndex == 0 -> 100; hintIndex == 1 -> 70; hintIndex == 2 -> 40; else -> 20 }
            val diffBonus = when (difficulty) { "Easy" -> 0; "Medium" -> 20; "Hard" -> 40; else -> 60 }
            streak++
            val streakBonus = (streak - 1) * 10
            score += base + diffBonus + streakBonus
            GameStats.recordSolve(this, "word_scramble", base + diffBonus + streakBonus)
            answerInput.isEnabled = false
            btnCheck.isEnabled = false
            statusView.setTextColor(Color.parseColor("#00E676"))
            statusView.text = "✅ Correct! +${base + diffBonus + streakBonus} pts in ${formatTime(elapsedSec)}${if (streak >= 3) " 🔥${streak}x streak" else ""}"
            answerInput.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            answerInput.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pop))
            celebration.burst(50)
            SoundFx.success()
            savePrefs()
        } else {
            streak = 0
            statusView.setTextColor(Color.parseColor("#FF4081"))
            statusView.text = "❌ Not quite — try again"
            answerInput.performHapticFeedback(HapticFeedbackConstants.REJECT)
            answerInput.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake_x))
            SoundFx.error()
            savePrefs()
        }
        updateHud()
    }

    private fun showHint() {
        val round = currentRound ?: return
        if (solved) return
        if (hintIndex >= round.hintLayers.size) {
            statusView.text = "No more hints — you're close!"
            return
        }
        hintView.text = "💡 ${round.hintLayers[hintIndex]}"
        hintView.visibility = android.view.View.VISIBLE
        hintIndex++
    }

    private fun chooseDifficulty() {
        handler.removeCallbacks(ticker)
        val items = arrayOf("Easy (3-5 letters)", "Medium (5-7 letters)", "Hard (7-9 letters)", "Expert (9-12 letters)")
        AlertDialog.Builder(this, R.style.HintDialog)
            .setTitle("🎯 Difficulty")
            .setItems(items) { _, i ->
                difficulty = arrayOf("Easy", "Medium", "Hard", "Expert")[i]
                roundNum = 1; score = 0; streak = 0
                savePrefs(); updateHud(); newRound()
            }
            .setNegativeButton("Cancel") { _, _ -> if (!solved) handler.postDelayed(ticker, 1000L) }
            .show()
    }

    private fun updateHud() {
        headerTitle.text = "🔤 Word Descrambler — $difficulty"
    }

    private fun updateTimer() {
        timerView.text = "⏱ ${formatTime(elapsedSec)}"
    }

    private fun formatTime(s: Int): String {
        val m = s / 60; val sec = s % 60
        return "$m:${sec.toString().padStart(2, '0')}"
    }

    private fun savePrefs() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_SCORE, score)
            .putInt(KEY_STREAK, streak)
            .putInt(KEY_ROUND, roundNum)
            .apply()
    }

    private fun nextRound() {
        roundNum++
        savePrefs()
        newRound()
    }
}

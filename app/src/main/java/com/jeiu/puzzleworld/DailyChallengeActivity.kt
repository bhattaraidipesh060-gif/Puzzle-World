package com.jeiu.puzzleworld

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Standalone Daily Challenge screen — zero coupling to the existing model layer.
 *
 * Fetches GET /api/daily-challenge over HttpURLConnection, parses with org.json
 * (no new dependencies), and renders the puzzle with inline answer grading.
 * Fully self-contained so it can live alongside the actively-in-flux
 * MainActivity / Models without colliding.
 *
 * Backend JSON shape:
 * {
 *   "challenge_date": "...",
 *   "seed": 123,
 *   "level": {
 *     "level_id": "...", "room_id": "...", "room_name": "...", "theme": "...",
 *     "title": "...", "difficulty": "...", "hints": ["..."],
 *     "mechanic": { "mechanic_type": "cipher_wheel|terminal_hack" (legacy: cipher|riddle|mechanical_lock|deduction_grid|wire_maze), ... }
 *   }
 * }
 */
class DailyChallengeActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PUZZLE_WORLD"
        private const val BASE_URL = ApiClient.BASE_URL
        private const val TIMEOUT_MS = 15000

        // palette (mirrors res/values/colors.xml — no new resources needed)
        private const val COLOR_SUCCESS = "#00E676"
        private const val COLOR_ERROR   = "#FF4081"
        private const val COLOR_MUTED   = "#B0B8C4"
    }

    private lateinit var headerTitle: TextView
    private lateinit var challengeInfo: TextView
    private lateinit var puzzlePrompt: TextView
    private lateinit var hintsView: TextView
    private lateinit var answerInput: EditText
    private lateinit var btnCheck: Button
    private lateinit var btnHint: Button
    private lateinit var statusText: TextView

    // parsed challenge state
    private var mechanicType: String = ""
    private var expectedAnswer: String = ""
    private var hints: List<String> = emptyList()
    private var hintIndex = 0
    private var solved = false
    private var challengeDate: String = ""
    private var attempts = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_daily_challenge)
        SoundFx.init(this)
        AnimatedBackgroundDrawable.applyTo(this, Color.parseColor("#14100A"), Color.parseColor("#1A0A2E"), Color.parseColor("#FFD740"))

        headerTitle   = findViewById(R.id.dc_header_title)
        challengeInfo = findViewById(R.id.dc_challenge_info)
        puzzlePrompt  = findViewById(R.id.dc_puzzle_prompt)
        hintsView     = findViewById(R.id.dc_hints)
        answerInput   = findViewById(R.id.dc_answer_input)
        btnCheck      = findViewById(R.id.dc_btn_check)
        btnHint       = findViewById(R.id.dc_btn_hint)
        statusText    = findViewById(R.id.dc_status)

        btnCheck.setOnClickListener { checkAnswer() }
        btnHint.setOnClickListener { showHint() }

        loadChallenge()
    }

    // ── Fetch & parse ────────────────────────────────────────────────────────

    private fun loadChallenge() {
        statusText.text = "Loading daily challenge…"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { fetchDailyChallenge() }
            result.onSuccess { renderChallenge(it) }
                   .onFailure { showError(it) }
        }
    }

    private fun fetchDailyChallenge(): Result<JSONObject> = try {
        val conn = (URL(BASE_URL.trimEnd('/') + "/api/daily-challenge").openConnection()
                as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            Result.failure(Exception("HTTP $code"))
        } else {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            Result.success(JSONObject(body))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun renderChallenge(root: JSONObject) {
        val level = root.optJSONObject("level")
        if (level == null) { showError(Exception("Malformed challenge — missing level")); return }

        val mechanic = level.optJSONObject("mechanic") ?: JSONObject()
        mechanicType = mechanic.optString("mechanic_type", "")
        challengeDate = root.optString("challenge_date", "")

        // header
        headerTitle.text = level.optString("title", "Daily Puzzle")
        challengeInfo.text = buildString {
            append("🗓️ ")
            append(root.optString("challenge_date", "Daily"))
            append("  ·  ")
            append(level.optString("difficulty", "Medium"))
            val room = level.optString("room_name", "")
            if (room.isNotEmpty()) { append("  ·  "); append(room) }
        }

        // prompt
        puzzlePrompt.text = renderMechanicPrompt(mechanic)
        puzzlePrompt.visibility = android.view.View.VISIBLE

        // hints
        hints = parseHints(level)
        hintIndex = 0
        hintsView.text = ""
        hintsView.visibility = android.view.View.GONE
        btnHint.isEnabled = hints.isNotEmpty()

        // grading
        expectedAnswer = computeExpectedAnswer(mechanic)
        solved = false

        // For wire_maze there is no text answer — disable input and explain.
        val visual = mechanicType == "wire_maze"
        answerInput.isEnabled = !visual
        btnCheck.isEnabled = !visual
        answerInput.text.clear()
        if (visual) {
            answerInput.hint = "Visual puzzle — no text answer"
            statusText.text = "Trace the path in the grid above"
        } else {
            statusText.text = "Ready"
        }

        Log.d(TAG, "Rendered daily challenge: mechanic=$mechanicType difficulty=${level.optString("difficulty")}")
    }

    private fun renderMechanicPrompt(m: JSONObject): String = when (mechanicType) {
        "cipher" -> buildString {
            append("🔐  ")
            append(m.optString("cipher_text", ""))
            append("\n\n    — ")
            append(m.optString("cipher_type", "CIPHER").uppercase())
            append(" CIPHER —")
        }
        "riddle" -> buildString {
            append("🤔  \"")
            append(m.optString("riddle_text", ""))
            append("\"")
        }
        "cipher_wheel" -> buildString {
            append("🔐  ")
            append(m.optString("cipher_text", ""))
            append("\n\n    — ")
            append(m.optString("cipher_type", "CAESAR").uppercase())
            append(" CIPHER WHEEL —")
        }
        "terminal_hack" -> buildString {
            append("💻  TERMINAL HACK — ")
            append(m.optString("mode", "").uppercase())
            append("\n\nBinary: ")
            append(m.optString("input", ""))
            append("\n\n(convert the binary stream to hex)")
        }
        "mechanical_lock" -> buildString {
            val combo = m.optJSONArray("combination")
            val n = combo?.length() ?: 0
            append("🔒  ")
            append(m.optString("lock_type", "DIAL").uppercase())
            append(" LOCK\n\n🔢 ")
            append("▣ ".repeat(n).trim())
            append("\n   (")
            append(n)
            append(" digits)")
        }
        "deduction_grid" -> buildString {
            val cats  = jsonArrayToStrings(m.optJSONArray("categories"))
            val items = flattenItems(m.optJSONArray("items"))
            val clues = jsonArrayToStrings(m.optJSONArray("clues"))
            append("🧩  ")
            append(cats.joinToString(" · "))
            append("\n\n")
            append(clues.joinToString("\n") { "• $it" })
            append("\n\n📋 items: ")
            append(items.joinToString(", "))
        }
        "wire_maze" -> buildString {
            val cols = m.optInt("gridCols", m.optInt("grid_cols", 8))
            val rows = m.optInt("gridRows", m.optInt("grid_rows", 8))
            append("🔌  Wire Maze — connect start → end\n\n")
            append("Grid: ").append(cols).append(" × ").append(rows)
            append("\n\n(Visual puzzle — solve by tracing the wire path.)")
        }
        else -> "❓ Unknown mechanic"
    }

    private fun parseHints(level: JSONObject): List<String> {
        val arr = level.optJSONArray("hints") ?: return emptyList()
        return (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
    }

    private fun computeExpectedAnswer(m: JSONObject): String = when (mechanicType) {
        "cipher"  -> m.optString("answer", "")
        "riddle"  -> m.optString("answer", "")
        "cipher_wheel"  -> m.optString("answer", "")
        "terminal_hack" -> m.optString("answer", "")
        "mechanical_lock" -> {
            val combo = m.optJSONArray("combination")
            if (combo == null) "" else (0 until combo.length()).joinToString("") { combo.get(it).toString() }
        }
        "deduction_grid" -> {
            val sol = m.optJSONObject("solution")
            if (sol == null) ""
            else sol.keys().asSequence().joinToString("") { sol.optString(it) }
        }
        else -> ""
    }

    // ── Grading ──────────────────────────────────────────────────────────────

    private fun checkAnswer() {
        if (solved || mechanicType.isEmpty()) return
        val input = answerInput.text.toString().trim()
        if (input.isEmpty()) {
            statusText.setTextColor(Color.parseColor(COLOR_MUTED))
            statusText.text = "Enter an answer first"
            SoundFx.error()
            return
        }

        attempts++
        val correct = grade(input)
        if (correct) {
            solved = true
            answerInput.isEnabled = false
            btnCheck.isEnabled = false
            statusText.setTextColor(Color.parseColor(COLOR_SUCCESS))
            statusText.text = "✅ Correct! Challenge complete."
            answerInput.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            answerInput.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pop))
            SoundFx.win()
            submitCompletion()
        } else {
            statusText.setTextColor(Color.parseColor(COLOR_ERROR))
            statusText.text = "❌ Not quite — try again"
            answerInput.performHapticFeedback(HapticFeedbackConstants.REJECT)
            answerInput.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake_x))
            SoundFx.error()
        }
    }

    private fun grade(input: String): Boolean = when (mechanicType) {
        "cipher"  -> input.equals(expectedAnswer, ignoreCase = true)
        "riddle"  -> input.equals(expectedAnswer, ignoreCase = true)
        "cipher_wheel"  -> input.equals(expectedAnswer, ignoreCase = true)
        "terminal_hack" -> input.equals(expectedAnswer, ignoreCase = true)
        "mechanical_lock" -> input == expectedAnswer
        "deduction_grid"  -> input.replace(" ", "").equals(expectedAnswer, ignoreCase = true)
        else -> false
    }

    // ── Sync ────────────────────────────────────────────────────────────────

    /** Report a completed daily challenge to the backend so it counts toward the leaderboard. */
    private fun submitCompletion() {
        val user = getSharedPreferences("puzzleworld_account", MODE_PRIVATE)
            .getString("username", "android_player") ?: "android_player"
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val payload = JSONObject().apply {
                        put("user_id", user)
                        if (challengeDate.isNotBlank()) put("challenge_date", challengeDate)
                        put("hints_used", hintIndex)
                        put("total_attempts", attempts)
                    }
                    val conn = (URL(BASE_URL.trimEnd('/') + "/api/daily-challenge/complete").openConnection()
                            as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = TIMEOUT_MS
                        readTimeout = TIMEOUT_MS
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Accept", "application/json")
                        doOutput = true
                    }
                    conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                    val code = conn.responseCode
                    conn.disconnect()
                    if (code in 200..299) Log.d(TAG, "Daily challenge completion synced")
                    else Log.w(TAG, "Daily challenge completion failed: HTTP $code")
                } catch (e: Exception) {
                    Log.w(TAG, "Daily challenge completion error: ${e.message}")
                }
            }
        }
    }

    // ── Hints ────────────────────────────────────────────────────────────────

    private fun showHint() {
        if (hints.isEmpty()) { statusText.text = "No hints available"; return }
        if (hintIndex >= hints.size) {
            statusText.text = "No more hints — you're close!"
            return
        }
        val hint = hints[hintIndex]
        hintIndex++
        val existing = if (hintsView.text.isNullOrEmpty()) "" else "${hintsView.text}\n\n"
        hintsView.text = existing + "💡 $hint"
        hintsView.visibility = android.view.View.VISIBLE
    }

    private fun showError(e: Throwable) {
        Log.w(TAG, "Daily challenge load failed: ${e.message}")
        headerTitle.text = "🗓️ Daily Challenge"
        challengeInfo.text = "Unavailable"
        puzzlePrompt.visibility = android.view.View.GONE
        hintsView.visibility = android.view.View.GONE
        answerInput.isEnabled = false
        btnCheck.isEnabled = false
        btnHint.isEnabled = false
        statusText.setTextColor(Color.parseColor(COLOR_ERROR))
        statusText.text = "⚠️ Couldn't reach the backend — is it running? (${e.message ?: "offline"})"
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun jsonArrayToStrings(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.optString(it) }
    }

    /** deduction_grid items may be a flat list or a 2D list of groups — flatten for display. */
    private fun flattenItems(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val v = arr.opt(i)
            if (v is JSONArray) for (k in 0 until v.length()) out.add(v.optString(k))
            else out.add(arr.optString(i))
        }
        return out
    }
}
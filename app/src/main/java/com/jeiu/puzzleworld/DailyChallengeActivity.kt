package com.jeiu.puzzleworld

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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
 *
 * Light advancement: deterministic daily modifier badges (par time / streak
 * shield / speed-run, derived from the challenge date) plus a completion
 * summary card on success.
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

        // submitAnswer() outcomes
        private const val SUBMIT_CORRECT   = 0
        private const val SUBMIT_INCORRECT = 1
        private const val SUBMIT_UNAUTH    = 2
        private const val SUBMIT_ERROR     = 3
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
    private var hints: List<String> = emptyList()
    private var hintIndex = 0
    private var solved = false
    private var challengeDate: String = ""
    private var attempts = 0
    private var startTime = 0L

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
        statusText.text = getString(R.string.dc_loading)
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
        startTime = System.currentTimeMillis()

        // header
        headerTitle.text = level.optString("title", "Daily Puzzle")
        challengeInfo.text = buildString {
            append("🗓️ ")
            append(root.optString("challenge_date", "Daily"))
            append("  ·  ")
            append(Labels.difficulty(this@DailyChallengeActivity, level.optString("difficulty", "Medium")))
            val room = level.optString("room_name", "")
            if (room.isNotEmpty()) { append("  ·  "); append(room) }
            append("\n")
            append(modifierBadges(root.optString("challenge_date", ""), level.optString("difficulty", "Medium")))
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

        // grading (server-side — the answer is never shipped to the client)
        solved = false

        // For wire_maze there is no text answer — disable input and explain.
        val visual = mechanicType == "wire_maze"
        answerInput.isEnabled = !visual
        btnCheck.isEnabled = !visual
        answerInput.text.clear()
        if (visual) {
            answerInput.hint = getString(R.string.dc_visual_no_text)
            statusText.text = getString(R.string.dc_trace_path)
        } else {
            statusText.text = getString(R.string.dc_ready)
        }

        Log.d(TAG, "Rendered daily challenge: mechanic=$mechanicType difficulty=${level.optString("difficulty")}")
    }

    private fun renderMechanicPrompt(m: JSONObject): String = when (mechanicType) {
        "cipher" -> buildString {
            append("🔐  ")
            append(m.optString("cipher_text", ""))
            append("\n\n    — ")
            append(m.optString("cipher_type", "CIPHER").uppercase())
            append(getString(R.string.dc_cipher_label))
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
            append(getString(R.string.dc_cipher_wheel_label))
        }
        "terminal_hack" -> buildString {
            append(getString(R.string.dc_terminal_label))
            append(m.optString("mode", "").uppercase())
            append(getString(R.string.dc_binary_label))
            append(m.optString("input", ""))
            append(getString(R.string.dc_convert_hint))
        }
        "mechanical_lock" -> buildString {
            val combo = m.optJSONArray("combination")
            val n = combo?.length() ?: 0
            append("🔒  ")
            append(m.optString("lock_type", "DIAL").uppercase())
            append(getString(R.string.dc_lock_label))
            append("▣ ".repeat(n).trim())
            append("\n   (")
            append(n)
            append(getString(R.string.dc_digits_suffix))
        }
        "deduction_grid" -> buildString {
            val cats  = jsonArrayToStrings(m.optJSONArray("categories"))
            val items = flattenItems(m.optJSONArray("items"))
            val clues = jsonArrayToStrings(m.optJSONArray("clues"))
            append("🧩  ")
            append(cats.joinToString(" · "))
            append("\n\n")
            append(clues.joinToString("\n") { "• $it" })
            append(getString(R.string.dc_items_label_prefixed))
            append(items.joinToString(", "))
        }
        "wire_maze" -> buildString {
            val cols = m.optInt("gridCols", m.optInt("grid_cols", 8))
            val rows = m.optInt("gridRows", m.optInt("grid_rows", 8))
            append(getString(R.string.dc_wire_maze_label))
            append(getString(R.string.dc_grid_label)).append(cols).append(" × ").append(rows)
            append(getString(R.string.dc_visual_hint))
        }
        else -> "❓ Unknown mechanic"
    }

    private fun parseHints(level: JSONObject): List<String> {
        val arr = level.optJSONArray("hints") ?: return emptyList()
        return (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
    }

    // ── Grading (server-side — the answer never ships to the client) ─────────

    private fun checkAnswer() {
        if (solved || mechanicType.isEmpty()) return
        val input = answerInput.text.toString().trim()
        if (input.isEmpty()) {
            statusText.setTextColor(Color.parseColor(COLOR_MUTED))
            statusText.text = getString(R.string.dc_enter_answer)
            SoundFx.error()
            return
        }

        attempts++
        statusText.setTextColor(Color.parseColor(COLOR_MUTED))
        statusText.text = getString(R.string.dc_checking)
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) { submitAnswer(input) }
            when (outcome) {
                SUBMIT_CORRECT -> {
                    solved = true
                    answerInput.isEnabled = false
                    btnCheck.isEnabled = false
                    statusText.setTextColor(Color.parseColor(COLOR_SUCCESS))
                    statusText.text = getString(R.string.dc_correct_complete)
                    answerInput.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    answerInput.startAnimation(AnimationUtils.loadAnimation(this@DailyChallengeActivity, R.anim.pop))
                    SoundFx.win()
                    showSummaryCard()
                }
                SUBMIT_INCORRECT -> {
                    statusText.setTextColor(Color.parseColor(COLOR_ERROR))
                    statusText.text = getString(R.string.word_not_quite)
                    answerInput.performHapticFeedback(HapticFeedbackConstants.REJECT)
                    answerInput.startAnimation(AnimationUtils.loadAnimation(this@DailyChallengeActivity, R.anim.shake_x))
                    SoundFx.error()
                }
                SUBMIT_UNAUTH -> {
                    statusText.setTextColor(Color.parseColor(COLOR_ERROR))
                    statusText.text = getString(R.string.dc_sign_in_submit)
                    SoundFx.error()
                }
                else -> {
                    statusText.setTextColor(Color.parseColor(COLOR_ERROR))
                    statusText.text = getString(R.string.dc_backend_error)
                    SoundFx.error()
                }
            }
        }
    }

    /** Submit the answer for server-side grading; returns an outcome constant. */
    private fun submitAnswer(input: String): Int {
        val userId = SessionManager.userId()
        val token = SessionManager.token()
        if (userId == null || token == null) return SUBMIT_UNAUTH
        return try {
            val payload = JSONObject().apply {
                put("user_id", userId)
                put("answer", input)
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
                setRequestProperty("Authorization", "Bearer $token")
                doOutput = true
            }
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            conn.disconnect()
            when (code) {
                in 200..299 -> SUBMIT_CORRECT
                400 -> SUBMIT_INCORRECT
                401, 403 -> SUBMIT_UNAUTH
                else -> SUBMIT_ERROR
            }
        } catch (e: Exception) {
            Log.w(TAG, "Daily challenge submit error: ${e.message}")
            SUBMIT_ERROR
        }
    }

    // ── Hints ────────────────────────────────────────────────────────────────

    private fun showHint() {
        if (hints.isEmpty()) { statusText.text = getString(R.string.dc_no_hints); return }
        if (hintIndex >= hints.size) {
            statusText.text = getString(R.string.game_no_more_hints)
            return
        }
        val hint = hints[hintIndex]
        hintIndex++
        val existing = if (hintsView.text.isNullOrEmpty()) "" else "${hintsView.text}\n\n"
        hintsView.text = existing + "💡 $hint"
        hintsView.visibility = android.view.View.VISIBLE
    }

    // ── Daily modifier badges + completion summary ───────────────────────────

    /** Deterministic daily modifier badge (derived from date) + par time. */
    private fun modifierBadges(date: String, difficulty: String): String {
        val par = when (difficulty) { "Easy" -> "2:00"; "Medium" -> "3:00"; "Hard" -> "5:00"; else -> "7:00" }
        val pool = listOf(
            getString(R.string.modifier_streak_shield),
            getString(R.string.modifier_speed_run, par),
            getString(R.string.modifier_no_hint),
            getString(R.string.modifier_par_time, par),
            getString(R.string.modifier_one_shot)
        )
        val pick = pool[Math.floorMod(date.hashCode(), pool.size)]
        return pick + getString(R.string.modifier_day, dayOfYear(date))
    }

    private fun dayOfYear(date: String): Int {
        val parts = date.substringBefore('T').split("-")
        if (parts.size >= 3) {
            val m = parts[1].toIntOrNull()
            val d = parts[2].toIntOrNull()
            if (m != null && d != null && m in 1..12) {
                val daysBefore = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
                return (daysBefore[m - 1] + d).coerceIn(1, 365)
            }
        }
        return Math.floorMod(date.hashCode(), 365) + 1
    }

    private fun formatTime(s: Int): String {
        val m = s / 60; val sec = s % 60
        return "$m:${sec.toString().padStart(2, '0')}"
    }

    private fun showSummaryCard() {
        val secs = ((System.currentTimeMillis() - startTime) / 1000).toInt().coerceAtLeast(0)
        val msg = buildString {
            append(getString(R.string.dc_time_label)).append(formatTime(secs)).append("\n")
            append(getString(R.string.dc_attempts_label)).append(attempts).append("\n")
            append(getString(R.string.dc_hints_label)).append(hintIndex).append("\n")
            append("🗓️ ").append(challengeDate)
        }
        AlertDialog.Builder(this, R.style.HintDialog)
            .setTitle(getString(R.string.dc_complete_title))
            .setMessage(msg)
            .setPositiveButton(getString(R.string.dc_nice), null)
            .show()
    }

    private fun showError(e: Throwable) {
        Log.w(TAG, "Daily challenge load failed: ${e.message}")
        headerTitle.text = getString(R.string.daily_title)
        challengeInfo.text = getString(R.string.dc_unavailable)
        puzzlePrompt.visibility = android.view.View.GONE
        hintsView.visibility = android.view.View.GONE
        answerInput.isEnabled = false
        btnCheck.isEnabled = false
        btnHint.isEnabled = false
        statusText.setTextColor(Color.parseColor(COLOR_ERROR))
        statusText.text = getString(R.string.dc_backend_down_fmt, e.message ?: "offline")
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

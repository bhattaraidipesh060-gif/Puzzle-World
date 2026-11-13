package com.jeiu.puzzleworld

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Leaderboard screen — fetches the backend's GET /api/progress (leaderboard)
 * and renders a ranked table of players, sorted by solved count then score.
 */
class LeaderboardActivity : AppCompatActivity() {

    companion object {
        private const val BASE_URL = ApiClient.BASE_URL
        private const val TIMEOUT_MS = 15000

        private val MEDALS = listOf("🥇", "🥈", "🥉")
    }

    private data class Row(val rank: Int, val name: String, val solved: Int, val score: Int)

    private lateinit var listContainer: LinearLayout
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leaderboard)
        AnimatedBackgroundDrawable.applyTo(this, Color.parseColor("#14100A"), Color.parseColor("#0A1428"), Color.parseColor("#FFD740"))
        listContainer = findViewById(R.id.lb_list)
        statusText = findViewById(R.id.lb_status)
        loadLeaderboard()
    }

    private fun loadLeaderboard() {
        statusText.text = "Loading leaderboard…"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { fetchLeaderboard() }
            result.onSuccess { render(it) }
                   .onFailure { renderError(it) }
        }
    }

    private fun fetchLeaderboard(): Result<List<Row>> = try {
        val conn = (URL(BASE_URL.trimEnd('/') + "/api/progress").openConnection() as HttpURLConnection).apply {
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
            val arr = JSONObject(body).optJSONArray("leaderboard")
            val rows = mutableListOf<Row>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    rows.add(
                        Row(
                            rank = i + 1,
                            name = e.optString("username", e.optString("user_id", "Player")),
                            solved = e.optInt("solved_count", 0),
                            score = e.optInt("score", 0)
                        )
                    )
                }
            }
            Result.success(rows)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun render(rows: List<Row>) {
        listContainer.removeAllViews()
        if (rows.isEmpty()) {
            statusText.text = "No players yet — solve a puzzle to appear here!"
            return
        }
        statusText.text = "${rows.size} player${if (rows.size == 1) "" else "s"}"
        rows.forEach { r -> listContainer.addView(rowView(r)) }
    }

    private fun rowView(r: Row): TextView {
        val medal = MEDALS.getOrElse(r.rank - 1) { "  $r.rank." }
        val label = "$medal  ${r.name}    ·    ${r.solved} solved    ·    ⭐${r.score}"
        return TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(Color.parseColor(if (r.rank <= 3) "#FFD740" else "#94A3B8"))
            setTypeface(typeface, if (r.rank <= 3) Typeface.BOLD else Typeface.NORMAL)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }
    }

    private fun renderError(e: Throwable) {
        listContainer.removeAllViews()
        statusText.setTextColor(Color.parseColor("#FFD740"))
        statusText.text = "📴 Offline — showing local scores"
        renderLocal()
    }

    /** Fall back to on-device scores when the backend is unreachable. */
    private fun renderLocal() {
        val board = GameStats.leaderboard(this)
        if (board.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = "No local scores yet — solve a puzzle!"
                textSize = 15f
                setTextColor(Color.parseColor("#94A3B8"))
                gravity = Gravity.CENTER
                setPadding(0, dp(24), 0, dp(24))
            })
            return
        }
        board.forEachIndexed { i, (name, score) ->
            listContainer.addView(rowView(Row(i + 1, name, 0, score)))
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

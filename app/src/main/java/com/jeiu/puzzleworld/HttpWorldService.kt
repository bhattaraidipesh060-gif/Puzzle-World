package com.jeiu.puzzleworld

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Real HTTP client for the PuzzleWorld backend (Node + Express + SQLite).
 *
 * Base URL is the configured ApiClient.BASE_URL (BuildConfig.API_BASE_URL) — the
 * hosted backend in release builds, emulator loopback (10.0.2.2) in debug. The
 * app remains offline-first: LocalStorage keeps bundled rooms and
 * player state; this service only augments with cloud sync / remote rooms.
 */
class HttpWorldService(
    private val baseUrl: String,
    private val userId: String = "android_player"
) : WorldRepository {

    companion object {
        private const val TAG = "PUZZLE_WORLD"
        private const val TIMEOUT_MS = 15000

        /**
         * Mirrors the backend's dailySeed(): Java String.hashCode over the
         * yyyyMMdd string, truncated to 32-bit each step, then absolute value.
         */
        fun dailySeed(dateStr: String): Int {
            var h = 0
            for (c in dateStr) h = (h shl 5) - h + c.code
            return kotlin.math.abs(h)
        }
    }

    override suspend fun isReachable(): Boolean = withContext(Dispatchers.IO) {
        try {
            open("GET", "/api/health", null).responseCode == 200
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun fetchRoom(roomId: String): Result<PuzzleRoom> = withContext(Dispatchers.IO) {
        try {
            val body = get("/api/rooms/$roomId")
            val json = flattenDeductionItems(JSONObject(body))
            Result.success(PuzzleRoom.fromJson(json))
        } catch (e: Exception) {
            Log.w(TAG, "fetchRoom($roomId) failed: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun syncProgress(state: PlayerState): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("user_id", userId)
                put("username", userId)
                put("solved_levels", JSONObject(state.solvedLevels))
                put("score", state.score)
                put("streak", state.streak)
                put("hints_used", state.hintsUsed)
                put("total_attempts", state.totalAttempts)
                if (state.currentRoom != null) put("current_room", state.currentRoom)
            }
            val conn = open("POST", "/api/progress", payload.toString())
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299) Result.success(Unit)
            else Result.failure(WorldNetworkException("HTTP $code"))
        } catch (e: Exception) {
            Log.w(TAG, "syncProgress failed: ${e.message}")
            Result.failure(e)
        }
    }

    // ── internals ────────────────────────────────────────────────────────

    private fun get(path: String): String {
        val conn = open("GET", path, null)
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            throw WorldNetworkException("HTTP $code for $path")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }.also { conn.disconnect() }
    }

    private fun open(method: String, path: String, body: String?): HttpURLConnection {
        val url = URL(baseUrl.trimEnd('/') + path)
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (body != null) doOutput = true
        }
    }

    /**
     * The backend stores deduction_grid `items` as a 2D array (one group per
     * category), but the Android model types it as a flat List<String>. Flatten
     * before deserialising so kotlinx.serialization parses cleanly.
     */
    private fun flattenDeductionItems(room: JSONObject): JSONObject {
        val levels = room.optJSONArray("levels") ?: return room
        for (i in 0 until levels.length()) {
            val level = levels.optJSONObject(i) ?: continue
            val mechanic = level.optJSONObject("mechanic") ?: continue
            if (mechanic.optString("mechanic_type") != "deduction_grid") continue
            val items = mechanic.optJSONArray("items") ?: continue
            val flat = JSONArray()
            for (j in 0 until items.length()) {
                val group = items.optJSONArray(j)
                if (group != null) for (k in 0 until group.length()) flat.put(group.get(k))
                else flat.put(items.get(j))
            }
            mechanic.put("items", flat)
        }
        return room
    }

    /** Fetches the global leaderboard (GET /api/progress → { leaderboard: [...] }). */
    suspend fun fetchLeaderboard(): List<LeaderboardEntry> = withContext(Dispatchers.IO) {
        try {
            val body = get("/api/progress")
            val arr = JSONObject(body).optJSONArray("leaderboard") ?: JSONArray()
            (0 until arr.length()).map { i ->
                val o = arr.optJSONObject(i)
                LeaderboardEntry(
                    userId = o.optString("user_id", ""),
                    username = o.optString("username", "Player"),
                    solvedCount = o.optInt("solved_count", 0),
                    score = o.optInt("score", 0)
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchLeaderboard failed: ${e.message}")
            emptyList()
        }
    }

    /** Fetches the user's rank badges (GET /api/user/profile). */
    suspend fun fetchProfile(): BadgeProfile? = withContext(Dispatchers.IO) {
        try {
            val q = java.net.URLEncoder.encode(userId, "UTF-8")
            val root = JSONObject(get("/api/user/profile?user_id=$q"))
            val arr = root.optJSONArray("badges") ?: JSONArray()
            val badges = (0 until arr.length()).map { i ->
                val b = arr.optJSONObject(i)
                val prog = b.optJSONObject("progress") ?: JSONObject()
                Badge(
                    id = b.optString("id", ""),
                    name = b.optString("name", ""),
                    icon = b.optString("icon", "🏅"),
                    description = b.optString("description", ""),
                    unlocked = b.optBoolean("unlocked", false),
                    current = prog.optInt("current", 0),
                    target = prog.optInt("target", 0)
                )
            }
            BadgeProfile(
                username = root.optString("username", "Player"),
                unlockedCount = root.optInt("unlocked_count", 0),
                totalBadges = root.optInt("total_badges", 0),
                badges = badges
            )
        } catch (e: Exception) {
            Log.w(TAG, "fetchProfile failed: ${e.message}")
            null
        }
    }
}

data class LeaderboardEntry(
    val userId: String,
    val username: String,
    val solvedCount: Int,
    val score: Int
)

data class Badge(
    val id: String,
    val name: String,
    val icon: String,
    val description: String,
    val unlocked: Boolean,
    val current: Int,
    val target: Int
)

data class BadgeProfile(
    val username: String,
    val unlockedCount: Int,
    val totalBadges: Int,
    val badges: List<Badge>
)

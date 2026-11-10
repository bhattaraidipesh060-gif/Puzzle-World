package com.jeiu.puzzleworld

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

// ═══════════════════════════════════════════════════════════════════════
// PuzzleItem
// ═══════════════════════════════════════════════════════════════════════

@Serializable
data class PuzzleItem(
    val id: String,
    val type: String,
    @SerialName("x_grid") val xGrid: Int = 0,
    @SerialName("y_grid") val yGrid: Int = 0,
    val label: String = "",
    val data: Map<String, String> = emptyMap()
) {
    companion object {
        fun fromJson(json: JSONObject) = PuzzleItem(
            id    = json.getString("id"),
            type  = json.getString("type"),
            xGrid = json.optInt("x_grid", 0),
            yGrid = json.optInt("y_grid", 0),
            label = json.optString("label", ""),
            data  = json.optJSONObject("data")?.let { obj ->
                obj.keys().asSequence().associateWith { obj.getString(it) }
            } ?: emptyMap()
        )
    }

    fun toJson() = JSONObject().apply {
        put("id", id); put("type", type)
        put("x_grid", xGrid); put("y_grid", yGrid)
        if (label.isNotEmpty()) put("label", label)
        if (data.isNotEmpty()) put("data", JSONObject(data))
    }
}

// ═══════════════════════════════════════════════════════════════════════
// PuzzleMechanic — sealed hierarchy (5 types)
// ═══════════════════════════════════════════════════════════════════════

@Serializable
sealed class PuzzleMechanic {
    abstract val mechanicType: String

    @Serializable @SerialName("wire_maze")
    data class WireMaze(
        val gridCols: Int = 8,
        val gridRows: Int = 8,
        val items: List<PuzzleItem> = emptyList()
    ) : PuzzleMechanic() { override val mechanicType = "wire_maze" }

    @Serializable @SerialName("cipher")
    data class Cipher(
        @SerialName("cipher_text") val cipherText: String,
        @SerialName("cipher_type") val cipherType: String = "caesar",
        val hint: String = "",
        val answer: String
    ) : PuzzleMechanic() { override val mechanicType = "cipher" }

    @Serializable @SerialName("riddle")
    data class Riddle(
        @SerialName("riddle_text") val riddleText: String,
        val hint: String = "",
        val answer: String
    ) : PuzzleMechanic() { override val mechanicType = "riddle" }

    @Serializable @SerialName("deduction_grid")
    data class DeductionGrid(
        val categories: List<String>,
        val items: List<String>,
        val clues: List<String>,
        val solution: Map<String, String>
    ) : PuzzleMechanic() { override val mechanicType = "deduction_grid" }

    @Serializable @SerialName("mechanical_lock")
    data class MechanicalLock(
        @SerialName("lock_type")    val lockType: String = "dial",
        val combination: List<Int>,
        @SerialName("max_attempts") val maxAttempts: Int = 5
    ) : PuzzleMechanic() { override val mechanicType = "mechanical_lock" }

    @Serializable @SerialName("pattern_lock")
    data class PatternLock(
        @SerialName("grid_size") val gridSize: Int = 3,
        val pattern: List<Int>
    ) : PuzzleMechanic() { override val mechanicType = "pattern_lock" }

    @Serializable @SerialName("color_sequence")
    data class ColorSequence(
        val colors: List<String>,
        val sequence: List<Int>,
        @SerialName("sequence_length") val sequenceLength: Int = 4,
        @SerialName("reveal_delay_ms") val revealDelayMs: Long = 800L
    ) : PuzzleMechanic() { override val mechanicType = "color_sequence" }

    @Serializable @SerialName("vault_dial")
    data class VaultDial(
        val combination: List<Int>,
        val digits: Int = 0,
        val range: Int = 9,
        @SerialName("max_attempts") val maxAttempts: Int = 6
    ) : PuzzleMechanic() { override val mechanicType = "vault_dial" }

    @Serializable @SerialName("cipher_wheel")
    data class CipherWheel(
        @SerialName("cipher_text") val cipherText: String,
        @SerialName("cipher_type") val cipherType: String = "caesar",
        val shift: Int = 0,
        val answer: String = "",
        val hint: String = ""
    ) : PuzzleMechanic() { override val mechanicType = "cipher_wheel" }

    @Serializable @SerialName("gear_alignment")
    data class GearAlignment(
        val gears: List<GearSegment> = emptyList()
    ) : PuzzleMechanic() { override val mechanicType = "gear_alignment" }

    @Serializable @SerialName("sliding_tile")
    data class SlidingTile(
        val size: Int = 3,
        val tiles: List<Int> = emptyList()
    ) : PuzzleMechanic() { override val mechanicType = "sliding_tile" }

    @Serializable @SerialName("terminal_hack")
    data class TerminalHack(
        val mode: String = "bin_to_hex",
        val input: String = "",
        val answer: String = "",
        val nodes: List<Int> = emptyList()
    ) : PuzzleMechanic() { override val mechanicType = "terminal_hack" }

    @Serializable @SerialName("potion_mix")
    data class PotionMix(
        val beakers: List<BeakerVolume> = emptyList(),
        val target: Int = 0
    ) : PuzzleMechanic() { override val mechanicType = "potion_mix" }

    @Serializable @SerialName("coordinate_maze")
    data class CoordinateMaze(
        val grid: List<String> = emptyList()
    ) : PuzzleMechanic() { override val mechanicType = "coordinate_maze" }

    @Serializable @SerialName("lock_pin")
    data class LockPin(
        val pins: List<Int> = emptyList(),
        @SerialName("max_attempts") val maxAttempts: Int = 5
    ) : PuzzleMechanic() { override val mechanicType = "lock_pin" }

    @Serializable @SerialName("neon_simon")
    data class NeonSimon(
        val colors: Int = 4,
        val sequence: List<Int> = emptyList(),
        @SerialName("speed_ms") val speedMs: Long = 700L
    ) : PuzzleMechanic() { override val mechanicType = "neon_simon" }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            classDiscriminator = "mechanic_type"
        }
        fun fromJsonString(s: String) = json.decodeFromString(serializer(), s)
        fun toJsonString(m: PuzzleMechanic) = json.encodeToString(serializer(), m)
    }
}

// Helper value types for the new minigame mechanics.
@Serializable
data class GearSegment(val angle: Int = 0, val target: Int = 0)

@Serializable
data class BeakerVolume(val id: Int = 0, val cap: Int = 0, val vol: Int = 0)

// ═══════════════════════════════════════════════════════════════════════
// PuzzleLevel
// ═══════════════════════════════════════════════════════════════════════

@Serializable
data class PuzzleLevel(
    @SerialName("level_id")  val levelId: String,
    val title: String,
    val difficulty: String,
    val mechanic: PuzzleMechanic,
    @SerialName("is_solved") val isSolved: Boolean = false,
    val hints: List<String> = emptyList()
) {
    companion object {
        val DIFFICULTIES = listOf("Easy", "Medium", "Hard", "Expert")
        fun fromJson(json: JSONObject): PuzzleLevel {
            val mJson = json.getJSONObject("mechanic")
            return PuzzleLevel(
                levelId    = json.getString("level_id"),
                title      = json.getString("title"),
                difficulty = json.getString("difficulty"),
                mechanic   = PuzzleMechanic.fromJsonString(mJson.toString()),
                isSolved   = json.optBoolean("is_solved", false),
                hints      = json.optJSONArray("hints")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList()
            )
        }
    }

    fun toJson() = JSONObject().apply {
        put("level_id", levelId); put("title", title)
        put("difficulty", difficulty)
        put("mechanic", JSONObject(PuzzleMechanic.toJsonString(mechanic)))
        put("is_solved", isSolved)
        if (hints.isNotEmpty()) put("hints", JSONArray(hints))
    }
}

// ═══════════════════════════════════════════════════════════════════════
// PuzzleRoom
// ═══════════════════════════════════════════════════════════════════════

@Serializable
data class PuzzleRoom(
    @SerialName("room_id")   val roomId: String,
    @SerialName("room_name") val roomName: String,
    val theme: String = "",
    val description: String = "",
    val levels: List<PuzzleLevel>,
    @SerialName("room_graph") val roomGraph: Map<String, List<String>> = emptyMap()
) {
    companion object {
        const val LEVELS_PER_ROOM = 10
        fun fromJson(json: JSONObject): PuzzleRoom {
            val levelsArr = json.getJSONArray("levels")
            val levels = (0 until levelsArr.length()).map { i ->
                PuzzleLevel.fromJson(levelsArr.getJSONObject(i))
            }
            val graph = json.optJSONObject("room_graph")?.let { obj ->
                obj.keys().asSequence().associateWith { key ->
                    val arr = obj.getJSONArray(key)
                    (0 until arr.length()).map { arr.getString(it) }
                }
            } ?: emptyMap()
            return PuzzleRoom(roomId      = json.getString("room_id"),
                              roomName    = json.getString("room_name"),
                              theme       = json.optString("theme", ""),
                              description = json.optString("description", ""),
                              levels      = levels,
                              roomGraph   = graph)
        }
    }

    fun toJson() = JSONObject().apply {
        put("room_id", roomId); put("room_name", roomName)
        put("theme", theme); put("description", description)
        put("levels", JSONArray().also { arr -> levels.forEach { arr.put(it.toJson()) } })
        if (roomGraph.isNotEmpty())
            put("room_graph", JSONObject().apply {
                roomGraph.forEach { (k, v) -> put(k, JSONArray(v)) }
            })
    }

    fun toJsonString() = toJson().toString(2)
    fun levelNumber(n: Int) = levels.getOrNull(n - 1)
    fun solvedCount(solved: Map<String, Boolean>) = levels.count { solved[it.levelId] == true }
}

// ═══════════════════════════════════════════════════════════════════════
// PlayerState
// ═══════════════════════════════════════════════════════════════════════

data class PlayerState(
    val solvedLevels: MutableMap<String, Boolean> = mutableMapOf(),
    val currentRoom: String? = null,
    var hintsUsed: Int = 0,
    var totalAttempts: Int = 0,
    var score: Int = 0,
    var streak: Int = 0
) {
    companion object {
        fun fromJson(json: JSONObject): PlayerState {
            val solved = mutableMapOf<String, Boolean>()
            json.optJSONObject("solved_levels")?.let { obj ->
                obj.keys().forEach { solved[it] = obj.getBoolean(it) }
            }
            return PlayerState(
                solvedLevels  = solved,
                currentRoom   = json.optString("current_room", "").ifBlank { null },
                hintsUsed     = json.optInt("hints_used", 0),
                totalAttempts = json.optInt("total_attempts", 0),
                score         = json.optInt("score", 0),
                streak        = json.optInt("streak", 0))
        }
    }

    fun toJson() = JSONObject().apply {
        put("solved_levels", JSONObject(solvedLevels))
        currentRoom?.let { put("current_room", it) }
        put("hints_used", hintsUsed)
        put("total_attempts", totalAttempts)
        put("score", score)
        put("streak", streak)
    }

    fun isSolved(id: String) = solvedLevels[id] == true
    fun markSolved(id: String) { solvedLevels[id] = true }

    fun nextUnsolved(room: PuzzleRoom): Int {
        for (i in 1..PuzzleRoom.LEVELS_PER_ROOM)
            room.levelNumber(i)?.let { if (!isSolved(it.levelId)) return i }
        return PuzzleRoom.LEVELS_PER_ROOM + 1
    }
}
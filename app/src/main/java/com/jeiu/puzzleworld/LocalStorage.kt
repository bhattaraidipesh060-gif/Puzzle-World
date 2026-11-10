package com.jeiu.puzzleworld

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class LocalStorage(private val context: Context) {

    companion object {
        private const val TAG = "PUZZLE_WORLD"
        private const val ROOMS_DIR = "rooms"
        private const val STATE_FILE = "player_state.json"
    }

    private val roomsDir
        get() = File(context.filesDir, ROOMS_DIR).also { it.mkdirs() }
    private val stateFile
        get() = File(context.filesDir, STATE_FILE)

    suspend fun loadRoom(roomId: String): PuzzleRoom? = withContext(Dispatchers.IO) {
        val f = File(roomsDir, "$roomId.json")
        if (!f.exists()) { Log.w(TAG, "loadRoom: not found — $roomId"); return@withContext null }
        try { PuzzleRoom.fromJson(JSONObject(f.readText())) }
        catch (e: Exception) { Log.e(TAG, "loadRoom: parse error $roomId", e); null }
    }

    suspend fun saveRoom(room: PuzzleRoom) = withContext(Dispatchers.IO) {
        File(roomsDir, "${room.roomId}.json").writeText(room.toJsonString())
        Log.d(TAG, "saveRoom: ${room.roomId}")
    }

    suspend fun listRoomIds(): List<String> = withContext(Dispatchers.IO) {
        roomsDir.listFiles()?.filter { it.extension == "json" }?.map { it.nameWithoutExtension }?.sorted()
            ?: emptyList()
    }

    suspend fun seedBuiltInRooms() = withContext(Dispatchers.IO) {
        for (id in listOf("room_01_vault", "room_02_cipher_lab", "room_03_observatory", "room_04_study", "room_05_crypto", "room_06_alchemy", "room_07_cartography", "room_08_locksmith", "room_09_arcade")) {
            val f = File(roomsDir, "$id.json")
            if (f.exists()) continue
            try {
                val text = context.assets.open("rooms/$id.json").bufferedReader().readText()
                f.writeText(text)
                Log.d(TAG, "seedBuiltInRooms: seeded $id")
            } catch (e: Exception) { Log.w(TAG, "seedBuiltInRooms: no asset $id") }
        }
    }

    suspend fun loadState(): PlayerState = withContext(Dispatchers.IO) {
        if (!stateFile.exists()) PlayerState()
        else try { PlayerState.fromJson(JSONObject(stateFile.readText())) }
        catch (e: Exception) { Log.e(TAG, "loadState: parse error", e); PlayerState() }
    }

    suspend fun saveState(state: PlayerState) = withContext(Dispatchers.IO) {
        stateFile.writeText(state.toJson().toString(2))
    }
}
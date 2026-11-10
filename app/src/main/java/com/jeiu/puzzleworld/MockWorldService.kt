package com.jeiu.puzzleworld

import android.util.Log
import kotlinx.coroutines.delay
import kotlin.random.Random

class MockWorldService : WorldRepository {

    companion object {
        private const val TAG = "PUZZLE_WORLD"
        private const val DELAY_MIN = 300L
        private const val DELAY_MAX = 1200L
        private const val FAIL_RATE = 0.10f
    }

    private val rng = Random(System.currentTimeMillis())

    override suspend fun syncProgress(state: PlayerState): Result<Unit> = call("sync") {
        Log.d(TAG, "syncProgress: ${state.solvedLevels.size} solved")
    }

    override suspend fun fetchRoom(roomId: String): Result<PuzzleRoom> = call("fetchRoom") {
        throw WorldNetworkException("No remote room — use local generator")
    }

    override suspend fun isReachable(): Boolean {
        delay(DELAY_MIN)
        return rng.nextFloat() > FAIL_RATE / 2f
    }

    private suspend fun <T> call(op: String, block: () -> T): Result<T> {
        delay(rng.nextLong(DELAY_MIN, DELAY_MAX + 1))
        return if (rng.nextFloat() < FAIL_RATE) Result.failure(WorldNetworkException("Simulated failure: $op"))
        else try { Result.success(block()) } catch (e: Exception) { Result.failure(e) }
    }
}

class WorldNetworkException(msg: String) : Exception(msg)
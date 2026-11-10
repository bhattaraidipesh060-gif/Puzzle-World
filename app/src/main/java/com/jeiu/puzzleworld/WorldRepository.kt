package com.jeiu.puzzleworld

interface WorldRepository {
    suspend fun syncProgress(state: PlayerState): Result<Unit>
    suspend fun fetchRoom(roomId: String): Result<PuzzleRoom>
    suspend fun isReachable(): Boolean
}
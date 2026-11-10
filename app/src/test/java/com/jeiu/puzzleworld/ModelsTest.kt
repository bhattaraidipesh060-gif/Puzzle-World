package com.jeiu.puzzleworld

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ModelsTest {

    @Test
    fun `PuzzleRoom round-trip through JSON`() {
        val room = PuzzleRoom("r1", "Test Room", "test", "desc", listOf(
            PuzzleLevel("r1_01", "P1", "Easy", PuzzleMechanic.Cipher("ABC", "caesar", "hint", "XYZ"))
        ), mapOf("r1_01" to listOf("r1_02")))
        val parsed = PuzzleRoom.fromJson(JSONObject(room.toJsonString()))
        assertEquals(room.roomId, parsed.roomId)
        assertEquals(room.roomName, parsed.roomName)
        assertEquals(1, parsed.levels.size)
        assertTrue(parsed.levels[0].mechanic is PuzzleMechanic.Cipher)
    }

    @Test
    fun `PlayerState round-trip`() {
        val s = PlayerState(mutableMapOf("a" to true, "b" to false), "r1", 3, 10)
        val parsed = PlayerState.fromJson(JSONObject(s.toJson().toString()))
        assertTrue(parsed.isSolved("a"))
        assertFalse(parsed.isSolved("b"))
        assertEquals("r1", parsed.currentRoom)
        assertEquals(3, parsed.hintsUsed)
    }

    @Test
    fun `mechanic serialization via kotlinx`() {
        val cipher = PuzzleMechanic.Cipher("HELLO", "caesar", "shift 3", "KHOOR")
        val json = PuzzleMechanic.toJsonString(cipher)
        assertTrue(json.contains("cipher"))
        val parsed = PuzzleMechanic.fromJsonString(json)
        assertTrue(parsed is PuzzleMechanic.Cipher)
        assertEquals("HELLO", (parsed as PuzzleMechanic.Cipher).cipherText)
    }

    @Test
    fun `solvedCount counts completed`() {
        val room = PuzzleRoom("r1", "R", "t", "d", (1..5).map {
            PuzzleLevel("r1_0$it", "P$it", "Easy", PuzzleMechanic.Riddle("Q", "h", "A"))
        })
        val solved = mapOf("r1_01" to true, "r1_02" to true, "r1_03" to true)
        assertEquals(3, room.solvedCount(solved))
    }

    @Test
    fun `nextUnsolved finds first incomplete`() {
        val room = PuzzleRoom("r1", "R", "t", "d", (1..5).map { n ->
            PuzzleLevel("r1_0$n", "P$n", "Easy", PuzzleMechanic.Riddle("Q", "h", "A"), n <= 2)
        })
        val state = PlayerState(mutableMapOf("r1_01" to true, "r1_02" to true))
        assertEquals(3, state.nextUnsolved(room))
    }
}
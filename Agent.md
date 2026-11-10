# AGENTS.md — AI Developer Guidelines for PuzzleWorld

## Project Overview
**PuzzleWorld** is a native Android advanced multi-genre puzzle game built with Kotlin. Unlike simple grid-moving games, PuzzleWorld is an ambitious, immersive experience featuring fully realized escape rooms, cryptic riddles, cipher decoding, logical deduction grids, interactive mechanical locks, and complex multi-layered environments. The architecture follows an offline-first modular pattern with local game state persistence, seed-based procedural puzzle generation, and JSON-driven room schemas, prepared for cloud synchronization via a mock network layer (`MockWorldService`).

---

## Technical Stack & Configuration
- **Language**: Kotlin (100% Kotlin idiomatic code)
- **Minimum SDK**: `minSdk = 28`
- **Compile SDK**: `compileSdk = 37`
- **Target SDK**: `targetSdk = 37`
- **Package Name**: `com.jeiu.puzzleworld`
- **Concurrency**: Kotlin Coroutines (`lifecycleScope`, `viewModelScope`, `Dispatchers.IO`)
- **JSON Engine**: `org.json` (`JSONObject`, `JSONArray`) / kotlinx.serialization for advanced room graphs

---

## Code Base & Directory Layout
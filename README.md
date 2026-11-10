# PuzzleWorld 🧩

An **offline-first escape-room puzzle game** for Android, paired with a web
portal and a Node.js + SQLite sync backend. Solve **90 puzzles across 9 themed
rooms** — each room is built around its own unique mini-game mechanic — and
race a global leaderboard while unlocking **15 rank badges**.

> **Stack:** Kotlin/Android · Express.js · SQLite (better-sqlite3) · Web Audio API

---

## ✨ Features

- **9 themed rooms × 10 levels = 90 puzzles**, each room with a distinct mechanic:
  | Room | Theme | Mechanic |
  |---|---|---|
  | The First Vault | Industrial | 🎚️ Vault dial |
  | The Cipher Laboratory | Laboratory | 🔐 Cipher wheel |
  | The Clockwork Observatory | Observatory | ⚙️ Gear alignment |
  | The Puzzle Master's Study | Study | 🧩 Sliding tile |
  | The Cryptographer's Cache | Crypto | 💻 Terminal hack |
  | The Alchemist's Laboratory | Alchemy | ⚗️ Potion mix |
  | The Cartographer's Vault | Cartography | 🗺️ Coordinate maze |
  | The Locksmith's Forge | Forge | 🔢 Lock PIN |
  | The Neon Arcade | Arcade | 🌈 Neon Simon |
- **Offline-first** — full game playable with no network; progress syncs to the
  backend when available.
- **Daily Challenge** — a fresh featured puzzle each day to keep your streak alive.
- **Leaderboard** — solved-count + score ranking across all players.
- **15 rank badges** — on-demand achievements (unlock thresholds, streaks, room
  completion) shown on the web portal and in the Android app.
- **JWT authentication** — register/login, HS256-signed tokens, salted-SHA256
  password hashing (no plaintext stored).
- **Dark glassmorphic UI** — Sora/Inter typography, gradient-border cards, neon
  glow accents, and synthesized sound effects (no asset files).

---

## 🛠 Tech Stack

| Layer | Technology |
|---|---|
| **Android app** | Kotlin 2.2.10 · AGP 9.2.1 · Gradle 9.5.0 · compileSdk/targetSdk 37 · minSdk 28 |
| **Networking** | Retrofit 2.11.0 · OkHttp 4.12.0 · kotlinx-serialization |
| **Local storage** | Room (SQLite) + EncryptedSharedPreferences (Keystore AES-256-GCM) |
| **Native audio** | Android `AudioTrack` (PCM synthesized SFX) |
| **Backend** | Node.js · Express 4 · better-sqlite3 (SQLite, WAL mode) |
| **Web portal** | Vanilla HTML/CSS/JS · **Web Audio API** (synthesized SFX) |

---

## 📁 Project Structure

```
.
├── app/                      # Android app (Kotlin) — the native game
│   └── src/main/
│       ├── assets/rooms/     # Room/level JSON — single source of truth for content
│       ├── java/com/jeiu/puzzleworld/
│       └── res/              # Layouts, drawables (ps_* design system), fonts
├── backend/                  # Node.js + Express + SQLite API + web portal
│   ├── routes/               # auth, rooms, progress, challenges, stats, profile
│   ├── public/               # Web dashboard (HTML/CSS/JS)
│   ├── database.js           # Auto-creates the schema on first run
│   ├── seed.js               # Seeds rooms/levels from app assets
│   └── badges.js             # Rank-badge evaluation
└── build.gradle.kts          # Root Gradle build
```

---

## 🚀 Getting Started

### Prerequisites

- **Node.js ≥ 20.12** (uses the built-in `process.loadEnvFile`) and npm
- **JDK 17** and Android Studio (for the Android app)

### 1. Backend

```bash
cd backend
npm install

# Configure secrets (creates backend/.env from the template)
cp .env.example .env
#   then edit .env and set PW_JWT_SECRET, e.g.:
#   node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"

npm run seed     # one-time: create the DB + seed 9 rooms / 90 levels
npm start        # → http://localhost:3000  (dev: npm run dev)
```

The schema is created automatically on first run by `database.js`, and
`npm run seed` populates the `rooms` and `levels` tables from the Android
app's bundled room JSON (`app/src/main/assets/rooms`).

### 2. Android app

```bash
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

Or open the project root in Android Studio and press **Run**. The app targets
the emulator's host loopback (`10.0.2.2:3000`) for sync — a physical device
needs your machine's LAN IP (see `ApiClient.kt`).

### 3. Web portal

The portal is served statically by the backend at **http://localhost:3000/**
(no separate build step — edit `backend/public/` and refresh).

---

## 🔑 Environment Variables

| Variable | Default | Purpose |
|---|---|---|
| `PW_JWT_SECRET` | *(required)* | Secret for signing HS256 auth tokens |
| `PW_DB_PATH` | `backend/puzzleworld.db` | SQLite database file path |
| `ROOMS_ASSETS_DIR` | `../app/src/main/assets/rooms` | Where `seed.js` reads room content |
| `PORT` | `3000` | HTTP port |

`.env` is git-ignored — see `.env.example` for the documented template.

---

## 🌐 API Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/health` | Service health check |
| `POST` | `/api/auth/register` | Create an account → `{ token, user }` |
| `POST` | `/api/auth/login` | Log in → `{ token, user }` |
| `GET` | `/api/auth/me` | Validate `Bearer` token |
| `GET` | `/api/rooms` | List all rooms |
| `GET` | `/api/rooms/:roomId` | Room detail with levels |
| `POST` | `/api/progress` | Report level completion |
| `GET` | `/api/progress/:userId` | Player progress |
| `GET` | `/api/progress` | Leaderboard |
| `GET` | `/api/daily-challenge` | Today's challenge |
| `GET` | `/api/stats/:userId` | Player stats |
| `GET` | `/api/user/profile` | Profile + rank badges |

---

## 📦 Building the APK

```bash
./gradlew assembleDebug      # debuggable APK
./gradlew assembleRelease    # minified + ProGuard (sign with your keystore)
```

The APK is written to `app/build/outputs/apk/`. To share a standalone build,
attach the `.apk` to a **GitHub Release** (Releases → *Create a new release* →
upload the APK), which gives you a permanent download link.

---

## 🔒 Security Notes

- No secrets are committed: the JWT secret lives in `backend/.env` (git-ignored);
  `.env.example` documents the required variables.
- Passwords are stored as **salted SHA-256** hashes, never plaintext.
- Auth tokens are HS256 JWTs (30-day expiry) verified with constant-time
  comparison.

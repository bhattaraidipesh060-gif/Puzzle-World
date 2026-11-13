package com.jeiu.puzzleworld

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    companion object { private const val TAG = "PUZZLE_WORLD" }

    private lateinit var world: WorldRepository
    private lateinit var storage: LocalStorage
    private lateinit var generator: PuzzleGenerator
    private lateinit var headerTitle: TextView
    private lateinit var puzzleInfo: TextView
    private lateinit var puzzleGrid: PuzzleGridView
    private lateinit var patternLockView: PatternLockView
    private lateinit var colorSequenceView: ColorSequenceView
    private lateinit var celebration: CelebrationView
    private lateinit var answerInput: EditText
    private lateinit var btnSubmit: Button
    private lateinit var btnHint: Button
    private lateinit var btnRooms: Button
    private lateinit var btnDaily: Button
    private lateinit var btnNext: Button
    private lateinit var btnRestart: TextView
    private lateinit var levelStrip: LinearLayout
    private lateinit var stripScroll: HorizontalScrollView
    private lateinit var rootLayout: LinearLayout
    private var headerCard: View? = null
    private lateinit var puzzlePrompt: TextView

    private var state = PlayerState()
    private var rooms: List<PuzzleRoom> = emptyList()
    private var currentRoom: PuzzleRoom? = null
    private var currentLevelNum = 1
    private val hintTier = mutableMapOf<String, Int>()
    private var wireMazePar = 0
    private lateinit var puzzleStage: FrameLayout
    private var minigameView: View? = null

    // ── Color references (from colors.xml) ──────────────────────────────
    private val colorActive  = Color.parseColor("#FF4081")
    private val colorSolved  = Color.parseColor("#00E676")
    private val colorLocked  = Color.parseColor("#2A2A2A")
    private val colorOpen    = Color.parseColor("#263449")
    private val colorAccent  = Color.parseColor("#00E5FF")
    private val colorGold    = Color.parseColor("#FFD740")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        SoundFx.init(this)

        headerTitle      = findViewById(R.id.header_title)
        puzzleInfo       = findViewById(R.id.puzzle_info)
        puzzleGrid       = findViewById(R.id.puzzle_grid)
        patternLockView  = findViewById(R.id.pattern_lock)
        colorSequenceView = findViewById(R.id.color_sequence)
        celebration      = findViewById(R.id.celebration)
        answerInput      = findViewById(R.id.answer_input)
        btnSubmit        = findViewById(R.id.btn_submit)
        btnHint          = findViewById(R.id.btn_hint)
        btnRooms         = findViewById(R.id.btn_rooms)
        btnDaily         = findViewById(R.id.btn_daily)
        btnNext          = findViewById(R.id.btn_next)
        btnRestart       = findViewById(R.id.btn_restart)
        levelStrip       = findViewById(R.id.level_strip)
        stripScroll      = findViewById(R.id.strip_scroll)
        rootLayout       = findViewById(R.id.root_layout)
        headerCard       = findViewById(R.id.header_card)
        puzzlePrompt     = findViewById(R.id.puzzle_prompt)
        puzzleStage      = findViewById(R.id.puzzle_stage)

        // Respect status bar (top) and navigation bar (bottom) insets (edge-to-edge).
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // Use the authenticated user's server id (or username fallback) as the backend identity.
        val username = getSharedPreferences("puzzleworld_account", MODE_PRIVATE)
            .getString("username", "android_player") ?: "android_player"
        val backendUserId = SessionManager.userId() ?: username
        world     = HttpWorldService(ApiClient.BASE_URL, backendUserId)
        storage   = LocalStorage(applicationContext)
        generator = PuzzleGenerator()

        // Style the EditText cursor
        answerInput.setTextColor(colorAccent)

        // Pattern lock listener
        patternLockView.onPatternComplete = { correct ->
            val level = currentRoom?.levelNumber(currentLevelNum)
            if (correct && level != null && !state.isSolved(level.levelId))
                completeLevel(level, "🔓 Pattern matched!")
            else if (!correct)
                Toast.makeText(this, "❌ Wrong pattern — try again", Toast.LENGTH_SHORT).show()
        }

        // Color sequence listener
        colorSequenceView.onSequenceComplete = { correct ->
            val level = currentRoom?.levelNumber(currentLevelNum)
            if (correct && level != null && !state.isSolved(level.levelId))
                completeLevel(level, "🎨 Flawless memory!")
            else
                Toast.makeText(this, "Wrong sequence — watch again…", Toast.LENGTH_SHORT).show()
        }
        colorSequenceView.onPhaseChange = { msg -> puzzleInfo.text = msg }

        btnSubmit.setOnClickListener {
            val lvl = currentRoom?.levelNumber(currentLevelNum) ?: return@setOnClickListener
            when (lvl.mechanic) {
                is PuzzleMechanic.WireMaze -> {
                    if (!state.isSolved(lvl.levelId)) puzzleGrid.resetPath()
                }
                is PuzzleMechanic.PatternLock -> patternLockView.reset()
                is PuzzleMechanic.ColorSequence -> {
                    colorSequenceView.reset()
                    colorSequenceView.startReveal()
                }
                else -> { if (isMinigame(lvl.mechanic)) loadCurrentLevel() else checkAnswer() }
            }
        }
        btnHint.setOnClickListener { showHint() }
        btnRooms.setOnClickListener { showRoomPicker() }
        btnDaily.setOnClickListener { startActivity(android.content.Intent(this, DailyChallengeActivity::class.java)) }
        btnNext.setOnClickListener { navigate(+1) }
        btnRestart.setOnClickListener { showRestartDialog() }

        findViewById<TextView>(R.id.btn_help).setOnClickListener {
            val lvl = currentRoom?.levelNumber(currentLevelNum)
            val type = lvl?.mechanic?.mechanicType ?: return@setOnClickListener
            TutorialEngine.show(this, type)
        }

        lifecycleScope.launch { bootstrap() }
    }

    override fun onPause() {
        super.onPause()
        lifecycleScope.launch { storage.saveState(state) }
    }

    private suspend fun bootstrap() {
        storage.seedBuiltInRooms()
        state = storage.loadState()
        val ids = storage.listRoomIds()
        rooms = ids.mapNotNull { storage.loadRoom(it) }

        if (rooms.isEmpty()) {
            val r = generator.generateRoom("room_01_vault", "The First Vault", "industrial", "pw_seed_01")
            storage.saveRoom(r); rooms = listOf(r)
        }
        // Deep-link: open the specific room requested from Home (else first room).
        val targetRoom = intent.getStringExtra("room_id")
        val startRoom = rooms.firstOrNull { it.roomId == targetRoom } ?: rooms.firstOrNull()
        if (startRoom != null) loadRoom(startRoom)
        syncToCloud()
    }

    private suspend fun syncToCloud() {
        if (!world.isReachable()) {
            Log.d(TAG, "Backend offline — running locally")
            return
        }
        world.syncProgress(state)
            .onSuccess { Log.d(TAG, "Cloud sync OK (${state.solvedLevels.size} solved levels)") }
            .onFailure { Log.d(TAG, "Cloud sync failed: ${it.message}") }
    }

    private fun loadRoom(room: PuzzleRoom) {
        currentRoom = room
        currentLevelNum = state.nextUnsolved(room).coerceAtMost(10)
        buildLevelStrip(room)
        loadCurrentLevel()
    }

    private fun loadCurrentLevel() {
        val room  = currentRoom ?: return
        val level = room.levelNumber(currentLevelNum) ?: return
        applyTheme(room, level)

        // Auto-show tutorial on first encounter of this mechanic
        if (TutorialEngine.shouldShow(this, level.mechanic.mechanicType)) {
            rootLayout.postDelayed({ TutorialEngine.show(this, level.mechanic.mechanicType) }, 600L)
        }

        val solved = if (state.isSolved(level.levelId)) " ✓" else ""
        val icon = mechanicIcon(level.mechanic)

        headerTitle.text = "${room.roomName} — ${level.title}"
        puzzleInfo.text  = "$icon  ${level.difficulty}$solved  ·  ${level.mechanic.mechanicType.replace('_',' ')}  ·  ⭐${state.score}"
        answerInput.isEnabled = !state.isSolved(level.levelId)
        btnSubmit.isEnabled   = !state.isSolved(level.levelId)
        answerInput.text.clear()
        answerInput.hint = when (level.mechanic) {
            is PuzzleMechanic.Cipher         -> "🔐 Decode the cipher..."
            is PuzzleMechanic.Riddle         -> "🤔 Your answer..."
            is PuzzleMechanic.MechanicalLock -> "🔒 Enter combination..."
            is PuzzleMechanic.DeductionGrid  -> "🧩 Enter solution..."
            is PuzzleMechanic.WireMaze       -> "🎯 Connect the wires!"
            is PuzzleMechanic.PatternLock    -> "🔓 Draw the pattern..."
            is PuzzleMechanic.ColorSequence  -> "🎨 Tap the colors..."
            else -> "🔑 Enter your answer..."
        }

        val isVisual = level.mechanic is PuzzleMechanic.WireMaze
                || level.mechanic is PuzzleMechanic.PatternLock
                || level.mechanic is PuzzleMechanic.ColorSequence

        btnSubmit.text = when {
            isMinigame(level.mechanic) && state.isSolved(level.levelId) -> "✓  SOLVED"
            isMinigame(level.mechanic) -> "↺  RESET"
            isVisual && state.isSolved(level.levelId) -> "✓  SOLVED"
            level.mechanic is PuzzleMechanic.WireMaze -> "↺  RESET"
            level.mechanic is PuzzleMechanic.PatternLock -> "↺  RETRY"
            level.mechanic is PuzzleMechanic.ColorSequence -> "🎨  REPLAY"
            else -> "▶  SUBMIT"
        }

        // Visual state for solved
        if (state.isSolved(level.levelId)) {
            answerInput.setBackgroundColor(Color.parseColor("#1A3320"))
            answerInput.hint = "✅ Solved!"
        } else {
            answerInput.setBackgroundColor(Color.parseColor("#263449"))
        }

        // hide all visual views first
        puzzleGrid.visibility = View.GONE
        patternLockView.visibility = View.GONE
        colorSequenceView.visibility = View.GONE
        puzzlePrompt.visibility = View.GONE
        answerInput.visibility = View.GONE

        when (val m = level.mechanic) {
            is PuzzleMechanic.WireMaze -> {
                puzzleGrid.visibility = View.VISIBLE
                puzzleGrid.gridCols = m.gridCols
                puzzleGrid.gridRows = m.gridRows
                puzzleGrid.items = m.items
                setupWireMaze(m, level)
                puzzleGrid.completed = state.isSolved(level.levelId)
            }
            is PuzzleMechanic.PatternLock -> {
                patternLockView.visibility = View.VISIBLE
                patternLockView.gridSize = m.gridSize
                patternLockView.correctPattern = m.pattern
                patternLockView.reset()
            }
            is PuzzleMechanic.ColorSequence -> {
                colorSequenceView.visibility = View.VISIBLE
                colorSequenceView.sequence = m.sequence
                colorSequenceView.panelColors = m.colors.map { Color.parseColor(it) }
                colorSequenceView.revealDelayMs = m.revealDelayMs
                if (!state.isSolved(level.levelId)) colorSequenceView.startReveal()
            }
            is PuzzleMechanic.VaultDial -> setupMinigame(level) {
                VaultDialView(this).apply {
                    setDial(m.combination.toIntArray())
                    onSolved = { completeLevel(level, "🔒 Vault cracked!") }
                }
            }
            is PuzzleMechanic.CipherWheel -> setupMinigame(level) {
                CipherWheelView(this).apply {
                    setPuzzle(m.cipherText, m.answer, m.shift)
                    onSolved = { completeLevel(level, "🔐 Cipher decoded!") }
                }
            }
            is PuzzleMechanic.GearAlignment -> setupMinigame(level) {
                GearView(this).apply {
                    setGears(m.gears.map { it.angle }.toIntArray(), m.gears.map { it.target }.toIntArray())
                    onSolved = { completeLevel(level, "⚙️ Mechanism aligned!") }
                }
            }
            is PuzzleMechanic.SlidingTile -> setupMinigame(level) {
                SlidingTileView(this).apply {
                    setPuzzle(m.size, m.tiles.toIntArray())
                    onSolved = { completeLevel(level, "📚 Tiles ordered!") }
                }
            }
            is PuzzleMechanic.TerminalHack -> setupMinigame(level) {
                TerminalHackView(this).apply {
                    setPuzzle(m.mode, m.input, m.answer, m.nodes.toIntArray())
                    onSolved = { completeLevel(level, "💻 Access granted!") }
                }
            }
            is PuzzleMechanic.PotionMix -> setupMinigame(level) {
                PotionMixView(this).apply {
                    setPuzzle(m.beakers.map { it.cap }.toIntArray(), m.target)
                    onSolved = { completeLevel(level, "⚗️ Elixir measured!") }
                }
            }
            is PuzzleMechanic.CoordinateMaze -> setupMinigame(level) {
                CoordinateMazeView(this).apply {
                    setGrid(m.grid)
                    onSolved = { completeLevel(level, "🧭 Destination reached!") }
                }
            }
            is PuzzleMechanic.LockPin -> setupMinigame(level) {
                LockPinView(this).apply {
                    setPins(m.pins.toIntArray())
                    onSolved = { completeLevel(level, "🔑 Lock picked!") }
                }
            }
            is PuzzleMechanic.NeonSimon -> setupMinigame(level) {
                NeonSimonView(this).apply {
                    setSequence(m.sequence.toIntArray(), m.speedMs)
                    onSolved = { completeLevel(level, "🕹️ Beat the machine!") }
                }
            }
            else -> {
                puzzlePrompt.visibility = View.VISIBLE
                answerInput.visibility = View.VISIBLE
                displayPuzzlePrompt(m)
            }
        }
        highlightStrip()
    }

    /** Render a visual figure for text-based puzzle mechanics. */
    private fun displayPuzzlePrompt(m: PuzzleMechanic) {
        puzzlePrompt.text = when (m) {
            is PuzzleMechanic.Cipher -> buildString {
                append("🔐  ")
                append(m.cipherText)
                append("\n")
                append("    — ${m.cipherType.uppercase()} CIPHER —")
            }
            is PuzzleMechanic.Riddle -> buildString {
                append("🤔  ")
                append("\"")
                append(m.riddleText)
                append("\"")
            }
            is PuzzleMechanic.DeductionGrid -> buildString {
                append("🧩  ")
                append(m.categories.joinToString(" · "))
                append("\n\n")
                append(m.clues.joinToString("\n") { "• $it" })
                append("\n\n📋 items: ")
                append(m.items.joinToString(", "))
            }
            is PuzzleMechanic.MechanicalLock -> buildString {
                append("🔒  ")
                append(m.lockType.uppercase())
                append(" LOCK\n\n")
                append("🔢 ")
                append("▣ ".repeat(m.combination.size).trim())
                append("\n   (")
                append(m.combination.size)
                append(" digits)")
            }
            is PuzzleMechanic.WireMaze -> ""
            is PuzzleMechanic.PatternLock -> buildString {
                append("🔓  Pattern Lock\\n\\n")
                append("Draw the secret pattern across the 3×3 grid")
            }
            is PuzzleMechanic.ColorSequence -> buildString {
                append("🎨  Color Sequence\\n\\n")
                append("Watch the panels light up, then\\ntap them in the same order")
            }
        else -> ""
        }
    }

    private fun setupWireMaze(m: PuzzleMechanic.WireMaze, level: PuzzleLevel) {
        val start = m.items.firstOrNull { it.type.contains("start") }?.let { Pair(it.xGrid, it.yGrid) } ?: Pair(0, 0)
        val end = m.items.firstOrNull { it.type.contains("end") }?.let { Pair(it.xGrid, it.yGrid) } ?: Pair(m.gridCols - 1, m.gridRows - 1)
        val existing = m.items.filter { it.type.contains("obstacle") }.map { Pair(it.xGrid, it.yGrid) }.toSet()
        val density = when (level.difficulty) { "Easy" -> 0.20f; "Medium" -> 0.25f; "Hard" -> 0.30f; else -> 0.35f }
        val obs = if (existing.isNotEmpty()) existing
                  else WireMazeEngine.generateObstacles(m.gridCols, m.gridRows, start, end, level.levelId.hashCode().toLong(), density)
        puzzleGrid.obstacles = obs
        wireMazePar = WireMazeEngine.shortestPathLength(m.gridCols, m.gridRows, start, end, obs)
        puzzleGrid.par = wireMazePar
        puzzleGrid.onCircuitComplete = { onWireMazeComplete(level) }
        puzzleGrid.onPathChange = { moves -> updateWireMazeInfo(level, moves) }
        updateWireMazeInfo(level, 0)
    }

    private fun onWireMazeComplete(level: PuzzleLevel) {
        if (state.isSolved(level.levelId)) return
        completeLevel(level, "💡 Circuit complete!")
    }

    private fun updateWireMazeInfo(level: PuzzleLevel, moves: Int) {
        val solved = if (state.isSolved(level.levelId)) " ✓" else ""
        puzzleInfo.text = "🔌  ${level.difficulty}$solved  ·  wire maze  ·  moves $moves / par $wireMazePar  ·  ⭐${state.score}"
    }

    private fun completeLevel(level: PuzzleLevel, toastPrefix: String) {
        if (state.isSolved(level.levelId)) return
        state.streak++
        val basePoints = when (level.difficulty) {
            "Easy" -> 10; "Medium" -> 20; "Hard" -> 35; else -> 50
        }
        val streakBonus = (state.streak - 1) * 5
        val hintPenalty = hintTier.getOrDefault(level.levelId, 0) * 3
        val earned = (basePoints + streakBonus - hintPenalty).coerceAtLeast(5)
        state.score += earned
        state.markSolved(level.levelId)
        GameStats.recordSolve(this, "escape_room", earned)
        val streakMsg = if (state.streak >= 3) " 🔥${state.streak}x streak!" else ""
        Toast.makeText(this, "$toastPrefix +$earned pts$streakMsg", Toast.LENGTH_SHORT).show()
        answerInput.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        celebration.burst(40)
        SoundFx.win()
        lifecycleScope.launch {
            storage.saveState(state)
            syncToCloud()
        }

        if (currentRoom?.solvedCount(state.solvedLevels) == 10) {
            celebration.burst(100)
            AlertDialog.Builder(this, R.style.HintDialog)
                .setTitle("🏆 Room Complete!")
                .setMessage("${currentRoom?.roomName} cleared!\nScore: ${state.score} pts\nStreak: ${state.streak}\n\nYou're a puzzle master!")
                .setPositiveButton("Next Room") { _, _ -> showRoomPicker() }
                .show()
            loadCurrentLevel()
        } else {
            // Auto-advance to the next level after the completion toast is visible.
            // Capture the solved level so a manual "Next" tap during the delay can't double-advance.
            val fromLevel = currentLevelNum
            rootLayout.postDelayed({ if (currentLevelNum == fromLevel) navigate(+1) }, 900)
        }
    }

    private fun isMinigame(m: PuzzleMechanic): Boolean = when (m) {
        is PuzzleMechanic.VaultDial, is PuzzleMechanic.CipherWheel, is PuzzleMechanic.GearAlignment,
        is PuzzleMechanic.SlidingTile, is PuzzleMechanic.TerminalHack, is PuzzleMechanic.PotionMix,
        is PuzzleMechanic.CoordinateMaze, is PuzzleMechanic.LockPin, is PuzzleMechanic.NeonSimon -> true
        else -> false
    }

    /** Mount (or clear) a minigame view into the puzzle stage for the current level. */
    private fun setupMinigame(level: PuzzleLevel, build: () -> View) {
        minigameView?.let { puzzleStage.removeView(it) }
        puzzlePrompt.visibility = View.GONE
        answerInput.visibility = View.GONE
        if (state.isSolved(level.levelId)) {
            puzzlePrompt.visibility = View.VISIBLE
            puzzlePrompt.text = "✅  Solved!"
            minigameView = null
            return
        }
        val v = build()
        v.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        minigameView = v
        puzzleStage.addView(v)
    }

    private fun checkAnswer() {
        val level = currentRoom?.levelNumber(currentLevelNum) ?: return
        SoundFx.click()
        val input = answerInput.text.toString().trim()
        val correct = when (val m = level.mechanic) {
            is PuzzleMechanic.Cipher -> input.equals(m.answer, ignoreCase = true)
            is PuzzleMechanic.Riddle          -> input.equals(m.answer, ignoreCase = true)
            is PuzzleMechanic.MechanicalLock  -> input == m.combination.joinToString("")
            is PuzzleMechanic.DeductionGrid   -> input.replace(" ", "").equals(
                m.solution.values.joinToString(""), ignoreCase = true)
            is PuzzleMechanic.WireMaze        -> true
            is PuzzleMechanic.PatternLock    -> true
            is PuzzleMechanic.ColorSequence  -> true
            else -> false
        }

        state.totalAttempts++
        if (correct) {
            completeLevel(level, "🎉 Correct!")
        } else {
            state.streak = 0
            answerInput.performHapticFeedback(HapticFeedbackConstants.REJECT)
            answerInput.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake_x))
            SoundFx.loss()
            answerInput.setBackgroundColor(Color.parseColor("#331A1A"))
            answerInput.postDelayed({
                if (currentRoom?.levelNumber(currentLevelNum)?.let { !state.isSolved(it.levelId) } == true)
                    answerInput.setBackgroundColor(Color.parseColor("#263449"))
            }, 400)
            Toast.makeText(this, "❌ Not quite. Streak reset.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showHint() {
        val level = currentRoom?.levelNumber(currentLevelNum) ?: return

        // Build the full hint chain: level hints + mechanic-specific fallback
        val chain = level.hints.toMutableList()
        if (chain.isEmpty()) {
            val fallback = when (val m = level.mechanic) {
                is PuzzleMechanic.Cipher         -> listOf(m.hint)
                is PuzzleMechanic.Riddle         -> listOf(m.hint)
                is PuzzleMechanic.MechanicalLock -> listOf("Enter the 3-digit combination.")
                is PuzzleMechanic.DeductionGrid  -> listOf("Use the clues to match each item.")
                is PuzzleMechanic.WireMaze       -> listOf("Drag the pieces to connect start → end.")
                is PuzzleMechanic.PatternLock    -> listOf("Drag your finger across the dots in order.")
                is PuzzleMechanic.ColorSequence  -> listOf("Watch the panels flash, then tap the sequence back.")
                else -> emptyList()
            }
            chain.addAll(fallback)
        }

        // Reveal hints one tier at a time
        val used = hintTier[level.levelId] ?: 0
        if (used >= chain.size) {
            AlertDialog.Builder(this, R.style.HintDialog)
                .setTitle("💡 No more hints")
                .setMessage("You've seen all ${chain.size} hints. Keep trying — you're close!")
                .setPositiveButton("OK", null).show()
            return
        }

        val hint = chain[used]
        hintTier[level.levelId] = used + 1
        state.hintsUsed++

        AlertDialog.Builder(this, R.style.HintDialog)
            .setTitle("💡 Hint ${used + 1} of ${chain.size}")
            .setMessage(hint)
            .setPositiveButton(if (used + 1 < chain.size) "More hints" else "Got it!", null)
            .show()
    }

    private fun isLevelUnlocked(room: PuzzleRoom, levelNum: Int): Boolean {
        if (levelNum <= 1) return true
        return room.levelNumber(levelNum - 1)?.let { state.isSolved(it.levelId) } == true
    }

    private fun navigate(delta: Int) {
        val room = currentRoom ?: return
        val target = (currentLevelNum + delta).coerceIn(1, 10)
        if (target > currentLevelNum && !isLevelUnlocked(room, target)) {
            Toast.makeText(this, "🔒 Complete the current level first!", Toast.LENGTH_SHORT).show()
            return
        }
        currentLevelNum = target
        loadCurrentLevel()
    }

    private fun showRoomPicker() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_rooms)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val list = dialog.findViewById<LinearLayout>(R.id.room_list)
        rooms.forEach { room ->
            val row = buildRoomRow(room)
            row.setOnClickListener { SoundFx.click(); dialog.dismiss(); loadRoom(room) }
            list.addView(row)
        }
        dialog.findViewById<TextView>(R.id.room_cancel).setOnClickListener {
            SoundFx.click(); dialog.dismiss()
        }

        dialog.show()
        val root = dialog.findViewById<View>(R.id.dialog_rooms_root)
        root.scaleX = 0.85f
        root.scaleY = 0.85f
        root.alpha = 0f
        root.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(220L).start()
    }

    private fun buildRoomRow(room: PuzzleRoom): View {
        val solved = room.solvedCount(state.solvedLevels)
        val total = room.levels.size

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val tv = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
            setBackgroundResource(tv.resourceId)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(TextView(this).apply {
            text = roomIcon(room.theme)
            textSize = 18f
        })
        top.addView(TextView(this).apply {
            text = room.roomName
            textSize = 15f
            setTextColor(Color.parseColor("#E2E8F0"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
            }
        })
        top.addView(TextView(this).apply {
            text = "$solved/$total"
            textSize = 13f
            setTextColor(if (solved > 0) Color.parseColor("#22D3EE") else Color.parseColor("#64748B"))
        })

        val track = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 100f
            setBackgroundColor(Color.parseColor("#1AFFFFFF"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(4)).apply {
                topMargin = dp(10)
            }
        }
        val pct = if (total == 0) 0 else (solved * 100 / total).coerceIn(0, 100)
        track.addView(View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#22D3EE"), Color.parseColor("#3B82F6"))
            ).apply { cornerRadius = dp(2).toFloat() }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, pct.toFloat())
        })

        row.addView(top)
        row.addView(track)
        return row
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun buildLevelStrip(room: PuzzleRoom) {
        levelStrip.removeAllViews(); stripScroll.visibility = View.VISIBLE
        for (i in 1..10) {
            val btn = Button(this).apply {
                text = "$i"
                textSize = 13f
                setPadding(16, 12, 16, 12)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                minWidth = 0; minimumWidth = 56
                (layoutParams as? LinearLayout.LayoutParams)?.apply {
                    width = LinearLayout.LayoutParams.WRAP_CONTENT
                    height = LinearLayout.LayoutParams.WRAP_CONTENT
                    marginEnd = 6
                }
                setOnClickListener {
                    if (!isLevelUnlocked(room, i)) {
                        Toast.makeText(this@MainActivity, "🔒 Complete level ${i - 1} first!", Toast.LENGTH_SHORT).show()
                    } else {
                        currentLevelNum = i
                        loadCurrentLevel()
                    }
                }
            }
            levelStrip.addView(btn)
        }
        highlightStrip()
    }

    private fun highlightStrip() {
        for (i in 0 until levelStrip.childCount) {
            val btn = levelStrip.getChildAt(i) as? Button ?: continue
            val lev = currentRoom?.levelNumber(i + 1)
            val locked = i > 0 && currentRoom?.levelNumber(i)?.let { !state.isSolved(it.levelId) } == true
            val solved = state.isSolved(lev?.levelId ?: "")
            val isActive = i + 1 == currentLevelNum

            val textColor = when {
                isActive -> Color.WHITE
                solved   -> Color.parseColor("#0B1120")
                locked   -> Color.parseColor("#555555")
                else     -> Color.parseColor("#B0B8C4")
            }
            btn.text = when {
                solved   -> "✓"
                locked   -> "🔒"
                isActive -> "▶${i + 1}"
                else     -> "${i + 1}"
            }
            btn.setTextColor(textColor)

            btn.background = GradientDrawable().apply {
                cornerRadius = 20f
                when {
                    isActive -> {
                        // Active level: pink→violet gradient + gold glow ring
                        setColors(intArrayOf(Color.parseColor("#FF4081"), Color.parseColor("#8B5CF6")))
                        setStroke(3, colorGold)
                    }
                    solved -> {
                        // Solved level: light-green→green gradient + soft glow
                        setColors(intArrayOf(Color.parseColor("#34D399"), colorSolved))
                        setStroke(2, Color.parseColor("#4400E676"))
                    }
                    locked -> setColor(colorLocked)
                    else   -> setColor(colorOpen)
                }
            }
        }
    }

    // Mechanic icon
    private fun mechanicIcon(m: PuzzleMechanic): String = when (m) {
        is PuzzleMechanic.WireMaze       -> "🔌"
        is PuzzleMechanic.Cipher         -> "🔐"
        is PuzzleMechanic.Riddle         -> "🤔"
        is PuzzleMechanic.DeductionGrid  -> "🧩"
        is PuzzleMechanic.MechanicalLock -> "🔒"
        is PuzzleMechanic.PatternLock    -> "🔓"
        is PuzzleMechanic.ColorSequence  -> "🎨"
        is PuzzleMechanic.VaultDial      -> "🔒"
        is PuzzleMechanic.CipherWheel    -> "🔐"
        is PuzzleMechanic.GearAlignment  -> "⚙️"
        is PuzzleMechanic.SlidingTile    -> "📚"
        is PuzzleMechanic.TerminalHack   -> "💻"
        is PuzzleMechanic.PotionMix      -> "⚗️"
        is PuzzleMechanic.CoordinateMaze -> "🧭"
        is PuzzleMechanic.LockPin        -> "🔑"
        is PuzzleMechanic.NeonSimon      -> "🕹️"
    }

    private fun roomIcon(theme: String): String = when {
        // Matches the web ROOM_EMOJI order (bank, lab, telescope, …).
        theme.contains("industrial")                             -> "🏦"
        theme.contains("laboratory") || theme.contains("lab")    -> "🔬"
        theme.contains("observatory")                            -> "🔭"
        theme.contains("study")                                  -> "📚"
        theme.contains("vault")                                  -> "🔐"
        theme.contains("crypto")                                 -> "🔐"
        theme.contains("alchemy")                                -> "⚗️"
        theme.contains("cartography")                            -> "🗺️"
        theme.contains("forge")                                  -> "🔑"
        theme.contains("arcade")                                 -> "🕹️"
        else -> "🚪"
    }

    // ── Themed backgrounds ──────────────────────────────────────────────

    private fun applyTheme(room: PuzzleRoom, level: PuzzleLevel) {
        // Animated room background
        val (top, bottom, accent) = roomThemeColors(room)
        rootLayout.background = AnimatedBackgroundDrawable(top, bottom, accent)

        // Mechanic card border
        val cardBorder = when (level.mechanic) {
            is PuzzleMechanic.WireMaze       -> R.drawable.card_wire
            is PuzzleMechanic.Cipher         -> R.drawable.card_cipher
            is PuzzleMechanic.Riddle         -> R.drawable.card_riddle
            is PuzzleMechanic.DeductionGrid  -> R.drawable.card_grid
            is PuzzleMechanic.MechanicalLock -> R.drawable.card_lock
            is PuzzleMechanic.PatternLock    -> R.drawable.card_lock
            is PuzzleMechanic.ColorSequence  -> R.drawable.card_riddle
            else -> R.drawable.card_cipher
        }
        headerCard?.background = resources.getDrawable(cardBorder, theme)
    }

    private fun roomThemeColors(room: PuzzleRoom): Triple<Int, Int, Int> = when {
        room.theme.contains("industrial") || room.roomId.contains("vault") ->
            Triple(Color.parseColor("#0F1A2E"), Color.parseColor("#1A1035"), Color.parseColor("#FFD740"))
        room.theme.contains("lab") || room.roomId.contains("lab") ->
            Triple(Color.parseColor("#0A1428"), Color.parseColor("#001B33"), Color.parseColor("#00E5FF"))
        room.theme.contains("observatory") || room.roomId.contains("observatory") ->
            Triple(Color.parseColor("#0B0B1E"), Color.parseColor("#1A0B2E"), Color.parseColor("#B388FF"))
        room.theme.contains("study") || room.roomId.contains("study") ->
            Triple(Color.parseColor("#1E1408"), Color.parseColor("#2E1A0A"), Color.parseColor("#FFB300"))
        room.theme.contains("vault") || room.roomId.contains("crypto") ->
            Triple(Color.parseColor("#120A1E"), Color.parseColor("#1A0A2E"), Color.parseColor("#00E5FF"))
        room.theme.contains("alchemy") ->
            Triple(Color.parseColor("#0A1A12"), Color.parseColor("#1A140A"), Color.parseColor("#69F0AE"))
        room.theme.contains("cartography") ->
            Triple(Color.parseColor("#14100A"), Color.parseColor("#0A1A2E"), Color.parseColor("#4FC3F7"))
        room.theme.contains("forge") ->
            Triple(Color.parseColor("#1A0E02"), Color.parseColor("#0A0806"), Color.parseColor("#FFA000"))
        room.theme.contains("arcade") ->
            Triple(Color.parseColor("#16031A"), Color.parseColor("#060012"), Color.parseColor("#FF40C4"))
        else ->
            Triple(Color.parseColor("#0B1120"), Color.parseColor("#161B22"), Color.parseColor("#00E5FF"))
    }

    // ── Restart ────────────────────────────────────────────────────────

    private fun showRestartDialog() {
        AlertDialog.Builder(this, R.style.HintDialog)
            .setTitle("🔄 Restart Game?")
            .setMessage("This will erase all progress:\n\n• Solved levels\n• Score (${state.score} pts)\n• Streak (${state.streak}x)\n• Hints used\n\nThis cannot be undone.")
            .setPositiveButton("Yes, restart") { _, _ -> resetGame() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resetGame() {
        state = PlayerState()
        puzzleGrid.completed = false
        lifecycleScope.launch {
            storage.saveState(state)
            bootstrap()
        }
        Toast.makeText(this, "🎮 Progress cleared!", Toast.LENGTH_SHORT).show()
    }
}
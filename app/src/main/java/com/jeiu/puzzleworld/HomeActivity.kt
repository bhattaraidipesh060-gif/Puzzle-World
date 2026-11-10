package com.jeiu.puzzleworld

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

/**
 * Home hub — mirrors the PuzzleWorld web portal: app bar (welcome + score +
 * sound + avatar), tabbed dashboard (All Games / Leaderboard / My Stats /
 * Settings), search that filters both games and rooms, category chips, a daily
 * hero with Play Now + Surprise me, games & rooms grids, and a mini leaderboard.
 */
class HomeActivity : AppCompatActivity() {

    companion object {
        private const val PREFS = "puzzleworld_account"
        private const val KEY_USERNAME = "username"
        private const val STATS_PREFS = "puzzleworld_stats"
    }

    private var activeCat = "all"
    private var query = ""
    private var username = "Player"
    private var rank: Int? = null
    private lateinit var adapter: GameCardAdapter
    private lateinit var roomAdapter: GameCardAdapter
    private lateinit var world: HttpWorldService

    private data class RoomCard(
        val id: String, val name: String, val icon: String,
        val artRes: Int, val tagline: String, var solved: Int = 0
    )

    // Themed room icons mirror the web ROOM_EMOJI order exactly.
    private val roomCards = listOf(
        RoomCard("room_01_vault", "The First Vault", "🏦", R.drawable.ps_art_cyan,
            "Crack the codes of an abandoned underground vault."),
        RoomCard("room_02_cipher_lab", "The Cipher Laboratory", "🔬", R.drawable.ps_art_purple,
            "Decode cryptic notes in a mysterious laboratory."),
        RoomCard("room_03_observatory", "The Clockwork Observatory", "🔭", R.drawable.ps_art_emerald,
            "Stop the celestial clock before time runs out."),
        RoomCard("room_04_study", "The Puzzle Master's Study", "📚", R.drawable.ps_art_amber,
            "Solve the Puzzle Master's greatest brain-teasers."),
        RoomCard("room_05_crypto", "The Cryptographer's Cache", "🔐", R.drawable.ps_art_cyan,
            "Crack layered encryptions to unlock the cache."),
        RoomCard("room_06_alchemy", "The Alchemist's Laboratory", "⚗️", R.drawable.ps_art_purple,
            "Complete the Great Work — puzzles into gold."),
        RoomCard("room_07_cartography", "The Cartographer's Vault", "🗺️", R.drawable.ps_art_emerald,
            "Chart lost maps through riddles and ciphers."),
        RoomCard("room_08_locksmith", "The Locksmith's Forge", "🔑", R.drawable.ps_art_amber,
            "Crack the forged locks, claim the master key."),
        RoomCard("room_09_arcade", "The Neon Arcade", "🕹️", R.drawable.ps_art_cyan,
            "Beat the machine at its own flashing game.")
    )

    private val allGames = listOf(
        GameCardAdapter.GameItem("rooms", "🚪", "Escape Rooms",
            "9 themed rooms · 90 brain-melting levels.", "EASY", R.color.ps_emerald,
            R.drawable.ps_art_cyan, "escape", "escape rooms vault rooms"),
        GameCardAdapter.GameItem("sudoku", "🔢", "Sudoku",
            "Classic 9×9 logic with notes, undo, hints.", "MEDIUM", R.color.ps_amber,
            R.drawable.ps_art_purple, "puzzle", "sudoku logic numbers grid"),
        GameCardAdapter.GameItem("word", "🔤", "Word Descrambler",
            "Unscramble words against the clock. Streaks multiply.", "MEDIUM", R.color.ps_amber,
            R.drawable.ps_art_emerald, "word", "word descrambler letters scramble"),
        GameCardAdapter.GameItem("daily", "📅", "Daily Challenge",
            "One fresh puzzle every day. Compete globally.", "HARD", R.color.ps_rose,
            R.drawable.ps_art_amber, "daily", "daily challenge today streak")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        SoundFx.init(this)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.home_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        username = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_USERNAME, "Player") ?: "Player"
        world = HttpWorldService("http://10.0.2.2:3000", SessionManager.userId() ?: username)

        val welcome = findViewById<TextView>(R.id.home_welcome)
        val avatar = findViewById<TextView>(R.id.home_logout)
        val soundBtn = findViewById<TextView>(R.id.home_sound)
        welcome.text = "Welcome, $username 👋"
        avatar.text = username.trim().take(1).uppercase().ifEmpty { "P" }

        // ---- Sound toggle ----
        fun syncSound() {
            val m = SoundFx.isMuted()
            soundBtn.text = if (m) "🔇" else "🔔"
            soundBtn.alpha = if (m) 0.45f else 1f
            findViewById<TextView>(R.id.settings_sound).text = if (m) "🔇 Off" else "🔊 On"
        }
        syncSound()
        soundBtn.setOnClickListener { toggleSound(); syncSound() }
        findViewById<TextView>(R.id.settings_sound).setOnClickListener { toggleSound(); syncSound() }

        // ---- Avatar menu ----
        avatar.setOnClickListener {
            SoundFx.click()
            showProfileMenu()
        }

        findViewById<TextView>(R.id.home_terms).setOnClickListener {
            SoundFx.click(); go(TermsActivity::class.java)
        }
        findViewById<TextView>(R.id.settings_terms).setOnClickListener {
            SoundFx.click(); go(TermsActivity::class.java)
        }
        findViewById<TextView>(R.id.settings_logout).setOnClickListener { signOut() }
        findViewById<TextView>(R.id.settings_reset).setOnClickListener { confirmReset() }
        findViewById<TextView>(R.id.settings_account).text = username
        findViewById<View>(R.id.stat_achievements_card).setOnClickListener {
            SoundFx.click(); go(AchievementsActivity::class.java)
        }

        val spanCount = if (resources.configuration.screenWidthDp >= 600) 3 else 2

        // ---- Games grid ----
        val grid = findViewById<RecyclerView>(R.id.games_grid)
        grid.layoutManager = GridLayoutManager(this, spanCount)
        grid.addItemDecoration(GridSpacingItemDecoration(dp(12)))
        adapter = GameCardAdapter(emptyList()) { item ->
            SoundFx.click()
            when (item.id) {
                "rooms" -> go(MainActivity::class.java)
                "sudoku" -> go(SudokuActivity::class.java)
                "word" -> go(WordDescramblerActivity::class.java)
                "daily" -> go(DailyChallengeActivity::class.java)
            }
        }
        grid.adapter = adapter
        applyFilter()

        // ---- Escape-Rooms grid ----
        val roomsGrid = findViewById<RecyclerView>(R.id.rooms_grid)
        roomsGrid.layoutManager = GridLayoutManager(this, spanCount)
        roomsGrid.addItemDecoration(GridSpacingItemDecoration(dp(12)))
        roomAdapter = GameCardAdapter(emptyList()) { item -> openRoom(item.id) }
        roomsGrid.adapter = roomAdapter
        rebuildRooms()

        // ---- Hero / surprise ----
        findViewById<View>(R.id.hero_daily).setOnClickListener {
            SoundFx.click(); go(DailyChallengeActivity::class.java)
        }
        findViewById<View>(R.id.btn_surprise).setOnClickListener { surpriseMe() }

        // ---- Tabs + nav + leaderboard ----
        findViewById<TextView>(R.id.tab_games).setOnClickListener { switchTab("games") }
        findViewById<TextView>(R.id.tab_board).setOnClickListener { switchTab("board") }
        findViewById<TextView>(R.id.tab_stats).setOnClickListener { switchTab("stats") }
        findViewById<TextView>(R.id.tab_settings).setOnClickListener { switchTab("settings") }
        findViewById<TextView>(R.id.nav_games).setOnClickListener { switchTab("games") }
        findViewById<TextView>(R.id.nav_board).setOnClickListener { switchTab("board") }
        findViewById<TextView>(R.id.nav_stats).setOnClickListener { switchTab("stats") }
        findViewById<TextView>(R.id.nav_settings).setOnClickListener { switchTab("settings") }
        findViewById<TextView>(R.id.lb_refresh).setOnClickListener {
            SoundFx.click(); lifecycleScope.launch { loadLeaderboard() }
        }
        findViewById<TextView>(R.id.mini_viewall).setOnClickListener {
            SoundFx.nav(); switchTab("board")
        }

        // ---- Search + chips ----
        val search = findViewById<EditText>(R.id.home_search)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                query = s?.toString()?.trim()?.lowercase() ?: ""
                applyFilter()
                rebuildRooms()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        findViewById<TextView>(R.id.chip_all).setOnClickListener { selectChip("all") }
        findViewById<TextView>(R.id.chip_escape).setOnClickListener { selectChip("escape") }
        findViewById<TextView>(R.id.chip_puzzle).setOnClickListener { selectChip("puzzle") }
        findViewById<TextView>(R.id.chip_word).setOnClickListener { selectChip("word") }
        findViewById<TextView>(R.id.chip_daily).setOnClickListener { selectChip("daily") }

        refreshStats()
    }

    override fun onResume() {
        super.onResume()
        refreshStats()
        lifecycleScope.launch {
            loadSolvedCounts()
            loadLeaderboard()
            loadBadgeCount()
        }
    }

    // ── Tabs ────────────────────────────────────────────────────────────
    private fun switchTab(name: String) {
        SoundFx.nav()
        val panels = mapOf(
            "games" to R.id.panel_games, "board" to R.id.panel_board,
            "stats" to R.id.panel_stats, "settings" to R.id.panel_settings
        )
        val tabs = mapOf(
            "games" to R.id.tab_games, "board" to R.id.tab_board,
            "stats" to R.id.tab_stats, "settings" to R.id.tab_settings
        )
        val navs = mapOf(
            "games" to R.id.nav_games, "board" to R.id.nav_board,
            "stats" to R.id.nav_stats, "settings" to R.id.nav_settings
        )
        panels.forEach { (n, id) ->
            findViewById<View>(id).visibility = if (n == name) View.VISIBLE else View.GONE
        }
        tabs.forEach { (n, id) ->
            val tv = findViewById<TextView>(id)
            val on = n == name
            tv.setBackgroundResource(if (on) R.drawable.ps_chip_active else 0)
            tv.setTextColor(getColor(if (on) R.color.ps_on_accent else R.color.ps_muted))
        }
        navs.forEach { (n, id) ->
            findViewById<TextView>(id).setTextColor(
                getColor(if (n == name) R.color.ps_cyan else R.color.ps_muted)
            )
        }
        if (name == "stats") lifecycleScope.launch { loadSolvedCounts() }
    }

    // ── Sound ───────────────────────────────────────────────────────────
    private fun toggleSound() {
        val next = !SoundFx.isMuted()
        SoundFx.setMuted(this, next)
        if (!next) SoundFx.click()
    }

    // ── Stats dashboard ─────────────────────────────────────────────────
    private fun refreshStats() {
        findViewById<TextView>(R.id.stat_score).text = GameStats.totalScore(this).toString()
        findViewById<TextView>(R.id.stat_solved).text = GameStats.totalSolved(this).toString()
        findViewById<TextView>(R.id.stat_achievements).text =
            "${Achievements.unlockedCount(this)}/${Achievements.ALL.size}"
        findViewById<TextView>(R.id.home_score).text = "⭐ ${GameStats.totalScore(this)}"
    }

    private suspend fun loadSolvedCounts() {
        val storage = LocalStorage(applicationContext)
        storage.seedBuiltInRooms()
        val state = storage.loadState()
        roomCards.forEach { rc ->
            rc.solved = state.solvedLevels.keys.count { it.startsWith("${rc.id}_") }
        }
        rebuildRooms()
        renderStats(state)
    }

    private fun renderStats(state: PlayerState) {
        val container = findViewById<LinearLayout>(R.id.stats_detail)
        container.removeAllViews()
        val solved = GameStats.totalSolved(this)
        val score = GameStats.totalScore(this)
        container.addView(statBlock("🧩 Puzzles solved", "$solved / 90"))
        container.addView(progressBar((solved * 100 / 90).coerceIn(0, 100)))
        container.addView(statBlock("⭐ Total score", score.toString()))
        container.addView(statBlock("🔥 Best streak", state.streak.toString()))
        container.addView(statBlock("🎮 Games played", state.totalAttempts.toString()))
        container.addView(statBlock("💡 Hints used", state.hintsUsed.toString()))
        container.addView(statBlock("🏆 Global rank", rank?.let { "#$it" } ?: "—"))
        container.addView(statBlock("👤 Account", username))
    }

    private fun statBlock(label: String, value: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.ps_card)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(10)
            layoutParams = lp
        }
        val l = TextView(this).apply {
            text = label; textSize = 14f; setTextColor(getColor(R.color.ps_muted))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            this.layoutParams = lp
        }
        val v = TextView(this).apply {
            text = value; textSize = 16f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(getColor(R.color.ps_text))
        }
        row.addView(l); row.addView(v)
        return row
    }

    private fun progressBar(pct: Int): View {
        val track = LinearLayout(this).apply {
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8)
            )
            lp.topMargin = dp(10)
            layoutParams = lp
            setBackgroundColor(0x22FFFFFF)
        }
        val fill = View(this).apply {
            setBackgroundColor(getColor(R.color.ps_cyan))
            val lp = LinearLayout.LayoutParams(
                (dp(320) * pct / 100).coerceAtLeast(dp(8)), ViewGroup.LayoutParams.MATCH_PARENT
            )
            layoutParams = lp
        }
        track.addView(fill)
        return track
    }

    // ── Badges ─────────────────────────────────────────────────────────
    private suspend fun loadBadgeCount() {
        val profile = world.fetchProfile()
        if (profile != null) {
            findViewById<TextView>(R.id.stat_achievements).text =
                "${profile.unlockedCount}/${profile.totalBadges}"
        }
    }

    // ── Rooms + filtering ───────────────────────────────────────────────
    private fun rebuildRooms() {
        val items = roomCards.filter {
            query.isEmpty() || it.name.contains(query, true) || it.id.contains(query, true)
        }.map { rc ->
            GameCardAdapter.GameItem(
                id = rc.id, emoji = rc.icon, title = rc.name, desc = rc.tagline,
                badgeText = "${rc.solved}/10",
                badgeColorRes = when {
                    rc.solved >= 10 -> R.color.ps_emerald
                    rc.solved > 0 -> R.color.ps_amber
                    else -> R.color.ps_cyan
                },
                artRes = rc.artRes, cat = "escape",
                searchText = "${rc.name} ${rc.id}"
            )
        }
        roomAdapter.update(items)
    }

    private fun applyFilter() {
        val filtered = allGames.filter {
            (activeCat == "all" || activeCat == it.cat) &&
                (query.isEmpty() || it.searchText.contains(query, true) || it.title.contains(query, true))
        }
        adapter.update(filtered)
    }

    private fun selectChip(cat: String) {
        SoundFx.click()
        activeCat = cat
        val chips = mapOf(
            "all" to R.id.chip_all, "escape" to R.id.chip_escape,
            "puzzle" to R.id.chip_puzzle, "word" to R.id.chip_word, "daily" to R.id.chip_daily
        )
        chips.forEach { (c, id) ->
            val tv = findViewById<TextView>(id) ?: return@forEach
            val on = c == cat
            tv.setBackgroundResource(if (on) R.drawable.ps_chip_active else R.drawable.ps_chip)
            tv.setTextColor(getColor(if (on) R.color.ps_on_accent else R.color.ps_muted))
            tv.typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        applyFilter()
    }

    private fun surpriseMe() {
        SoundFx.nav()
        val pick = roomCards.randomOrNull() ?: return
        openRoom(pick.id)
    }

    // ── Leaderboard ─────────────────────────────────────────────────────
    private suspend fun loadLeaderboard() {
        val me = SessionManager.userId() ?: username
        val entries = world.fetchLeaderboard()
        if (entries.isNotEmpty()) {
            rank = entries.indexOfFirst { it.userId == me || it.username == username }
                .let { if (it >= 0) it + 1 else null }
            renderLeaderboard(entries, me)
        } else {
            // Offline fallback — local scoreboard.
            rank = null
            val local = GameStats.leaderboard(this).mapIndexed { i, (n, s) ->
                LeaderboardEntry("local$i", n, 0, s)
            }
            renderLeaderboard(local, username)
            findViewById<TextView>(R.id.lb_status).apply {
                text = "Offline — showing local scores"
                visibility = View.VISIBLE
            }
        }
    }

    private fun renderLeaderboard(entries: List<LeaderboardEntry>, meId: String) {
        val list = findViewById<LinearLayout>(R.id.lb_list)
        val status = findViewById<TextView>(R.id.lb_status)
        list.removeAllViews()
        if (entries.isEmpty()) {
            status.text = "No scores yet — be the first!"
            status.visibility = View.VISIBLE
            findViewById<LinearLayout>(R.id.mini_board).removeAllViews()
            return
        }
        status.visibility = View.GONE
        val medals = listOf("🥇", "🥈", "🥉")
        entries.take(10).forEachIndexed { i, e ->
            val rankLabel = if (i < 3) medals[i] else "#${i + 1}"
            list.addView(lbRow(rankLabel, e.username, "${e.solvedCount} solved", e.score.toString(), e.userId == meId || e.username == username))
        }
        renderMini(entries.take(5), meId)
    }

    private fun renderMini(entries: List<LeaderboardEntry>, meId: String) {
        val box = findViewById<LinearLayout>(R.id.mini_board)
        box.removeAllViews()
        if (entries.isEmpty()) {
            box.addView(TextView(this).apply {
                text = "No scores yet — be the first!"
                textSize = 13f; setTextColor(getColor(R.color.ps_muted))
            })
            return
        }
        val medals = listOf("🥇", "🥈", "🥉")
        entries.forEachIndexed { i, e ->
            val rankLabel = if (i < 3) medals[i] else "#${i + 1}"
            box.addView(miniRow(rankLabel, e.username, "${e.solvedCount} solved", e.userId == meId || e.username == username))
        }
    }

    private fun lbRow(rankLabel: String, name: String, solved: String, score: String, isMe: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.ps_card)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(8)
            layoutParams = lp
        }
        row.addView(TextView(this).apply {
            text = rankLabel; textSize = 16f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        row.addView(TextView(this).apply {
            text = name; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(getColor(if (isMe) R.color.ps_cyan else R.color.ps_text))
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {
            text = solved; textSize = 11f; setTextColor(getColor(R.color.ps_muted))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(10) }
        })
        row.addView(TextView(this).apply {
            text = score; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(getColor(R.color.ps_cyan))
        })
        return row
    }

    private fun miniRow(rankLabel: String, name: String, solved: String, isMe: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(7), 0, dp(7))
        }
        row.addView(TextView(this).apply {
            text = rankLabel; textSize = 16f
            layoutParams = LinearLayout.LayoutParams(dp(38), ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        row.addView(TextView(this).apply {
            text = name; textSize = 13.5f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(getColor(if (isMe) R.color.ps_cyan else R.color.ps_text))
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {
            text = solved; textSize = 11.5f; setTextColor(getColor(R.color.ps_muted))
        })
        return row
    }

    // ── Profile menu ─────────────────────────────────────────────────────
    private fun showProfileMenu() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_profile)
        dialog.window?.setBackgroundDrawable(ColorDrawable(0x00000000))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.findViewById<TextView>(R.id.profile_name).text = username
        dialog.findViewById<TextView>(R.id.profile_avatar).text =
            username.trim().take(1).uppercase().ifEmpty { "P" }

        dialog.findViewById<View>(R.id.profile_stats).setOnClickListener {
            SoundFx.click(); dialog.dismiss(); switchTab("stats")
        }
        dialog.findViewById<View>(R.id.profile_settings).setOnClickListener {
            SoundFx.click(); dialog.dismiss(); switchTab("settings")
        }
        dialog.findViewById<View>(R.id.profile_signout).setOnClickListener {
            SoundFx.click(); dialog.dismiss(); signOut()
        }
        dialog.findViewById<View>(R.id.profile_terms).setOnClickListener {
            SoundFx.click(); dialog.dismiss(); go(TermsActivity::class.java)
        }

        dialog.show()

        // Pop-in entrance
        val root = dialog.findViewById<View>(R.id.dialog_profile_root)
        root.scaleX = 0.82f
        root.scaleY = 0.82f
        root.alpha = 0f
        root.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(220L).start()
    }

    // ── Reset / sign-out / navigation ───────────────────────────────────
    private fun confirmReset() {
        SoundFx.click()
        AlertDialog.Builder(this)
            .setTitle("Reset progress?")
            .setMessage("This clears your score, streaks, and solved levels. It can't be undone.")
            .setPositiveButton("Reset") { _, _ ->
                getSharedPreferences(STATS_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
                lifecycleScope.launch {
                    LocalStorage(applicationContext).saveState(PlayerState())
                    refreshStats()
                    loadSolvedCounts()
                }
                SoundFx.success()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun signOut() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_USERNAME, "Guest").apply()
        SessionManager.clear()
        startActivity(Intent(this, LoginActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun go(cls: Class<*>) {
        startActivity(Intent(this, cls))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun openRoom(roomId: String) {
        SoundFx.click()
        startActivity(Intent(this, MainActivity::class.java).putExtra("room_id", roomId))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    /** Even 12dp spacing between grid cells (half inset on each side). */
    class GridSpacingItemDecoration(private val spacing: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val half = spacing / 2
            outRect.set(half, half, half, half)
        }
    }
}

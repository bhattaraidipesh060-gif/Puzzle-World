package com.jeiu.puzzleworld

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Rank badges screen — renders the SQLite-backed badge list from the backend
 * (GET /api/user/profile) rather than the old local achievement store.
 *
 * Locked badges render as semi-transparent grayscale cards with a 🔒 overlay and
 * a progress bar toward their unlock condition; unlocked badges render glowing
 * and full-contrast. Tapping a badge shows its condition; newly-unlocked badges
 * pop in with a scale + glow pulse.
 */
class AchievementsActivity : AppCompatActivity() {

    companion object {
        private const val BADGE_CACHE = "puzzleworld_badge_cache"
        private const val BG_TOP    = "#0F172A"
        private const val BG_BOTTOM = "#111C31"
        private const val GOLD      = "#FFD740"
        private const val CYAN      = "#22D3EE"
        private const val GREEN     = "#34D399"
        private const val TEXT_ON   = "#E2E8F0"
        private const val TEXT_MUTE = "#94A3B8"
    }

    private lateinit var headerCount: TextView
    private lateinit var listContainer: LinearLayout
    private lateinit var world: HttpWorldService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_achievements)
        SoundFx.init(this)

        AnimatedBackgroundDrawable.applyTo(
            this,
            Color.parseColor(BG_TOP),
            Color.parseColor(BG_BOTTOM),
            Color.parseColor(GOLD)
        )

        headerCount = findViewById(R.id.ac_header_count)
        listContainer = findViewById(R.id.ac_list)

        val username = getSharedPreferences("puzzleworld_account", MODE_PRIVATE)
            .getString("username", "Player") ?: "Player"
        world = HttpWorldService(ApiClient.BASE_URL, SessionManager.userId() ?: username)

        listContainer.addView(TextView(this).apply {
            text = "Loading badges…"
            textSize = 14f
            setTextColor(Color.parseColor(TEXT_MUTE))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        })

        lifecycleScope.launch { load() }
    }

    private suspend fun load() {
        val profile = world.fetchProfile()
        if (profile == null) renderOffline() else render(profile)
    }

    private fun renderOffline() {
        listContainer.removeAllViews()
        listContainer.addView(TextView(this).apply {
            text = "⚠️ Offline — badges sync from the cloud.\nConnect to see your rank badges."
            textSize = 14f
            setTextColor(Color.parseColor(TEXT_MUTE))
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(28), dp(16), dp(28))
        })
    }

    private fun render(profile: BadgeProfile) {
        headerCount.text = "${profile.unlockedCount} / ${profile.totalBadges} unlocked"
        listContainer.removeAllViews()

        val cache = getSharedPreferences(BADGE_CACHE, MODE_PRIVATE)
        // Badges unlocked but not yet seen get the pop-in animation, then are cached.
        val newlyUnlocked = profile.badges.filter { it.unlocked && !cache.getBoolean(it.id, false) }
        if (newlyUnlocked.isNotEmpty()) {
            val e = cache.edit()
            profile.badges.filter { it.unlocked }.forEach { e.putBoolean(it.id, true) }
            e.apply()
        }

        var row: LinearLayout? = null
        profile.badges.forEachIndexed { i, b ->
            if (i % 2 == 0) {
                row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                listContainer.addView(row)
            }
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = if (i % 2 == 1) dp(6) else 0
                marginEnd = if (i % 2 == 0) dp(6) else 0
                bottomMargin = dp(10)
            }
            row?.addView(badgeCard(b, newlyUnlocked.any { it.id == b.id }), lp)
        }
    }

    private fun badgeCard(b: Badge, animate: Boolean): View {
        val unlocked = b.unlocked
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(12), dp(8), dp(12))
            background = resources.getDrawable(
                if (unlocked) R.drawable.ps_card_gold else R.drawable.ps_card, theme
            )
            elevation = if (unlocked) 6f else 2f
        }
        if (animate) card.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pop))

        card.addView(TextView(this).apply {
            text = if (unlocked) b.icon else "${b.icon}\n🔒"
            textSize = 30f
            gravity = Gravity.CENTER
            alpha = if (unlocked) 1f else 0.45f
        })

        card.addView(TextView(this).apply {
            text = b.name
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(if (unlocked) GOLD else TEXT_ON))
            maxLines = 1
            setPadding(0, dp(4), 0, 0)
        })

        if (unlocked) {
            card.addView(TextView(this).apply {
                text = "✅ Unlocked"
                textSize = 11f
                setTextColor(Color.parseColor(GREEN))
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            })
        } else {
            card.addView(miniProgress(b.current, b.target))
            card.addView(TextView(this).apply {
                text = "${b.current}/${b.target}"
                textSize = 11f
                setTextColor(Color.parseColor(TEXT_MUTE))
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            })
        }

        card.setOnClickListener {
            SoundFx.click()
            if (unlocked) {
                AlertDialog.Builder(this)
                    .setTitle("${b.icon} ${b.name}")
                    .setMessage("Unlocked!\n\n${b.description}")
                    .setPositiveButton("OK", null)
                    .show()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("🔒 ${b.name}")
                    .setMessage("${b.description}\n\nProgress: ${b.current} / ${b.target}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
        return card
    }

    private fun miniProgress(current: Int, target: Int): View {
        val pct = if (target <= 0) 0 else (current * 100 / target).coerceIn(0, 100)
        val track = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(6)
            ).apply { topMargin = dp(8) }
            setBackgroundColor(0x22FFFFFF)
        }
        track.addView(View(this).apply {
            setBackgroundColor(Color.parseColor(CYAN))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, pct.toFloat())
        })
        track.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (100 - pct).toFloat())
        })
        return track
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

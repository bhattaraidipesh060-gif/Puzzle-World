package com.jeiu.puzzleworld

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/**
 * Login / registration screen. Authenticates against the Express/SQLite backend
 * (POST /api/auth/register, POST /api/auth/login) with a username + password and
 * persists the returned JWT in an encrypted session store. Falls back to local
 * SQLite credentials when the backend is unreachable (offline-first).
 * Auto-logins on launch when a valid session exists.
 */
class LoginActivity : AppCompatActivity() {

    companion object {
        private const val PREFS = "puzzleworld_account"
        private const val KEY_USERNAME = "username"
    }

    private lateinit var usernameInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var confirmInput: TextInputEditText
    private lateinit var confirmLayout: TextInputLayout
    private lateinit var statusView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var toggleView: TextView
    private lateinit var btnLogin: Button
    private lateinit var btnGuest: Button
    private lateinit var loading: ProgressBar

    private var registerMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (LanguageManager.hasLanguage(this)) {
            LanguageManager.apply(this, LanguageManager.current(this))
        }
        setContentView(R.layout.activity_login)
        SoundFx.init(this)
        SessionManager.init(this)
        AnimatedBackgroundDrawable.applyTo(this, Color.parseColor("#0A1428"), Color.parseColor("#001B33"), Color.parseColor("#00E5FF"))

        // Mandatory pre-login language selection on first launch.
        if (!LanguageManager.hasLanguage(this)) {
            showLanguageSelection()
            return
        }

        // Auto-login: a stored, non-expired session skips the login form.
        if (SessionManager.isLoggedIn()) {
            goHome()
            return
        }

        usernameInput = findViewById(R.id.login_username)
        passwordInput = findViewById(R.id.login_password)
        confirmInput = findViewById(R.id.login_confirm)
        confirmLayout = findViewById(R.id.login_confirm_layout)
        statusView = findViewById(R.id.login_status)
        subtitleView = findViewById(R.id.login_subtitle)
        toggleView = findViewById(R.id.login_toggle)
        btnLogin = findViewById(R.id.login_btn_submit)
        btnGuest = findViewById(R.id.login_btn_guest)
        loading = findViewById(R.id.login_loading)
        val termsCheck = findViewById<CheckBox>(R.id.login_terms_check)
        val termsLink = findViewById<TextView>(R.id.login_terms_link)

        btnLogin.setOnClickListener { SoundFx.click(); attemptSubmit() }
        btnGuest.setOnClickListener { SoundFx.click(); enterAsGuest() }
        toggleView.setOnClickListener { toggleMode() }
        termsCheck.setOnCheckedChangeListener { _, checked ->
            if (checked) statusView.text = ""
        }
        termsLink.setOnClickListener {
            startActivity(Intent(this, TermsActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        playEntrance()
    }

    /** First-launch gate: require an explicit English/Korean choice before login. */
    private fun showLanguageSelection() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.language_choose))
            .setCancelable(false)
            .setItems(arrayOf("English", "한국어")) { _, which ->
                LanguageManager.apply(this, if (which == 0) "en" else "ko")
                recreate()
            }
            .show()
    }

    /** Staggered entrance: card + inputs slide up and fade in; buttons scale in. */
    private fun playEntrance() {
        val card = findViewById<View>(R.id.login_card)
        card.alpha = 0f
        card.translationY = 60f
        card.animate().alpha(1f).translationY(0f).setDuration(420L).setStartDelay(40L).start()

        listOf(R.id.login_username_layout, R.id.login_password_layout).forEachIndexed { i, id ->
            val v = findViewById<View>(id)
            v.alpha = 0f
            v.translationY = 30f
            v.animate().alpha(1f).translationY(0f).setDuration(300L).setStartDelay(200L + i * 80L).start()
        }

        // Buttons scale in only — alpha stays bound to the terms checkbox (disabled state).
        listOf(R.id.login_btn_submit, R.id.login_btn_guest).forEachIndexed { i, id ->
            val v = findViewById<View>(id)
            v.scaleX = 0.85f
            v.scaleY = 0.85f
            v.animate().scaleX(1f).scaleY(1f).setDuration(300L).setStartDelay(360L + i * 80L).start()
        }
    }

    private fun attemptSubmit() {
        if (!termsAccepted()) return
        val username = usernameInput.text?.toString()?.trim().orEmpty()
        val pass = passwordInput.text?.toString().orEmpty()
        if (!validate(username, pass, registerMode)) return

        setLoading(true)
        statusView.text = if (registerMode) getString(R.string.login_creating) else getString(R.string.login_signing)
        statusView.setTextColor(Color.parseColor("#94A3B8"))

        val request = AuthRequest(username = username, password = pass)

        lifecycleScope.launch {
            try {
                val response = if (registerMode) ApiClient.authApi.register(request) else ApiClient.authApi.login(request)
                onAuthSuccess(response)
            } catch (e: HttpException) {
                setLoading(false)
                showError(
                    when (e.code()) {
                        409 -> getString(R.string.login_username_taken)
                        401 -> getString(R.string.login_incorrect)
                        400 -> getString(R.string.login_check_credentials)
                        else -> getString(R.string.login_unknown_error, e.code())
                    }
                )
            } catch (e: IOException) {
                setLoading(false)
                offlineAuth(username, pass)
            } catch (e: Exception) {
                setLoading(false)
                offlineAuth(username, pass)
            }
        }
    }

    private fun onAuthSuccess(response: AuthResponse) {
        SessionManager.save(response.token, response.user)
        setLoading(false)
        val name = response.user.username ?: "Player"
        persistUsername(name)
        Toast.makeText(this, getString(R.string.login_welcome, name), Toast.LENGTH_SHORT).show()
        goHome()
    }

    /** Offline-first fallback: authenticate against the local SQLite store. */
    private fun offlineAuth(username: String, pass: String) {
        val db = AuthDatabase(this)
        val ok = try {
            if (db.exists(username)) {
                if (!db.verify(username, pass)) {
                    showError(getString(R.string.login_offline_password))
                    false
                } else true
            } else if (registerMode) {
                db.register(username, pass)
            } else {
                showError(getString(R.string.login_no_account))
                false
            }
        } finally {
            db.close()
        }
        if (ok) {
            persistUsername(username)
            Toast.makeText(this, getString(R.string.login_welcome_offline, username), Toast.LENGTH_SHORT).show()
            goHome()
        }
    }

    private fun enterAsGuest() {
        if (!termsAccepted()) return
        persistUsername("Guest")
        goHome()
    }

    private fun toggleMode() {
        registerMode = !registerMode
        confirmLayout.visibility = if (registerMode) View.VISIBLE else View.GONE
        findViewById<TextInputLayout>(R.id.login_password_layout).helperText =
            if (registerMode) "8+ characters — uppercase, lowercase & a number" else null
        subtitleView.text = if (registerMode) getString(R.string.login_subtitle_register) else getString(R.string.login_subtitle)
        btnLogin.text = if (registerMode) getString(R.string.login_create_account) else getString(R.string.login_sign_in)
        toggleView.text = if (registerMode) getString(R.string.login_toggle_register) else getString(R.string.login_toggle_login)
        statusView.text = ""
        statusView.setTextColor(Color.parseColor("#94A3B8"))
        SoundFx.click()
    }

    private fun setLoading(loading: Boolean) {
        this.loading.visibility = if (loading) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !loading
        btnGuest.isEnabled = !loading
        btnLogin.alpha = if (loading) 0.6f else 1f
        btnGuest.alpha = if (loading) 0.6f else 1f
        if (loading) {
            btnLogin.text = ""
        } else {
            btnLogin.text = if (registerMode) getString(R.string.login_create_account) else getString(R.string.login_sign_in)
        }
        usernameInput.isEnabled = !loading
        passwordInput.isEnabled = !loading
        confirmInput.isEnabled = !loading
    }

    private fun validate(username: String, pass: String, register: Boolean): Boolean {
        if (username.isEmpty()) {
            showError(getString(R.string.login_enter_username))
            return false
        }
        if (!username.matches(Regex("^[A-Za-z0-9_.-]{3,32}$"))) {
            showError(getString(R.string.login_invalid_username))
            return false
        }
        if (pass.isEmpty()) { showError(getString(R.string.login_enter_password)); return false }
        if (register) {
            if (pass.length < 8) { showError(getString(R.string.login_password_len)); return false }
            if (!pass.any { it.isUpperCase() } || !pass.any { it.isLowerCase() } || !pass.any { it.isDigit() }) {
                showError(getString(R.string.login_password_policy))
                return false
            }
            val confirm = confirmInput.text?.toString().orEmpty()
            if (confirm != pass) { showError(getString(R.string.login_password_mismatch)); return false }
        }
        return true
    }

    private fun showError(msg: String) {
        statusView.setTextColor(Color.parseColor("#FF4081"))
        statusView.text = msg
        passwordInput.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake_x))
        SoundFx.error()
    }

    /** True only when the user has explicitly agreed to the Terms & Privacy Policy. */
    private fun termsAccepted(): Boolean {
        val check = findViewById<CheckBox>(R.id.login_terms_check)
        if (!check.isChecked) {
            statusView.setTextColor(Color.parseColor("#FF4081"))
            statusView.text = getString(R.string.login_accept_terms)
            check.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake_x))
            SoundFx.error()
            return false
        }
        return true
    }

    private fun persistUsername(name: String) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_USERNAME, name)
            .apply()
    }

    private fun goHome() {
        SoundFx.success()
        startActivity(Intent(this, HomeActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}

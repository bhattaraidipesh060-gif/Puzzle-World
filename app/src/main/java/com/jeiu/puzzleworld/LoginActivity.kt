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
 * (POST /api/auth/register, POST /api/auth/login) and persists the returned JWT
 * in an encrypted session store. Falls back to local SQLite credentials when the
 * backend is unreachable (offline-first). Auto-logins on launch when a valid
 * session exists.
 *
 * Two-step setup: accounts are identified by email; a brand-new user is prompted
 * to pick a unique username (POST /api/auth/set-username) before entering the app.
 */
class LoginActivity : AppCompatActivity() {

    companion object {
        private const val PREFS = "puzzleworld_account"
        private const val KEY_USERNAME = "username"
    }

    private lateinit var emailInput: TextInputEditText
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
        LanguageManager.apply(this, LanguageManager.current(this))
        setContentView(R.layout.activity_login)
        SoundFx.init(this)
        SessionManager.init(this)
        AnimatedBackgroundDrawable.applyTo(this, Color.parseColor("#0A1428"), Color.parseColor("#001B33"), Color.parseColor("#00E5FF"))

        // Auto-login: a stored, non-expired session skips the login form.
        if (SessionManager.isLoggedIn()) {
            goHome()
            return
        }

        emailInput = findViewById(R.id.login_username)
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
        val email = emailInput.text?.toString()?.trim().orEmpty()
        val pass = passwordInput.text?.toString().orEmpty()
        if (!validate(email, pass, registerMode)) return

        setLoading(true)
        statusView.text = if (registerMode) "Creating account…" else "Signing in…"
        statusView.setTextColor(Color.parseColor("#94A3B8"))

        val request = AuthRequest(email = email, password = pass)

        lifecycleScope.launch {
            try {
                if (registerMode) {
                    // Register sends a verification code — no token yet.
                    ApiClient.authApi.register(request)
                    setLoading(false)
                    promptForOtp(email, "We emailed a 6-digit code to $email.")
                } else {
                    val response = ApiClient.authApi.login(request)
                    onAuthSuccess(response)
                }
            } catch (e: HttpException) {
                setLoading(false)
                if (!registerMode && e.code() == 403) {
                    val unverified = parseUnverifiedEmail(e)
                    if (unverified != null) {
                        promptForOtp(unverified, "Your email isn't verified yet. Enter the code we sent to $unverified.")
                    } else {
                        showError(getString(R.string.login_incorrect))
                    }
                } else {
                    showError(
                        when (e.code()) {
                            409 -> getString(R.string.login_email_taken)
                            401 -> getString(R.string.login_incorrect)
                            400 -> getString(R.string.login_check_email)
                            403 -> getString(R.string.login_unverified)
                            else -> getString(R.string.login_unknown_error, e.code())
                        }
                    )
                }
            } catch (e: IOException) {
                setLoading(false)
                offlineAuth(email, pass)
            } catch (e: Exception) {
                setLoading(false)
                offlineAuth(email, pass)
            }
        }
    }

    private fun onAuthSuccess(response: AuthResponse) {
        SessionManager.save(response.token, response.user)
        setLoading(false)
        SoundFx.success()
        val name = response.user.username
        if (name.isNullOrBlank()) {
            // Two-step setup: account identified by email; username not chosen yet.
            promptForUsername(response)
        } else {
            persistUsername(name)
            Toast.makeText(this, "Welcome back, $name! 🎉", Toast.LENGTH_SHORT).show()
            goHome()
        }
    }

    /** Step 2: ask for a unique username after an email-based sign-up/sign-in. */
    private fun promptForUsername(response: AuthResponse) {
        val input = TextInputEditText(this).apply {
            hint = "Username (3–32 characters)"
            setTextColor(Color.parseColor("#E2E8F0"))
            setHintTextColor(Color.parseColor("#64748B"))
            setSingleLine()
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.login_username_title))
            .setMessage(getString(R.string.login_username_message))
            .setView(input)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.login_username_save)) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.length !in 3..32) {
                    Toast.makeText(this, getString(R.string.login_username_hint), Toast.LENGTH_SHORT).show()
                    promptForUsername(response)
                } else {
                    saveUsername(name, response)
                }
            }
            .setNegativeButton(getString(R.string.login_username_skip)) { _, _ ->
                persistUsername(response.user.email.substringBefore('@').ifBlank { "Player" })
                Toast.makeText(this, "Welcome! 🎉", Toast.LENGTH_SHORT).show()
                goHome()
            }
            .show()
    }

    /** Persist the chosen username (two-step completion). */
    private fun saveUsername(name: String, response: AuthResponse) {
        setLoading(true)
        statusView.text = getString(R.string.login_saving_username)
        statusView.setTextColor(Color.parseColor("#94A3B8"))
        lifecycleScope.launch {
            try {
                val res = ApiClient.authApi.setUsername(SetUsernameRequest(name))
                SessionManager.save(response.token, res.user)
                persistUsername(res.user.username ?: name)
                setLoading(false)
                SoundFx.success()
                Toast.makeText(this@LoginActivity, "Welcome, ${res.user.username ?: name}! 🎉", Toast.LENGTH_SHORT).show()
                goHome()
            } catch (e: HttpException) {
                setLoading(false)
                Toast.makeText(
                    this@LoginActivity,
                    when (e.code()) {
                        409 -> "That username is already taken"
                        400 -> "Username must be 3–32 characters"
                        else -> "Couldn't save username (${e.code()})"
                    },
                    Toast.LENGTH_SHORT
                ).show()
                promptForUsername(response)
            } catch (e: Exception) {
                setLoading(false)
                Toast.makeText(this@LoginActivity, "Couldn't save username — check your connection", Toast.LENGTH_SHORT).show()
                promptForUsername(response)
            }
        }
    }

    /** Step 1b: ask for the 6-digit OTP after registration / unverified login. */
    private fun promptForOtp(email: String, message: String) {
        val input = TextInputEditText(this).apply {
            hint = "6-digit code"
            setTextColor(Color.parseColor("#E2E8F0"))
            setHintTextColor(Color.parseColor("#64748B"))
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setSingleLine()
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.login_otp_title))
            .setMessage(message)
            .setView(input)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.login_otp_verify)) { _, _ ->
                val otp = input.text?.toString()?.trim().orEmpty()
                if (otp.length != 6) {
                    Toast.makeText(this, getString(R.string.login_otp_hint), Toast.LENGTH_SHORT).show()
                    promptForOtp(email, message)
                } else {
                    verifyOtp(email, otp)
                }
            }
            .setNegativeButton(getString(R.string.login_otp_resend)) { _, _ -> resendOtp(email, message) }
            .show()
    }

    private fun verifyOtp(email: String, otp: String) {
        setLoading(true)
        statusView.text = getString(R.string.login_verifying)
        statusView.setTextColor(Color.parseColor("#94A3B8"))
        lifecycleScope.launch {
            try {
                val response = ApiClient.authApi.verifyOtp(VerifyOtpRequest(email, otp))
                onAuthSuccess(response)
            } catch (e: HttpException) {
                setLoading(false)
                Toast.makeText(
                    this@LoginActivity,
                    if (e.code() == 400) "Invalid or expired code" else "Verification failed (${e.code()})",
                    Toast.LENGTH_SHORT
                ).show()
                promptForOtp(email, "Enter the 6-digit code sent to $email.")
            } catch (e: Exception) {
                setLoading(false)
                Toast.makeText(this@LoginActivity, "Check your connection and try again", Toast.LENGTH_SHORT).show()
                promptForOtp(email, "Enter the 6-digit code sent to $email.")
            }
        }
    }

    private fun resendOtp(email: String, message: String) {
        lifecycleScope.launch {
            try {
                ApiClient.authApi.resendOtp(ResendOtpRequest(email))
                Toast.makeText(this@LoginActivity, "New code sent to $email", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "Couldn't resend — check your connection", Toast.LENGTH_SHORT).show()
            }
            promptForOtp(email, message)
        }
    }

    /** Reads the `unverified` email from a 403 login response body, if present. */
    private fun parseUnverifiedEmail(e: HttpException): String? = try {
        val body = e.response()?.errorBody()?.string().orEmpty()
        val obj = org.json.JSONObject(body)
        if (obj.optBoolean("unverified", false)) obj.optString("email", "").ifBlank { null } else null
    } catch (ex: Exception) {
        null
    }

    /** Offline-first fallback: authenticate against the local SQLite store. */
    private fun offlineAuth(user: String, pass: String) {
        val db = AuthDatabase(this)
        val ok = try {
            if (db.exists(user)) {
                if (!db.verify(user, pass)) {
                    showError(getString(R.string.login_offline_password))
                    false
                } else true
            } else if (registerMode) {
                db.register(user, pass)
            } else {
                showError(getString(R.string.login_no_account))
                false
            }
        } finally {
            db.close()
        }
        if (ok) {
            persistUsername(user)
            Toast.makeText(this, "Offline mode — welcome, $user! 🎉", Toast.LENGTH_SHORT).show()
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
        emailInput.isEnabled = !loading
        passwordInput.isEnabled = !loading
        confirmInput.isEnabled = !loading
    }

    private fun validate(ident: String, pass: String, register: Boolean): Boolean {
        if (ident.isEmpty()) {
            showError(getString(R.string.login_enter_email))
            return false
        }
        val isEmail = android.util.Patterns.EMAIL_ADDRESS.matcher(ident).matches()
        if (!isEmail) { showError(getString(R.string.login_invalid_email)); return false }
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

package com.jeiu.puzzleworld

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

/**
 * Secure session store. Persists the JWT + user profile in EncryptedSharedPreferences
 * (AES-256-GCM, Android Keystore-backed) so the token never rests in plaintext.
 * Falls back to a plain prefs file if the Keystore is unavailable on a device.
 */
object SessionManager {

    private const val TAG = "PUZZLE_SESSION"
    private const val FILE = "puzzleworld_secure_session"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_EXP = "exp"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = try {
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context.applicationContext,
                FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences unavailable; using plain prefs", e)
            context.applicationContext.getSharedPreferences("${FILE}_plain", Context.MODE_PRIVATE)
        }
    }

    fun save(token: String, user: UserProfile) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER_ID, user.user_id)
            .putString(KEY_USERNAME, user.username)
            .putLong(KEY_EXP, decodeExp(token))
            .apply()
    }

    fun token(): String? =
        if (::prefs.isInitialized) prefs.getString(KEY_TOKEN, null) else null

    fun userId(): String? =
        if (::prefs.isInitialized) prefs.getString(KEY_USER_ID, null) else null

    fun username(): String? =
        if (::prefs.isInitialized) prefs.getString(KEY_USERNAME, null) else null

    /** True when a non-expired token is stored (used for auto-login). */
    fun isLoggedIn(): Boolean {
        val token = token() ?: return false
        val exp = if (::prefs.isInitialized) prefs.getLong(KEY_EXP, 0L) else 0L
        return exp <= 0L || exp > System.currentTimeMillis() / 1000
    }

    fun clear() {
        if (::prefs.isInitialized) prefs.edit().clear().apply()
    }

    /** Decodes the JWT `exp` claim (seconds since epoch) without a JWT library. */
    private fun decodeExp(token: String): Long = try {
        val parts = token.split('.')
        if (parts.size != 3) 0L
        else {
            val payload = String(
                Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
                Charsets.UTF_8
            )
            JSONObject(payload).optLong("exp", 0L)
        }
    } catch (e: Exception) {
        0L
    }
}

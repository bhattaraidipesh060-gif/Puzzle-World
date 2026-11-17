package com.jeiu.puzzleworld

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Stores the user's language preference and applies it as the app locale. */
object LanguageManager {
    private const val PREFS = "puzzleworld_settings"
    private const val KEY_LANG = "language"

    // Process-wide cache so interceptors can read the tag without a Context.
    @Volatile
    private var cachedLang: String = "en"

    fun current(context: Context): String {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANG, null)
        if (stored != null) cachedLang = stored
        return cachedLang
    }

    /** True once the user has explicitly chosen a language (pre-login gate). */
    fun hasLanguage(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY_LANG)

    fun apply(context: Context, lang: String) {
        cachedLang = lang
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LANG, lang).apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
    }

    /** Current language tag without a Context (for OkHttp interceptors). */
    fun currentTag(): String = cachedLang

    fun label(context: Context): String = if (current(context) == "ko") "한국어" else "English"
}

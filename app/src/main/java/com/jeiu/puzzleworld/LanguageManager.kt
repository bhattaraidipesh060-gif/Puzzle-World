package com.jeiu.puzzleworld

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Stores the user's language preference and applies it as the app locale. */
object LanguageManager {
    private const val PREFS = "puzzleworld_settings"
    private const val KEY_LANG = "language"

    fun current(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANG, "en") ?: "en"

    fun apply(context: Context, lang: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LANG, lang).apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
    }

    fun label(context: Context): String = if (current(context) == "ko") "한국어" else "English"
}

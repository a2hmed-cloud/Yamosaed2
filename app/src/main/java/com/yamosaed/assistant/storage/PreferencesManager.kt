package com.yamosaed.assistant.storage

import android.content.Context
import android.content.SharedPreferences
import com.yamosaed.assistant.BuildConfig

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("yamosaed_prefs", Context.MODE_PRIVATE)

    var geminiApiKey: String
        get() {
            val saved = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
            if (saved.isNotBlank()) return saved
            return try {
                val key = BuildConfig.GEMINI_API_KEY
                if (key.isNotBlank() && !key.contains("DummyKeyPlaceholder") && !key.contains("your_api_key")) key else ""
            } catch (_: Exception) { "" }
        }
        set(value) {
            prefs.edit().putString(KEY_GEMINI_API_KEY, value.trim()).apply()
        }

    var selectedGeminiModel: String
        get() = prefs.getString(KEY_GEMINI_MODEL, "gemini-1.5-flash") ?: "gemini-1.5-flash"
        set(value) {
            prefs.edit().putString(KEY_GEMINI_MODEL, value.trim()).apply()
        }

    var homeCity: String
        get() = prefs.getString(KEY_HOME_CITY, "الإسماعيلية") ?: "الإسماعيلية"
        set(value) {
            prefs.edit().putString(KEY_HOME_CITY, value.trim()).apply()
        }

    var defaultMusicApp: String
        get() = prefs.getString(KEY_DEFAULT_MUSIC_APP, "spotify") ?: "spotify"
        set(value) {
            prefs.edit().putString(KEY_DEFAULT_MUSIC_APP, value).apply()
        }

    var isBackgroundWakeEnabled: Boolean
        get() = prefs.getBoolean(KEY_BG_WAKE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_BG_WAKE, value).apply()
        }

    var speechRate: Float
        get() = prefs.getFloat(KEY_SPEECH_RATE, 1.0f)
        set(value) {
            prefs.edit().putFloat(KEY_SPEECH_RATE, value).apply()
        }

    fun clearApiKey() {
        prefs.edit().remove(KEY_GEMINI_API_KEY).apply()
    }

    companion object {
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_GEMINI_MODEL = "gemini_model"
        private const val KEY_HOME_CITY = "home_city"
        private const val KEY_DEFAULT_MUSIC_APP = "default_music_app"
        private const val KEY_BG_WAKE = "background_wake_enabled"
        private const val KEY_SPEECH_RATE = "speech_rate"

    }
}

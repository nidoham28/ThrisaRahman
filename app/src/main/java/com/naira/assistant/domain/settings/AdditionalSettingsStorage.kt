package com.naira.assistant.domain.settings

import android.content.Context
import androidx.core.content.edit

/** Settings for the background wake-word listener. */
class BackgroundWakeWordSettingsStorage(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isServiceEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)

    fun setServiceEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
    }

    fun getDetectionThreshold(): Float = prefs.getFloat(KEY_THRESHOLD, DEFAULT_THRESHOLD)
        .coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)

    fun setDetectionThreshold(value: Float) {
        prefs.edit { putFloat(KEY_THRESHOLD, value.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)) }
    }

    private companion object {
        const val PREFS_NAME = "background_wake_word"
        const val KEY_ENABLED = "service_enabled"
        const val KEY_THRESHOLD = "detection_threshold"
        const val DEFAULT_THRESHOLD = 0.25f
        const val MIN_THRESHOLD = 0.01f
        const val MAX_THRESHOLD = 1.0f
    }
}

/** Visual behavior preferences used by the app's settings screen. */
class InterfaceSettingsStorage(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun showAssistantAvatar(): Boolean = prefs.getBoolean(KEY_AVATAR, true)
    fun setShowAssistantAvatar(value: Boolean) = prefs.edit { putBoolean(KEY_AVATAR, value) }
    fun codeWordWrap(): Boolean = prefs.getBoolean(KEY_WORD_WRAP, true)
    fun setCodeWordWrap(value: Boolean) = prefs.edit { putBoolean(KEY_WORD_WRAP, value) }
    fun collapseCodeBlocks(): Boolean = prefs.getBoolean(KEY_COLLAPSE_CODE, false)
    fun setCollapseCodeBlocks(value: Boolean) = prefs.edit { putBoolean(KEY_COLLAPSE_CODE, value) }
    fun showCodeLineNumbers(): Boolean = prefs.getBoolean(KEY_LINE_NUMBERS, true)
    fun setShowCodeLineNumbers(value: Boolean) = prefs.edit { putBoolean(KEY_LINE_NUMBERS, value) }

    private companion object {
        const val PREFS_NAME = "interface_settings"
        const val KEY_AVATAR = "show_assistant_avatar"
        const val KEY_WORD_WRAP = "code_word_wrap"
        const val KEY_COLLAPSE_CODE = "collapse_code_blocks"
        const val KEY_LINE_NUMBERS = "show_code_line_numbers"
    }
}

/** Network identity preferences for browser-style extractor requests. */
class NetworkSettingsStorage(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCustomUserAgent(): String = prefs.getString(KEY_USER_AGENT, "").orEmpty()
    fun setCustomUserAgent(value: String) {
        prefs.edit { putString(KEY_USER_AGENT, value.trim()) }
    }

    private companion object {
        const val PREFS_NAME = "network_settings"
        const val KEY_USER_AGENT = "custom_user_agent"
    }
}

package com.naira.assistant.domain

/** In-memory cache for the non-secret fields synchronized from Supabase. */
class UserSettingsStorage {
    fun getTheme(): String = theme
    fun setTheme(value: String) { theme = value }

    fun getFontSize(): String = fontSize
    fun setFontSize(value: String) { fontSize = value }

    fun getStoredHistory(): Boolean = storedHistory
    fun setStoredHistory(value: Boolean) { storedHistory = value }

    fun getStoredMemories(): Boolean = storedMemories
    fun setStoredMemories(value: Boolean) { storedMemories = value }

    fun getVoiceOutputEnabled(): Boolean = voiceOutputEnabled
    fun setVoiceOutputEnabled(value: Boolean) { voiceOutputEnabled = value }

    private companion object {
        @Volatile
        var theme: String = "system"

        @Volatile
        var fontSize: String = "medium"

        @Volatile
        var storedHistory: Boolean = true

        @Volatile
        var storedMemories: Boolean = true

        @Volatile
        var voiceOutputEnabled: Boolean = true
    }
}

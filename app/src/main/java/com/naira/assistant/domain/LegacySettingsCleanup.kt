package com.naira.assistant.domain

import android.content.Context
import androidx.core.content.edit

object LegacySettingsCleanup {
    fun clearUnsupportedSettings(context: Context) {
        val appContext = context.applicationContext
        listOf(
            "account_settings",
            "speech_settings",
            "roleplay_settings",
            "cloud_settings_sync",
        ).forEach { name ->
            val preferences = appContext.getSharedPreferences(name, Context.MODE_PRIVATE)
            if (preferences.all.isNotEmpty()) preferences.edit { clear() }
        }

        val apiPreferences = appContext.getSharedPreferences("api_key_prefs", Context.MODE_PRIVATE)
        val obsoleteKeys = apiPreferences.all.keys.filterNot(::isSupportedApiPreferenceKey)
        if (obsoleteKeys.isNotEmpty()) {
            apiPreferences.edit {
                obsoleteKeys.forEach(::remove)
            }
        }
    }
}

internal fun isSupportedApiPreferenceKey(key: String): Boolean =
    key.startsWith("key_") ||
        key == "selected_provider" ||
        key.startsWith("selected_provider_") ||
        key == "openrouter_model" ||
        key.startsWith("openrouter_model_")

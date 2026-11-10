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
        val obsoleteKeys = apiPreferences.all.keys.filterNot { it.startsWith("key_") }
        if (obsoleteKeys.isNotEmpty()) {
            apiPreferences.edit {
                obsoleteKeys.forEach(::remove)
            }
        }
    }
}

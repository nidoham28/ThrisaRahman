package com.nidoham.thrisa.domain

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class ApiKeyStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    fun saveApiKey(provider: String, key: String) {
        prefs.edit { putString("key_$provider", key) }
    }

    fun getApiKey(provider: String): String? =
        prefs.getString("key_$provider", null)

    fun clearApiKey(provider: String) {
        prefs.edit { remove("key_$provider") }
    }

    private companion object {
        const val PREFS_NAME = "api_key_prefs"
    }
}
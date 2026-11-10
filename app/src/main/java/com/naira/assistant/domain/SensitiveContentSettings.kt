package com.naira.assistant.domain

import android.content.Context
import androidx.core.content.edit

class SensitiveContentSettingsStorage(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "assistant_mode_access",
        Context.MODE_PRIVATE,
    )

    fun getReduceSensitiveContent(userId: String?): Boolean =
        preferences.getBoolean(reduceSensitiveContentKey(userId), true)

    fun setReduceSensitiveContent(userId: String?, enabled: Boolean) {
        preferences.edit {
            putBoolean(reduceSensitiveContentKey(userId), enabled)
        }
    }

    private fun reduceSensitiveContentKey(userId: String?): String =
        userId?.let { "reduce_sensitive_content_${it.lowercase()}" }
            ?: "reduce_sensitive_content"
}

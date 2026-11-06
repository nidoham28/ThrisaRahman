package com.nidoham.thrisa.domain

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

enum class AssistantProvider(
    val displayName: String,
    val placeholder: String,
    val defaultModelId: String,
) {
    GEMINI("Gemini", "AIza...", "gemini-3.5-flash-lite"),
    OPENROUTER("OpenRouter", "sk-or-...", "openrouter/auto");

    companion object {
        fun fromStorageValue(value: String?): AssistantProvider =
            entries.firstOrNull { it.name == value } ?: GEMINI
    }
}

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

    fun getSelectedProvider(): AssistantProvider =
        AssistantProvider.fromStorageValue(prefs.getString(KEY_SELECTED_PROVIDER, null))

    fun saveSelectedProvider(provider: AssistantProvider) {
        prefs.edit { putString(KEY_SELECTED_PROVIDER, provider.name) }
    }

    fun getOpenRouterModelId(): String =
        prefs.getString(KEY_OPENROUTER_MODEL_ID, AssistantProvider.OPENROUTER.defaultModelId)
            ?: AssistantProvider.OPENROUTER.defaultModelId

    fun saveOpenRouterModelId(modelId: String) {
        require(modelId.isNotBlank()) { "OpenRouter model ID must not be blank" }
        prefs.edit { putString(KEY_OPENROUTER_MODEL_ID, modelId.trim()) }
    }

    private companion object {
        const val PREFS_NAME = "api_key_prefs"
        const val KEY_SELECTED_PROVIDER = "selected_provider"
        const val KEY_OPENROUTER_MODEL_ID = "openrouter_model_id"
    }
}
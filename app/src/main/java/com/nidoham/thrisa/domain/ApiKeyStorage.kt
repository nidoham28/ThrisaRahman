package com.nidoham.thrisa.domain

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.Locale

enum class AssistantProvider(
    val displayName: String,
    val placeholder: String,
    val defaultModelId: String,
) {
    GEMINI("Gemini", "AIza...", "gemini-3.5-flash-lite"),
    OPENROUTER("OpenRouter", "sk-or-...", "openrouter/auto");

    companion object {
        fun fromStorageValue(value: String?): AssistantProvider =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: GEMINI
    }
}

class ApiKeyStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    /** Reads the saved key directly (plaintext), falling back to the pre-vault entry. */
    fun getApiKey(provider: String, userId: String): String? {
        val selected = AssistantProvider.entries.firstOrNull {
            it.name.equals(provider, ignoreCase = true)
        } ?: return null
        return prefs.getString(apiKeyKey(selected, userId), null)
            ?.takeIf(String::isNotBlank)
            ?: prefs.getString("key_${selected.name}", null)?.takeIf(String::isNotBlank)
    }

    /** Saves the key as-is; blank values remove the entry. */
    fun saveApiKey(provider: String, userId: String, key: String) {
        val selected = AssistantProvider.entries.firstOrNull {
            it.name.equals(provider, ignoreCase = true)
        } ?: return
        val cleaned = key.trim()
        prefs.edit {
            if (cleaned.isBlank()) remove(apiKeyKey(selected, userId))
            else putString(apiKeyKey(selected, userId), cleaned)
        }
    }

    fun getCricApiKey(userId: String): String? =
        prefs.getString(cricApiKeyKey(userId), null)?.takeIf(String::isNotBlank)

    fun saveCricApiKey(userId: String, key: String) {
        val cleaned = key.trim()
        prefs.edit {
            if (cleaned.isBlank()) remove(cricApiKeyKey(userId))
            else putString(cricApiKeyKey(userId), cleaned)
        }
    }

    fun getSelectedProvider(userId: String? = null): AssistantProvider =
        AssistantProvider.fromStorageValue(
            prefs.getString(selectedProviderKey(userId), null)
        )

    fun saveSelectedProvider(provider: AssistantProvider, userId: String? = null) {
        val key = userId?.let(::selectedProviderKey) ?: SELECTED_PROVIDER_KEY
        prefs.edit { putString(key, provider.name) }
    }

    fun getOpenRouterModelId(userId: String? = null): String =
        prefs.getString(openRouterModelKey(userId), null)
            ?: AssistantProvider.OPENROUTER.defaultModelId

    fun saveOpenRouterModelId(modelId: String, userId: String? = null) {
        require(modelId.isNotBlank()) { "OpenRouter model ID must not be blank" }
        prefs.edit { putString(openRouterModelKey(userId), modelId.trim()) }
    }

    private companion object {
        const val PREFS_NAME = "api_key_prefs"
        const val SELECTED_PROVIDER_KEY = "selected_provider"

        fun selectedProviderKey(userId: String?) =
            userId?.let { "selected_provider_${it.lowercase(Locale.ROOT)}" }
                ?: SELECTED_PROVIDER_KEY

        fun apiKeyKey(provider: AssistantProvider, userId: String) =
            "key_${provider.name}_${userId.lowercase(Locale.ROOT)}"

        fun cricApiKeyKey(userId: String) =
            "key_cricapi_${userId.lowercase(Locale.ROOT)}"

        fun openRouterModelKey(userId: String?) =
            userId?.let { "openrouter_model_${it.lowercase(Locale.ROOT)}" } ?: "openrouter_model"
    }
}
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

    fun getApiKey(provider: String, userId: String): String? {
        val selected = AssistantProvider.entries.firstOrNull {
            it.name.equals(provider, ignoreCase = true)
        } ?: return null
        return ProviderCredentialSession.getKey(userId, selected)
    }

    fun getSelectedProvider(userId: String? = null): AssistantProvider {
        if (userId != null) {
            ProviderCredentialSession.activeProvider(userId)?.let { return it }
            return AssistantProvider.fromStorageValue(
                prefs.getString(selectedProviderKey(userId), null)
            )
        }
        return AssistantProvider.fromStorageValue(prefs.getString(SELECTED_PROVIDER_KEY, null))
    }

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

    internal fun legacyCredentials(): Map<AssistantProvider, String> =
        AssistantProvider.entries.mapNotNull { provider ->
            prefs.getString("key_${provider.name}", null)
                ?.takeIf(String::isNotBlank)
                ?.let { provider to it }
        }.toMap()

    internal fun clearLegacyCredentials() {
        val editor = prefs.edit()
        AssistantProvider.entries.forEach { editor.remove("key_${it.name}") }
        check(editor.commit()) { "Could not remove the old unencrypted API keys." }
    }

    private companion object {
        const val PREFS_NAME = "api_key_prefs"
        const val SELECTED_PROVIDER_KEY = "selected_provider"

        fun selectedProviderKey(userId: String) =
            "selected_provider_${userId.lowercase(Locale.ROOT)}"

        fun openRouterModelKey(userId: String?) =
            userId?.let { "openrouter_model_${it.lowercase(Locale.ROOT)}" } ?: "openrouter_model"
    }
}
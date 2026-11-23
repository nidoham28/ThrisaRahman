package com.naira.assistant.domain.settings

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
    OPENROUTER("OpenRouter", "sk-or-...", "openrouter/auto"),
    GROQ("Groq", "gsk_...", "openai/gpt-oss-120b"),
    CUSTOM("Custom Provider", "Enter your API key", "custom-model");

    companion object {
        fun fromStorageValue(value: String?): AssistantProvider =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: GEMINI
    }
}

internal fun resolveSelectedProvider(accountValue: String?, deviceValue: String?): AssistantProvider =
    AssistantProvider.fromStorageValue(accountValue ?: deviceValue)

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

    fun getCustomTtsApiKey(userId: String): String? =
        prefs.getString(customTtsApiKeyKey(userId), null)?.takeIf(String::isNotBlank)

    fun saveCustomTtsApiKey(userId: String, key: String) {
        val cleaned = key.trim()
        prefs.edit {
            if (cleaned.isBlank()) remove(customTtsApiKeyKey(userId))
            else putString(customTtsApiKeyKey(userId), cleaned)
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

    fun getWeatherApiKey(userId: String): String? =
        prefs.getString(weatherApiKeyKey(userId), null)?.takeIf(String::isNotBlank)

    fun saveWeatherApiKey(userId: String, key: String) {
        val cleaned = key.trim()
        prefs.edit {
            if (cleaned.isBlank()) remove(weatherApiKeyKey(userId))
            else putString(weatherApiKeyKey(userId), cleaned)
        }
    }

    fun getSelectedProvider(userId: String? = null): AssistantProvider {
        val accountValue = userId?.let { prefs.getString(selectedProviderKey(it), null) }
        val deviceValue = prefs.getString(SELECTED_PROVIDER_KEY, null)
        return resolveSelectedProvider(accountValue, deviceValue)
    }

    fun saveSelectedProvider(provider: AssistantProvider, userId: String? = null) {
        prefs.edit {
            putString(SELECTED_PROVIDER_KEY, provider.name)
            userId?.let { putString(selectedProviderKey(it), provider.name) }
        }
    }

    fun getGeminiModelId(userId: String? = null): String =
        userId?.let { prefs.getString(geminiModelKey(it), null) }
            ?: prefs.getString(geminiModelKey(null), null)
            ?: AssistantProvider.GEMINI.defaultModelId

    fun saveGeminiModelId(modelId: String, userId: String? = null) {
        require(modelId.isNotBlank()) { "Gemini model ID must not be blank" }
        val cleaned = modelId.trim()
        prefs.edit {
            putString(geminiModelKey(null), cleaned)
            userId?.let { putString(geminiModelKey(it), cleaned) }
        }
    }

    fun getOpenRouterModelId(userId: String? = null): String =
        userId?.let { prefs.getString(openRouterModelKey(it), null) }
            ?: prefs.getString(openRouterModelKey(null), null)
            ?: AssistantProvider.OPENROUTER.defaultModelId

    fun saveOpenRouterModelId(modelId: String, userId: String? = null) {
        require(modelId.isNotBlank()) { "OpenRouter model ID must not be blank" }
        val cleaned = modelId.trim()
        prefs.edit {
            putString(openRouterModelKey(null), cleaned)
            userId?.let { putString(openRouterModelKey(it), cleaned) }
        }
    }

    fun getOpenRouterBaseUrl(userId: String? = null): String =
        userId?.let { prefs.getString(openRouterBaseUrlKey(it), null) }
            ?: prefs.getString(openRouterBaseUrlKey(null), null)
            ?: ""

    fun saveOpenRouterBaseUrl(baseUrl: String, userId: String? = null) {
        saveBaseUrl(openRouterBaseUrlKey(null), userId?.let { openRouterBaseUrlKey(it) }, baseUrl)
    }

    fun getGroqModelId(userId: String? = null): String =
        userId?.let { prefs.getString(groqModelKey(it), null) }
            ?: prefs.getString(groqModelKey(null), null)
            ?: AssistantProvider.GROQ.defaultModelId

    fun saveGroqModelId(modelId: String, userId: String? = null) {
        require(modelId.isNotBlank()) { "Groq model ID must not be blank" }
        val cleaned = modelId.trim()
        prefs.edit {
            putString(groqModelKey(null), cleaned)
            userId?.let { putString(groqModelKey(it), cleaned) }
        }
    }

    fun getGroqBaseUrl(userId: String? = null): String =
        userId?.let { prefs.getString(groqBaseUrlKey(it), null) }
            ?: prefs.getString(groqBaseUrlKey(null), null)
            ?: ""

    fun saveGroqBaseUrl(baseUrl: String, userId: String? = null) {
        saveBaseUrl(groqBaseUrlKey(null), userId?.let { groqBaseUrlKey(it) }, baseUrl)
    }

    fun getCustomProviderModelId(): String =
        prefs.getString(CUSTOM_PROVIDER_MODEL_KEY, null)?.trim().orEmpty()

    fun saveCustomProviderModelId(modelId: String) {
        prefs.edit { putString(CUSTOM_PROVIDER_MODEL_KEY, modelId.trim()) }
    }

    fun getCustomProviderBaseUrl(): String =
        prefs.getString(CUSTOM_PROVIDER_BASE_URL_KEY, null)?.trim().orEmpty()

    fun saveCustomProviderBaseUrl(baseUrl: String) {
        prefs.edit { putString(CUSTOM_PROVIDER_BASE_URL_KEY, baseUrl.trim()) }
    }

    private fun saveBaseUrl(deviceKey: String, accountKey: String?, baseUrl: String) {
        val cleaned = baseUrl.trim()
        prefs.edit {
            if (cleaned.isBlank()) remove(deviceKey) else putString(deviceKey, cleaned)
            accountKey?.let {
                if (cleaned.isBlank()) remove(it) else putString(it, cleaned)
            }
        }
    }

    private companion object {
        const val PREFS_NAME = "api_key_prefs"
        const val SELECTED_PROVIDER_KEY = "selected_provider"
        const val CUSTOM_PROVIDER_MODEL_KEY = "custom_provider_model"
        const val CUSTOM_PROVIDER_BASE_URL_KEY = "custom_provider_base_url"

        private fun customTtsApiKeyKey(userId: String) = "custom_tts_api_key_${userId.lowercase(Locale.ROOT)}"

        fun selectedProviderKey(userId: String?) =
            userId?.let { "selected_provider_${it.lowercase(Locale.ROOT)}" }
                ?: SELECTED_PROVIDER_KEY

        fun apiKeyKey(provider: AssistantProvider, userId: String) =
            "key_${provider.name}_${userId.lowercase(Locale.ROOT)}"

        fun cricApiKeyKey(userId: String) =
            "key_cricapi_${userId.lowercase(Locale.ROOT)}"

        fun weatherApiKeyKey(userId: String) =
            "key_weatherapi_${userId.lowercase(Locale.ROOT)}"

        fun openRouterModelKey(userId: String?) =
            userId?.let { "openrouter_model_${it.lowercase(Locale.ROOT)}" } ?: "openrouter_model"

        fun geminiModelKey(userId: String?) =
            userId?.let { "gemini_model_${it.lowercase(Locale.ROOT)}" } ?: "gemini_model"

        fun groqModelKey(userId: String?) =
            userId?.let { "groq_model_${it.lowercase(Locale.ROOT)}" } ?: "groq_model"

        fun openRouterBaseUrlKey(userId: String?) =
            userId?.let { "openrouter_base_url_${it.lowercase(Locale.ROOT)}" }
                ?: "openrouter_base_url"

        fun groqBaseUrlKey(userId: String?) =
            userId?.let { "groq_base_url_${it.lowercase(Locale.ROOT)}" } ?: "groq_base_url"
    }
}

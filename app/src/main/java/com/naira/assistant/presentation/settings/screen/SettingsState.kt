@file:Suppress("SpellCheckingInspection")

package com.naira.assistant.presentation.settings.screen

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.naira.assistant.core.sound.SpeechEngine
import com.naira.assistant.domain.settings.AccountSettingsStorage
import com.naira.assistant.domain.settings.ApiKeyStorage
import com.naira.assistant.domain.settings.AssistantLanguage
import com.naira.assistant.domain.settings.AssistantProvider
import com.naira.assistant.domain.settings.AssistantSettingsStorage
import com.naira.assistant.domain.settings.AssistantThinkingMode
import com.naira.assistant.domain.settings.Gender
import com.naira.assistant.domain.settings.InterfaceSettingsStorage
import com.naira.assistant.domain.settings.NetworkSettingsStorage
import com.naira.assistant.domain.settings.RelationType
import com.naira.assistant.domain.settings.SpeechSettingsStorage
import com.naira.assistant.domain.settings.UserSettingsStorage
import com.naira.assistant.presentation.settings.model.EditField


/* ------------------------------------------------------------------ */
/*  State holder                                                       */
/* ------------------------------------------------------------------ */

@Stable
internal class SettingsState(
    private val storage: ApiKeyStorage,
    context: Context,
    private val currentUserId: () -> String?,
) {
    private val userSettingsStorage = UserSettingsStorage()
    var theme by mutableStateOf(userSettingsStorage.getTheme())
    var fontSize by mutableStateOf(userSettingsStorage.getFontSize())
    var selectedProvider by mutableStateOf(storage.getSelectedProvider(currentUserId()))
    private var geminiKey by mutableStateOf(
        currentUserId()?.let { storage.getApiKey(AssistantProvider.GEMINI.name, it) }.orEmpty()
    )
    private var openRouterKey by mutableStateOf(
        currentUserId()?.let { storage.getApiKey(AssistantProvider.OPENROUTER.name, it) }.orEmpty()
    )
    private var groqKey by mutableStateOf(
        currentUserId()?.let { storage.getApiKey(AssistantProvider.GROQ.name, it) }.orEmpty()
    )
    private var customProviderKey by mutableStateOf(
        currentUserId()?.let { storage.getApiKey(AssistantProvider.CUSTOM.name, it) }.orEmpty()
    )
    var cricketApiKey by mutableStateOf(
        currentUserId()?.let(storage::getCricApiKey).orEmpty()
    )
    var weatherApiKey by mutableStateOf(
        currentUserId()?.let(storage::getWeatherApiKey).orEmpty()
    )
    var openRouterModelId by mutableStateOf(storage.getOpenRouterModelId(currentUserId()))
    var geminiModelId by mutableStateOf(storage.getGeminiModelId(currentUserId()))
    var openRouterBaseUrl by mutableStateOf(storage.getOpenRouterBaseUrl(currentUserId()))
    var groqModelId by mutableStateOf(storage.getGroqModelId(currentUserId()))
    var groqBaseUrl by mutableStateOf(storage.getGroqBaseUrl(currentUserId()))
    var customProviderModelId by mutableStateOf(storage.getCustomProviderModelId())
    var customProviderBaseUrl by mutableStateOf(storage.getCustomProviderBaseUrl())
    private val speechStorage = SpeechSettingsStorage(context)
    private val accountStorage = AccountSettingsStorage(context)
    private val assistantStorage = AssistantSettingsStorage(context)
    private val interfaceSettingsStorage = InterfaceSettingsStorage(context)
    private val networkSettingsStorage = NetworkSettingsStorage(context)
    var assistantLanguage by mutableStateOf(assistantStorage.getLanguage())
    var assistantThinkingMode by mutableStateOf(assistantStorage.getThinkingMode())
    var sensitiveToolAccessEnabled by mutableStateOf(assistantStorage.getSensitiveToolAccessEnabled())
    var username by mutableStateOf(accountStorage.getUserName())
    var phone by mutableStateOf(accountStorage.getPhoneNumber())
    var gender by mutableStateOf(accountStorage.getGender())
    var assistantName by mutableStateOf(assistantStorage.getName())
    var relationship by mutableStateOf(assistantStorage.getRelationship())
    var showAssistantAvatar by mutableStateOf(interfaceSettingsStorage.showAssistantAvatar())
    var codeWordWrap by mutableStateOf(interfaceSettingsStorage.codeWordWrap())
    var collapseCodeBlocks by mutableStateOf(interfaceSettingsStorage.collapseCodeBlocks())
    var showCodeLineNumbers by mutableStateOf(interfaceSettingsStorage.showCodeLineNumbers())
    var customUserAgent by mutableStateOf(networkSettingsStorage.getCustomUserAgent())
    var speechEngine by mutableStateOf(
        runCatching { SpeechEngine.valueOf(speechStorage.getEngine()) }
            .getOrDefault(SpeechEngine.SYSTEM)
    )
    fun keyFor(provider: AssistantProvider): String = when (provider) {
        AssistantProvider.GEMINI -> geminiKey
        AssistantProvider.OPENROUTER -> openRouterKey
        AssistantProvider.GROQ -> groqKey
        AssistantProvider.CUSTOM -> customProviderKey
    }

    fun saveApiKey(provider: AssistantProvider, value: String) {
        val cleaned = value.trim()
        when (provider) {
            AssistantProvider.GEMINI -> geminiKey = cleaned
            AssistantProvider.OPENROUTER -> openRouterKey = cleaned
            AssistantProvider.GROQ -> groqKey = cleaned
            AssistantProvider.CUSTOM -> customProviderKey = cleaned
        }
        currentUserId()?.let { storage.saveApiKey(provider.name, it, cleaned) }
    }

    fun saveCricketApiKey(value: String) {
        val cleaned = value.trim()
        cricketApiKey = cleaned
        currentUserId()?.let { storage.saveCricApiKey(it, cleaned) }
    }

    fun saveWeatherApiKey(value: String) {
        val cleaned = value.trim()
        weatherApiKey = cleaned
        currentUserId()?.let { storage.saveWeatherApiKey(it, cleaned) }
    }

    fun saveProvider(provider: AssistantProvider, userId: String?) {
        selectedProvider = provider
        storage.saveSelectedProvider(provider, userId)
    }

    fun saveOpenRouterModelId(value: String) {
        val cleaned = value.trim()
        storage.saveOpenRouterModelId(cleaned, currentUserId())
        openRouterModelId = cleaned
    }

    fun saveGeminiModelId(value: String) {
        val cleaned = value.trim()
        storage.saveGeminiModelId(cleaned, currentUserId())
        geminiModelId = cleaned
    }

    fun saveOpenRouterBaseUrl(value: String) {
        val cleaned = value.trim()
        storage.saveOpenRouterBaseUrl(cleaned, currentUserId())
        openRouterBaseUrl = cleaned
    }

    fun saveGroqModelId(value: String) {
        val cleaned = value.trim()
        storage.saveGroqModelId(cleaned, currentUserId())
        groqModelId = cleaned
    }

    fun saveGroqBaseUrl(value: String) {
        val cleaned = value.trim()
        storage.saveGroqBaseUrl(cleaned, currentUserId())
        groqBaseUrl = cleaned
    }

    fun saveCustomProviderModelId(value: String) {
        val cleaned = value.trim()
        storage.saveCustomProviderModelId(cleaned)
        customProviderModelId = cleaned
    }

    fun saveCustomProviderBaseUrl(value: String) {
        val cleaned = value.trim()
        storage.saveCustomProviderBaseUrl(cleaned)
        customProviderBaseUrl = cleaned
    }

    fun saveSpeechEngine(engine: SpeechEngine) {
        speechEngine = engine
        speechStorage.setEngine(engine.name)
    }

    fun saveTheme(value: String) {
        val selected = when (value.lowercase()) {
            "light" -> "light"
            "dark" -> "dark"
            else -> "system"
        }
        userSettingsStorage.setTheme(selected)
        theme = selected
    }

    fun saveAssistantLanguage(value: AssistantLanguage) {
        assistantLanguage = value
        assistantStorage.setLanguage(value)
    }

    fun saveAssistantThinkingMode(value: AssistantThinkingMode) {
        assistantThinkingMode = value
        assistantStorage.setThinkingMode(value)
    }

    fun saveSensitiveToolAccess(enabled: Boolean) {
        sensitiveToolAccessEnabled = enabled
        assistantStorage.setSensitiveToolAccessEnabled(enabled)
    }

    fun saveShowAssistantAvatar(enabled: Boolean) {
        showAssistantAvatar = enabled
        interfaceSettingsStorage.setShowAssistantAvatar(enabled)
    }

    fun saveCodeWordWrap(enabled: Boolean) {
        codeWordWrap = enabled
        interfaceSettingsStorage.setCodeWordWrap(enabled)
    }

    fun saveCollapseCodeBlocks(enabled: Boolean) {
        collapseCodeBlocks = enabled
        interfaceSettingsStorage.setCollapseCodeBlocks(enabled)
    }

    fun saveShowCodeLineNumbers(enabled: Boolean) {
        showCodeLineNumbers = enabled
        interfaceSettingsStorage.setShowCodeLineNumbers(enabled)
    }

    fun saveCustomUserAgent(value: String) {
        val cleaned = value.trim()
        networkSettingsStorage.setCustomUserAgent(cleaned)
        customUserAgent = cleaned
    }

    fun saveProfileField(field: EditField, value: String) {
        val trimmed = value.trim()
        when (field) {
            EditField.USERNAME -> { username = trimmed; accountStorage.setUserName(trimmed) }
            EditField.PHONE -> { phone = trimmed; accountStorage.setPhoneNumber(trimmed) }
            else -> Unit
        }
    }

    fun saveAssistantName(value: String) {
        val trimmed = value.trim()
        assistantName = trimmed
        assistantStorage.setName(trimmed)
    }

    fun saveGender(value: Gender) {
        gender = value
        accountStorage.setGender(value)
    }

    fun saveRelationship(value: RelationType) {
        relationship = value
        assistantStorage.setRelationship(value)
    }

    fun refreshCloudBackedSettings() {
        val userId = currentUserId()
        selectedProvider = storage.getSelectedProvider(userId)
        geminiModelId = storage.getGeminiModelId(userId)
        openRouterModelId = storage.getOpenRouterModelId(userId)
        openRouterBaseUrl = storage.getOpenRouterBaseUrl(userId)
        groqModelId = storage.getGroqModelId(userId)
        groqBaseUrl = storage.getGroqBaseUrl(userId)
        assistantLanguage = assistantStorage.getLanguage()
        assistantThinkingMode = assistantStorage.getThinkingMode()
        speechEngine = runCatching { SpeechEngine.valueOf(speechStorage.getEngine()) }
            .getOrDefault(SpeechEngine.SYSTEM)
        username = accountStorage.getUserName()
        phone = accountStorage.getPhoneNumber()
        gender = accountStorage.getGender()
        assistantName = assistantStorage.getName()
        relationship = assistantStorage.getRelationship()
        theme = userSettingsStorage.getTheme()
        fontSize = userSettingsStorage.getFontSize()
    }
}

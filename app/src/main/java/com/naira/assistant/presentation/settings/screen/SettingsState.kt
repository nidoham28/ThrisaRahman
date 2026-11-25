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
import com.naira.assistant.domain.settings.BackgroundWakeWordSettingsStorage
import com.naira.assistant.domain.settings.AssistantThinkingMode
import com.naira.assistant.domain.settings.EmailContactStorage
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
    private val speechStorage = SpeechSettingsStorage(context)
    private val emailContactStorage = EmailContactStorage(context)
    private val backgroundWakeWordSettingsStorage = BackgroundWakeWordSettingsStorage(context)
    var theme by mutableStateOf(userSettingsStorage.getTheme())
    var fontSize by mutableStateOf(userSettingsStorage.getFontSize())
    var selectedProvider by mutableStateOf(storage.getSelectedProvider(currentUserId()))
    private var geminiKey by mutableStateOf(
        currentUserId()?.let { storage.getApiKey(AssistantProvider.GEMINI.name, it) }.orEmpty()
    )
    private var openRouterKey by mutableStateOf(
        currentUserId()?.let { storage.getApiKey(AssistantProvider.OPENROUTER.name, it) }.orEmpty()
    )
    private var tokensBdKey by mutableStateOf(
        currentUserId()?.let { storage.getApiKey(AssistantProvider.TOKENS_BD.name, it) }.orEmpty()
    )
    private var customProviderKey by mutableStateOf(
        currentUserId()?.let { storage.getApiKey(AssistantProvider.CUSTOM.name, it) }.orEmpty()
    )
    var customTtsApiKey by mutableStateOf(currentUserId()?.let(storage::getCustomTtsApiKey).orEmpty())
    var cricketApiKey by mutableStateOf(
        currentUserId()?.let(storage::getCricApiKey).orEmpty()
    )
    var weatherApiKey by mutableStateOf(
        currentUserId()?.let(storage::getWeatherApiKey).orEmpty()
    )
    var openRouterModelId by mutableStateOf(storage.getOpenRouterModelId(currentUserId()))
    var geminiModelId by mutableStateOf(storage.getGeminiModelId(currentUserId()))
    var openRouterBaseUrl by mutableStateOf(storage.getOpenRouterBaseUrl(currentUserId()))
    var customProviderModelId by mutableStateOf(storage.getCustomProviderModelId())
    var customProviderBaseUrl by mutableStateOf(storage.getCustomProviderBaseUrl())
    var emailContacts by mutableStateOf(emailContactStorage.getContacts(currentUserId()))
        private set
    var customTtsModel by mutableStateOf(speechStorage.getCustomTtsModel())
    var customTtsBaseUrl by mutableStateOf(speechStorage.getCustomTtsBaseUrl())
    private val accountStorage = AccountSettingsStorage(context)
    private val assistantStorage = AssistantSettingsStorage(context)
    private val interfaceSettingsStorage = InterfaceSettingsStorage(context)
    private val networkSettingsStorage = NetworkSettingsStorage(context)
    var assistantLanguage by mutableStateOf(assistantStorage.getLanguage())
    var assistantThinkingMode by mutableStateOf(assistantStorage.getThinkingMode())
    var sensitiveToolAccessEnabled by mutableStateOf(assistantStorage.getSensitiveToolAccessEnabled())
    var username by mutableStateOf(accountStorage.getUserName())
    var email by mutableStateOf(accountStorage.getEmail())
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
    var wakeWordDetectionThreshold by mutableStateOf(
        backgroundWakeWordSettingsStorage.getDetectionThreshold(),
    )
    var wakeWordServiceEnabled by mutableStateOf(backgroundWakeWordSettingsStorage.isServiceEnabled())
    var geminiTtsModel by mutableStateOf(speechStorage.getGeminiTtsModel())
    var geminiTtsVoice by mutableStateOf(speechStorage.getGeminiTtsVoice())
    var customTtsVoice by mutableStateOf(speechStorage.getCustomTtsVoice())

    fun saveGeminiTtsModel(value: String) {
        speechStorage.setGeminiTtsModel(value)
        geminiTtsModel = speechStorage.getGeminiTtsModel()
    }

    fun saveGeminiTtsVoice(value: String) {
        speechStorage.setGeminiTtsVoice(value)
        geminiTtsVoice = speechStorage.getGeminiTtsVoice()
    }

    fun saveCustomTtsVoice(value: String) {
        speechStorage.setCustomTtsVoice(value)
        customTtsVoice = speechStorage.getCustomTtsVoice()
    }

    fun saveWakeWordDetectionThreshold(value: Float) {
        backgroundWakeWordSettingsStorage.setDetectionThreshold(value)
        wakeWordDetectionThreshold = backgroundWakeWordSettingsStorage.getDetectionThreshold()
    }

    fun saveWakeWordServiceEnabled(enabled: Boolean) {
        backgroundWakeWordSettingsStorage.setServiceEnabled(enabled)
        wakeWordServiceEnabled = backgroundWakeWordSettingsStorage.isServiceEnabled()
    }
    fun keyFor(provider: AssistantProvider): String = when (provider) {
        AssistantProvider.GEMINI -> geminiKey
        AssistantProvider.OPENROUTER -> openRouterKey
        AssistantProvider.TOKENS_BD -> tokensBdKey
        AssistantProvider.CUSTOM -> customProviderKey
    }

    /** Model ID currently in effect for [provider], falling back to its default. */
    fun modelIdFor(provider: AssistantProvider): String = when (provider) {
        AssistantProvider.GEMINI -> geminiModelId.trim()
            .ifEmpty { provider.info.defaultModelId }

        AssistantProvider.OPENROUTER -> openRouterModelId.trim()
            .ifEmpty { provider.info.defaultModelId }

        AssistantProvider.TOKENS_BD -> provider.info.defaultModelId
        // The custom placeholder ("custom-model") is not a real model, so an
        // unset custom provider must stay empty rather than look configured.
        AssistantProvider.CUSTOM -> customProviderModelId.trim()
    }

    fun saveModelId(provider: AssistantProvider, value: String) {
        if (value.isBlank()) return
        when (provider) {
            AssistantProvider.GEMINI -> saveGeminiModelId(value)
            AssistantProvider.OPENROUTER -> saveOpenRouterModelId(value)
            AssistantProvider.TOKENS_BD -> Unit
            AssistantProvider.CUSTOM -> saveCustomProviderModelId(value)
        }
    }

    fun saveApiKey(provider: AssistantProvider, value: String) {
        val cleaned = value.trim()
        when (provider) {
            AssistantProvider.GEMINI -> geminiKey = cleaned
            AssistantProvider.OPENROUTER -> openRouterKey = cleaned
            AssistantProvider.TOKENS_BD -> tokensBdKey = cleaned
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

    fun saveEmailContact(name: String, email: String, replacingEmail: String? = null) {
        emailContacts = emailContactStorage.saveContact(
            name = name,
            email = email,
            userId = currentUserId(),
            replacingEmail = replacingEmail,
        )
    }

    fun removeEmailContact(email: String) {
        emailContacts = emailContactStorage.removeContact(email, currentUserId())
    }

    fun refreshEmailContacts() {
        emailContacts = emailContactStorage.getContacts(currentUserId())
    }

    fun saveCustomTtsApiKey(value: String) {
        val cleaned = value.trim()
        customTtsApiKey = cleaned
        currentUserId()?.let { storage.saveCustomTtsApiKey(it, cleaned) }
    }

    fun saveCustomTtsModel(value: String) {
        speechStorage.setCustomTtsModel(value)
        customTtsModel = speechStorage.getCustomTtsModel()
    }

    fun saveCustomTtsBaseUrl(value: String) {
        speechStorage.setCustomTtsBaseUrl(value)
        customTtsBaseUrl = speechStorage.getCustomTtsBaseUrl()
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
            EditField.EMAIL -> { email = trimmed; accountStorage.setEmail(trimmed) }
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
        assistantLanguage = assistantStorage.getLanguage()
        assistantThinkingMode = assistantStorage.getThinkingMode()
        speechEngine = runCatching { SpeechEngine.valueOf(speechStorage.getEngine()) }
            .getOrDefault(SpeechEngine.SYSTEM)
        username = accountStorage.getUserName()
        email = accountStorage.getEmail()
        phone = accountStorage.getPhoneNumber()
        gender = accountStorage.getGender()
        assistantName = assistantStorage.getName()
        relationship = assistantStorage.getRelationship()
        theme = userSettingsStorage.getTheme()
        fontSize = userSettingsStorage.getFontSize()
    }
}

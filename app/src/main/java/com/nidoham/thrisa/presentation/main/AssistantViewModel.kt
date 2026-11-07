package com.nidoham.thrisa.presentation.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nidoham.thrisa.ai.AssistantApiService
import com.nidoham.thrisa.core.prompt.Instructions
import com.nidoham.thrisa.core.prompt.InstructionsConfig
import com.nidoham.thrisa.core.service.image.InlineImage
import com.nidoham.thrisa.core.service.SupabaseAuthService
import com.nidoham.thrisa.core.sync.SupabaseApiKeyStore
import com.nidoham.thrisa.core.tools.executor.BuiltinToolExecutor
import com.nidoham.thrisa.core.tools.executor.ImageToolExecutor
import com.nidoham.thrisa.core.tools.executor.ScreenToolExecutor
import com.nidoham.thrisa.domain.ApiKeyStorage
import com.nidoham.thrisa.domain.AssistantProvider
import com.nidoham.thrisa.domain.AccountSettingsStorage
import com.nidoham.thrisa.domain.AssistantLanguage
import com.nidoham.thrisa.domain.AssistantSettingsStorage
import com.nidoham.thrisa.domain.UserSettingsStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.agent.AgentEvent
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage as AgentMessage
import me.rerere.ai.ui.UIMessagePart as AgentPart
import com.nidoham.thrisa.domain.FunctionCall
import androidx.core.net.toUri

/** One bubble in the chat UI. Kept separate from the AI module's message model. */
data class UiMessage(
    val id: Long,
    val text: String,
    val isUser: Boolean,
    val isError: Boolean = false,
    val imageUri: String? = null,
    val imageSavedTo: String? = null,
)

internal fun resolveAssistantModelId(
    provider: AssistantProvider,
    configuredOpenRouterModelId: String?,
): String = when (provider) {
    AssistantProvider.GEMINI -> AssistantApiService.DEFAULT_MODEL_ID
    AssistantProvider.OPENROUTER -> configuredOpenRouterModelId
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: AssistantApiService.DEFAULT_MODEL_OPENROUTER
}

/** OpenRouter response bodies that mean "the request carried no usable API key". */
private val OPENROUTER_KEY_REJECTED = listOf(
    "Missing Authentication header",
    "No auth credentials found",
    "No cookie auth credentials found",
    "User not found",
)

/** Translates a chat failure into the text shown in the error bubble. */
internal fun assistantErrorText(t: Throwable, provider: AssistantProvider): String {
    val message = t.message.orEmpty()
    return when {
        // OpenRouter returns these bodies for unparseable or unknown keys;
        // show what a correct key looks like instead of the raw API text.
        provider == AssistantProvider.OPENROUTER &&
            OPENROUTER_KEY_REJECTED.any { message.contains(it, ignoreCase = true) } ->
            "OpenRouter rejected your API key. Save a valid key starting with " +
                "sk-or- in Settings → AI Providers → Credentials.\n$message"
        message.contains("401") || message.contains("403") ->
            "Authentication failed. Check your ${provider.displayName} API key.\n$message"
        message.contains("429") || message.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ->
            "${provider.displayName} rate limit reached. Try again later.\n$message"
        message.contains("timeout", ignoreCase = true) || t is java.io.IOException ->
            "Network error. Check your connection and try again.\n$message"
        message.isNotBlank() -> message
        else -> "Unexpected error: ${t::class.simpleName}"
    }
}

/**
 * Bridges the AI service and the Compose UI.
 *
 * The system prompt is built from [Instructions] — persona + rules only.
 * Everything runtime-specific (tool definitions, conversation history, the
 * user's message) travels to the service as separate arguments; nothing is
 * duplicated inside the prompt.
 *
 * Existing behaviour preserved:
 *  - the API key comes from the active provider's PIN-unlocked saved credentials,
 *  - every failure is caught and translated into a readable error bubble,
 *  - tool progress is surfaced through agent events into
 *    [busyStatus] so the UI can show what the assistant is doing right now,
 *  - in-flight requests can be cancelled with [cancelCurrent].
 */
class AssistantViewModel(
    context: Context,
) : ViewModel() {

    private val appContext = context.applicationContext
    private val assistantApi = AssistantApiService(appContext)
    private val apiKeyStorage = ApiKeyStorage(appContext)
    private val authService = SupabaseAuthService()
    private val apiKeySync = SupabaseApiKeyStore()
    private val modelHistory = mutableListOf<AgentMessage>()
    private val accountStorage = AccountSettingsStorage(appContext)
    private val assistantSettingsStorage = AssistantSettingsStorage(appContext)
    private val userSettingsStorage = UserSettingsStorage()
    private var activeLanguage: AssistantLanguage = assistantSettingsStorage.getLanguage()
    private var systemInstruction = buildInstructions()

    /** Tool executor with access to the Android context (needed for device/battery info). */
    private val toolExecutor = BuiltinToolExecutor.executor(appContext)

    private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> = _messages.asStateFlow()

    /** Null = idle; non-null = a one-line "what is happening right now" status. */
    private val _busyStatus = MutableStateFlow<String?>(null)
    val busyStatus: StateFlow<String?> = _busyStatus.asStateFlow()

    private val sessionMoods = mutableListOf<String>()
    private val _currentSessionMood = MutableStateFlow<String?>(null)
    val currentSessionMood: StateFlow<String?> = _currentSessionMood.asStateFlow()

    private var currentJob: Job? = null
    private var nextId = 0L
    private var chatGeneration = 0L
    private var activeApiKey: String? = null
    private var activeProvider = AssistantProvider.GEMINI
    private var activeModelId = AssistantApiService.DEFAULT_MODEL_ID

    init { refreshSettings() }

    private fun buildInstructions(): String {
        val base = Instructions(
            InstructionsConfig(
                assistantName = assistantSettingsStorage.getName(),
                language = activeLanguage.code,
                relationship = assistantSettingsStorage.getRelationship().id,
                userName = accountStorage.getDisplayName().ifBlank { accountStorage.getUserName() },
                userContext = "",
                voiceMode = userSettingsStorage.getVoiceOutputEnabled(),
            )
        ).build()
        return """
            $base

            For every final answer to the user, return exactly one valid JSON object with exactly
            these two string fields and no other fields or markdown:
            {"response":"the complete answer for the user","mood":"Happy"}
            Write the response naturally in the user's configured language. The mood describes
            your own present conversational tone, not an inferred mood of the user. Choose exactly
            one mood label from: ${assistantMoods.joinToString(", ")}.
            Keep all user-facing answer text inside response. Never speak or show the JSON wrapper
            to the user; response is the only content for chat and voice.
        """.trimIndent()
    }

    /** Reload the cloud-backed assistant language when returning from settings. */
    fun refreshAssistantIdentity() {
        activeLanguage = assistantSettingsStorage.getLanguage()
        systemInstruction = buildInstructions()
    }

    /** Reload cloud language and saved API key when returning from settings. */
    fun refreshSettings() {
        refreshAssistantIdentity()
        refreshApiKey()
    }

    /** Reload the saved key after returning from provider settings. */
    fun refreshApiKey() {
        val userId = authService.currentUser()?.id
        activeProvider = apiKeyStorage.getSelectedProvider(userId)
        val configuredOpenRouterModelId =
            if (activeProvider == AssistantProvider.OPENROUTER) {
                apiKeyStorage.getOpenRouterModelId(userId)
            } else {
                null
            }
        activeModelId = resolveAssistantModelId(activeProvider, configuredOpenRouterModelId)
        val savedKey = userId
            ?.let { apiKeyStorage.getApiKey(activeProvider.name, it) }
            .orEmpty()
            .trim()
        activeApiKey = savedKey.takeIf { it.isNotEmpty() }
        pullCloudApiKeys()
    }

    /**
     * The cloud (`user_provider_keys`) is the source of truth for API keys:
     * pull keys saved on other devices into the local cache, then reload.
     */
    private fun pullCloudApiKeys() {
        viewModelScope.launch {
            val userId = authService.currentUser()?.id ?: return@launch
            val cloudKeys = apiKeySync.fetchAll()
            if (cloudKeys.isEmpty()) return@launch
            var changed = false
            for ((provider, key) in cloudKeys) {
                if (apiKeyStorage.getApiKey(provider.name, userId) != key) {
                    apiKeyStorage.saveApiKey(provider.name, userId, key)
                    changed = true
                }
            }
            if (changed) refreshApiKey()
        }
    }

    /** Non-streaming, text-only variant. */
    fun sendMessage(message: String) = runChat(message, streaming = false)

    /**
     * Streaming variant — the assistant bubble grows in real time. Attach
     * [imageUri] (content:// or file://) to make the message multimodal: the
     * model sees the picture (image → text) and can transform it through the
     * generate_image tool when the request is an edit (image → image).
     */
    fun sendMessageStream(message: String, imageUri: String? = null) =
        runChat(message, streaming = true, imageUri = imageUri)

    /** Aborts the in-flight request (safe to call when idle). */
    fun cancelCurrent() {
        currentJob?.cancel()
    }

    /** Wipes the conversation and the Gemini turn history. */
    fun newChat() {
        chatGeneration++
        currentJob?.cancel()
        modelHistory.clear()
        sessionMoods.clear()
        _currentSessionMood.value = null
        _messages.value = emptyList()
        _busyStatus.value = null
    }

    /**
     * Call from `DisposableEffect { onDispose { ... } }` - `remember { ... }`
     * ViewModels never reach [onCleared].
     */
    fun dispose() {
        assistantApi.close()
        activeApiKey = null
    }

    override fun onCleared() {
        assistantApi.close()
    }

    // ----------------------------------------------------------------- internals

    private fun runChat(message: String, streaming: Boolean, imageUri: String? = null) {
        if (message.isBlank() && imageUri == null) return
        if (_busyStatus.value != null) return // one request at a time

        val userId = nextMessageId()
        _messages.update { it + UiMessage(userId, message, isUser = true, imageUri = imageUri) }

        val assistantId = nextMessageId()
        if (streaming) _messages.update { it + UiMessage(assistantId, "", isUser = false) }

        _busyStatus.value = "Thinking..."
        val requestGeneration = chatGeneration
        val provider = activeProvider
        val modelId = activeModelId
        var generatedImageUri: String? = null
        var generatedImageSavedTo: String? = null
        var finishedAssistantResponse: AssistantResponse? = null
        currentJob = viewModelScope.launch {
            try {
                val apiKey = activeApiKey
                if (apiKey.isNullOrBlank()) {
                    val userId = authService.currentUser()?.id
                    val message = when {
                        userId == null -> "Sign in to use your saved ${provider.displayName} API key."
                        else -> "Add a ${provider.displayName} API key in Settings → AI Providers → Credentials."
                    }
                    throw IllegalStateException(message)
                }
                if (modelId.isBlank()) {
                    throw IllegalStateException(
                        "Set an OpenRouter model ID in Settings → AI Providers → OpenRouter Model."
                    )
                }

                var attachedImage: InlineImage.Loaded? = null
                if (imageUri != null) {
                    attachedImage = withContext(Dispatchers.IO) {
                        InlineImage.load(appContext, imageUri.toUri())
                    }
                    if (requestGeneration != chatGeneration) return@launch
                }

                val userParts = buildList {
                    if (message.isNotBlank()) add(AgentPart.Text(message))
                    attachedImage?.let { image ->
                        add(AgentPart.Image("data:${image.mimeType};base64,${image.base64()}"))
                    }
                }
                val userTurn = AgentMessage(role = MessageRole.USER, parts = userParts)
                val requestHistory = modelHistory.toList() + userTurn
                var finishedAssistantMessage: AgentMessage? = null
                val executeTool: suspend (FunctionCall) -> com.nidoham.thrisa.ai.AssistantToolResult =
                    { call ->
                        when (call.name) {
                            "generate_image" -> com.nidoham.thrisa.ai.AssistantToolResult(
                                response = ImageToolExecutor.executeGenerateImage(call, appContext, attachedImage),
                            )
                            "capture_screen" -> {
                                val response = ScreenToolExecutor.executeCaptureScreen(call, appContext)
                                com.nidoham.thrisa.ai.AssistantToolResult(
                                    response = response,
                                    image = ScreenToolExecutor.consumeLastCapture(),
                                )
                            }
                            "perform_screen_action" -> {
                                val response = ScreenToolExecutor.executePerformScreenAction(call, appContext)
                                com.nidoham.thrisa.ai.AssistantToolResult(
                                    response = response,
                                    image = ScreenToolExecutor.consumeLastCapture(),
                                )
                            }
                            else -> com.nidoham.thrisa.ai.AssistantToolResult(toolExecutor(call))
                        }
                    }

                assistantApi.streamChat(
                    apiKey = apiKey,
                    messages = requestHistory,
                    systemPrompt = systemInstruction,
                    toolDefinitions = BuiltinToolExecutor.definitions,
                    executeTool = executeTool,
                    streaming = streaming,
                    provider = provider,
                    modelId = modelId,
                ).collect { event ->
                    val assistantMessage = when (event) {
                        is AgentEvent.MessageUpdated -> event.message
                        is AgentEvent.Finished -> event.message
                        is AgentEvent.Retrying -> null
                    }
                    if (assistantMessage != null) {
                        if (event is AgentEvent.Finished) finishedAssistantMessage = assistantMessage
                        val decodedResponse = decodeAssistantResponse(
                            raw = assistantMessage.toText(),
                            isFinal = event is AgentEvent.Finished,
                        )
                        if (event is AgentEvent.Finished && requestGeneration == chatGeneration) {
                            finishedAssistantResponse = decodedResponse
                            decodedResponse?.mood?.let { mood ->
                                sessionMoods += mood
                                _currentSessionMood.value = mostFrequentAssistantMood(sessionMoods)
                            }
                        }
                        if (streaming) {
                            setText(assistantId, decodedResponse?.response.orEmpty())
                        }
                        assistantMessage.getTools().forEach { tool ->
                            val output = tool.output.filterIsInstance<AgentPart.Text>()
                                .firstOrNull()?.text.orEmpty()
                            val response = runCatching { Json.parseToJsonElement(output).jsonObject }.getOrNull()
                            val toolError = response?.get("error")?.jsonPrimitive?.contentOrNull
                            if (!toolError.isNullOrBlank() && requestGeneration == chatGeneration) {
                                _busyStatus.value = "Tool ${tool.toolName} failed: $toolError"
                            }
                            val imageUri = response?.get("image_uri")?.jsonPrimitive?.contentOrNull
                            if (imageUri != null) {
                                if (tool.toolName == "generate_image") {
                                    generatedImageUri = imageUri
                                    generatedImageSavedTo = response["saved_to"]?.jsonPrimitive?.contentOrNull
                                } else if (tool.toolName == "capture_screen" ||
                                    tool.toolName == "perform_screen_action"
                                ) {
                                    generatedImageUri = imageUri
                                    generatedImageSavedTo = null
                                }
                            }
                            if (!tool.isExecuted && requestGeneration == chatGeneration) {
                                _busyStatus.value = "Running tool: ${tool.toolName}..."
                            }
                        }
                        _messages.update { list ->
                            list.map {
                                if (it.id == assistantId) it.copy(
                                    text = if (streaming) it.text
                                    else decodedResponse?.response.orEmpty(),
                                    imageUri = generatedImageUri ?: it.imageUri,
                                    imageSavedTo = generatedImageSavedTo ?: it.imageSavedTo,
                                ) else it
                            }
                        }
                        if (event is AgentEvent.Finished) _busyStatus.value = null
                    }
                }

                finishedAssistantMessage?.let { finished ->
                    modelHistory.clear()
                    modelHistory += requestHistory
                    modelHistory += finished
                }
                if (!streaming) {
                    _messages.update {
                        it + UiMessage(
                            assistantId, finishedAssistantResponse?.response.orEmpty(), isUser = false,
                            imageUri = generatedImageUri,
                            imageSavedTo = generatedImageSavedTo,
                        )
                    }
                }
            } catch (_: CancellationException) {
                if (streaming) markCancelled(assistantId)
            } catch (t: Throwable) {
                if (requestGeneration == chatGeneration) showError(assistantId, streaming, t, provider)
            } finally {
                if (requestGeneration == chatGeneration) {
                    _busyStatus.value = null
                    currentJob = null
                }
            }
        }
    }

    private fun showError(
        assistantId: Long,
        streaming: Boolean,
        t: Throwable,
        provider: AssistantProvider,
    ) {
        val text = friendlyMessage(t, provider)
        _messages.update { list ->
            val existing = list.firstOrNull { it.id == assistantId }
            if (existing == null) {
                // Non-streaming: turn the reserved id into an error bubble.
                list + UiMessage(assistantId, text, isUser = false, isError = true)
            } else {
                // Streaming: keep any partial answer and append the failure.
                list.map {
                    if (it.id != assistantId) it
                    else it.copy(
                        text = if (it.text.isBlank()) text else it.text + "\n\n" + text,
                        isError = true,
                    )
                }
            }
        }
    }

    private fun markCancelled(id: Long) {
        _messages.update { list ->
            list.map {
                if (it.id != id) it
                else it.copy(text = if (it.text.isBlank()) "(cancelled)" else it.text + " (cancelled)")
            }
        }
    }

    private fun appendTo(id: Long, chunk: String) {
        if (chunk.isEmpty()) return
        _messages.update { list -> list.map { if (it.id == id) it.copy(text = it.text + chunk) else it } }
    }

    private fun setText(id: Long, text: String) {
        _messages.update { list -> list.map { if (it.id == id) it.copy(text = text) else it } }
    }

    private fun nextMessageId(): Long = nextId++

    private fun friendlyMessage(t: Throwable, provider: AssistantProvider): String =
        assistantErrorText(t, provider)
}

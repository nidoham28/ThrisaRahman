package com.naira.assistant.presentation.main

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naira.assistant.ai.AgentOrchestrator
import com.naira.assistant.ai.DelegationProgress
import com.naira.assistant.ai.AssistantApiService
import com.naira.assistant.ai.toReasoningLevel
import com.naira.assistant.core.prompt.Instructions
import com.naira.assistant.core.prompt.character.util.Character
import com.naira.assistant.core.prompt.character.util.CharacterGender
import com.naira.assistant.core.prompt.character.util.CharacterType
import com.naira.assistant.core.prompt.user.User
import com.naira.assistant.core.service.image.InlineImage
import com.naira.assistant.core.service.FirebaseAuthService
import com.naira.assistant.core.sync.FirebaseApiKeyStore
import com.naira.assistant.core.tools.CommandExecution
import com.naira.assistant.core.tools.ToolCallDispatcher
import com.naira.assistant.domain.settings.ApiKeyStorage
import com.naira.assistant.domain.settings.AssistantProvider
import com.naira.assistant.domain.settings.AssistantProviderBaseUrlPolicy
import com.naira.assistant.domain.settings.SensitiveContentSettingsStorage
import com.naira.assistant.domain.settings.AccountSettingsStorage
import com.naira.assistant.domain.settings.AssistantCharacter
import com.naira.assistant.domain.settings.AssistantLanguage
import com.naira.assistant.domain.settings.AssistantSettingsStorage
import com.naira.assistant.domain.settings.Gender
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.agent.AgentEvent
import me.rerere.ai.agent.AgentFinishReason
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.ui.UIMessage as AgentMessage
import me.rerere.ai.ui.UIMessagePart as AgentPart
import androidx.core.net.toUri
import com.naira.assistant.ai.AssistantToolResult
import com.naira.assistant.ai.compactConversationHistory
import java.util.concurrent.atomic.AtomicInteger

/** One bubble in the chat UI. Kept separate from the AI module's message model. */
data class UiMessage(
    val id: Long,
    val text: String,
    val isUser: Boolean,
    val isError: Boolean = false,
    val imageUri: String? = null,
    val imageSavedTo: String? = null,
    val toolCalls: List<UiToolCall> = emptyList(),
)

/** One tool call performed during an assistant turn, for display in the chat bubble. */
data class UiToolCall(
    val id: String,
    val name: String,
    val input: String,
    val isRunning: Boolean,
    val isError: Boolean,
    val result: String,
)

private data class AgentRunContext(
    val assistantId: Long,
    val requestHistory: List<AgentMessage>,
    val provider: AssistantProvider,
    val modelId: String,
    val providerBaseUrl: String?,
    val apiKey: String,
    val reasoningLevel: ReasoningLevel,
    val streaming: Boolean,
    val requestGeneration: Long,
    val attachedImage: InlineImage.Loaded?,
    var generatedImageUri: String? = null,
    var generatedImageSavedTo: String? = null,
)

private val toolDisplayJson = Json { prettyPrint = true }
private const val AGENT_STEP_LIMIT_MESSAGE =
    "I reached the tool-call limit for this turn before completing the request. Please ask me to continue or split the task."

/** Pretty-prints a JSON string; returns null when the text is not valid JSON. */
private fun String.toPrettyJsonOrNull(): String? = runCatching {
    toolDisplayJson.encodeToString(
        kotlinx.serialization.json.JsonElement.serializer(),
        Json.parseToJsonElement(this),
    )
}.getOrNull()

/**
 * Maps the AI module's tool parts into the flat display model shown in chat
 * bubbles. Pure function (no Android deps) so it can be unit tested.
 *
 * While a call is still streaming, its input is partial JSON that fails to
 * parse — the raw text is kept as-is in that case.
 */
internal fun List<AgentPart.Tool>.toUiToolCalls(): List<UiToolCall> = map { tool ->
    val rawResult = tool.output.filterIsInstance<AgentPart.Text>()
        .joinToString(separator = "\n") { it.text }
        .trim()
    val parsed = runCatching { Json.parseToJsonElement(rawResult.ifBlank { "{}" }).jsonObject }
        .getOrNull()
    val specialistFailure = (parsed?.get("specialists") as? JsonArray)
        ?.any { specialist ->
            (specialist as? JsonObject)
                ?.get("error")
                ?.jsonPrimitive
                ?.booleanOrNull == true
        } == true
    val executionFailure =
        (parsed?.get("success") as? JsonPrimitive)?.booleanOrNull == false
    UiToolCall(
        id = tool.toolCallId,
        name = tool.toolName,
        input = tool.input.toPrettyJsonOrNull() ?: tool.input,
        isRunning = !tool.isExecuted,
        isError = parsed?.containsKey("error") == true ||
                executionFailure ||
                specialistFailure,
        result = when {
            rawResult.isBlank() -> ""
            else -> rawResult.toPrettyJsonOrNull() ?: rawResult
        },
    )
}

internal fun String.withSensitiveToolAccessNotice(enabled: Boolean): String =
    if (enabled) {
        this
    } else {
        "$this\n\nSome sensitive device, screen, communication, and data actions are disabled " +
                "by the user's privacy setting. Do not claim to have performed those actions. " +
                "Tell the user they can enable 'Sensitive assistant actions' in Settings > Permissions."
    }

internal fun resolveAssistantModelId(
    provider: AssistantProvider,
    configuredOpenRouterModelId: String?,
    configuredCustomModelId: String? = null,
    configuredGeminiModelId: String? = null,
): String {
    val configuredModelId = when (provider) {
        AssistantProvider.GEMINI -> configuredGeminiModelId
        AssistantProvider.OPENROUTER -> configuredOpenRouterModelId
        AssistantProvider.TOKENS_BD -> null
        AssistantProvider.CUSTOM -> configuredCustomModelId
    }
    return configuredModelId
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: provider.info.fallbackModelId
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
                    "sk-or- in Settings → Providers & API Keys → API Keys.\n$message"
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

internal fun assistantResponseText(
    response: String?,
    currentText: String,
    stepLimitReached: Boolean,
): String {
    response?.takeIf(String::isNotBlank)?.let { return it }
    if (!stepLimitReached) return currentText
    return if (currentText.isBlank()) {
        AGENT_STEP_LIMIT_MESSAGE
    } else {
        "$currentText\n\n$AGENT_STEP_LIMIT_MESSAGE"
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
 * Existing behavior preserved:
 *  - the API key comes from the active provider's PIN-unlocked saved credentials,
 *  - every failure is caught and translated into a readable error bubble,
 *  - tool progress is surfaced through agent events into
 *    [busyStatus] so the UI can show what the assistant is doing right now,
 *  - in-flight requests can be canceled with [cancelCurrent].
 */
class AssistantViewModel(
    context: Context,
) : ViewModel() {

    private val appContext = context.applicationContext
    private val assistantApi = AssistantApiService(appContext)
    private val apiKeyStorage = ApiKeyStorage(appContext)
    private val sensitiveContentSettingsStorage = SensitiveContentSettingsStorage(appContext)
    private val authService = FirebaseAuthService()
    private val apiKeySync = FirebaseApiKeyStore()
    private val modelHistory = mutableListOf<AgentMessage>()
    private val accountStorage = AccountSettingsStorage(appContext)
    private val assistantSettingsStorage = AssistantSettingsStorage(appContext)
    private var activeLanguage: AssistantLanguage = assistantSettingsStorage.getLanguage()

    /** Tool executor with access to the Android context (needed for device/battery info). */
    private val toolExecutor = CommandExecution.executor(appContext)

    private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> = _messages.asStateFlow()

    /** Null = idle; non-null = a one-line "what is happening right now" status. */
    private val _busyStatus = MutableStateFlow<String?>(null)
    val busyStatus: StateFlow<String?> = _busyStatus.asStateFlow()
    private val sessionMoods = mutableListOf<String>()
    private val _currentSessionMood = MutableStateFlow<String?>(null)
    val currentSessionMood: StateFlow<String?> = _currentSessionMood.asStateFlow()

    private val _reduceSensitiveContent = MutableStateFlow(
        sensitiveContentSettingsStorage.getReduceSensitiveContent(authService.currentUser()?.uid)
    )

    private var currentJob: Job? = null
    private var nextId = 0L
    private var chatGeneration = 0L
    private var activeApiKey: String? = null
    private var activeProvider = AssistantProvider.GEMINI
    private var activeModelId = AssistantProvider.GEMINI.info.fallbackModelId
    private var activeProviderBaseUrl: String? = null

    init { refreshSettings() }

    private fun buildInstructions(): String {
        val configuredCharacter = assistantSettingsStorage.getCharacter()
        val character = Character(
            name = assistantSettingsStorage.getName(),
            type = when (configuredCharacter) {
                AssistantCharacter.VENOM -> CharacterType.MONSTER
                AssistantCharacter.NAIRA, AssistantCharacter.JERVIS -> CharacterType.REALISTIC
            },
            gender = when (configuredCharacter.gender.lowercase()) {
                "female" -> CharacterGender.FEMALE
                "male" -> CharacterGender.MALE
                else -> CharacterGender.CUSTOM
            },
            personality = listOf(configuredCharacter.persona),
        )
        val selectedGender = accountStorage.getGender()
        val configuredRelationship = assistantSettingsStorage.getRelationship()
        val user = User(
            username = accountStorage.getUserName().takeIf(String::isNotBlank),
            gender = selectedGender.label.takeUnless { selectedGender == Gender.UNSPECIFIED },
        )
        return Instructions(
            character = character,
            user = user,
            language = activeLanguage.label,
            relationship = "${configuredRelationship.title}: ${configuredRelationship.description}",
            moodLabels = assistantMoods,
        ).build()
    }

    /** Reload the cloud-backed assistant language when returning from settings. */
    fun refreshAssistantIdentity() {
        activeLanguage = assistantSettingsStorage.getLanguage()
        val userId = authService.currentUser()?.uid
        _reduceSensitiveContent.value =
            sensitiveContentSettingsStorage.getReduceSensitiveContent(userId)
    }

    /** Reload cloud language and saved API key when returning from settings. */
    fun refreshSettings() {
        refreshAssistantIdentity()
        refreshApiKey()
    }

    /** Reload the saved key after returning from provider settings. */
    fun refreshApiKey() {
        viewModelScope.launch {
            try {
                reloadProviderSettings(syncCloud = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Failed to refresh saved provider settings", e)
            }
        }
    }

    private fun loadLocalProviderSettings(userId: String?) {
        activeProvider = apiKeyStorage.getSelectedProvider(userId)
        val configuredGeminiModelId =
            if (activeProvider == AssistantProvider.GEMINI) {
                apiKeyStorage.getGeminiModelId(userId)
            } else {
                null
            }
        val configuredOpenRouterModelId =
            if (activeProvider == AssistantProvider.OPENROUTER) {
                apiKeyStorage.getOpenRouterModelId(userId)
            } else {
                null
            }
        val configuredCustomModelId =
            if (activeProvider == AssistantProvider.CUSTOM) {
                apiKeyStorage.getCustomProviderModelId()
            } else {
                null
            }
        activeProviderBaseUrl = when (activeProvider.info.baseUrlPolicy) {
            AssistantProviderBaseUrlPolicy.NONE,
            AssistantProviderBaseUrlPolicy.FIXED -> activeProvider.info.defaultBaseUrl
            AssistantProviderBaseUrlPolicy.OPTIONAL_OVERRIDE -> when (activeProvider) {
                AssistantProvider.OPENROUTER -> apiKeyStorage.getOpenRouterBaseUrl(userId)
                else -> error("Base URL storage is not configured for ${activeProvider.displayName}.")
            }
            AssistantProviderBaseUrlPolicy.REQUIRED -> when (activeProvider) {
                AssistantProvider.CUSTOM -> apiKeyStorage.getCustomProviderBaseUrl()
                else -> error("Base URL storage is not configured for ${activeProvider.displayName}.")
            }
        }
        activeModelId = resolveAssistantModelId(
            activeProvider,
            configuredOpenRouterModelId,
            configuredCustomModelId,
            configuredGeminiModelId,
        )
        val savedKey = userId
            ?.let { apiKeyStorage.getApiKey(activeProvider.name, it) }
            .orEmpty()
            .trim()
        activeApiKey = savedKey.takeIf { it.isNotEmpty() }
    }

    /**
     * The cloud (`user_provider_keys`) is the source of truth for API keys:
     * pull keys saved on other devices into the local cache, then reload.
     */
    private suspend fun reloadProviderSettings(syncCloud: Boolean): String? {
        authService.awaitInitialization()
        val userId = authService.currentUser()?.uid
        loadLocalProviderSettings(userId)
        if (userId != null && (syncCloud || activeApiKey.isNullOrBlank())) {
            pullCloudApiKeys(userId)
            loadLocalProviderSettings(userId)
        }
        return userId
    }

    private suspend fun pullCloudApiKeys(userId: String) {
        val cloudKeys = apiKeySync.fetchAll()
        if (authService.currentUser()?.uid != userId) return
        for ((provider, key) in cloudKeys) {
            if (apiKeyStorage.getApiKey(provider.name, userId) != key) {
                apiKeyStorage.saveApiKey(provider.name, userId, key)
            }
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

    fun setReduceSensitiveContent(enabled: Boolean) {
        val userId = authService.currentUser()?.uid
        sensitiveContentSettingsStorage.setReduceSensitiveContent(userId, enabled)
        _reduceSensitiveContent.value = enabled
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

    /** Releases API resources if the process-scoped owner explicitly disposes this ViewModel. */
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
        if (_busyStatus.value != null) return

        val userId = nextMessageId()
        _messages.update { it + UiMessage(userId, message, isUser = true, imageUri = imageUri) }

        val assistantId = nextMessageId()
        if (streaming) _messages.update { it + UiMessage(assistantId, "", isUser = false) }

        _busyStatus.value = "Thinking..."
        val requestGeneration = chatGeneration
        currentJob = viewModelScope.launch {
            var provider = activeProvider
            try {
                val accountUserId = reloadProviderSettings(syncCloud = false)
                provider = activeProvider
                val modelId = activeModelId
                val apiKey = activeApiKey
                val reasoningLevel = assistantSettingsStorage.getThinkingMode().toReasoningLevel()
                if (apiKey.isNullOrBlank()) {
                    val message = when {
                        accountUserId == null ->
                            "Sign in to use your saved ${provider.displayName} API key."
                        else ->
                            "Add a ${provider.displayName} API key in Settings → Providers & API Keys → API Keys."
                    }
                    throw IllegalStateException(message)
                }
                if (modelId.isBlank()) {
                    throw IllegalStateException(
                        "Set an OpenRouter model ID in Settings → Providers & API Keys → OpenRouter model."
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
                collectAgentRun(
                    AgentRunContext(
                        assistantId = assistantId,
                        requestHistory = requestHistory,
                        provider = provider,
                        modelId = modelId,
                        providerBaseUrl = activeProviderBaseUrl,
                        apiKey = apiKey,
                        reasoningLevel = reasoningLevel,
                        streaming = streaming,
                        requestGeneration = requestGeneration,
                        attachedImage = attachedImage,
                    )
                )
            } catch (e: CancellationException) {
                if (streaming) markCancelled(assistantId)
                throw e
            } catch (t: Exception) {
                if (requestGeneration == chatGeneration) showError(assistantId, streaming, t, provider)
            } finally {
                if (requestGeneration == chatGeneration) {
                    _busyStatus.value = null
                    currentJob = null
                }
            }
        }
    }

    private suspend fun collectAgentRun(context: AgentRunContext) {
        var finishedMessage: AgentMessage? = null
        val completedSpecialists = AtomicInteger()
        var delegatedSpecialists = 0
        val requestHistory = context.requestHistory
        val sensitiveActionsEnabled = assistantSettingsStorage.getSensitiveToolAccessEnabled()

        val toolDispatcher = ToolCallDispatcher(
            context = appContext,
            sensitiveActionsEnabled = sensitiveActionsEnabled,
            executeRegisteredTool = toolExecutor,
            executeSpecialists = { call ->
                val delegation = AgentOrchestrator.parseRequest(call.args)
                if (!AgentOrchestrator.canReserveSpecialists(
                        alreadyUsed = delegatedSpecialists,
                        requested = delegation.tasks.size,
                    )
                ) {
                    AssistantToolResult(
                        buildJsonObject {
                            put(
                                "error",
                                "This assistant run has reached its limit of " +
                                        "${AgentOrchestrator.MAX_SPECIALISTS_PER_RUN} specialist tasks.",
                            )
                        }
                    )
                } else {
                    delegatedSpecialists += delegation.tasks.size
                    val result = assistantApi.runSpecialists(
                        apiKey = context.apiKey,
                        messages = requestHistory,
                        tasks = delegation,
                        provider = context.provider,
                        modelId = context.modelId,
                        reasoningLevel = context.reasoningLevel,
                        providerBaseUrl = context.providerBaseUrl,
                        onProgress = { progress: DelegationProgress ->
                            if (context.requestGeneration == chatGeneration) {
                                val completed = if (progress.completed) {
                                    completedSpecialists.incrementAndGet()
                                } else {
                                    completedSpecialists.get()
                                }
                                val verb = when {
                                    !progress.completed -> "working"
                                    progress.error -> "failed"
                                    else -> "finished"
                                }
                                _busyStatus.value =
                                    "${progress.role.displayName} $verb " +
                                            "($completed/${delegation.tasks.size})"
                            }
                        },
                    )
                    AssistantToolResult(result)
                }
            },
        )
        val executeTool = toolDispatcher::execute

        assistantApi.streamChat(
            apiKey = context.apiKey,
            messages = requestHistory,
            systemPrompt = buildInstructions().withSensitiveToolAccessNotice(
                enabled = sensitiveActionsEnabled,
            ),
            toolDefinitions = CommandExecution.definitions,
            executeTool = executeTool,
            streaming = context.streaming,
            provider = context.provider,
            modelId = context.modelId,
            reasoningLevel = context.reasoningLevel,
            providerBaseUrl = context.providerBaseUrl,
            sensitiveActionsEnabled = sensitiveActionsEnabled,
        ).collect { event ->
            val assistantMessage = when (event) {
                is AgentEvent.MessageUpdated -> event.message
                is AgentEvent.Finished -> event.message
                is AgentEvent.Retrying -> null
            } ?: return@collect

            if (event is AgentEvent.Finished) {
                finishedMessage = assistantMessage
            }
            val decoded = decodeAssistantResponse(
                raw = assistantMessage.toText(),
                isFinal = event is AgentEvent.Finished,
            )
            if (event is AgentEvent.Finished &&
                event.reason == AgentFinishReason.Completed &&
                context.requestGeneration == chatGeneration
            ) {
                decoded?.mood?.let { mood ->
                    sessionMoods += mood
                    _currentSessionMood.value = mostFrequentAssistantMood(sessionMoods)
                }
            }

            assistantMessage.getTools().forEach { tool ->
                val output = tool.output.filterIsInstance<AgentPart.Text>()
                    .firstOrNull()?.text.orEmpty()
                val response = runCatching { Json.parseToJsonElement(output).jsonObject }.getOrNull()
                val toolError = response?.get("error")?.jsonPrimitive?.contentOrNull
                if (!toolError.isNullOrBlank() && context.requestGeneration == chatGeneration) {
                    _busyStatus.value = "Tool ${tool.toolName} failed: $toolError"
                }
                val imageUri = response?.get("image_uri")?.jsonPrimitive?.contentOrNull
                if (imageUri != null) {
                    if (tool.toolName == "generate_image") {
                        context.generatedImageUri = imageUri
                        context.generatedImageSavedTo = response["saved_to"]?.jsonPrimitive?.contentOrNull
                    } else if (tool.toolName == "capture_screen" ||
                        tool.toolName == "perform_screen_action"
                    ) {
                        context.generatedImageUri = imageUri
                        context.generatedImageSavedTo = null
                    }
                }
                if (!tool.isExecuted && context.requestGeneration == chatGeneration) {
                    _busyStatus.value = "Running tool: ${tool.toolName}..."
                }
            }

            if (context.requestGeneration == chatGeneration) {
                renderAgentMessage(
                    context = context,
                    message = assistantMessage,
                    isFinal = event is AgentEvent.Finished,
                    stepLimitReached = event is AgentEvent.Finished &&
                        event.reason == AgentFinishReason.StepLimitReached,
                )
            }
        }

        val finalMessage = finishedMessage ?: return
        if (context.requestGeneration != chatGeneration) return
        modelHistory.clear()
        modelHistory += compactConversationHistory(context.requestHistory + finalMessage)
    }

    private fun renderAgentMessage(
        context: AgentRunContext,
        message: AgentMessage,
        isFinal: Boolean,
        stepLimitReached: Boolean,
    ) {
        val decoded = decodeAssistantResponse(message.toText(), isFinal)
        _messages.update { current ->
            var found = false
            val updated = current.map { item ->
                if (item.id != context.assistantId) {
                    item
                } else {
                    found = true
                    item.copy(
                        text = assistantResponseText(decoded?.response, item.text, stepLimitReached),
                        imageUri = context.generatedImageUri ?: item.imageUri,
                        imageSavedTo = context.generatedImageSavedTo ?: item.imageSavedTo,
                        toolCalls = message.getTools().toUiToolCalls(),
                    )
                }
            }
            if (found) updated else updated + UiMessage(
                id = context.assistantId,
                text = assistantResponseText(decoded?.response, "", stepLimitReached),
                isUser = false,
                imageUri = context.generatedImageUri,
                imageSavedTo = context.generatedImageSavedTo,
                toolCalls = message.getTools().toUiToolCalls(),
            )
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

    private fun nextMessageId(): Long = nextId++

    private fun friendlyMessage(t: Throwable, provider: AssistantProvider): String =
        assistantErrorText(t, provider)
}

package com.naira.assistant.presentation.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naira.assistant.ai.AgentOrchestrator
import com.naira.assistant.ai.DELEGATE_TOOL_NAME
import com.naira.assistant.ai.DelegationProgress
import com.naira.assistant.ai.AssistantApiService
import com.naira.assistant.core.prompt.Instructions
import com.naira.assistant.core.prompt.InstructionsConfig
import com.naira.assistant.core.service.image.InlineImage
import com.naira.assistant.core.service.SupabaseAuthService
import com.naira.assistant.core.sync.SupabaseApiKeyStore
import com.naira.assistant.core.tools.executor.BuiltinToolExecutor
import com.naira.assistant.core.tools.executor.ImageToolExecutor
import com.naira.assistant.core.tools.executor.ScreenToolExecutor
import com.naira.assistant.domain.ApiKeyStorage
import com.naira.assistant.domain.AssistantProvider
import com.naira.assistant.domain.SensitiveContentSettingsStorage
import com.naira.assistant.domain.AccountSettingsStorage
import com.naira.assistant.domain.AssistantLanguage
import com.naira.assistant.domain.AssistantSettingsStorage
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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.agent.AgentEvent
import me.rerere.ai.agent.AgentFinishReason
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage as AgentMessage
import me.rerere.ai.ui.UIMessagePart as AgentPart
import me.rerere.ai.ui.ToolApprovalState
import com.naira.assistant.domain.FunctionCall
import androidx.core.net.toUri
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
    val approvalPending: Boolean = false,
)

private data class AgentRunContext(
    val assistantId: Long,
    val requestHistory: List<AgentMessage>,
    val provider: AssistantProvider,
    val modelId: String,
    val apiKey: String,
    val streaming: Boolean,
    val requestGeneration: Long,
    val attachedImage: InlineImage.Loaded?,
    var generatedImageUri: String? = null,
    var generatedImageSavedTo: String? = null,
)

private data class PendingAgentRun(
    val context: AgentRunContext,
    val assistantMessage: AgentMessage,
)

private val toolDisplayJson = Json { prettyPrint = true }

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
    UiToolCall(
        id = tool.toolCallId,
        name = tool.toolName,
        input = tool.input.toPrettyJsonOrNull() ?: tool.input,
        isRunning = !tool.isExecuted && tool.approvalState !is ToolApprovalState.Pending,
        isError = parsed?.containsKey("error") == true || specialistFailure,
        result = when {
            rawResult.isBlank() -> ""
            else -> rawResult.toPrettyJsonOrNull() ?: rawResult
        },
        approvalPending = tool.approvalState is ToolApprovalState.Pending,
    )
}

internal fun resolveAssistantModelId(
    provider: AssistantProvider,
    configuredOpenRouterModelId: String?,
): String = when (provider) {
    AssistantProvider.GEMINI -> AssistantApiService.DEFAULT_MODEL_ID
    AssistantProvider.OPENROUTER -> configuredOpenRouterModelId
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: AssistantApiService.DEFAULT_MODEL_OPENROUTER
    AssistantProvider.GROQ -> provider.defaultModelId
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
    private val authService = SupabaseAuthService()
    private val apiKeySync = SupabaseApiKeyStore()
    private val modelHistory = mutableListOf<AgentMessage>()
    private val accountStorage = AccountSettingsStorage(appContext)
    private val assistantSettingsStorage = AssistantSettingsStorage(appContext)
    private var activeLanguage: AssistantLanguage = assistantSettingsStorage.getLanguage()

    /** Tool executor with access to the Android context (needed for device/battery info). */
    private val toolExecutor = BuiltinToolExecutor.executor(appContext)

    private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> = _messages.asStateFlow()

    /** Null = idle; non-null = a one-line "what is happening right now" status. */
    private val _busyStatus = MutableStateFlow<String?>(null)
    val busyStatus: StateFlow<String?> = _busyStatus.asStateFlow()

    private val _awaitingApproval = MutableStateFlow(false)
    val awaitingApproval: StateFlow<Boolean> = _awaitingApproval.asStateFlow()

    private val sessionMoods = mutableListOf<String>()
    private val _currentSessionMood = MutableStateFlow<String?>(null)
    val currentSessionMood: StateFlow<String?> = _currentSessionMood.asStateFlow()

    private val _reduceSensitiveContent = MutableStateFlow(
        sensitiveContentSettingsStorage.getReduceSensitiveContent(authService.currentUser()?.id)
    )

    private var currentJob: Job? = null
    private var pendingAgentRun: PendingAgentRun? = null
    private var nextId = 0L
    private var chatGeneration = 0L
    private var activeApiKey: String? = null
    private var activeProvider = AssistantProvider.GEMINI
    private var activeModelId = AssistantApiService.DEFAULT_MODEL_ID

    init { refreshSettings() }

    private fun buildInstructions(): String {
        val character = assistantSettingsStorage.getCharacter()
        val defaultPersona = InstructionsConfig().persona
        val base = Instructions(
            InstructionsConfig(
                assistantName = assistantSettingsStorage.getName(),
                persona = character.instructions.ifBlank { defaultPersona },
                sex = character.gender.ifBlank { "female" },
                language = activeLanguage.code,
                relationship = assistantSettingsStorage.getRelationship().id,
                username = accountStorage.getDisplayName().ifBlank { accountStorage.getUserName() },
                userContext = "",
            )
        ).build()
        return """
            $base

            ## Emotion Label and Response Format
            For every final answer, return exactly one valid JSON object with exactly two string
            fields, no extra keys, and no markdown or text outside the JSON:
            {"response":"the complete answer for the user","mood":"Happy"}
            Put only the concise, necessary user-facing reply in response; this exact text appears
            in chat and is spoken aloud. Set mood to the single best matching emotion or explicitly
            established fictional state of your virtual companion in this moment—not a diagnosis
            or guess about the user's inner state. Use physical-state labels only for the character
            in an established fictional scene. Choose one label from this vocabulary:
            ${assistantMoods.joinToString(", ")}. Choose the closest fit when needed. Mood is only
            the label: never add explanations, details, or punctuation to it. Do not mention JSON
            or these instructions.
        """.trimIndent()
    }

    /** Reload the cloud-backed assistant language when returning from settings. */
    fun refreshAssistantIdentity() {
        activeLanguage = assistantSettingsStorage.getLanguage()
        val userId = authService.currentUser()?.id
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
        val accountUserId = authService.currentUser()?.id
        loadLocalProviderSettings(accountUserId)
        pullCloudApiKeys()
    }

    private fun loadLocalProviderSettings(userId: String?) {
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

    fun setReduceSensitiveContent(enabled: Boolean) {
        val userId = authService.currentUser()?.id
        sensitiveContentSettingsStorage.setReduceSensitiveContent(userId, enabled)
        _reduceSensitiveContent.value = enabled
    }

    /** Wipes the conversation and the Gemini turn history. */
    fun newChat() {
        chatGeneration++
        currentJob?.cancel()
        pendingAgentRun = null
        _awaitingApproval.value = false
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
        pendingAgentRun = null
        _awaitingApproval.value = false
    }

    override fun onCleared() {
        assistantApi.close()
    }

    // ----------------------------------------------------------------- internals

    private fun runChat(message: String, streaming: Boolean, imageUri: String? = null) {
        if (message.isBlank() && imageUri == null) return
        if (_busyStatus.value != null || pendingAgentRun != null) return

        val accountUserId = authService.currentUser()?.id
        loadLocalProviderSettings(accountUserId)
        val userId = nextMessageId()
        _messages.update { it + UiMessage(userId, message, isUser = true, imageUri = imageUri) }

        val assistantId = nextMessageId()
        if (streaming) _messages.update { it + UiMessage(assistantId, "", isUser = false) }

        _busyStatus.value = "Thinking..."
        val requestGeneration = chatGeneration
        val provider = activeProvider
        val modelId = activeModelId
        val apiKey = activeApiKey
        currentJob = viewModelScope.launch {
            try {
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
                        apiKey = apiKey,
                        streaming = streaming,
                        requestGeneration = requestGeneration,
                        attachedImage = attachedImage,
                    )
                )
            } catch (e: CancellationException) {
                if (streaming) markCancelled(assistantId)
                restoreInterruptedApproval(assistantId)
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

    fun resolveToolApproval(assistantId: Long, toolCallId: String, approved: Boolean) {
        if (_busyStatus.value != null) return
        val pending = pendingAgentRun ?: return
        if (pending.context.assistantId != assistantId) return

        var changed = false
        val updated = pending.assistantMessage.copy(
            parts = pending.assistantMessage.parts.map { part ->
                if (part is AgentPart.Tool &&
                    part.toolCallId == toolCallId &&
                    part.approvalState is ToolApprovalState.Pending
                ) {
                    changed = true
                    part.copy(
                        approvalState = if (approved) {
                            ToolApprovalState.Approved
                        } else {
                            ToolApprovalState.Denied("Denied by user")
                        }
                    )
                } else {
                    part
                }
            }
        )
        if (!changed) return

        pendingAgentRun = pending.copy(assistantMessage = updated)
        _awaitingApproval.value = false
        renderAgentMessage(pending.context, updated, isFinal = false)
        _busyStatus.value = if (approved) "Resuming approved action..." else "Applying your decision..."
        currentJob = viewModelScope.launch {
            try {
                collectAgentRun(pending.context, updated)
            } catch (e: CancellationException) {
                markCancelled(assistantId)
                restoreInterruptedApproval(assistantId)
                throw e
            } catch (t: Exception) {
                restoreInterruptedApproval(assistantId)
                showError(assistantId, pending.context.streaming, t, pending.context.provider)
            } finally {
                if (pending.context.requestGeneration == chatGeneration) {
                    _busyStatus.value = null
                    currentJob = null
                }
            }
        }
    }

    private suspend fun collectAgentRun(
        context: AgentRunContext,
        resumeMessage: AgentMessage? = null,
    ) {
        var finishedMessage: AgentMessage? = null
        var finishedReason: AgentFinishReason? = null
        val completedSpecialists = AtomicInteger()
        var delegatedSpecialists = 0
        val requestHistory = context.requestHistory
        if (resumeMessage != null) pendingAgentRun = PendingAgentRun(context, resumeMessage)

        val executeTool: suspend (FunctionCall) -> com.naira.assistant.ai.AssistantToolResult = { call ->
            if (call.name == DELEGATE_TOOL_NAME) {
                val delegation = AgentOrchestrator.parseRequest(call.args)
                if (!AgentOrchestrator.canReserveSpecialists(
                        alreadyUsed = delegatedSpecialists,
                        requested = delegation.tasks.size,
                    )
                ) {
                    com.naira.assistant.ai.AssistantToolResult(
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
                    com.naira.assistant.ai.AssistantToolResult(result)
                }
            } else {
                when (call.name) {
                    "generate_image" -> com.naira.assistant.ai.AssistantToolResult(
                        response = ImageToolExecutor.executeGenerateImage(
                            call,
                            appContext,
                            context.attachedImage,
                        ),
                    )
                    "capture_screen" -> {
                        val response = ScreenToolExecutor.executeCaptureScreen(call, appContext)
                        com.naira.assistant.ai.AssistantToolResult(
                            response = response,
                            image = ScreenToolExecutor.consumeLastCapture(),
                        )
                    }
                    "perform_screen_action" -> {
                        val response = ScreenToolExecutor.executePerformScreenAction(call, appContext)
                        com.naira.assistant.ai.AssistantToolResult(
                            response = response,
                            image = ScreenToolExecutor.consumeLastCapture(),
                        )
                    }
                    else -> com.naira.assistant.ai.AssistantToolResult(toolExecutor(call))
                }
            }
        }

        assistantApi.streamChat(
            apiKey = context.apiKey,
            messages = requestHistory + listOfNotNull(resumeMessage),
            systemPrompt = buildInstructions(),
            toolDefinitions = BuiltinToolExecutor.definitions,
            executeTool = executeTool,
            streaming = context.streaming,
            provider = context.provider,
            modelId = context.modelId,
        ).collect { event ->
            val assistantMessage = when (event) {
                is AgentEvent.MessageUpdated -> event.message
                is AgentEvent.Finished -> event.message
                is AgentEvent.Retrying -> null
            } ?: return@collect

            if (event is AgentEvent.Finished) {
                finishedMessage = assistantMessage
                finishedReason = event.reason
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
                if (!tool.isExecuted &&
                    tool.approvalState !is ToolApprovalState.Pending &&
                    context.requestGeneration == chatGeneration
                ) {
                    _busyStatus.value = "Running tool: ${tool.toolName}..."
                }
            }

            if (context.requestGeneration == chatGeneration) {
                renderAgentMessage(context, assistantMessage, event is AgentEvent.Finished)
                if (pendingAgentRun?.context?.assistantId == context.assistantId) {
                    pendingAgentRun = PendingAgentRun(context, assistantMessage)
                }
            }
        }

        val finalMessage = finishedMessage ?: return
        if (context.requestGeneration != chatGeneration) return
        if (finishedReason is AgentFinishReason.AwaitingApproval) {
            pendingAgentRun = PendingAgentRun(context, finalMessage)
            _awaitingApproval.value = true
        } else {
            pendingAgentRun = null
            _awaitingApproval.value = false
            modelHistory.clear()
            modelHistory += context.requestHistory
            modelHistory += finalMessage
        }
    }

    private fun renderAgentMessage(
        context: AgentRunContext,
        message: AgentMessage,
        isFinal: Boolean,
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
                        text = decoded?.response ?: item.text,
                        imageUri = context.generatedImageUri ?: item.imageUri,
                        imageSavedTo = context.generatedImageSavedTo ?: item.imageSavedTo,
                        toolCalls = message.getTools().toUiToolCalls(),
                    )
                }
            }
            if (found) updated else updated + UiMessage(
                id = context.assistantId,
                text = decoded?.response.orEmpty(),
                isUser = false,
                imageUri = context.generatedImageUri,
                imageSavedTo = context.generatedImageSavedTo,
                toolCalls = message.getTools().toUiToolCalls(),
            )
        }
    }

    private fun restoreInterruptedApproval(assistantId: Long) {
        val pending = pendingAgentRun ?: return
        if (pending.context.assistantId != assistantId) return
        val restored = pending.assistantMessage.copy(
            parts = pending.assistantMessage.parts.map { part ->
                if (part is AgentPart.Tool &&
                    !part.isExecuted &&
                    part.approvalState == ToolApprovalState.Approved
                ) {
                    part.copy(
                        approvalState = ToolApprovalState.Pending,
                        startedAt = null,
                        finishedAt = null,
                    )
                } else {
                    part
                }
            }
        )
        pendingAgentRun = PendingAgentRun(pending.context, restored)
        val stillAwaitingApproval = restored.getTools().any {
            it.approvalState is ToolApprovalState.Pending
        }
        if (stillAwaitingApproval) {
            _awaitingApproval.value = true
        } else {
            _awaitingApproval.value = false
            modelHistory.clear()
            modelHistory += pending.context.requestHistory
            modelHistory += restored
            pendingAgentRun = null
        }
        renderAgentMessage(pending.context, restored, isFinal = false)
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
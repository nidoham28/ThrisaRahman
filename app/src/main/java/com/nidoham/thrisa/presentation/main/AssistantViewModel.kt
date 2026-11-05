package com.nidoham.thrisa.presentation.main

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nidoham.thrisa.ai.AssistantApiService
import com.nidoham.thrisa.core.config.API_KEYS
import com.nidoham.thrisa.core.prompt.Instructions
import com.nidoham.thrisa.core.prompt.InstructionsConfig
import com.nidoham.thrisa.core.service.image.InlineImage
import com.nidoham.thrisa.core.tools.executor.BuiltinToolExecutor
import com.nidoham.thrisa.core.tools.executor.ImageToolExecutor
import com.nidoham.thrisa.core.tools.executor.ScreenToolExecutor
import com.nidoham.thrisa.domain.RelationType
import com.nidoham.thrisa.domain.RoleplaySettingsStorage
import com.nidoham.thrisa.domain.AccountSettingsStorage
import com.nidoham.thrisa.domain.AssistantLanguage
import com.nidoham.thrisa.domain.Gender
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

/** One bubble in the chat UI. Kept separate from the AI module's message model. */
data class UiMessage(
    val id: Long,
    val text: String,
    val isUser: Boolean,
    val isError: Boolean = false,
    val imageUri: String? = null,
    val imageSavedTo: String? = null,
)

/**
 * Bridges the AI service and the Compose UI.
 *
 * The system prompt is built from [Instructions] — persona + rules only.
 * Everything runtime-specific (tool definitions, conversation history, the
 * user's message) travels to the service as separate arguments; nothing is
 * duplicated inside the prompt.
 *
 * Existing behaviour preserved:
 *  - the API key comes from the user's saved Gemini provider settings,
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
    private val modelHistory = mutableListOf<AgentMessage>()
    private val roleplayStorage = RoleplaySettingsStorage(appContext)
    private var activeRelationType: RelationType = roleplayStorage.getRelationType()
    private val accountStorage = AccountSettingsStorage(appContext)
    private var activeLanguage: AssistantLanguage = accountStorage.getLanguage()
    private var activeAssistantName: String = accountStorage.getAssistantName()
    private var activeUserName: String = accountStorage.getUserName()
    private var activeUserGender: String =
        accountStorage.getGender().takeIf { it != Gender.PREFER_NOT_TO_SAY }?.label.orEmpty()
    private var systemInstruction = buildInstructions()

    /** Tool executor with access to the Android context (needed for device/battery info). */
    private val toolExecutor = BuiltinToolExecutor.executor(appContext)

    private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> = _messages.asStateFlow()

    /** Null = idle; non-null = a one-line "what is happening right now" status. */
    private val _busyStatus = MutableStateFlow<String?>(null)
    val busyStatus: StateFlow<String?> = _busyStatus.asStateFlow()

    private var currentJob: Job? = null
    private var nextId = 0L
    private var chatGeneration = 0L
    private var activeApiKey: String? = null

    init { refreshSettings() }

    private fun buildInstructions(): String = Instructions(
        InstructionsConfig(
            assistantName = activeAssistantName,
            language = activeLanguage.code,
            relationship = activeRelationType.name, // or a label/description property if your enum has one
            userName = activeUserName,
            userContext = if (activeUserGender.isNotBlank()) "Gender: $activeUserGender" else "",
            voiceMode = false, // wire to your voice setting if you have one
        )
    ).build()

    /** Reload the relationship instructions whenever the user changes the setting. */
    fun refreshRelationType() {
        val current = roleplayStorage.getRelationType()
        if (current != activeRelationType) {
            activeRelationType = current
            systemInstruction = buildInstructions()
        }
    }

    /** Reload the account / assistant-identity instructions when settings change. */
    fun refreshAssistantIdentity() {
        val language = accountStorage.getLanguage()
        val assistantName = accountStorage.getAssistantName()
        val userName = accountStorage.getUserName()
        val userGender = accountStorage.getGender()
            .takeIf { it != Gender.PREFER_NOT_TO_SAY }?.label.orEmpty()
        if (language != activeLanguage || assistantName != activeAssistantName ||
            userName != activeUserName || userGender != activeUserGender
        ) {
            activeLanguage = language
            activeAssistantName = assistantName
            activeUserName = userName
            activeUserGender = userGender
            systemInstruction = buildInstructions()
        }
    }

    /** Reload settings (API key, relationship prompt, identity) when returning from settings. */
    fun refreshSettings() {
        refreshRelationType()
        refreshAssistantIdentity()
        refreshApiKey()
    }

    /** Reload the saved key after returning from provider settings. */
    fun refreshApiKey() {
        val savedKey = API_KEYS(appContext).trim()
        activeApiKey = savedKey.takeIf { it.isNotEmpty() }
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
        var generatedImageUri: String? = null
        var generatedImageSavedTo: String? = null
        currentJob = viewModelScope.launch {
            try {
                val apiKey = activeApiKey
                if (apiKey.isNullOrBlank()) {
                    throw IllegalStateException("Add a Gemini API key in Settings → AI Providers → Credentials.")
                }

                var attachedImage: InlineImage.Loaded? = null
                if (imageUri != null) {
                    attachedImage = withContext(Dispatchers.IO) {
                        InlineImage.load(appContext, Uri.parse(imageUri))
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
                ).collect { event ->
                    val assistantMessage = when (event) {
                        is AgentEvent.MessageUpdated -> event.message
                        is AgentEvent.Finished -> event.message
                        is AgentEvent.Retrying -> null
                    }
                    if (assistantMessage != null) {
                        if (event is AgentEvent.Finished) finishedAssistantMessage = assistantMessage
                        if (streaming) {
                            setText(assistantId, assistantMessage.toText())
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
                                    text = if (streaming) it.text else assistantMessage.toText(),
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
                            assistantId, finishedAssistantMessage?.toText().orEmpty(), isUser = false,
                            imageUri = generatedImageUri,
                            imageSavedTo = generatedImageSavedTo,
                        )
                    }
                }
            } catch (e: CancellationException) {
                if (streaming) markCancelled(assistantId)
            } catch (t: Throwable) {
                if (requestGeneration == chatGeneration) showError(assistantId, streaming, t)
            } finally {
                if (requestGeneration == chatGeneration) {
                    _busyStatus.value = null
                    currentJob = null
                }
            }
        }
    }

    private fun showError(assistantId: Long, streaming: Boolean, t: Throwable) {
        val text = friendlyMessage(t)
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

    private fun friendlyMessage(t: Throwable): String {
        val message = t.message.orEmpty()
        return when {
            message.contains("401") || message.contains("403") ->
                "Authentication failed. Check your Gemini API key.\n$message"
            message.contains("429") || message.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ->
                "Gemini rate limit reached. Try again later.\n$message"
            message.contains("timeout", ignoreCase = true) || t is java.io.IOException ->
                "Network error. Check your connection and try again.\n$message"
            message.isNotBlank() -> message
            else -> "Unexpected error: ${t::class.simpleName}"
        }
    }
}

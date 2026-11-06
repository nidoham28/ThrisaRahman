package com.nidoham.thrisa.ai

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.agent.AgentEvent
import me.rerere.ai.agent.AgentHooks
import me.rerere.ai.agent.StepRequest
import me.rerere.ai.agent.runAgent
import me.rerere.ai.core.Tool as AiTool
import me.rerere.ai.core.InputSchema
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSManager
import me.rerere.tts.provider.TTSProviderSetting
import okhttp3.OkHttpClient
import com.nidoham.thrisa.core.tools.ToolDefinition
import com.nidoham.thrisa.domain.AssistantProvider
import com.nidoham.thrisa.domain.AssistantVoice
import com.nidoham.thrisa.domain.FunctionCall
import me.rerere.ai.agent.AgentRequest
import okhttp3.Request

data class AssistantToolResult(
    val response: JsonObject,
    val image: ByteArray? = null,
    val imageMimeType: String = "image/jpeg",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AssistantToolResult

        if (response != other.response) return false
        if (!image.contentEquals(other.image)) return false
        if (imageMimeType != other.imageMimeType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = response.hashCode()
        result = 31 * result + (image?.contentHashCode() ?: 0)
        result = 31 * result + imageMimeType.hashCode()
        return result
    }
}

class AssistantApiService(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val httpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(normalizeOpenRouterAuthorization(chain.request()))
        }
        .build()
    private val providerManager by lazy {
        ProviderManager(
            client = httpClient,
            context = appContext,
            accountCredentials = { _, _ ->
                error("Account authentication is not configured for the Gemini assistant")
            },
        )
    }
    private val ttsManager by lazy {
        TTSManager(appContext)
    }

    fun streamChat(
        apiKey: String,
        messages: List<UIMessage>,
        systemPrompt: String,
        toolDefinitions: List<ToolDefinition>,
        executeTool: suspend (FunctionCall) -> AssistantToolResult,
        streaming: Boolean = true,
        provider: AssistantProvider = AssistantProvider.GEMINI,
        modelId: String = provider.defaultModelId,
    ): Flow<AgentEvent> {
        require(apiKey.isNotBlank()) { "${provider.displayName} API key must not be blank" }
        require(modelId.isNotBlank()) { "Model id must not be blank" }

        val setting = createAssistantProviderSetting(provider, apiKey)
        val model = Model(
            modelId = modelId,
            displayName = modelId,
            abilities = listOf(ModelAbility.TOOL),
        )
        val tools = toolDefinitions.map { it.toAgentTool(executeTool) }
        val provider = providerManager.getProviderByType<ProviderSetting>(setting)
        val request = AgentRequest(
            provider = provider,
            providerSetting = setting,
            messages = listOf(UIMessage.system(systemPrompt)) + messages,
            tools = tools,
            stream = streaming,
            maxSteps = MAX_AGENT_STEPS,
        )
        val hooks = object : AgentHooks {
            override suspend fun prepareStep(messages: List<UIMessage>) = StepRequest(
                messages = messages,
                params = TextGenerationParams(model = model),
            )
        }
        return runAgent(request, hooks)
    }

    fun generateSpeech(
        apiKey: String,
        text: String,
        model: String = DEFAULT_TTS_MODEL,
        voiceName: String = AssistantVoice.FEMALE.geminiVoiceName,
    ): Flow<AudioChunk> {
        require(apiKey.isNotBlank()) { "Gemini API key must not be blank" }
        require(text.isNotBlank()) { "Speech text must not be blank" }
        return ttsManager.generateSpeech(
            providerSetting = TTSProviderSetting.Gemini(
                apiKey = apiKey.trim(),
                model = model,
                voiceName = voiceName,
            ),
            request = TTSRequest(text = text),
        )
    }

    override fun close() {
        httpClient.dispatcher.cancelAll()
        httpClient.dispatcher.executorService.execute {
            httpClient.connectionPool.evictAll()
        }
    }

    companion object {
        const val DEFAULT_MODEL_ID = "gemini-3.5-flash-lite"
        const val DEFAULT_MODEL_OPENROUTER = "openrouter/free"
        const val OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"
        const val DEFAULT_TTS_MODEL = "gemini-3.8-flash-tts"
        private const val MAX_AGENT_STEPS = 33
    }
}

/**
 * Cleans an OpenRouter key as the user pasted it: outer quotes, stray spacing
 * and an accidental "Bearer" prefix (a saved "Bearer sk-or-…" becomes a wire
 * header of "Bearer Bearer sk-or-…", which OpenRouter rejects with
 * "Missing Authentication header").
 */
internal fun sanitizeOpenRouterApiKey(raw: String): String {
    var token = raw.trim()
    val bearerPrefix = Regex("^bearer\\s+", RegexOption.IGNORE_CASE)
    while (true) {
        val stripped = when {
            token.length >= 2 &&
                ((token.first() == '"' && token.last() == '"') ||
                    (token.first() == '\'' && token.last() == '\'')) ->
                token.substring(1, token.length - 1).trim()
            else -> bearerPrefix.replace(token, "").trim()
        }
        if (stripped == token) return token
        token = stripped
    }
}

internal fun normalizeOpenRouterAuthorization(request: Request): Request {
    if (request.url.host != "openrouter.ai") return request

    val apiKey = request.headers.values("Authorization")
        .asReversed()
        .firstNotNullOfOrNull { header ->
            if (!header.startsWith("Bearer ", ignoreCase = true)) return@firstNotNullOfOrNull null
            sanitizeOpenRouterApiKey(header.substringAfter(' ')).takeIf(String::isNotEmpty)
        }
        ?: error("OpenRouter API key is missing from the request authorization header.")

    return request.newBuilder()
        .header("Authorization", "Bearer $apiKey")
        .build()
}

internal fun createAssistantProviderSetting(
    provider: AssistantProvider,
    apiKey: String,
): ProviderSetting = when (provider) {
    AssistantProvider.GEMINI -> ProviderSetting.Google(
        apiKey = apiKey.trim(),
        useInteractionsApi = true,
    )
    AssistantProvider.OPENROUTER -> ProviderSetting.OpenAI(
        apiKey = sanitizeOpenRouterApiKey(apiKey),
        baseUrl = AssistantApiService.OPENROUTER_BASE_URL,
        useResponseApi = false,
    )
}

internal fun ToolDefinition.toAgentTool(
    executeTool: suspend (FunctionCall) -> AssistantToolResult,
): AiTool {
    val properties = parameters["properties"] as? JsonObject ?: JsonObject(emptyMap())
    val required = (parameters["required"] as? JsonArray)
        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
    return AiTool(
        name = name,
        description = description,
        parameters = { InputSchema.Obj(properties = properties, required = required) },
        execute = { arguments ->
            val result = executeTool(
                FunctionCall(
                    name = name,
                    args = arguments as? JsonObject ?: JsonObject(emptyMap()),
                ),
            )
            buildList {
                add(UIMessagePart.Text(result.response.toString()))
                result.image?.let { bytes ->
                    add(
                        UIMessagePart.Image(
                            url = "data:${result.imageMimeType};base64," +
                                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP),
                        ),
                    )
                }
            }
        },
    )
}

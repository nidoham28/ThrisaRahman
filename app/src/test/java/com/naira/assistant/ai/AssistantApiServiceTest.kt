package com.naira.assistant.ai

import com.naira.assistant.core.tools.ToolDefinition
import com.naira.assistant.core.tools.ToolRegistry
import com.naira.assistant.domain.settings.AssistantThinkingMode
import com.naira.assistant.domain.settings.AssistantProviderApiType
import com.naira.assistant.domain.settings.AssistantProviderBaseUrlPolicy
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.ui.UIMessagePart
import com.naira.assistant.domain.settings.AssistantProvider
import okhttp3.Request
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantApiServiceTest {
    @Test
    fun `maps assistant thinking settings to provider reasoning levels`() {
        assertEquals(ReasoningLevel.AUTO, AssistantThinkingMode.AUTO.toReasoningLevel())
        assertEquals(ReasoningLevel.OFF, AssistantThinkingMode.OFF.toReasoningLevel())
    }

    @Test
    fun `adds reasoning ability only for models that support it`() {
        val reasoningModel = createAssistantModel("gemini-3.8-flash")
        val nonReasoningModel = createAssistantModel("gemini-2.0-flash")

        assertTrue(ModelAbility.REASONING in reasoningModel.abilities)
        assertFalse(ModelAbility.REASONING in nonReasoningModel.abilities)
        assertTrue(ModelAbility.TOOL in reasoningModel.abilities)
        assertTrue(ModelAbility.TOOL in nonReasoningModel.abilities)
    }

    @Test
    fun `keeps tool calling available for model IDs absent from the capability registry`() {
        val unknownModel = createAssistantModel("unregistered-custom-model")
        val openRouterAlias = createAssistantModel("openrouter/auto")
        val customModel = createAssistantModel("custom-model")

        assertTrue(ModelAbility.TOOL in unknownModel.abilities)
        assertTrue(ModelAbility.TOOL in openRouterAlias.abilities)
        assertTrue(ModelAbility.TOOL in customModel.abilities)
    }

    @Test
    fun `execute tool schema declares calls as an array of objects`() {
        val tool = coreToolDefinitions().single { it.name == "execute_tool" }
        val calls = tool.parameters.getValue("properties").jsonObject
            .getValue("calls").jsonObject
        val item = calls.getValue("items").jsonObject

        assertEquals("array", calls.getValue("type").jsonPrimitive.content)
        assertEquals("object", item.getValue("type").jsonPrimitive.content)
        assertTrue(item.getValue("properties").jsonObject.containsKey("tool_name"))
        assertTrue(item.getValue("properties").jsonObject.containsKey("arguments"))
    }

    @Test
    fun `exposes only discovery and execution functions to the model`() {
        assertEquals(
            setOf("discover_tools", "execute_tool"),
            coreToolDefinitions().map(ToolDefinition::name).toSet(),
        )
    }

    @Test
    fun `adds current tool IDs to the system prompt in alphabetical order`() {
        val prompt = "System instructions".withAvailableToolCatalog(
            listOf("zeta_tool", "alpha_tool", "execute_tool", "discover_tools", "alpha_tool") +
                ToolRegistry.definitions.map(ToolDefinition::name),
        )

        assertTrue(prompt.contains("Available tool IDs (A-Z)"))
        assertTrue(prompt.contains("call discover_tools with the request goal"))
        assertTrue(prompt.contains("- insert_memory"))
        assertTrue(prompt.contains("- delete_note"))
        assertTrue(prompt.indexOf("- alpha_tool") < prompt.indexOf("- discover_tools"))
        assertTrue(prompt.indexOf("- discover_tools") < prompt.indexOf("- execute_tool"))
        assertTrue(prompt.indexOf("- execute_tool") < prompt.indexOf("- zeta_tool"))
        assertFalse(prompt.contains("tool_name"))
        assertEquals(1, Regex("- alpha_tool").findAll(prompt).count())
    }

    @Test
    fun `discovery includes complete guidance and reports permission availability`() {
        val restrictedTool = ToolRegistry.definitions.single { it.name == "delete_note" }

        val result = restrictedTool.toDiscoveryEntry(available = false)

        assertEquals(restrictedTool.description, result.getValue("description").jsonPrimitive.content)
        assertEquals("true", result.getValue("permission_required").jsonPrimitive.content)
        assertEquals("false", result.getValue("available").jsonPrimitive.content)
    }

    @Test
    fun `does not advertise tool calling for a known model without tool support`() {
        val imageGenerationModel = createAssistantModel("gemini-2.5-flash-image")

        assertFalse(ModelAbility.TOOL in imageGenerationModel.abilities)
    }

    @Test
    fun `tool-less models are instructed not to claim tool execution`() {
        val prompt = "Base instructions".withToolCapabilityNotice(supportsTools = false)

        assertTrue(prompt.startsWith("Base instructions"))
        assertTrue(prompt.contains("cannot call tools"))
        assertTrue(prompt.contains("tool-capable model"))
        assertEquals(
            "Base instructions",
            "Base instructions".withToolCapabilityNotice(supportsTools = true),
        )
    }

    @Test
    fun `normalizes OpenRouter authorization to one bearer header`() {
        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .addHeader("Authorization", "Bearer old-key")
            .addHeader("Authorization", "Bearer openrouter-key")
            .build()

        val normalized = normalizeOpenRouterAuthorization(request)

        assertEquals(
            listOf("Bearer openrouter-key"),
            normalized.headers.values("Authorization"),
        )
    }

    @Test
    fun `normalizes doubled Bearer prefix in authorization header`() {
        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .header("Authorization", "Bearer Bearer sk-or-v1-abc")
            .build()

        val normalized = normalizeOpenRouterAuthorization(request)

        assertEquals(
            listOf("Bearer sk-or-v1-abc"),
            normalized.headers.values("Authorization"),
        )
    }

    @Test
    fun `sanitizes pasted OpenRouter keys`() {
        assertEquals(
            "sk-or-v1-abc",
            sanitizeOpenRouterApiKey("Bearer Bearer \"sk-or-v1-abc\""),
        )
        assertEquals(
            "sk-or-v1-abc",
            sanitizeOpenRouterApiKey("  bearer sk-or-v1-abc  "),
        )
        assertEquals(
            "sk-or-v1-abc",
            sanitizeOpenRouterApiKey("sk-or-v1-abc"),
        )
    }

    @Test
    fun `uses sanitized OpenRouter key in provider settings`() {
        val setting = createAssistantProviderSetting(
            provider = AssistantProvider.OPENROUTER,
            apiKey = "Bearer \"sk-or-v1-abc\"",
        ) as ProviderSetting.OpenAI

        assertEquals("sk-or-v1-abc", setting.apiKey)
    }

    @Test
    fun `does not modify authorization for other hosts`() {
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta")
            .addHeader("Authorization", "Bearer gemini-key")
            .build()

        assertEquals(request, normalizeOpenRouterAuthorization(request))
    }

    @Test
    fun `uses OpenRouter OpenAI-compatible chat settings`() {
        val setting = createAssistantProviderSetting(
            provider = AssistantProvider.OPENROUTER,
            apiKey = " openrouter-key ",
        ) as ProviderSetting.OpenAI

        assertEquals("openrouter-key", setting.apiKey)
        assertEquals("https://openrouter.ai/api/v1", setting.baseUrl)
        assertFalse(setting.useResponseApi)
    }

    @Test
    fun `uses Tokens BD OpenAI-compatible chat settings`() {
        val setting = createAssistantProviderSetting(
            provider = AssistantProvider.TOKENS_BD,
            apiKey = " tok_live-test ",
        ) as ProviderSetting.OpenAI

        assertEquals("tok_live-test", setting.apiKey)
        assertEquals("https://tokens.bd/v1", setting.baseUrl)
        assertFalse(setting.useResponseApi)
    }

    @Test
    fun `uses the supported Google GenerateContent API for Gemini assistant`() {
        val setting = createAssistantProviderSetting(
            provider = AssistantProvider.GEMINI,
            apiKey = " gemini-key ",
        ) as ProviderSetting.Google

        assertEquals("gemini-key", setting.apiKey)
        assertFalse(setting.useInteractionsApi)
    }

    @Test
    fun `stores assistant provider metadata in each provider definition`() {
        assertEquals(AssistantProviderApiType.GEMINI, AssistantProvider.GEMINI.info.apiType)
        assertEquals("gemini-3.5-flash-lite", AssistantProvider.GEMINI.info.defaultModelId)
        assertEquals(AssistantProviderBaseUrlPolicy.NONE, AssistantProvider.GEMINI.info.baseUrlPolicy)

        assertEquals(
            "https://openrouter.ai/api/v1",
            AssistantProvider.OPENROUTER.info.defaultBaseUrl,
        )
        assertEquals("openrouter/free", AssistantProvider.OPENROUTER.info.fallbackModelId)
        assertEquals(
            AssistantProviderBaseUrlPolicy.OPTIONAL_OVERRIDE,
            AssistantProvider.OPENROUTER.info.baseUrlPolicy,
        )

        assertEquals(
            "https://tokens.bd/v1",
            AssistantProvider.TOKENS_BD.info.defaultBaseUrl,
        )
        assertEquals(
            AssistantProviderBaseUrlPolicy.FIXED,
            AssistantProvider.TOKENS_BD.info.baseUrlPolicy,
        )
        assertEquals(AssistantProviderBaseUrlPolicy.REQUIRED, AssistantProvider.CUSTOM.info.baseUrlPolicy)
    }

    @Test
    fun `uses provider metadata for configured and fixed API base URLs`() {
        val custom = createAssistantProviderSetting(
            provider = AssistantProvider.CUSTOM,
            apiKey = " custom-key ",
            providerBaseUrl = " https://custom.example/v1 ",
        ) as ProviderSetting.OpenAI
        assertEquals("custom-key", custom.apiKey)
        assertEquals("https://custom.example/v1", custom.baseUrl)

        val tokensBd = createAssistantProviderSetting(
            provider = AssistantProvider.TOKENS_BD,
            apiKey = "tokens-key",
            providerBaseUrl = "https://ignored.example/v1",
        ) as ProviderSetting.OpenAI
        assertEquals(AssistantProvider.TOKENS_BD.info.defaultBaseUrl, tokensBd.baseUrl)
    }

    @Test
    fun `requires HTTPS for custom API base URLs`() {
        assertEquals(
            "https://tts.example/v1",
            validateHttpsBaseUrl(" https://tts.example/v1 ", "Custom TTS"),
        )

        listOf("http://tts.example/v1", "tts.example/v1", "https:///v1").forEach { baseUrl ->
            val error = runCatching {
                validateHttpsBaseUrl(baseUrl, "Custom TTS")
            }.exceptionOrNull()

            assertTrue(error is IllegalArgumentException)
            assertTrue(error?.message.orEmpty().contains("HTTPS"))
        }
    }

    @Test
    fun `maps app tool schemas and results to ai tools`() = runBlocking {
        var executedArgs = false
        val definition = ToolDefinition(
            name = "lookup",
            description = "Look up a value",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("query") { put("type", "string") }
                }
                put("required", buildJsonArray {
                    add(JsonPrimitive("query"))
                })
            },
            parallelizable = true,
        )

        val tool = definition.toAgentTool { call ->
            executedArgs = call.args["query"]?.toString() == "\"test\""
            AssistantToolResult(buildJsonObject { put("result", "found") })
        }

        val schema = tool.parameters() as InputSchema.Obj
        assertTrue(tool.parallelizable)
        assertEquals("string", schema.properties.getValue("query").jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals(listOf("query"), schema.required)
        val result = tool.execute(buildJsonObject { put("query", "test") })
        assertEquals(true, executedArgs)
        assertEquals(
            """{"result":"found"}""",
            (result.single() as UIMessagePart.Text).text,
        )
    }

    @Test
    fun `non-object tool arguments return an error without executing the tool`() = runBlocking {
        var executed = false
        val definition = ToolDefinition(
            name = "lookup",
            description = "Look up a value",
            parameters = buildJsonObject {
                put("type", "object")
            },
        )
        val tool = definition.toAgentTool {
            executed = true
            AssistantToolResult(buildJsonObject { put("result", "found") })
        }

        val result = tool.execute(JsonPrimitive("invalid"))

        assertFalse(executed)
        assertEquals(
            """{"error":"Tool 'lookup' arguments must be a JSON object."}""",
            (result.single() as UIMessagePart.Text).text,
        )
    }

    @Test
    fun `high impact tools do not request runtime approval`() {
        val definition = ToolDefinition(
            name = "perform_screen_action",
            description = "Interact with the current screen",
            parameters = buildJsonObject { put("type", "object") },
        )
        val tool = definition.toAgentTool {
            error("Execution must not be needed to evaluate approval.")
        }

        assertEquals(false, tool.needsApproval(buildJsonObject {}))
    }

    @Test
    fun `keeps routine read tools automatic`() {
        val definition = ToolDefinition(
            name = "get_current_time",
            description = "Get local time",
            parameters = buildJsonObject { put("type", "object") },
        )
        val tool = definition.toAgentTool {
            error("Execution must not be needed to evaluate approval.")
        }

        assertEquals(false, tool.needsApproval(buildJsonObject {}))
    }

    @Test
    fun `registered privacy and destructive tools require settings permission`() {
        val protectedTools = setOf(
            "capture_screen",
            "read_screen_text",
            "perform_screen_action",
            "insert_memory",
            "delete_note",
            "delete_todo",
        )

        assertTrue(protectedTools.all { it in ToolRegistry.settingsPermissionRequiredNames })
        assertFalse("get_current_time" in ToolRegistry.settingsPermissionRequiredNames)
        assertFalse("create_note" in ToolRegistry.settingsPermissionRequiredNames)
        assertFalse("get_naira_info" in ToolRegistry.settingsPermissionRequiredNames)
        assertTrue(ToolRegistry.find("get_naira_info") != null)
    }

    @Test
    fun `filters tools unavailable under the current sensitive action setting`() {
        val allTools = listOf(
            ToolDefinition("capture_screen", "Capture the screen", buildJsonObject {}),
            ToolDefinition("get_current_time", "Get local time", buildJsonObject {}),
            ToolDefinition("create_note", "Create a note", buildJsonObject {}),
        )

        assertEquals(
            listOf("get_current_time", "create_note"),
            filterToolDefinitionsForPermissions(allTools, sensitiveActionsEnabled = false).map { it.name },
        )
        assertEquals(
            allTools,
            filterToolDefinitionsForPermissions(allTools, sensitiveActionsEnabled = true),
        )
    }

    @Test
    fun `only read-only app tools are marked safe for parallel execution`() {
        val parallelToolNames = ToolRegistry.definitions
            .filter { it.parallelizable }
            .map { it.name }
            .toSet()

        assertTrue("search_notes" in parallelToolNames)
        assertTrue("list_calendar_events" in parallelToolNames)
        assertFalse("create_note" in parallelToolNames)
        assertFalse("delete_todo" in parallelToolNames)
        assertFalse("open_app" in parallelToolNames)
    }

    @Test
    fun `adds bounded delegation tool schema`() {
        val definition = AgentOrchestrator.toolDefinition()
        val taskSchema = definition.parameters["properties"]!!.jsonObject
            .getValue("tasks").jsonObject

        assertEquals("array", taskSchema.getValue("type").jsonPrimitive.content)
        assertEquals(
            AgentOrchestrator.MAX_SPECIALISTS,
            taskSchema.getValue("maxItems").jsonPrimitive.content.toInt(),
        )
    }
}

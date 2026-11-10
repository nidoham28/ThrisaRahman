package com.naira.assistant.ai

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.InputSchema
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessagePart
import com.naira.assistant.domain.AssistantProvider
import okhttp3.Request
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantApiServiceTest {
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
    fun `keeps Gemini interactions API settings`() {
        val setting = createAssistantProviderSetting(
            provider = AssistantProvider.GEMINI,
            apiKey = " gemini-key ",
        ) as ProviderSetting.Google

        assertEquals("gemini-key", setting.apiKey)
        assertEquals(true, setting.useInteractionsApi)
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
        )

        val tool = definition.toAgentTool { call ->
            executedArgs = call.args["query"]?.toString() == "\"test\""
            AssistantToolResult(buildJsonObject { put("result", "found") })
        }

        val schema = tool.parameters() as InputSchema.Obj
        assertEquals("string", schema.properties.getValue("query").jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals(listOf("query"), schema.required)
        val result = tool.execute(buildJsonObject { put("query", "test") })
        assertEquals(true, executedArgs)
        assertEquals(
            """{"result":"found"}""",
            (result.single() as UIMessagePart.Text).text,
        )
    }
}

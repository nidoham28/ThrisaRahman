package com.nidoham.thrisa.ai

import com.nidoham.thrisa.core.tools.ToolDefinition
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
import com.nidoham.thrisa.domain.AssistantProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantApiServiceTest {
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

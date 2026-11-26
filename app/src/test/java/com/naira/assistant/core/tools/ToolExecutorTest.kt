package com.naira.assistant.core.tools

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolExecutorTest {
    private val definition = ToolDefinition("sample", "sample", buildJsonObject {
        putJsonObject("properties") {
            putJsonObject("name") { put("type", "string") }
            putJsonObject("count") { put("type", "integer") }
        }
        putJsonArray("required") { add(JsonPrimitive("name")) }
    })

    @Test
    fun `validates missing required and incompatible types`() {
        assertEquals("Missing required argument 'name'.", ToolExecutor.validate(definition, buildJsonObject {}))
        assertEquals("Argument 'count' must be integer.", ToolExecutor.validate(definition, buildJsonObject {
            put("name", "x")
            put("count", "many")
        }))
        assertNull(ToolExecutor.validate(definition, buildJsonObject { put("name", "x"); put("count", 2) }))
    }
}

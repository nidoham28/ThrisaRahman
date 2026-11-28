package com.naira.assistant.core.tools

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolSearchEngineTest {
    private fun tool(name: String, description: String) = ToolDefinition(
        name = name,
        description = description,
        parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") { putJsonObject("query") { put("type", "string") } }
        },
    )

    @Test
    fun `keyword ranking prioritizes relevant tools and respects topK`() {
        val engine = ToolSearchEngine(listOf(
            tool("create_calendar_event", "Create a calendar appointment"),
            tool("search_notes", "Search saved notes"),
            tool("set_timer", "Start a countdown timer"),
        ))

        val results = engine.search("create calendar appointment", topK = 1)

        assertEquals("create_calendar_event", results.single().name)
    }

    @Test
    fun `cosine vectors contribute when provided`() {
        val engine = ToolSearchEngine(listOf(
            tool("alpha", "unrelated").copy(embeddings = listOf(1f, 0f)),
            tool("beta", "also unrelated").copy(embeddings = listOf(0f, 1f)),
        ))

        assertTrue(engine.search("unknown phrase", 1, listOf(0f, 1f)).single().name == "beta")
    }
}

package com.naira.assistant.core.tools

import com.naira.assistant.core.service.cricket.CricApiService
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class CricketToolsTest {

    @Test
    fun `cricket tools are registered with scorecard match id requirement`() {
        val tools = ToolRegistry.definitions
            .filter { it.name.startsWith("get_") && it.name.contains("cricket") }
            .associateBy { it.name }

        assertEquals(
            setOf(
                "get_live_cricket_scores",
                "get_cricket_matches",
                "get_cricket_scorecard",
            ),
            tools.keys,
        )
        assertEquals(
            "match_id",
            tools.getValue("get_cricket_scorecard").parameters["required"]
                ?.jsonArray?.single()?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `parses successful CricAPI response data and metadata`() {
        val result = CricApiService.parseResponse(
            """{"status":"success","data":[{"id":"match-1"}],"info":{"hitsToday":2}}""",
            200,
        )

        assertEquals("success", result["status"]?.jsonPrimitive?.content)
        assertEquals("match-1", result["data"]?.jsonArray?.single()
            ?.jsonObject?.get("id")?.jsonPrimitive?.content)
        assertEquals(2, result["info"]?.jsonObject?.get("hitsToday")?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun `reports CricAPI errors and malformed successful responses`() {
        val apiFailure = assertThrows(IllegalStateException::class.java) {
            CricApiService.parseResponse(
                """{"status":"failure","reason":"Invalid API key"}""",
                200,
            )
        }
        assertEquals("CricAPI request failed: Invalid API key", apiFailure.message)

        val httpFailure = assertThrows(IllegalStateException::class.java) {
            CricApiService.parseResponse(
                """{"status":"failure","message":"Rate limit"}""",
                429,
            )
        }
        assertEquals("CricAPI request failed (HTTP 429). Rate limit", httpFailure.message)

        val keyFailure = assertThrows(IllegalStateException::class.java) {
            CricApiService.parseResponse(
                """{"status":"failure","reason":"Rejected API-SECRET"}""",
                401,
                "API-SECRET",
            )
        }
        assertTrue(keyFailure.message.orEmpty().contains("[redacted]"))
        assertFalse(keyFailure.message.orEmpty().contains("API-SECRET"))

        assertThrows(IllegalStateException::class.java) {
            CricApiService.parseResponse("""{"status":"success"}""", 200)
        }
        assertThrows(IllegalStateException::class.java) {
            CricApiService.parseResponse("not-json", 200)
        }
    }
}

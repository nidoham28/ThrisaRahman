package com.naira.assistant.presentation.main

import com.naira.assistant.domain.AssistantProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantErrorMappingTest {
    @Test
    fun `maps OpenRouter key rejection to actionable guidance`() {
        val text = assistantErrorText(
            RuntimeException("Missing Authentication header"),
            AssistantProvider.OPENROUTER,
        )

        assertTrue(text.startsWith("OpenRouter rejected your API key."))
        assertTrue(text.contains("Settings → AI Providers → Credentials"))
    }

    @Test
    fun `maps unknown well-formed OpenRouter key the same way`() {
        val text = assistantErrorText(
            RuntimeException("User not found."),
            AssistantProvider.OPENROUTER,
        )

        assertTrue(text.startsWith("OpenRouter rejected your API key."))
    }

    @Test
    fun `keeps generic auth failure wording for other providers`() {
        val text = assistantErrorText(
            RuntimeException("Failed to get response: 401 nope"),
            AssistantProvider.GEMINI,
        )

        assertEquals(
            "Authentication failed. Check your Gemini API key.\nFailed to get response: 401 nope",
            text,
        )
    }

    @Test
    fun `passes unmatched provider messages through`() {
        val text = assistantErrorText(
            RuntimeException("Model not found"),
            AssistantProvider.OPENROUTER,
        )

        assertEquals("Model not found", text)
    }
}
